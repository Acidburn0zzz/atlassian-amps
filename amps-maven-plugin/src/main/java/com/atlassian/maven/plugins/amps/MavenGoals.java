package com.atlassian.maven.plugins.amps;

import aQute.bnd.osgi.Constants;
import com.atlassian.maven.plugins.amps.product.ImportMethod;
import com.atlassian.maven.plugins.amps.product.jira.JiraDatabase;
import com.atlassian.maven.plugins.amps.product.jira.JiraDatabaseFactory;
import com.atlassian.maven.plugins.amps.util.AmpsCreatePluginPrompter;
import com.atlassian.maven.plugins.amps.util.CreatePluginProperties;
import com.atlassian.maven.plugins.amps.util.PluginXmlUtils;
import com.atlassian.maven.plugins.amps.util.VersionUtils;
import com.atlassian.maven.plugins.amps.util.minifier.MinifierParameters;
import com.atlassian.maven.plugins.amps.util.minifier.ResourcesMinifier;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.googlecode.htmlcompressor.compressor.XmlCompressor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.archetype.common.DefaultPomManager;
import org.apache.maven.archetype.common.MavenJDOMWriter;
import org.apache.maven.archetype.common.util.Format;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.Element;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.regex.Matcher;

import static com.atlassian.maven.plugins.amps.product.jira.JiraDatabaseFactory.getJiraDatabaseFactory;
import static com.atlassian.maven.plugins.amps.util.FileUtils.file;
import static com.atlassian.maven.plugins.amps.util.FileUtils.fixWindowsSlashes;
import static com.atlassian.maven.plugins.amps.util.ProductHandlerUtil.pickFreePort;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Executes specific maven goals
 */
public class MavenGoals
{
    @VisibleForTesting
    static final String AJP_PORT_PROPERTY = "cargo.tomcat.ajp.port";

    @VisibleForTesting
    final Map<String, String> defaultArtifactIdToVersionMap;

    private final MavenContext ctx;
    private final Map<String, Container> idToContainerMap = ImmutableMap.<String, Container>builder()
            .put("tomcat5x", new Container("tomcat5x", "org.apache.tomcat", "apache-tomcat", "5.5.36"))
            .put("tomcat6x", new Container("tomcat6x", "org.apache.tomcat", "apache-tomcat", "6.0.41"))
            .put("tomcat7x", new Container("tomcat7x", "org.apache.tomcat", "apache-tomcat", "7.0.73", "windows-x64"))
            .put("tomcat8x", new Container("tomcat8x", "org.apache.tomcat", "apache-tomcat", "8.0.53-atlassian-hosted", "windows-x64"))
            .put("tomcat85x", new Container("tomcat8x", "org.apache.tomcat", "apache-tomcat", "8.5.33-atlassian-hosted", "windows-x64"))
            .put("tomcat85_6", new Container("tomcat8x", "org.apache.tomcat", "apache-tomcat", "8.5.6-atlassian-hosted", "windows-x64"))
            .put("tomcat9x", new Container("tomcat9x", "org.apache.tomcat", "apache-tomcat", "9.0.11-atlassian-hosted", "windows-x64"))
            .put("jetty6x", new Container("jetty6x"))
            .put("jetty7x", new Container("jetty7x"))
            .put("jetty8x", new Container("jetty8x"))
            .put("jetty9x", new Container("jetty9x"))
            .build();
    private final Log log;

    public MavenGoals(final MavenContext ctx)
    {
        this.ctx = ctx;

        defaultArtifactIdToVersionMap = Collections.unmodifiableMap(getArtifactIdToVersionMap(ctx));
        log = ctx.getLog();
    }

    private Map<String,String> getArtifactIdToVersionMap(MavenContext ctx)
    {
        final Properties overrides = ctx.getVersionOverrides();

        return ImmutableMap.<String, String>builder()
                //overrides.getProperty(JUNIT_ARTIFACT_ID,"
                .put("atlassian-pdk", overrides.getProperty("atlassian-pdk","2.3.3"))
                .put("build-helper-maven-plugin", overrides.getProperty("build-helper-maven-plugin","3.0.0"))
                .put("cargo-maven2-plugin", overrides.getProperty("cargo-maven2-plugin","1.6.10"))
                .put("maven-archetype-plugin", overrides.getProperty("maven-archetype-plugin","3.0.1"))
                .put("maven-bundle-plugin", overrides.getProperty("maven-bundle-plugin","3.5.0"))
                .put("maven-cli-plugin", overrides.getProperty("maven-cli-plugin","1.0.11"))
                .put("maven-dependency-plugin", overrides.getProperty("maven-dependency-plugin","3.1.1"))
                .put("maven-deploy-plugin", overrides.getProperty("maven-deploy-plugin","2.8.2"))
                .put("maven-exec-plugin", overrides.getProperty("maven-exec-plugin","1.2.1"))
                .put("maven-failsafe-plugin", overrides.getProperty("maven-failsafe-plugin","2.22.0"))
                .put("maven-install-plugin", overrides.getProperty("maven-install-plugin","2.5.2"))
                .put("maven-jar-plugin", overrides.getProperty("maven-jar-plugin","3.0.2"))
                .put("maven-javadoc-plugin", overrides.getProperty("maven-javadoc-plugin", "3.0.1"))
                .put("maven-release-plugin", overrides.getProperty("maven-release-plugin", "2.5.3"))
                .put("maven-resources-plugin", overrides.getProperty("maven-resources-plugin","2.6"))
                .put("maven-surefire-plugin", overrides.getProperty("maven-surefire-plugin","2.22.0"))
                .put("sql-maven-plugin", overrides.getProperty("sql-maven-plugin", "1.5"))
                .put("yuicompressor-maven-plugin", overrides.getProperty("yuicompressor-maven-plugin","1.5.1"))
                .build();
    }

    private ExecutionEnvironment executionEnvironment()
    {
        return ctx.getExecutionEnvironment();
    }

    public MavenProject getContextProject()
    {
        return ctx.getProject();
    }

    public void executeAmpsRecursively(final String ampsVersion, final String ampsGoal, Xpp3Dom cfg) throws MojoExecutionException
    {
        executeMojo(
            plugin(
                groupId("com.atlassian.maven.plugins"),
                artifactId("amps-maven-plugin"),
                version(ampsVersion)
            ),
            goal(ampsGoal),
            cfg,
            executionEnvironment());
    }

    public static void warnDeprecated(Log log) {
        log.warn("");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("");
        log.warn(">>>  WARNING: atlas-cli and fastdev are DEPRECATED in favour of QuickReload  <<<");
        log.warn("");
        log.warn(">>>  WARNING: Support for atlas-cli and fastdev will be completely removed in the next AMPS version.  <<<");
        log.warn("");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        log.warn("");
    }

    public void startCli(final PluginInformation pluginInformation, final int port) throws MojoExecutionException
    {
        warnDeprecated(log);
        final String groupId = pluginInformation.getGroupId();
        final String artifactId = pluginInformation.getArtifactId();

        final List<Element> configs = new ArrayList<>();
        configs.add(element(name("commands"),
                element(name("pi"),
                        groupId + ":" + artifactId + ":copy-bundled-dependencies" + " "
                                + groupId + ":" + artifactId + ":compress-resources" + " "
                                + "org.apache.maven.plugins:maven-resources-plugin:resources" + " "
                                + groupId + ":" + artifactId + ":filter-plugin-descriptor" + " "
                                + "compile" + " "
                                + groupId + ":" + artifactId + ":generate-manifest" + " "
                                + groupId + ":" + artifactId + ":validate-manifest" + " "
                                + groupId + ":" + artifactId + ":jar" + " "
                                + "org.apache.maven.plugins:maven-install-plugin:install" + " "
                                + groupId + ":" + artifactId + ":install"),
                element(name("tpi"),
                        groupId + ":" + artifactId + ":copy-bundled-dependencies" + " "
                                + groupId + ":" + artifactId + ":compress-resources" + " "
                                + "org.apache.maven.plugins:maven-resources-plugin:resources" + " "
                                + groupId + ":" + artifactId + ":filter-plugin-descriptor" + " "
                                + "compile" + " "
                                + groupId + ":" + artifactId + ":generate-manifest" + " "
                                + "org.apache.maven.plugins:maven-resources-plugin:testResources" + " "
                                + groupId + ":" + artifactId + ":filter-test-plugin-descriptor" + " "
                                +groupId + ":" + artifactId + ":copy-test-bundled-dependencies" + " "
                                + "org.apache.maven.plugins:maven-compiler-plugin:testCompile" + " "
                                + groupId + ":" + artifactId + ":generate-test-manifest" + " "
                                + groupId + ":" + artifactId + ":validate-manifest" + " "
                                + groupId + ":" + artifactId + ":validate-test-manifest" + " "
                                + groupId + ":" + artifactId + ":jar" + " "
                                + groupId + ":" + artifactId + ":test-jar" + " "
                                + "org.apache.maven.plugins:maven-install-plugin:install" + " "
                                + groupId + ":" + artifactId + ":install" + " "
                                + groupId + ":" + artifactId + ":test-install"),
                element(name("package"),
                        groupId + ":" + artifactId + ":copy-bundled-dependencies" + " "
                                + groupId + ":" + artifactId + ":compress-resources" + " "
                                + "org.apache.maven.plugins:maven-resources-plugin:resources" + " "
                                + groupId + ":" + artifactId + ":filter-plugin-descriptor" + " "
                                + "compile" + " "
                                + groupId + ":" + artifactId + ":generate-manifest" + " "
                                + groupId + ":" + artifactId + ":validate-manifest" + " "
                                + groupId + ":" + artifactId + ":jar" + " ")));
        if (port > 0)
        {
            configs.add(element(name("port"), String.valueOf(port)));
        }
        executeMojo(
                plugin(
                        groupId("org.twdata.maven"),
                        artifactId("maven-cli-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-cli-plugin"))
                ),
                goal("execute"),
                configuration(configs.toArray(new Element[0])),
                executionEnvironment());
    }

    public void createPlugin(final String productId, AmpsCreatePluginPrompter createPrompter) throws MojoExecutionException
    {
        CreatePluginProperties props = null;
        Properties systemProps = System.getProperties();

        if (systemProps.containsKey("groupId")
                && systemProps.containsKey("artifactId")
                && systemProps.containsKey("version")
                && systemProps.containsKey("package"))
        {
            props = new CreatePluginProperties(systemProps.getProperty("groupId"),
                    systemProps.getProperty("artifactId"), systemProps.getProperty("version"),
                    systemProps.getProperty("package"));
        }
        if (null == props)
        {
            try
            {
                props = createPrompter.prompt();
            }
            catch (PrompterException e)
            {
                throw new MojoExecutionException("Unable to gather properties",e);
            }
        }

        if (null != props)
        {
            ExecutionEnvironment execEnv = executionEnvironment();

            Properties sysProps = execEnv.getMavenSession().getExecutionProperties();
            sysProps.setProperty("groupId",props.getGroupId());
            sysProps.setProperty("artifactId",props.getArtifactId());
            sysProps.setProperty("version",props.getVersion());
            sysProps.setProperty("package",props.getThePackage());

            executeMojo(
                    plugin(
                            groupId("org.apache.maven.plugins"),
                            artifactId("maven-archetype-plugin"),
                            version(defaultArtifactIdToVersionMap.get("maven-archetype-plugin"))
                    ),
                    goal("generate"),
                    configuration(
                            element(name("archetypeGroupId"), "com.atlassian.maven.archetypes"),
                            element(name("archetypeArtifactId"), (productId.equals("all") ? "" : productId + "-") + "plugin-archetype"),
                            element(name("archetypeVersion"), VersionUtils.getVersion()),
                            element(name("interactiveMode"), "false")
                    ),
                    execEnv);

            /*
            The problem is if plugin is sub of multiple module project, then the pom file will be add parent section.
            When add parent section to module pom file, maven use the default Format with lineEnding is \r\n.
            This step add \r\n character as the line ending.
            Call the function below to remove cr (\r) character
            */
            correctCrlf(props.getArtifactId());

            File pluginDir = new File(ctx.getProject().getBasedir(),props.getArtifactId());

            if (pluginDir.exists())
            {
                File src = new File(pluginDir,"src");
                File test = new File(src,"test");
                File java = new File(test,"java");

                String packagePath = props.getThePackage().replaceAll("\\.", Matcher.quoteReplacement(File.separator));
                File packageFile = new File(java,packagePath);
                File packageUT = new File(packageFile,"ut");
                File packageIT = new File(packageFile,"it");

                File ut = new File(new File(java,"ut"),packagePath);
                File it = new File(new File(java,"it"),packagePath);

                if (packageFile.exists())
                {
                    try
                    {
                        if (packageUT.exists())
                        {
                            FileUtils.copyDirectory(packageUT, ut);
                        }

                        if (packageIT.exists())
                        {
                            FileUtils.copyDirectory(packageIT, it);
                        }

                        IOFileFilter filter = FileFilterUtils.and(FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter("it")),FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter("ut")));

                        com.atlassian.maven.plugins.amps.util.FileUtils.cleanDirectory(java,filter);

                    }
                    catch (IOException e)
                    {
                        //for now just ignore
                    }
                }
            }
        }
    }

    /**
     * Helper function to scan all generated folder and list all pom.xml files that need to be re-write to remove \r
     */
    private void correctCrlf(String artifactId)
    {
        if (null != ctx && null != ctx.getProject()
            && null != ctx.getProject().getBasedir() && ctx.getProject().getBasedir().exists()) {
            File outputDirectoryFile = new File(ctx.getProject().getBasedir(), artifactId);

            if (outputDirectoryFile.exists()) {
                FilenameFilter pomFilter = (dir, name) -> "pom.xml".equals(name);

                File[] pomFiles = outputDirectoryFile.listFiles(pomFilter);
                DefaultPomManager pomManager = new DefaultPomManager();

                for (File pom : pomFiles) {
                    processCorrectCrlf(pomManager, pom);
                }
            }
        }
    }

    /**
     * Helper function to re-write pom.xml file with lineEnding \n instead of \r\n
     */
    protected void processCorrectCrlf(DefaultPomManager pomManager, File pom)
    {
        InputStream inputStream = null;
        Writer outputStreamWriter = null;
        final Model model;
        try {
            model = pomManager.readPom(pom);
            String fileEncoding = StringUtils.isEmpty(model.getModelEncoding()) ? model.getModelEncoding() : "UTF-8";

            inputStream = new FileInputStream(pom);

            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(inputStream);

            // The cdata parts of the pom are not preserved from initial to target
            MavenJDOMWriter writer = new MavenJDOMWriter();

            outputStreamWriter = new OutputStreamWriter(new FileOutputStream(pom), fileEncoding);

            Format form = Format.getRawFormat().setEncoding(fileEncoding);
            form.setLineSeparator("\n");
            writer.write(model, doc, outputStreamWriter, form);
        } catch (Exception e) {
            log.error("Have exception when try correct line ending.", e);
        } finally {
            IOUtil.close(inputStream);
            IOUtil.close(outputStreamWriter);
        }
    }

    public void copyBundledDependencies() throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy-dependencies"),
                configuration(
                        element(name("includeScope"), "runtime"),
                        element(name("excludeScope"), "provided"),
                        element(name("excludeScope"), "test"),
                        element(name("includeTypes"), "jar"),
                        element(name("outputDirectory"), "${project.build.outputDirectory}/META-INF/lib")
                ),
                executionEnvironment()
        );
    }

    public void copyTestBundledDependencies(List<ProductArtifact> testBundleExcludes) throws MojoExecutionException
    {
        StringBuilder sb = new StringBuilder();

        for(ProductArtifact artifact : testBundleExcludes)
        {
            log.info("excluding artifact from test jar: " + artifact.getArtifactId());
                sb.append(",").append(artifact.getArtifactId());
        }

        String customExcludes = sb.toString();

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy-dependencies"),
                configuration(
                        element(name("includeScope"), "test"),
                        element(name("excludeScope"), "provided"),
                        element(name("excludeArtifactIds"),"junit" + customExcludes),
                        element(name("useSubDirectoryPerScope"),"true"),
                        element(name("outputDirectory"), "${project.build.directory}/testlibs")
                ),
                executionEnvironment()
        );

        File targetDir = new File(ctx.getProject().getBuild().getDirectory());
        File testlibsDir = new File(targetDir,"testlibs");
        File compileLibs = new File(testlibsDir,"compile");
        File testLibs = new File(testlibsDir,"test");


        File testClassesDir = new File(ctx.getProject().getBuild().getTestOutputDirectory());
        File metainfDir = new File(testClassesDir,"META-INF");
        File libDir = new File(metainfDir,"lib");

        try
        {
            compileLibs.mkdirs();
            testLibs.mkdirs();
            libDir.mkdirs();

            FileUtils.copyDirectory(compileLibs,libDir);
            FileUtils.copyDirectory(testLibs,libDir);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("unable to copy test libs", e);
        }

    }

    public void copyTestBundledDependenciesExcludingTestScope(List<ProductArtifact> testBundleExcludes) throws MojoExecutionException
    {
        StringBuilder sb = new StringBuilder();

        for(ProductArtifact artifact : testBundleExcludes)
        {
            log.info("excluding artifact from test jar: " + artifact.getArtifactId());
            sb.append(",").append(artifact.getArtifactId());
        }

        String customExcludes = sb.toString();

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy-dependencies"),
                configuration(
                        element(name("includeScope"), "runtime"),
                        element(name("excludeScope"), "provided"),
                        element(name("excludeScope"), "test"),
                        element(name("includeTypes"), "jar"),
                        element(name("excludeArtifactIds"),"junit" + customExcludes),
                        element(name("outputDirectory"), "${project.build.testOutputDirectory}/META-INF/lib")
                ),
                executionEnvironment()
        );
    }

    public void extractBundledDependencies() throws MojoExecutionException
    {
         executeMojo(
                 plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("unpack-dependencies"),
                configuration(
                        element(name("includeScope"), "runtime"),
                        element(name("excludeScope"), "provided"),
                        element(name("excludeScope"), "test"),
                        element(name("includeTypes"), "jar"),
                        element(name("excludes"), "atlassian-plugin.xml, META-INF/MANIFEST.MF, META-INF/*.DSA, META-INF/*.SF"),
                        element(name("outputDirectory"), "${project.build.outputDirectory}")
                ),
                executionEnvironment()
        );
    }

    public void extractTestBundledDependenciesExcludingTestScope(List<ProductArtifact> testBundleExcludes) throws MojoExecutionException
    {
        StringBuilder sb = new StringBuilder();

        for(ProductArtifact artifact : testBundleExcludes)
        {
            sb.append(",").append(artifact.getArtifactId());
        }

        String customExcludes = sb.toString();

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("unpack-dependencies"),
                configuration(
                        element(name("includeScope"), "runtime"),
                        element(name("excludeScope"), "provided"),
                        element(name("excludeScope"), "test"),
                        element(name("includeTypes"), "jar"),
                        element(name("excludeArtifactIds"),"junit" + customExcludes),
                        element(name("excludes"), "META-INF/MANIFEST.MF, META-INF/*.DSA, META-INF/*.SF"),
                        element(name("outputDirectory"), "${project.build.testOutputDirectory}")
                ),
                executionEnvironment()
        );
    }

    public void extractTestBundledDependencies(List<ProductArtifact> testBundleExcludes) throws MojoExecutionException
    {
        StringBuilder sb = new StringBuilder();

        for(ProductArtifact artifact : testBundleExcludes)
        {
            sb.append(",").append(artifact.getArtifactId());
        }

        String customExcludes = sb.toString();

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("unpack-dependencies"),
                configuration(
                        element(name("includeScope"), "test"),
                        element(name("excludeScope"), "provided"),
                        element(name("excludeArtifactIds"),"junit" + customExcludes),
                        element(name("includeTypes"), "jar"),
                        element(name("useSubDirectoryPerScope"),"true"),
                        element(name("outputDirectory"), "${project.build.directory}/testlibs")
                ),
                executionEnvironment()
        );

        File targetDir = new File(ctx.getProject().getBuild().getDirectory());
        File testlibsDir = new File(targetDir,"testlibs");
        File compileLibs = new File(testlibsDir,"compile");
        File testLibs = new File(testlibsDir,"test");


        File testClassesDir = new File(ctx.getProject().getBuild().getTestOutputDirectory());

        try
        {
            compileLibs.mkdirs();
            testLibs.mkdirs();

            FileUtils.copyDirectory(compileLibs,testClassesDir,FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter("META-INF")));
            FileUtils.copyDirectory(testLibs,testClassesDir,FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter("META-INF")));
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("unable to copy test libs", e);
        }
    }

    public void compressResources(boolean compressJs, boolean compressCss, boolean useClosureForJs, Charset cs, Map<String,String> closureOptions) throws MojoExecutionException
    {
        MinifierParameters closureParameters = new MinifierParameters(compressJs,
                compressCss,
                useClosureForJs,
                cs,
                log,
                closureOptions
        );
        ResourcesMinifier.minify(ctx.getProject().getBuild().getResources(), ctx.getProject().getBuild().getOutputDirectory(), closureParameters);
    }

    public void filterPluginDescriptor() throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-resources-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-resources-plugin"))
                ),
                goal("copy-resources"),
                configuration(
                        element(name("encoding"), "UTF-8"),
                        element(name("resources"),
                                element(name("resource"),
                                        element(name("directory"), "src/main/resources"),
                                        element(name("filtering"), "true"),
                                        element(name("includes"),
                                                element(name("include"), "atlassian-plugin.xml"))
                                )
                        ),
                        element(name("outputDirectory"), "${project.build.outputDirectory}")
                ),
                executionEnvironment()
        );


        XmlCompressor compressor = new XmlCompressor();
        File pluginXmlFile = new File(ctx.getProject().getBuild().getOutputDirectory(), "atlassian-plugin.xml");

        if (pluginXmlFile.exists())
        {
            try
            {
                String source = FileUtils.readFileToString(pluginXmlFile);
                String min = compressor.compress(source);
                FileUtils.writeStringToFile(pluginXmlFile,min);
            }
            catch (IOException e)
            {
                throw new MojoExecutionException("IOException while minifying plugin XML file", e);
            }
        }
    }

    public void filterTestPluginDescriptor() throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-resources-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-resources-plugin"))
                ),
                goal("copy-resources"),
                configuration(
                        element(name("encoding"), "UTF-8"),
                        element(name("resources"),
                                element(name("resource"),
                                        element(name("directory"), "src/test/resources"),
                                        element(name("filtering"), "true"),
                                        element(name("includes"),
                                                element(name("include"), "atlassian-plugin.xml"))
                                )
                        ),
                        element(name("outputDirectory"), "${project.build.testOutputDirectory}")
                ),
                executionEnvironment()
        );

    }

    public void runUnitTests(Map<String, Object> systemProperties) throws MojoExecutionException {
        runUnitTests(systemProperties, null, null);
    }

    public void runUnitTests(Map<String, Object> systemProperties, String excludedGroups, final String category) throws MojoExecutionException
    {
        final Element systemProps = convertPropsToElements(systemProperties);
        final Xpp3Dom config = configuration(
                systemProps,
                element(name("excludes"),
                        // The exclude handling in Surefire has changed, and patterns that don't start with "**/"
                        // are now prefixed with it automatically. That means "it/**" becomes "**/it/**", which
                        // ignores any test in any "it" directory anywhere, not just at the root.
                        // Regex matches are _not_ automatically prefixed, so using a regex pattern here allows
                        // us to continue only excluding tests in a root "it" package.
                        element(name("exclude"), "%regex[it/.*]"),
                        element(name("exclude"), "**/*$*")),
                element(name("excludedGroups"), excludedGroups)
        );

        if (isRelevantCategory(category))
        {
            appendJunitCategoryToConfiguration(category, config);
        }

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-surefire-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-surefire-plugin"))
                ),
                goal("test"),
                config,
                executionEnvironment()
        );
    }

    public File copyWebappWar(final String productId, final File targetDirectory, final ProductArtifact artifact)
            throws MojoExecutionException
    {
        final File webappWarFile = new File(targetDirectory, productId + "-original.war");
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), artifact.getGroupId()),
                                        element(name("artifactId"), artifact.getArtifactId()),
                                        element(name("type"), "war"),
                                        element(name("version"), artifact.getVersion()),
                                        element(name("destFileName"), webappWarFile.getName()))),
                        element(name("outputDirectory"), targetDirectory.getPath())
                ),
                executionEnvironment()
        );
        return webappWarFile;
    }

    public void unpackWebappWar(final File targetDirectory, final ProductArtifact artifact)
            throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("unpack"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), artifact.getGroupId()),
                                        element(name("artifactId"), artifact.getArtifactId()),
                                        element(name("type"), "war"),
                                        element(name("version"), artifact.getVersion()))),
                        element(name("outputDirectory"), targetDirectory.getPath()),
                        //Overwrite needs to be enabled or unpack won't unpack multiple copies
                        //of the same dependency GAV, _even to different output directories_
                        element(name("overWriteReleases"), "true"),
                        element(name("overWriteSnapshots"), "true")
                ),
                executionEnvironment()
        );
    }

    public File copyArtifact(final String targetFileName, final File targetDirectory,
                             final ProductArtifact artifact, String type) throws MojoExecutionException
    {
        final File targetFile = new File(targetDirectory, targetFileName);
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), artifact.getGroupId()),
                                        element(name("artifactId"), artifact.getArtifactId()),
                                        element(name("type"), type),
                                        element(name("version"), artifact.getVersion()),
                                        element(name("destFileName"), targetFile.getName()))),
                        element(name("outputDirectory"), targetDirectory.getPath())
                ),
                executionEnvironment()
        );
        return targetFile;
    }

    /**
     * Copies {@code artifacts} to the {@code outputDirectory}. Artifacts are looked up in order: <ol> <li>in the maven
     * reactor</li> <li>in the maven repositories</li> </ol> This can't be used in a goal that happens before the
     * <em>package</em> phase as artifacts in the reactor will be not be packaged (and therefore 'copiable') until this
     * phase.
     *
     * @param outputDirectory the directory to copy artifacts to
     * @param artifacts       the list of artifact to copy to the given directory
     */
    public void copyPlugins(final File outputDirectory, final List<ProductArtifact> artifacts)
            throws MojoExecutionException
    {
        for (ProductArtifact artifact : artifacts)
        {
            final MavenProject artifactReactorProject = getReactorProjectForArtifact(artifact);
            if (artifactReactorProject != null)
            {

                log.debug(artifact + " will be copied from reactor project " + artifactReactorProject);
                final File artifactFile = artifactReactorProject.getArtifact().getFile();
                if (artifactFile == null)
                {
                    log.warn("The plugin " + artifact + " is in the reactor but not the file hasn't been attached.  Skipping.");
                }
                else
                {
                    log.debug("Copying " + artifactFile + " to " + outputDirectory);
                    try
                    {
                        FileUtils.copyFile(artifactFile, new File(outputDirectory, artifactFile.getName()));
                    }
                    catch (IOException e)
                    {
                        throw new MojoExecutionException("Could not copy " + artifact + " to " + outputDirectory, e);
                    }
                }

            }
            else
            {
                executeMojo(
                        plugin(
                                groupId("org.apache.maven.plugins"),
                                artifactId("maven-dependency-plugin"),
                                version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                        ),
                        goal("copy"),
                        configuration(
                                element(name("artifactItems"),
                                        element(name("artifactItem"),
                                                element(name("groupId"), artifact.getGroupId()),
                                                element(name("artifactId"), artifact.getArtifactId()),
                                                element(name("version"), artifact.getVersion()))),
                                element(name("outputDirectory"), outputDirectory.getPath())
                        ),
                        executionEnvironment());
            }
        }
    }

    private MavenProject getReactorProjectForArtifact(ProductArtifact artifact)
    {
        for (final MavenProject project : ctx.getReactor())
        {
            if (project.getGroupId().equals(artifact.getGroupId())
                    && project.getArtifactId().equals(artifact.getArtifactId())
                    && project.getVersion().equals(artifact.getVersion()))
            {
                return project;
            }
        }
        return null;
    }

    private void unpackContainer(final Container container) throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("unpack"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), container.getGroupId()),
                                        element(name("artifactId"), container.getArtifactId()),
                                        element(name("version"), container.getVersion()),
                                        element(name("classifier"), container.getClassifier()),
                                        element(name("type"), "zip"))),
                        element(name("outputDirectory"), container.getRootDirectory(getBuildDirectory()))
                ),
                executionEnvironment());
    }

    private String getBuildDirectory()
    {
        return ctx.getProject().getBuild().getDirectory();
    }

    private static Xpp3Dom configurationWithoutNullElements(Element... elements)
    {
        return configuration(removeNullElements(elements));
    }

    private static Element[] removeNullElements(Element... elements) {
        return Arrays.stream(elements)
                     .filter(Objects::nonNull)
                     .toArray(Element[]::new);
    }

    private Plugin bndPlugin()
    {
        String bundleVersion = defaultArtifactIdToVersionMap.get("maven-bundle-plugin");
        log.info("using maven-bundle-plugin v" + bundleVersion);

        return plugin(
                groupId("org.apache.felix"),
                artifactId("maven-bundle-plugin"),
                version(bundleVersion));
    }

    /**
     * Wrap execute Mojo function for temporary removing global Cargo configuration
     * before starting AMPS internal Cargo
     */
    @VisibleForTesting
    protected void executeMojoExcludeProductCargoConfig(Plugin internalCargo, String goal, Xpp3Dom configuration, ExecutionEnvironment env)
            throws MojoExecutionException
    {
        // remove application cargo plugin for avoiding amps standalone cargo merges configuration
        Plugin globalCargo = env.getMavenProject().getPlugin("org.codehaus.cargo:cargo-maven2-plugin");
        env.getMavenProject().getBuild().removePlugin(globalCargo);
        MojoExecutor.executeMojo(internalCargo, goal, configuration, env);
        // restore application cargo plugin for maven next tasks
        if (null != globalCargo)
        {
            env.getMavenProject().getBuild().addPlugin(globalCargo);
        }
    }

    public int startWebapp(final String productInstanceId, final File war, final Map<String, String> systemProperties,
                           final List<ProductArtifact> extraContainerDependencies,
                           final List<ProductArtifact> extraProductDeployables,
                           final Product webappContext) throws MojoExecutionException
    {
        final Container container = findContainer(webappContext.getContainerId());
        File containerDir = new File(container.getRootDirectory(getBuildDirectory()));

        // retrieve non-embedded containers
        if (!container.isEmbedded())
        {
            if (containerDir.exists())
            {
                log.info("Reusing unpacked container '" + container.getId() + "' from " + containerDir.getPath());
            }
            else
            {
                log.info("Unpacking container '" + container.getId() + "' from container artifact: " + container.toString());
                unpackContainer(container);
            }
        }

        final int rmiPort = pickFreePort(webappContext.getRmiPort());
        final int actualAjpPort = pickFreePort(webappContext.getAjpPort());
        final int actualHttpPort;
        String protocol = "http";

        if(webappContext.getUseHttps())
        {
            actualHttpPort = webappContext.getHttpsPort();
            protocol = "https";
        }
        else
        {
            actualHttpPort = pickFreePort(webappContext.getHttpPort());
        }

        final List<Element> sysProps = new ArrayList<>();

        for (final Map.Entry<String, String> entry : systemProperties.entrySet())
        {
            sysProps.add(element(name(entry.getKey()), entry.getValue()));
        }
        log.info("Starting " + productInstanceId + " on the " + container.getId() + " container on ports "
                + actualHttpPort + " (" + protocol + "), " + rmiPort + " (rmi) and " + actualAjpPort + " (ajp)");

        final String baseUrl = getBaseUrl(webappContext, actualHttpPort);
        sysProps.add(element(name("baseurl"), baseUrl));

        final List<Element> deps = extractDependencies(extraContainerDependencies, webappContext);

        final List<Element> deployables = new ArrayList<>();
        deployables.add(element(name("deployable"),
                element(name("groupId"), "foo"),
                element(name("artifactId"), "bar"),
                element(name("type"), "war"),
                element(name("location"), war.getPath()),
                element(name("properties"),
                        element(name("context"), webappContext.getContextPath())
                )
        ));

        for (final ProductArtifact extra : extraProductDeployables)
        {
            deployables.add(element(name("deployable"),
                    element(name("groupId"), extra.getGroupId()),
                    element(name("artifactId"), extra.getArtifactId()),
                    element(name("type"), extra.getType()),
                    element(name("location"), extra.getPath())
            ));
        }

        final List<Element> props =
                getConfigurationProperties(systemProperties, webappContext, rmiPort, actualHttpPort, actualAjpPort, protocol);

        int startupTimeout = webappContext.getStartupTimeout();
        if (Boolean.FALSE.equals(webappContext.getSynchronousStartup()))
        {
            startupTimeout = 0;
        }

        Plugin cargo = cargo(webappContext);
        executeMojoExcludeProductCargoConfig(
                cargo,
                goal("start"),
                configurationWithoutNullElements(
                        element(name("deployables"), deployables.toArray(new Element[0])),
                        waitElement(cargo), // This may be null
                        element(name("container"),
                                element(name("containerId"), container.getId()),
                                element(name("type"), container.getType()),
                                element(name("home"), container.getInstallDirectory(getBuildDirectory())),
                                element(name("output"), webappContext.getOutput()),
                                element(name("systemProperties"), sysProps.toArray(new Element[0])),
                                element(name("dependencies"), deps.toArray(new Element[0])),
                                element(name("timeout"), String.valueOf(startupTimeout))
                        ),
                        element(name("configuration"), removeNullElements(
                                    element(name("home"), container.getConfigDirectory(getBuildDirectory(), productInstanceId)),
                                    element(name("type"), "standalone"),
                                    element(name("properties"), props.toArray(new Element[0])),
                                    xmlReplacementsElement(webappContext.getCargoXmlOverrides())) // This may be null

                        ),
                        // Fix issue AMPS copy 2 War files to container
                        // Refer to Cargo documentation: when project's packaging is war, ear, ejb
                        // the generated artifact will automatic copy to target container.
                        // For avoiding this behavior, just add an empty <deployer/> element
                        element(name("deployer"))
                ),
                executionEnvironment()
        );
        return actualHttpPort;
    }

    private List<Element> extractDependencies(final List<ProductArtifact> extraContainerDependencies, final Product webappContext) throws MojoExecutionException {
        final List<Element> deps = new ArrayList<>();

        for (final ProductArtifact dep : extraContainerDependencies)
        {
            deps.add(element(name("dependency"),
                    element(name("location"), webappContext.getArtifactRetriever().resolve(dep))
            ));
        }

        for (DataSource dataSource : webappContext.getDataSources())
        {
            for (ProductArtifact containerDependency : dataSource.getLibArtifacts())
            {
                deps.add(element(name("dependency"),
                        element(name("location"), webappContext.getArtifactRetriever().resolve(containerDependency))
                ));
            }
        }
        return deps;
    }

    private Element xmlReplacementsElement(final Collection<XmlOverride> cargoXmlOverrides) {
        if (cargoXmlOverrides == null) {
            return null;
        }

        Element[] xmlReplacementsElements = cargoXmlOverrides.stream().map(xmlOverride ->
                element(name("xmlReplacement"),
                        element(name("file"), xmlOverride.getFile()),
                        element(name("xpathExpression"), xmlOverride.getxPathExpression()),
                        element(name("attributeName"), xmlOverride.getAttributeName()),
                        element(name("value"), xmlOverride.getValue()))
        ).toArray(Element[]::new);

        return element(name("xmlReplacements"), xmlReplacementsElements);
    }

    @VisibleForTesting
    List<Element> getConfigurationProperties(final Map<String, String> systemProperties,
            final Product webappContext, final int rmiPort, final int actualHttpPort, final int actualAjpPort, final String protocol)
    {
        final List<Element> props = new ArrayList<>();
        for (final Map.Entry<String, String> entry : systemProperties.entrySet())
        {
            props.add(element(name(entry.getKey()), entry.getValue()));
        }
        props.add(element(name("cargo.servlet.port"), String.valueOf(actualHttpPort)));

        if (webappContext.getUseHttps())
        {
            log.debug("starting tomcat using Https via cargo with the following parameters:");
            log.debug("cargo.servlet.port = " + actualHttpPort);
            log.debug("cargo.protocol = " + protocol);
            props.add(element(name("cargo.protocol"), protocol));

            log.debug("cargo.tomcat.connector.clientAuth = " + webappContext.getHttpsClientAuth());
            props.add(element(name("cargo.tomcat.connector.clientAuth"), webappContext.getHttpsClientAuth()));

            log.debug("cargo.tomcat.connector.sslProtocol = " +  webappContext.getHttpsSSLProtocol());
            props.add(element(name("cargo.tomcat.connector.sslProtocol"), webappContext.getHttpsSSLProtocol()));

            log.debug("cargo.tomcat.connector.keystoreFile = " + webappContext.getHttpsKeystoreFile());
            props.add(element(name("cargo.tomcat.connector.keystoreFile"), webappContext.getHttpsKeystoreFile()));

            log.debug("cargo.tomcat.connector.keystorePass = " + webappContext.getHttpsKeystorePass());
            props.add(element(name("cargo.tomcat.connector.keystorePass"), webappContext.getHttpsKeystorePass()));

            log.debug("cargo.tomcat.connector.keyAlias = " +webappContext.getHttpsKeyAlias());
            props.add(element(name("cargo.tomcat.connector.keyAlias"), webappContext.getHttpsKeyAlias()));

            log.debug("cargo.tomcat.httpSecure = " + webappContext.getHttpsHttpSecure().toString());
            props.add(element(name("cargo.tomcat.httpSecure"), webappContext.getHttpsHttpSecure().toString()));
        }

        props.add(element(name(AJP_PORT_PROPERTY), String.valueOf(actualAjpPort)));
        props.add(element(name("cargo.rmi.port"), String.valueOf(rmiPort)));
        props.add(element(name("cargo.jvmargs"), webappContext.getJvmArgs() + webappContext.getDebugArgs()));
        return props;
    }

    public void stopWebapp(final String productId, final String containerId, final Product webappContext) throws MojoExecutionException
    {
        final Container container = findContainer(containerId);

        String actualShutdownTimeout = webappContext.getSynchronousStartup() ? "0" : String.valueOf(webappContext.getShutdownTimeout());

        executeMojoExcludeProductCargoConfig(
        cargo(webappContext),
        goal("stop"),
        configuration(
                element(name("container"),
                        element(name("containerId"), container.getId()),
                        element(name("type"), container.getType()),
                        element(name("timeout"), actualShutdownTimeout),
                        // org.codehaus.cargo
                        element(name("home"), container.getInstallDirectory(getBuildDirectory()))
                ),
                element(name("configuration"),
                        // org.twdata.maven
                        element(name("home"), container.getConfigDirectory(getBuildDirectory(), productId)),
                        //we don't need that atm. since timeout is 0 for org.codehaus.cargo
                        //hoping this will fix AMPS-987
                        element(name("properties"), createShutdownPortsPropertiesConfiguration(webappContext))
                )
        ),
        executionEnvironment()
        );
    }

    /**
     * Cargo waits (org.codehaus.cargo.container.tomcat.internal.AbstractCatalinaInstalledLocalContainer#waitForCompletion(boolean waitForStarting)) for 3 ports, but the AJP and RMI ports may
     * not be correct (see below), so we configure it to wait on the HTTP port only.
     *
     * Since we're not configuring the AJP port it defaults to 8009. This port might have been taken by a different application (the container will still come up though, see
     * "INFO: Port busy 8009 java.net.BindException: Address already in use" in the log). Thus we don't want to wait for it because it might be still open also the container
     * is shut down.
     *
     * The RMI port is randomly chosen (see startWebapp), thus we don't have any information close at hand. As a future optimisation, e.g. when we move away from cargo to let's say
     * Apache's Tomcat Maven Plugin we could retrieve the actual configuration from the server.xml on shutdown and thus know exactly for what which port to wait until it gets closed.
     * We could do that already in cargo (e.g. container/tomcat85x/<productHome>/conf/server.xml) but that means that we have to support all the containers we are supporting with cargo.
     *
     * Since the HTTP port is the only one that interests us, we set all three ports to this one when calling stop. But since that may be randomly chosen as well we might be waiting
     * for the wrong port to get closed. Since this is the minor use case, one has to either accept the timeout if the default port is open, or configure product.stop.timeout to 0 in
     * order to skip the wait.
     */
    private Element[] createShutdownPortsPropertiesConfiguration(final Product webappContext)
    {
        final List<Element> properties = new ArrayList<>();
        final String portUsedToDetermineIfShutdownSucceeded = String.valueOf(webappContext.getHttpPort());
        properties.add(element(name("cargo.servlet.port"), portUsedToDetermineIfShutdownSucceeded));
        properties.add(element(name("cargo.rmi.port"), portUsedToDetermineIfShutdownSucceeded));
        properties.add(element(name(AJP_PORT_PROPERTY), portUsedToDetermineIfShutdownSucceeded));
        return properties.toArray(new Element[0]);
    }

    /**
     * THIS USED TO Decide whether to use the org.twdata.maven.cargo-maven2-plugin or the org.codehaus.cargo.cargo-maven2-plugin.
     * <p/>
     * This has now been changed to just return the codehaus version since there are new features/fixes we need and the twdata version is no longer useful.
     */
    protected Plugin cargo(Product context)
    {
        String cargoVersion = defaultArtifactIdToVersionMap.get("cargo-maven2-plugin");
        log.info("using codehaus cargo v" + cargoVersion);
        return plugin(
                groupId("org.codehaus.cargo"),
                artifactId("cargo-maven2-plugin"),
                version(cargoVersion));
    }

    private Element waitElement(Plugin cargo)
    {
        if (cargo.getGroupId().equals("org.twdata.maven"))
        {
            return element(name("wait"), "false");
        }
        // If not using twdata's cargo, we avoid passing wait=false, because it's the default and it generates
        // a deprecation warning
        return null;
    }

    public static String getBaseUrl(Product product, int actualHttpPort)
    {
        return getBaseUrl(product.getServer(), product.getHttpPort(), product.getContextPath());
    }

    private static String getBaseUrl(String server, int actualHttpPort, String contextPath)
    {
        String port = actualHttpPort != 80 ? ":" + actualHttpPort : "";
        server = server.startsWith("http") ? server : "http://" + server;
        if (!contextPath.startsWith("/") && StringUtils.isNotBlank(contextPath))
        {
            contextPath = "/" + contextPath;
        }
        return server + port + contextPath;
    }

    public void runIntegrationTests(String testGroupId, String containerId, List<String> includes, List<String> excludes, Map<String, Object> systemProperties,
            final File targetDirectory, final String category, final boolean skipVerifyGoal)
    		throws MojoExecutionException
	{
        List<Element> includeElements = new ArrayList<>(includes.size());
    	for (String include : includes)
    	{
    		includeElements.add(element(name("include"), include));
    	}

        List<Element> excludeElements = new ArrayList<>(excludes.size() + 2);
        excludeElements.add(element(name("exclude"), "**/*$*"));
        excludeElements.add(element(name("exclude"), "**/Abstract*"));
        for (String exclude : excludes)
        {
        	excludeElements.add(element(name("exclude"), exclude));
        }

        final String testOutputDir = targetDirectory.getAbsolutePath() + "/" + testGroupId + "/" + containerId + "/surefire-reports";
        final String reportsDirectory = "reportsDirectory";
        systemProperties.put(reportsDirectory, testOutputDir);

        final Element systemProps = convertPropsToElements(systemProperties);
        final Xpp3Dom itconfig = configuration(
                element(name("includes"),
                        includeElements.toArray(new Element[0])
                ),
                element(name("excludes"),
                        excludeElements.toArray(new Element[0])
                ),
                systemProps,
                element(name(reportsDirectory), testOutputDir)
        );

        if (isRelevantCategory(category))
        {
            appendJunitCategoryToConfiguration(category, itconfig);
        }

        final Xpp3Dom verifyconfig = configuration(element(name(reportsDirectory), testOutputDir));


        log.info("Failsafe integration-test configuration:");
        log.info(itconfig.toString());

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-failsafe-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-failsafe-plugin"))
                ),
                goal("integration-test"),
                itconfig,
                executionEnvironment()
        );
        if (!skipVerifyGoal) {
            executeMojo(
                    plugin(
                            groupId("org.apache.maven.plugins"),
                            artifactId("maven-failsafe-plugin"),
                            version(defaultArtifactIdToVersionMap.get("maven-failsafe-plugin"))
                    ),
                    goal("verify"),
                    verifyconfig,
                    executionEnvironment()
            );
        } else {
            log.info("Skipping failsafe IT failure verification.");
        }
    }

    public void runPreIntegrationTest(final DataSource dataSource) throws MojoExecutionException
    {
        final String dumpFilePath = dataSource.getDumpFilePath();
        final JiraDatabaseFactory factory = getJiraDatabaseFactory();
        final JiraDatabase jiraDatabase = factory.getJiraDatabase(dataSource);
        final Xpp3Dom sqlMavenPluginConfiguration = jiraDatabase.getPluginConfiguration();
        final List<Dependency> libs = jiraDatabase.getDependencies();
        final Plugin sqlMaven = plugin(
                groupId("org.codehaus.mojo"),
                artifactId("sql-maven-plugin"),
                version(defaultArtifactIdToVersionMap.get("sql-maven-plugin"))
        );
        sqlMaven.getDependencies().addAll(libs);
        executeMojo(
                sqlMaven,
                goal("execute"),
                sqlMavenPluginConfiguration,
                executionEnvironment()
        );
        if (StringUtils.isNotEmpty(dumpFilePath))
        {
            log.info("Do import for dump file: " + dumpFilePath);
            File dumpFile = new File(dumpFilePath);
            if(!dumpFile.exists() || !dumpFile.isFile())
            {
                throw new MojoExecutionException("SQL dump file does not exist: " + dumpFilePath);
            }
            if(ImportMethod.SQL.equals(ImportMethod.getValueOf(dataSource.getImportMethod())))
            {
                // Use JDBC to import sql standard dump file
                executeMojo(
                        sqlMaven,
                        goal("execute"),
                        jiraDatabase.getConfigImportFile(),
                        executionEnvironment()
                );
            }
            else
            {
                final Xpp3Dom configDatabaseTool = jiraDatabase.getConfigDatabaseTool();
                final Plugin execMaven = plugin(
                        groupId("org.codehaus.mojo"),
                        artifactId("exec-maven-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-exec-plugin"))
                );
                // Use database specific tool to import dump file
                executeMojo(
                        execMaven,
                        goal("exec"),
                        configDatabaseTool,
                        executionEnvironment()
                );
            }

        }
    }

    private void appendJunitCategoryToConfiguration(final String category, final Xpp3Dom config)
    {
        final Element groups = element(name("groups"), category);
        config.addChild(groups.toDom());
    }

    private boolean isRelevantCategory(final String category)
    {
        return category != null && !"".equals(category);
    }

    /**
     * Converts a map of System properties to maven config elements
     */
    private Element convertPropsToElements(Map<String, Object> systemProperties)
    {
        ArrayList<Element> properties = new ArrayList<>();
        for (Map.Entry<String, Object> entry: systemProperties.entrySet())
        {
            log.info("adding system property to configuration: " + entry.getKey() + "::" + entry.getValue());

            properties.add(element(name(entry.getKey()),entry.getValue().toString()));
        }

        return element(name("systemPropertyVariables"), properties.toArray(new Element[0]));
    }

    private Container findContainer(final String containerId)
    {
        final Container container = idToContainerMap.get(containerId);
        if (container == null)
        {
            throw new IllegalArgumentException("Container " + containerId + " not supported");
        }
        return container;
    }

    public void installPlugin(PdkParams pdkParams)
            throws MojoExecutionException
    {
        final String baseUrl = getBaseUrl(pdkParams.getServer(), pdkParams.getPort(), pdkParams.getContextPath());
        executeMojo(
                plugin(
                        groupId("com.atlassian.maven.plugins"),
                        artifactId("atlassian-pdk"),
                        version(defaultArtifactIdToVersionMap.get("atlassian-pdk"))
                ),
                goal("install"),
                configuration(
                        element(name("pluginFile"), pdkParams.getPluginFile()),
                        element(name("username"), pdkParams.getUsername()),
                        element(name("password"), pdkParams.getPassword()),
                        element(name("serverUrl"), baseUrl),
                        element(name("pluginKey"), pdkParams.getPluginKey())
                ),
                executionEnvironment()
        );
    }

    public void uninstallPlugin(final String pluginKey, final String server, final int port, final String contextPath)
            throws MojoExecutionException
    {
        final String baseUrl = getBaseUrl(server, port, contextPath);
        executeMojo(
                plugin(
                        groupId("com.atlassian.maven.plugins"),
                        artifactId("atlassian-pdk"),
                        version(defaultArtifactIdToVersionMap.get("atlassian-pdk"))
                ),
                goal("uninstall"),
                configuration(
                        element(name("username"), "admin"),
                        element(name("password"), "admin"),
                        element(name("serverUrl"), baseUrl),
                        element(name("pluginKey"), pluginKey)
                ),
                executionEnvironment()
        );
    }

    public void installIdeaPlugin() throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.twdata.maven"),
                        artifactId("maven-cli-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-cli-plugin"))
                ),
                goal("idea"),
                configuration(),
                executionEnvironment()
        );
    }

    public File copyDist(final File targetDirectory, final ProductArtifact artifact) throws MojoExecutionException
    {
        return copyZip(targetDirectory, artifact, "test-dist.zip");
    }

    public File copyHome(final File targetDirectory, final ProductArtifact artifact) throws MojoExecutionException
    {
        return copyZip(targetDirectory, artifact, artifact.getArtifactId() + ".zip");
    }

    public File copyZip(final File targetDirectory, final ProductArtifact artifact, final String localName) throws MojoExecutionException
    {
        final File artifactZip = new File(targetDirectory, localName);
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), artifact.getGroupId()),
                                        element(name("artifactId"), artifact.getArtifactId()),
                                        element(name("type"), "zip"),
                                        element(name("version"), artifact.getVersion()),
                                        element(name("destFileName"), artifactZip.getName()))),
                        element(name("outputDirectory"), artifactZip.getParent())
                ),
                executionEnvironment()
        );
        return artifactZip;
    }

    public void generateBundleManifest(final Map<String, String> instructions, final Map<String, String> basicAttributes) throws MojoExecutionException
    {
        final List<Element> instlist = new ArrayList<>();
        for (final Map.Entry<String, String> entry : instructions.entrySet())
        {
            instlist.add(element(entry.getKey(), entry.getValue()));
        }
        if (!instructions.containsKey(Constants.IMPORT_PACKAGE))
        {
            instlist.add(element(Constants.IMPORT_PACKAGE, "*;resolution:=optional"));
            // BND will expand the wildcard to a list of actually-used packages, but this tells it to mark
            // them all as optional
        }
        for (final Map.Entry<String, String> entry : basicAttributes.entrySet())
        {
            instlist.add(element(entry.getKey(), entry.getValue()));
        }
        executeMojo(
                bndPlugin(),
                goal("manifest"),
                configuration(
                        element(name("supportedProjectTypes"),
                                element(name("supportedProjectType"), "jar"),
                                element(name("supportedProjectType"), "bundle"),
                                element(name("supportedProjectType"), "war"),
                                element(name("supportedProjectType"), "atlassian-plugin")),
                        element(name("instructions"), instlist.toArray(new Element[0]))
                ),
                executionEnvironment()
        );
    }

    public void generateTestBundleManifest(final Map<String, String> instructions, final Map<String, String> basicAttributes) throws MojoExecutionException
    {
        final List<Element> instlist = new ArrayList<>();
        for (final Map.Entry<String, String> entry : instructions.entrySet())
        {
            instlist.add(element(entry.getKey(), entry.getValue()));
        }
        if (!instructions.containsKey(Constants.IMPORT_PACKAGE))
        {
            instlist.add(element(Constants.IMPORT_PACKAGE, "*;resolution:=optional"));
            // BND will expand the wildcard to a list of actually-used packages, but this tells it to mark
            // them all as optional
        }
        for (final Map.Entry<String, String> entry : basicAttributes.entrySet())
        {
            instlist.add(element(entry.getKey(), entry.getValue()));
        }
        executeMojo(
                bndPlugin(),
                goal("manifest"),
                configuration(
                        element(name("manifestLocation"),"${project.build.testOutputDirectory}/META-INF"),
                        element(name("supportedProjectTypes"),
                                element(name("supportedProjectType"), "jar"),
                                element(name("supportedProjectType"), "bundle"),
                                element(name("supportedProjectType"), "war"),
                                element(name("supportedProjectType"), "atlassian-plugin")),
                        element(name("instructions"), instlist.toArray(new Element[0]))
                ),
                executionEnvironment()
        );
    }

    public void generateMinimalManifest(final Map<String, String> basicAttributes) throws MojoExecutionException
    {
        File metaInf = file(ctx.getProject().getBuild().getOutputDirectory(), "META-INF");
        if (!metaInf.exists())
        {
            metaInf.mkdirs();
        }
        File mf = file(ctx.getProject().getBuild().getOutputDirectory(), "META-INF", "MANIFEST.MF");
        Manifest m = new Manifest();
        m.getMainAttributes().putValue("Manifest-Version", "1.0");
        for (Map.Entry<String, String> entry : basicAttributes.entrySet())
        {
            m.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }

        try (FileOutputStream fos = new FileOutputStream(mf))
        {
            m.write(fos);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Unable to create manifest", e);
        }
    }

    public void generateTestMinimalManifest(final Map<String, String> basicAttributes) throws MojoExecutionException
    {
        File metaInf = file(ctx.getProject().getBuild().getTestOutputDirectory(), "META-INF");
        if (!metaInf.exists())
        {
            metaInf.mkdirs();
        }
        File mf = file(ctx.getProject().getBuild().getTestOutputDirectory(), "META-INF", "MANIFEST.MF");
        Manifest m = new Manifest();
        m.getMainAttributes().putValue("Manifest-Version", "1.0");
        for (Map.Entry<String, String> entry : basicAttributes.entrySet())
        {
            m.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }

        try (FileOutputStream fos = new FileOutputStream(mf))
        {
            m.write(fos);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Unable to create manifest", e);
        }
    }

    public void jarWithOptionalManifest(final boolean manifestExists) throws MojoExecutionException
    {
        Element[] archive = new Element[0];
        if (manifestExists)
        {
            archive = new Element[]{element(name("manifestFile"), "${project.build.outputDirectory}/META-INF/MANIFEST.MF")};
        }

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-jar-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-jar-plugin"))
                ),
                goal("jar"),
                configuration(
                        element(name("archive"), archive)
                ),
                executionEnvironment()
        );

    }

    private String artifactToString(final Artifact artifact)
    {
        return String.format("GAV: %s:%s:%s Type: %s, Classifier: %s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), artifact.getClassifier());
    }

    public void jarTests(String finalName) throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-jar-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-jar-plugin"))
                ),
                goal("test-jar"),
                configuration(
                        element(name("finalName"), finalName),
                        element(name("archive"),
                                element(name("manifestFile"), "${project.build.testOutputDirectory}/META-INF/MANIFEST.MF"))
                ),
                executionEnvironment()
        );
    }

    public void generateObrXml(File dep, File obrXml) throws MojoExecutionException
    {
        executeMojo(
                bndPlugin(),
                goal("install-file"),
                configuration(
                        element(name("obrRepository"), obrXml.getPath()),

                        // the following three settings are required but not really used
                        element(name("groupId"), "doesntmatter"),
                        element(name("artifactId"), "doesntmatter"),
                        element(name("version"), "doesntmatter"),

                        element(name("packaging"), "jar"),
                        element(name("file"), dep.getPath())

                ),
                executionEnvironment()
        );
    }

    /**
     * Adds the file to the artifacts of this build.
     * The artifact will be deployed using the name and version of the current project,
     * as in if your artifactId is 'MyProject', it will be MyProject-1.0-SNAPSHOT.jar,
     * overriding any artifact created at compilation time.
     *
     * Attached artifacts get installed (at install phase) and deployed (at deploy phase)
     * @param file the file
     * @param type the type of the file, default 'jar'
     */
    public void attachArtifact(File file, String type) throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.codehaus.mojo"),
                        artifactId("build-helper-maven-plugin"),
                        version(defaultArtifactIdToVersionMap.get("build-helper-maven-plugin"))
                ),
                goal("attach-artifact"),
                configuration(
                        element(name("artifacts"),
                                element(name("artifact"),
                                        element(name("file"), file.getAbsolutePath()),
                                        element(name("type"), type)
                                    )
                                )
                        ),
                executionEnvironment());

    }

    public void release(String mavenArgs) throws MojoExecutionException
    {
        String args = "";

        if(StringUtils.isNotBlank(mavenArgs)) {
            args = mavenArgs;
        }

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-release-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-release-plugin"))
                ),
                goal("prepare"),
                configuration(
                        element(name("arguments"), args)
                        ,element(name("autoVersionSubmodules"),"true")
                ),
                executionEnvironment()
        );

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-release-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-release-plugin"))
                ),
                goal("perform"),
                configuration(
                        element(name("arguments"), args),
                        element(name("useReleaseProfile"),"true")
                ),
                executionEnvironment()
        );
    }

    public void generateRestDocs(String jacksonModules) throws MojoExecutionException
    {
        MavenProject prj = ctx.getProject();
        StringBuffer packagesPath = new StringBuffer();
        List<PluginXmlUtils.RESTModuleInfo> restModules = PluginXmlUtils.getRestModules(ctx);

        for(PluginXmlUtils.RESTModuleInfo moduleInfo : restModules)
        {
            List<String> packageList = moduleInfo.getPackagesToScan();

            for(String packageToScan : packageList)
            {
                if(packagesPath.length() > 0)
                {
                    packagesPath.append(File.pathSeparator);
                }

                String filePath = prj.getBuild().getSourceDirectory() + File.separator + packageToScan.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
                packagesPath.append(filePath);
            }
        }

        if(!restModules.isEmpty() && packagesPath.length() > 0)
        {
            Set<String> docletPaths = new HashSet<>();
            StringBuffer docletPath = new StringBuffer(File.pathSeparator + prj.getBuild().getOutputDirectory());
            String resourcedocPath = fixWindowsSlashes(prj.getBuild().getOutputDirectory() + File.separator + "resourcedoc.xml");

            PluginXmlUtils.PluginInfo pluginInfo = PluginXmlUtils.getPluginInfo(ctx);

            try
            {
                docletPaths.addAll(prj.getCompileClasspathElements());
                docletPaths.addAll(prj.getRuntimeClasspathElements());
                docletPaths.addAll(prj.getSystemClasspathElements());

                //AMPS-663: add plugin execution classes to doclet path
                URL[] pluginUrls = ((URLClassLoader)Thread.currentThread().getContextClassLoader()).getURLs();
                for(URL pluginUrl : pluginUrls)
                {
                    docletPaths.add(new File(pluginUrl.getFile()).getPath());
                }

                for(String path : docletPaths) {
                    docletPath.append(File.pathSeparator);
                    docletPath.append(path);
                }

            } catch (DependencyResolutionRequiredException e)
            {
                throw new MojoExecutionException("Dependencies must be resolved", e);
            }

            Element outputOption = element(name("additionalOption"), "-output \"" + resourcedocPath + "\"");
            Element[] additionalOptions = jacksonModules == null
                    ? new Element[] {outputOption}
                    : new Element[] {outputOption, element(name("additionalOption"), " -modules \"" + jacksonModules + "\"")};

            //AMPSDEV-127: 'generate-rest-docs' fails with JDK8 - invalid flag: -Xdoclint:all
            //Root cause: ResourceDocletJSON doclet does not support option doclint
            //Solution: Temporary remove global javadoc configuration(remove doclint)
            final Plugin globalJavadoc = executionEnvironment().getMavenProject().getPlugin("org.apache.maven.plugins:maven-javadoc-plugin");
            if (null != globalJavadoc)
            {
                executionEnvironment().getMavenProject().getBuild().removePlugin(globalJavadoc);
            }
            executeMojo(
                    plugin(
                            groupId("org.apache.maven.plugins"),
                            artifactId("maven-javadoc-plugin"),
                            version(defaultArtifactIdToVersionMap.get("maven-javadoc-plugin"))
                    ),
                    goal("javadoc"),
                    configuration(
                            element(name("maxmemory"),"1024m"),
                            element(name("sourcepath"),packagesPath.toString()),
                            element(name("doclet"), "com.sun.jersey.wadl.resourcedoc.ResourceDocletJSON"),
                            element(name("docletPath"), docletPath.toString()),
                            element(name("docletArtifacts"),
                                    element(name("docletArtifact"),
                                            element(name("groupId"), "com.atlassian.plugins.rest"),
                                            element(name("artifactId"), "atlassian-rest-doclet"),
                                            element(name("version"), "2.9.2")
                                    ),
                                    element(name("docletArtifact"),
                                            element(name("groupId"), "xerces"),
                                            element(name("artifactId"), "xercesImpl"),
                                            element(name("version"), "2.9.1")
                                    ),
                                    element(name("docletArtifact"),
                                            element(name("groupId"), "commons-lang"),
                                            element(name("artifactId"), "commons-lang"),
                                            element(name("version"), "2.6")
                                    )
                            ),
                            element(name("outputDirectory")),
                            element(name("additionalOptions"), additionalOptions),
                            element(name("useStandardDocletOptions"),"false")
                    ),
                    executionEnvironment()
            );
            // restore global javadoc plugin for maven next tasks
            if (null != globalJavadoc)
            {
                executionEnvironment().getMavenProject().getBuild().addPlugin(globalJavadoc);
            }
            try {

                File userAppDocs = new File(prj.getBuild().getOutputDirectory(),"application-doc.xml");
                if(!userAppDocs.exists())
                {
                    String appDocText = com.atlassian.core.util.FileUtils.getResourceContent("application-doc.xml");
                    appDocText = StringUtils.replace(appDocText, "${rest.doc.title}", pluginInfo.getName());
                    appDocText = StringUtils.replace(appDocText,"${rest.doc.description}",pluginInfo.getDescription());
                    File appDocFile = new File(prj.getBuild().getOutputDirectory(), "application-doc.xml");

                    FileUtils.writeStringToFile(appDocFile,appDocText);
                    log.info("Wrote " + appDocFile.getAbsolutePath());
                }

                File userGrammars = new File(prj.getBuild().getOutputDirectory(),"application-grammars.xml");
                if(!userGrammars.exists())
                {
                    String grammarText = com.atlassian.core.util.FileUtils.getResourceContent("application-grammars.xml");
                    File grammarFile = new File(prj.getBuild().getOutputDirectory(), "application-grammars.xml");

                    FileUtils.writeStringToFile(grammarFile,grammarText);

                    log.info("Wrote " + grammarFile.getAbsolutePath());
                }

            } catch (Exception e)
            {
                throw new MojoExecutionException("Error writing REST application xml files",e);
            }
        }
    }

    public void copyContainerToOutputDirectory(String containerVersion) throws
            MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), "com.atlassian.plugins"),
                                        element(name("artifactId"), "remotable-plugins-container"),
                                        element(name("version"), containerVersion))),
                        element(name("stripVersion"), "true"),
                        element(name("outputDirectory"), "${project.build.directory}")
                ),
                executionEnvironment()
        );
    }

    public void debugStandaloneContainer(File pluginFile) throws MojoExecutionException
    {
        StringBuilder resourceProp = new StringBuilder();
        @SuppressWarnings("unchecked") List<Resource> resList = getContextProject().getResources();
        for (int i = 0; i < resList.size(); i++) {
            resourceProp.append(resList.get(i).getDirectory());
            if (i + 1 != resList.size()) {
                resourceProp.append(",");
            }
        }

        executeMojo(
                plugin(
                        groupId("org.codehaus.mojo"),
                        artifactId("exec-maven-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-exec-plugin"))
                ),
                goal("exec"),
                configuration(
                        element(name("executable"), "java"),
                        element(name("arguments"),
                                element(name("argument"), "-Datlassian.dev.mode=true"),
                                element(name("argument"), "-Dplugin.resource.directories=" + resourceProp),
                                element(name("argument"), "-Xdebug"),
                                element(name("argument"), "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5004"),
                                element(name("argument"), "-jar"),
                                element(name("argument"), "${project.build.directory}/remotable-plugins-container-standalone.jar"),
                                element(name("argument"), pluginFile.getPath()))
                ),
                executionEnvironment()
        );
    }

    public void saveArtifactToCurrentDirectory(String groupId, String artifactId, String version, String type, String filename) throws MojoExecutionException
    {
        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-dependency-plugin"),
                        version(defaultArtifactIdToVersionMap.get("maven-dependency-plugin"))
                ),
                goal("copy"),
                configuration(
                        element(name("artifactItems"),
                                element(name("artifactItem"),
                                        element(name("groupId"), groupId),
                                        element(name("artifactId"), artifactId),
                                        element(name("version"), version),
                                        element(name("type"), type),
                                        element(name("destFileName"), filename))),
                        element(name("outputDirectory"), ".")
                ),
                executionEnvironment()
        );
    }

    private static class Container extends ProductArtifact
    {
        private final String id;
        private final String type;
        private final String classifier;

        /**
         * Installable container that can be downloaded by Maven.
         *
         * @param id         identifier of container, eg. "tomcat5x".
         * @param groupId    groupId of container.
         * @param artifactId artifactId of container.
         * @param version    version number of container.
         */
        public Container(final String id, final String groupId, final String artifactId, final String version)
        {
            super(groupId, artifactId, version);
            this.id = id;
            this.type = "installed";
            this.classifier = "";
        }

        /**
         * Installable container that can be downloaded by Maven.
         *
         * @param id         identifier of container, eg. "tomcat5x".
         * @param groupId    groupId of container.
         * @param artifactId artifactId of container.
         * @param version    version number of container.
         * @param classifier classifier of the container.
         */
        public Container(final String id, final String groupId, final String artifactId, final String version, final String classifier)
        {
            super(groupId, artifactId, version);
            this.id = id;
            this.type = "installed";
            this.classifier = classifier;
        }

        /**
         * Embedded container packaged with Cargo.
         *
         * @param id identifier of container, eg. "jetty6x".
         */
        public Container(final String id)
        {
            this.id = id;
            this.type = "embedded";
            this.classifier = "";
        }

        /**
         * @return identifier of container.
         */
        public String getId()
        {
            return id;
        }

        /**
         * @return "installed" or "embedded".
         */
        public String getType()
        {
            return type;
        }

        /**
         * @return classifier the classifier of the ProductArtifact
         */
        public String getClassifier()
        {
            return classifier;
        }

        /**
         * @return <code>true</code> if the container type is "embedded".
         */
        public boolean isEmbedded()
        {
            return "embedded".equals(type);
        }

        /**
         * @param buildDir project.build.directory.
         * @return root directory of the container that will house the container installation and configuration.
         */
        public String getRootDirectory(String buildDir)
        {
            return buildDir + File.separator + "container" + File.separator + getId();
        }

        /**
         * @param buildDir project.build.directory.
         * @return directory housing the installed container.
         */
        public String getInstallDirectory(String buildDir)
        {
            String installDirectory = getRootDirectory(buildDir) + File.separator + getArtifactId() + "-";
            String version = getVersion();
            if (version.endsWith("-atlassian-hosted") && !new File(installDirectory + version).exists()) {
                version = version.substring(0, version.indexOf("-atlassian-hosted"));
            }
            return installDirectory + version;
        }

        /**
         * @param buildDir  project.build.directory.
         * @param productId product name.
         * @return directory to house the container configuration for the specified product.
         */
        public String getConfigDirectory(String buildDir, String productId)
        {
            return getRootDirectory(buildDir) + File.separator + "cargo-" + productId + "-home";
        }
    }
}