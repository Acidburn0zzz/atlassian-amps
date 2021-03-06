package com.atlassian.maven.plugins.amps;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;

import com.atlassian.maven.plugins.amps.util.ClassUtils;
import com.atlassian.maven.plugins.amps.util.WiredTestInfo;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.wink.client.ClientConfig;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.Resource;
import org.apache.wink.client.RestClient;
import org.apache.wink.client.handlers.BasicAuthSecurityHandler;
import org.apache.wink.client.httpclient.ApacheHttpClientConfig;
import org.apache.wink.common.http.HttpStatus;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import aQute.bnd.osgi.Constants;

@Mojo(name = "remote-test", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
@Execute(phase = LifecyclePhase.PACKAGE)
public class RemoteTestMojo extends AbstractProductHandlerMojo
{
    /**
     * The Jar archiver.
     */
    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    /**
     * The archive configuration to use. See <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
     * Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * Pattern for to use to find integration tests.  Only used if no test groups are defined.
     */
    @Parameter(property = "functional.test.pattern")
    private String functionalTestPattern = "it/**";

    /**
     * The directory containing generated test classes of the project being tested.
     */
    @Parameter(property = "project.build.testOutputDirectory", required = true)
    private File testClassesDirectory;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean testsSkip = false;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests = false;

    /**
     * Skip the integration tests along with any product startups
     */
    @Parameter(property = "skipITs", defaultValue = "false")
    private boolean skipITs = false;

    /**
     * HTTP port for the servlet containers
     */
    @Parameter(property = "http.port", defaultValue = "80")
    protected int httpPort;

    /**
     * Username of user that will install the plugin
     */
    @Parameter(property = "pdk.username", defaultValue = "admin")
    protected String pdkUsername;

    /**
     * Password of user that will install the plugin
     */
    @Parameter(property = "pdk.password", defaultValue = "admin")
    protected String pdkPassword;

    @Parameter
    protected List<ProductArtifact> deployArtifacts = new ArrayList<>();

    /**
     * Denotes test category as defined by surefire/failsafe notion of groups. In JUnit4, this affects tests annotated
     * with {@link org.junit.experimental.categories.Category @Category} annotation.
     */
    @Parameter
    protected String category;

    protected void doExecute() throws MojoExecutionException
    {
        if (StringUtils.isBlank(server))
        {
            getLog().error("server is not set!");
            return;
        }

        if (null == contextPath || StringUtils.trim(contextPath).equals("/"))
        {
            contextPath = "";
        }
        
        if(httpPort < 1)
        {
            httpPort = 80;
        }

        if (!shouldBuildTestPlugin())
        {
            getLog().info("shouldBuildTestPlugin is false... skipping test run!");
            return;
        }

        final MavenProject project = getMavenContext().getProject();

        // workaround for MNG-1682/MNG-2426: force maven to install artifact using the "jar" handler
        project.getArtifact().setArtifactHandler(artifactHandlerManager.getArtifactHandler("jar"));

        if (!new File(testClassesDirectory, "it").exists())
        {
            getLog().info("No integration tests found");
            return;
        }

        if (skipTests || testsSkip || skipITs)
        {
            getLog().info("Integration tests skipped");
            return;
        }

        final MavenGoals goals = getMavenGoals();
        final String pluginJar = targetDirectory.getAbsolutePath() + "/" + finalName + ".jar";

        runTestsForTestGroup(NO_TEST_GROUP, goals, pluginJar, copy(systemPropertyVariables));

    }

    private Map<String, Object> copy(Map<String, Object> systemPropertyVariables)
    {
        return new HashMap<>(systemPropertyVariables);
    }

    private String getBaseUrl(String server, int actualHttpPort, String contextPath)
    {
        String port = actualHttpPort != 80 ? ":" + actualHttpPort : "";
        server = server.startsWith("http") ? server : "http://" + server;
        if (!contextPath.startsWith("/") && StringUtils.isNotBlank(contextPath))
        {
            contextPath = "/" + contextPath;
        }
        return server + port + contextPath;
    }

    private void runTestsForTestGroup(String testGroupId, MavenGoals goals, String pluginJar, Map<String, Object> systemProperties) throws MojoExecutionException
    {
        try
        {
            MavenContext ctx = getMavenContext();
            Build build = ctx.getProject().getBuild();
            File buildDir = new File(build.getDirectory());
            File testClassesDir = new File(build.getTestOutputDirectory());

            List<String> wiredTestClasses = getWiredTestClassnames(testClassesDir);
            
            if(wiredTestClasses.isEmpty())
            {
                getLog().info("No wired integration tests found, skipping remote testing...");
                return;
            }
            
            List<String> includes = new ArrayList<>(wiredTestClasses.size());
            for(String wiredClass : wiredTestClasses)
            {
                String includePath = wiredClass.replaceAll("\\.", "/");
                includes.add(includePath + "*");
            }
            
            List<String> excludes = Collections.emptyList();

            systemProperties.put("http.port", httpPort);
            systemProperties.put("context.path", contextPath);
            systemProperties.put("plugin.jar", pluginJar);

            // yes, this means you only get one base url if multiple products, but that is what selenium would expect
            if (!systemProperties.containsKey("baseurl"))
            {
                systemProperties.put("baseurl", getBaseUrl(server, httpPort, contextPath));
            }

            Map<ProductArtifact,File> frameworkFiles = getFrameworkFiles();
            File junitFile = null;

            //we MUST install junit first!
            for(Map.Entry<ProductArtifact,File> entry : frameworkFiles.entrySet())
            {
                ProductArtifact artifact = entry.getKey();
                File artifactFile = entry.getValue();
                
                if(artifactFile.getName().startsWith("org.apache.servicemix.bundles.junit"))
                {
                    junitFile = artifactFile;
                    frameworkFiles.remove(artifact);
                    break;
                }
            }
            
            File mainPlugin = new File(buildDir, finalName + ".jar");
            File testPlugin = new File(buildDir, finalName + "-tests.jar");

            if(null == junitFile || !junitFile.exists())
            {
                throw new MojoExecutionException("couldn't find junit!!!!");
            }

            installPluginFileIfNotInstalled(junitFile);

            for(Map.Entry<ProductArtifact,File> pluginEntry : frameworkFiles.entrySet())
            {
                installPluginFileIfNotInstalled(pluginEntry.getValue());
            }


            installPluginFile(mainPlugin);
            installPluginFile(testPlugin);

            // Actually run the tests
            goals.runIntegrationTests("group-" + testGroupId, "remote", includes, excludes, systemProperties, targetDirectory, category, false, null);
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error running remote tests...", e);
        }

    }

    private void installPluginFileIfNotInstalled(File pluginFile) throws IOException, MojoExecutionException
    {
        getLog().info("checking to see if we need to install " + pluginFile.getName());
        JarFile jar = new JarFile(pluginFile);
        Manifest mf = jar.getManifest();
        String pluginKey = null;
        String pluginVersion = null;
        
        if(null != mf)
        {
            pluginKey = mf.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
            pluginVersion = mf.getMainAttributes().getValue(Constants.BUNDLE_VERSION);
            getLog().info("pluginKet from manifest is: " + pluginKey);
        }
        else
        {
            getLog().info("no manifext found for plugin!!");
        }
        
        if(StringUtils.isBlank(pluginKey))
        {
            getLog().info("no plugin key found, installing without check...");
            //just install it
            installPluginFile(pluginFile);
        }
        else
        {
            boolean foundPlugin = false;
            
            ClientConfig config = new ApacheHttpClientConfig();
            config.readTimeout(1800000);
            BasicAuthSecurityHandler basicAuthHandler = new BasicAuthSecurityHandler();
            basicAuthHandler.setUserName(pdkUsername);
            basicAuthHandler.setPassword(pdkPassword);
            config.handlers(basicAuthHandler);
            config.followRedirects(true);

            RestClient client = new RestClient(config);
            URI uri = UriBuilder.fromPath(contextPath + "/rest/plugins/1.0/" + pluginKey + "-key")
                    .scheme("http")
                    .host(server)
                    .port(httpPort)
                    .build();

            getLog().info("looking up plugin from: " + uri.toURL().toString());
            Resource resource = client.resource(uri);

            ClientResponse response = resource.get();
            
            if(response.getStatusCode() == HttpStatus.NOT_FOUND.getCode())
            {
                getLog().info("got a 404, trying again with version tacked on...");
                //try again with version tacked on
                uri = UriBuilder.fromPath(contextPath + "/rest/plugins/1.0/" + pluginKey + "-" + pluginVersion + "-key")
                                    .scheme("http")
                                    .host(server)
                                    .port(httpPort)
                                    .build();

                getLog().info("looking up plugin from: " + uri.toURL().toString());
                resource = client.resource(uri);
                response = resource.get();

                if(response.getStatusCode() != HttpStatus.NOT_FOUND.getCode())
                {
                    getLog().info("found the plugin!!");
                    foundPlugin = true;
                }
            }
            else
            {
                getLog().info("found the plugin!!");
                foundPlugin = true;
            }
            
            if(!foundPlugin)
            {
                getLog().info("didn't find the plugin so I'm installing it...");
                installPluginFile(pluginFile);
            }
            else
            {
                //TODO: check version and remove/install if we have an update
            }
        }
    }

    private void installPluginFile(File pluginFile) throws MojoExecutionException
    {
        getLog().info("trying to install plugin with the following properties:");
        getLog().info("pluginFile: " + pluginFile.getAbsolutePath());
        getLog().info("pluginKey: " + pluginFile.getName());
        getLog().info("server: " + server);
        getLog().info("httpPort: " + httpPort);
        getLog().info("contextPath: " + contextPath);
        getLog().info("username: " + pdkUsername);

        getMavenGoals().installPlugin(new PdkParams.Builder()
                .pluginFile(pluginFile.getAbsolutePath())
                .pluginKey(pluginFile.getName())
                .server(server)
                .port(httpPort)
                .contextPath(contextPath)
                .username(pdkUsername)
                .password(pdkPassword)
                .build());
    }

    private List<String> getWiredTestClassnames(File testClassesDir)
    {
        MavenProject prj = getMavenContext().getProject();
        List<String> wiredClasses = new ArrayList<>();
        
        if (testClassesDir.exists())
        {
            Collection<File> classFiles = FileUtils.listFiles(testClassesDir, new String[]{"class"}, true);

            for (File classFile : classFiles)
            {
                String className = ClassUtils.getClassnameFromFile(classFile, prj.getBuild().getTestOutputDirectory());
                WiredTestInfo wiredInfo = ClassUtils.getWiredTestInfo(classFile);
                if (wiredInfo.isWiredTest())
                {
                    wiredClasses.add(className);
                }
            }

        }

        return wiredClasses;
    }

    private Map<ProductArtifact,File> getFrameworkFiles() throws MojoExecutionException
    {
        List<ProductArtifact> pluginsToDeploy = new ArrayList<>(getTestFrameworkPlugins());
        pluginsToDeploy.addAll(deployArtifacts);

        Map<ProductArtifact,File> artifactFileMap = new HashMap<>(pluginsToDeploy.size());
        
        try
        {
            File tmpDir = new File(getMavenContext().getProject().getBuild().getDirectory(), "tmp-artifacts");
            FileUtils.forceMkdir(tmpDir);
            FileUtils.cleanDirectory(tmpDir);

            getMavenGoals().copyPlugins(tmpDir, pluginsToDeploy);

            for(File file : tmpDir.listFiles())
            {
                final File artifactFile = file;
                
                Collection<ProductArtifact> filtered = Collections2.filter(pluginsToDeploy, new Predicate<ProductArtifact>() {
                    @Override
                    public boolean apply(@Nullable ProductArtifact productArtifact)
                    {
                        return artifactFile.getName().startsWith(productArtifact.getArtifactId());
                    }
                });
                
                if(!filtered.isEmpty())
                {
                    artifactFileMap.put(filtered.iterator().next(),artifactFile);
                }
            }
            
            return artifactFileMap;
            
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error copying framework files", e);
        }
    }

}
