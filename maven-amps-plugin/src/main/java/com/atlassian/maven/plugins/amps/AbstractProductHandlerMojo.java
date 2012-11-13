package com.atlassian.maven.plugins.amps;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.atlassian.maven.plugins.amps.product.ProductHandler;
import com.atlassian.maven.plugins.amps.product.ProductHandlerFactory;
import com.atlassian.maven.plugins.amps.product.studio.StudioProductHandler;
import com.atlassian.maven.plugins.amps.util.ArtifactRetriever;
import com.atlassian.maven.plugins.amps.util.ProjectUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.atlassian.maven.plugins.amps.product.ProductHandlerFactory.STUDIO;

/**
 * Base class for webapp mojos
 */
public abstract class AbstractProductHandlerMojo extends AbstractProductHandlerAwareMojo {

    // ------ start inline product context

    protected static final String DEFAULT_CONTAINER = "tomcat6x";
    protected static final String JUNIT_VERSION = "4.10_1";
    protected static final String ATLASSIAN_TEST_RUNNER_VERSION = "1.1-rc5";
    protected static final String NO_TEST_GROUP = "__no_test_group__";
    protected static final String DEFAULT_SERVER;
    protected static final String DEFAULT_PDK_VERSION = "0.4";
    protected static final String DEFAULT_WEB_CONSOLE_VERSION = "1.2.8";
    protected static final String DEFAULT_FASTDEV_VERSION = "2.0-rc5";
    protected static final String DEFAULT_DEV_TOOLBOX_VERSION = "2.0.4-rc3";
    protected static final String DEFAULT_PDE_VERSION = "1.2";

    /**
     *  The artifacts to deploy for the test console if needed
     */
    protected final List<ProductArtifact> testFrameworkPlugins = new ArrayList<ProductArtifact>()
    {{
            add(new ProductArtifact("org.apache.servicemix.bundles","org.apache.servicemix.bundles.junit",JUNIT_VERSION));
            add(new ProductArtifact("com.atlassian.plugins","atlassian-plugins-osgi-testrunner-bundle",ATLASSIAN_TEST_RUNNER_VERSION));
        }};
    

    /**
      * Default product startup timeout: three minutes
     */
    private static final int DEFAULT_PRODUCT_STARTUP_TIMEOUT = 1000 * 60 * 10;

    /**
      * Default product shutdown timeout: three minutes
      */
    private static final int DEFAULT_PRODUCT_SHUTDOWN_TIMEOUT = 1000 * 60 * 10;

    static
    {
        String localHostName = null;
        try
        {
            localHostName = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e)
        {
            localHostName = "localhost";
        }
        DEFAULT_SERVER = localHostName;
    }

    /**
     * Container to run in
     */
    @Parameter(property = "container", defaultValue = DEFAULT_CONTAINER)
    protected String containerId;

    /**
     * HTTP port for the servlet containers
     */
    @Parameter(property = "http.port", defaultValue = "0")
    private int httpPort;

    /**
     * If product should be started with https on port 443
     */
    @Parameter(property = "use.https", defaultValue = "false")
    protected boolean useHttps;

    /**
     * Application context path
     */
    @Parameter(property = "context.path")
    protected String contextPath;

    /**
     * Application server
     */
    @Parameter(property = "server")
    protected String server;

    /**
     * Webapp version
     */
    @Parameter(property = "product.version")
    private String productVersion;

    /**
     * JVM arguments to pass to cargo
     */
    @Parameter(property = "jvmargs")
    protected String jvmArgs;

    /**
     * Product startup timeout in milliseconds
     */
    @Parameter(property = "product.start.timeout")
    private int startupTimeout;

    /**
     * Product shutdown timeout in milliseconds
     */
    @Parameter(property = "product.stop.timeout")
    private int shutdownTimeout;

    /**
     * System systemProperties to pass to cargo
     *
     * @deprecated Since 3.2, use systemPropertyVariables instead
     */
    @Parameter
    @Deprecated
    protected Properties systemProperties = new Properties();

    /**
     * System Properties to pass to cargo using a more familiar syntax.
     *
     * @since 3.2
     */
    @Parameter
    protected Map<String, Object> systemPropertyVariables = new HashMap<String, Object>();


    /**
     * A log4j systemProperties file
     */
    @Parameter
    protected File log4jProperties;

    /**
     * The test resources version
     * @deprecated Since 3.0-beta2
     */
    @Deprecated
    @Parameter(property = "test.resources.version")
    private String testResourcesVersion;

    /**
     * The test resources version
     */
    @Parameter(property = "product.data.version")
    private String productDataVersion;

    /**
     * The path to a custom test resources zip
     */
    @Parameter(property = "product.data.path")
    private String productDataPath;

    /**
     * If FastDev should be enabled
     */
    @Parameter(property = "fastdev.enable", defaultValue = "true")
    protected boolean enableFastdev;

    /**
     * The version of FastDev to bundle
     */
    @Parameter(property = "fastdev.version", defaultValue = DEFAULT_FASTDEV_VERSION)
    protected String fastdevVersion;

    /**
     * If DevToolbox should be enabled
     */
    @Parameter(property = "devtoolbox.enable", defaultValue = "true")
    protected boolean enableDevToolbox;

    /**
     * The version of DevToolbox to bundle
     */
    @Parameter(property = "devtoolbox.version", defaultValue = DEFAULT_DEV_TOOLBOX_VERSION)
    protected String devToolboxVersion;

    /**
     * If PDE should be enabled
     */
    @Parameter(property = "pde.enable", defaultValue = "true")
    protected boolean enablePde;

    /**
     * The version of the PDE to bundle
     */
    @Parameter(property = "pde.version", defaultValue = DEFAULT_PDE_VERSION)
    protected String pdeVersion;

    @Parameter
    private List<ProductArtifact> pluginArtifacts = new ArrayList<ProductArtifact>();

    /**
     */
    @Parameter
    private List<ProductArtifact> libArtifacts = new ArrayList<ProductArtifact>();

    /**
     */
    @Parameter
    private List<ProductArtifact> bundledArtifacts = new ArrayList<ProductArtifact>();

    /**
     * SAL version
     * @deprecated Since 3.2, use {@link #pluginArtifacts} instead
     */
    @Deprecated
    @Parameter
    private String salVersion;

    /**
     * Atlassian Plugin Development Kit (PDK) version
     * @deprecated Since 3.2, use {@link #pluginArtifacts} instead
     */
    @Deprecated
    @Parameter(defaultValue = DEFAULT_PDK_VERSION)
    private String pdkVersion;

    /**
     * Atlassian REST module version
     * @deprecated Since 3.2, use {@link #pluginArtifacts} instead
     */
    @Deprecated
    @Parameter
    private String restVersion;


    /**
     * Felix OSGi web console version
     * @deprecated Since 3.2, use {@link #pluginArtifacts} instead
     */
    @Deprecated
    @Parameter(defaultValue =  DEFAULT_WEB_CONSOLE_VERSION)
    private String webConsoleVersion;

    // ---------------- end product context

    /**
     * Comma-delimited list of plugin artifacts in GROUP_ID:ARTIFACT_ID:VERSION form, where version can be
     * ommitted, defaulting to LATEST
     */
    @Parameter(property = "plugins")
    private String pluginArtifactsString;

    /**
     * Comma-delimited list of lib artifacts in GROUP_ID:ARTIFACT_ID:VERSION form, where version can be
     * ommitted, defaulting to LATEST
     */
    @Parameter(property = "lib.plugins")
    private String libArtifactsString;

    /**
     * Comma-delimited list of bundled plugin artifacts in GROUP_ID:ARTIFACT_ID:VERSION form, where version can be
     * ommitted, defaulting to LATEST
     */
    @Parameter(property = "bundled.plugins")
    private String bundledArtifactsString;

    /**
     * The build directory
     */
    @Parameter(property = "project.build.directory", required = true)
    protected File targetDirectory;

    /**
     * The jar name
     */
    @Parameter(property = "project.build.finalName", required = true)
    protected String finalName;

    /**
     * If the plugin and optionally its test plugin should be installed
     */
    @Parameter(property = "install.plugin", defaultValue = "true")
    protected boolean installPlugin;

    /**
     * The artifact resolver is used to dynamically resolve JARs that have to be in the embedded
     * container's classpaths. Another solution would have been to statitically define them a
     * dependencies in the plugin's POM. Resolving them in a dynamic manner is much better as only
     * the required JARs for the defined embedded container are downloaded.
     */
    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * The local Maven repository. This is used by the artifact resolver to download resolved
     * JARs and put them in the local repository so that they won't have to be fetched again next
     * time the plugin is executed.
     */
    @Parameter(property = "localRepository")
    protected ArtifactRepository localRepository;


    /**
     * The remote Maven repositories used by the artifact resolver to look for JARs.
     */
    @Parameter(property = "project.remoteArtifactRepositories")
    protected List repositories;

    /**
     * The artifact factory is used to create valid Maven
     * {@link org.apache.maven.artifact.Artifact} objects. This is used to pass Maven artifacts to
     * the artifact resolver so that it can download the required JARs to put in the embedded
     * container's classpaths.
     */
    @Component
    protected ArtifactFactory artifactFactory;

    /**
     * A list of product-specific configurations (as literally provided in the pom.xml)
     */
    @Parameter
    protected List<Product> products = new ArrayList<Product>();

    /**
     * A map of {instanceId -> Product}, initialized by {@link #createProductContexts()}.
     * Cannot be set by the user.
     */
    private Map<String, Product> productMap;

    /**
     * File the container logging output will be sent to.
     */
    @Parameter
    private String output;

    /**
     * Comma-delimited list of bundled plugin artifacts in GROUP_ID:ARTIFACT_ID:VERSION form, where version can be
     * ommitted, defaulting to LATEST
     */
    @Parameter(property = "additional.resource.folders")
    private String additionalResourceFolders;

    /**
     * Start the products in parallel (TestGroups and Studio).
     */
    @Parameter(property = "parallel", defaultValue = "false")
    protected boolean parallel;


    protected Product createDefaultProductContext() throws MojoExecutionException
    {
        Product ctx = new Product();
        ctx.setId(getProductId());
        ctx.setContainerId(containerId);
        ctx.setServer(server);
        ctx.setContextPath(contextPath);
        ctx.setJvmArgs(jvmArgs);
        ctx.setStartupTimeout(startupTimeout);
        ctx.setShutdownTimeout(shutdownTimeout);

        // If they aren't defined, define those system properties. They will override the product
        // handler's properties.
        Map<String, Object> properties = new HashMap<String, Object>(systemPropertyVariables);
        properties.put("atlassian.sdk.version", getSdkVersion());
        setDefaultSystemProperty(properties, "atlassian.dev.mode", "true");
        setDefaultSystemProperty(properties, "java.awt.headless", "true");
        setDefaultSystemProperty(properties, "plugin.resource.directories", buildResourcesList());
        setDefaultSystemProperty(properties, "plugin.root.directories", buildRootProperty());

        ctx.setSystemPropertyVariables(properties);
        ctx.setBundledArtifacts(bundledArtifacts);
        ctx.setLibArtifacts(libArtifacts);
        ctx.setPluginArtifacts(pluginArtifacts);
        ctx.setLog4jProperties(log4jProperties);
        ctx.setHttpPort(httpPort);
        ctx.setUseHttps(useHttps);

        ctx.setVersion(productVersion);
        ctx.setDataVersion(productDataVersion);
        ctx.setDataPath(productDataPath);

        // continue to have these work for now
        ctx.setRestVersion(restVersion);
        ctx.setSalVersion(salVersion);
        ctx.setPdkVersion(pdkVersion);
        ctx.setWebConsoleVersion(webConsoleVersion);

        ctx.setEnableFastdev(enableFastdev);
        ctx.setFastdevVersion(fastdevVersion);

        ctx.setEnableDevToolbox(enableDevToolbox);
        ctx.setDevToolboxVersion(devToolboxVersion);

        ctx.setEnablePde(enablePde);
        ctx.setPdeVersion(pdeVersion);

        ctx.setHttpPort(httpPort);
        return ctx;
    }

    /**
     * @return a comma-separated list of resource directories.  If a test plugin is detected, the
     * test resources directories are included as well.
     */
    private String buildResourcesList()
    {
        // collect all resource directories and make them available for
        // on-the-fly reloading
        StringBuilder resourceProp = new StringBuilder();
        if(StringUtils.isNotBlank(additionalResourceFolders))
        {
            String[] dirs = StringUtils.split(additionalResourceFolders,",");
            for(String rDir : dirs)
            {
                resourceProp.append(StringUtils.trim(rDir)).append(",");
            }
        }
        
        MavenProject mavenProject = getMavenContext().getProject();
        @SuppressWarnings("unchecked") List<Resource> resList = mavenProject.getResources();
        for (int i = 0; i < resList.size(); i++) {
            resourceProp.append(resList.get(i).getDirectory());
            if (i + 1 != resList.size()) {
                resourceProp.append(",");
            }
        }

        if (ProjectUtils.shouldDeployTestJar(getMavenContext()))
        {
            @SuppressWarnings("unchecked") List<Resource> testResList = mavenProject.getTestResources();
            for (int i = 0; i < testResList.size(); i++) {
                if (i == 0 && resourceProp.length() > 0)
                {
                    resourceProp.append(",");
                }
                resourceProp.append(testResList.get(i).getDirectory());
                if (i + 1 != testResList.size()) {
                    resourceProp.append(",");
                }
            }
        }
        return resourceProp.toString();
    }

    /**
     * @return the path of the project root, for the <tt>plugin.root.directories</tt> system property.
     *
     * @since 3.6
     */
    private String buildRootProperty()
    {
        MavenProject mavenProject = getMavenContext().getProject();
        return mavenProject.getBasedir().getPath();
    }

    private static void setDefaultSystemProperty(final Map<String,Object> props, final String key, final String value)
    {
        if (!props.containsKey(key))
        {
            props.put(key, System.getProperty(key, value));
        }
    }

    /**
     * Set the default values for the product
     * @param product the product
     * @param handler the product handler associated to the product
     */
    protected void setDefaultValues(Product product, ProductHandler handler)
    {
        product.setInstanceId(getProductInstanceId(product));

        //Apply the common default values
        String dversion = System.getProperty("product.data.version", product.getDataVersion());
        String pversion = System.getProperty("product.version", product.getVersion());
        String dpath = System.getProperty("product.data.path", product.getDataPath());

        // If it's a Studio product, some defaults are different (ex: context path for Confluence is /wiki)
        if (!StudioProductHandler.setDefaultValues(getMavenContext(), product))
        {
            // hacky workaround for AMPS-738:  avoid applying the regular product defaults to a Studio sub-product;
            // however, do apply them to the main Studio product if and only if we're explicitly running Studio (so
            // we'll get the right result for command-line options like "-Dproduct=studio -Dproduct.version=108.3").
            if (!STUDIO.equals(product.getId()) || STUDIO.equals(System.getProperty("product")))
            {
                product.setVersion(pversion);
	            product.setDataVersion(dversion);
                product.setDataPath(dpath);
            }
        }

        product.setArtifactRetriever(new ArtifactRetriever(artifactResolver, artifactFactory, localRepository, repositories));

        if (product.getContainerId() == null)
        {
            product.setContainerId(DEFAULT_CONTAINER);
        }

        if (product.getServer() == null)
        {
            product.setServer(DEFAULT_SERVER);
        }

        if (product.getPdkVersion() == null)
        {
            product.setPdkVersion(DEFAULT_PDK_VERSION);
        }

        if (product.getWebConsoleVersion() == null)
        {
            product.setWebConsoleVersion(DEFAULT_WEB_CONSOLE_VERSION);
        }

        if (product.isEnableFastdev() == null)
        {
            product.setEnableFastdev(true);
        }

        if (product.getFastdevVersion() == null)
        {
            product.setFastdevVersion(DEFAULT_FASTDEV_VERSION);
        }

        if (product.isEnableDevToolbox() == null)
        {
            product.setEnableDevToolbox(true);
        }

        if (product.getDevToolboxVersion() == null)
        {
            product.setDevToolboxVersion(DEFAULT_DEV_TOOLBOX_VERSION);
        }

        if (product.getPdeVersion() == null)
        {
            product.setPdeVersion(DEFAULT_PDE_VERSION);
        }

        if (product.getOutput() == null)
        {
            product.setOutput(output);
        }

        if (product.getStartupTimeout() <= 0)
        {
            product.setStartupTimeout(DEFAULT_PRODUCT_STARTUP_TIMEOUT);
        }

        if (product.getShutdownTimeout() <= 0)
        {
            product.setShutdownTimeout(DEFAULT_PRODUCT_SHUTDOWN_TIMEOUT);
        }

        if (product.getHttpPort() == 0)
        {
            product.setHttpPort(handler.getDefaultHttpPort());
        }

        if (product.getUseHttps() == null)
        {
            product.setUseHttps(false);
        }

        if (product.getVersion() == null)
        {
            product.setVersion("RELEASE");
        }

        if (product.getDataVersion() == null)
        {
            // Default the productDataVersion to match the productVersion. Defaulting to LATEST
            // is bad because there is no guarantee that a snapshots let alone a more recent
            // version of a product's data is compatible with an earlier version of the product or
            // that a product is required to provide a 'downgrade' task. Developers can still
            // specify LATEST explicitly
            product.setDataVersion(product.getVersion());
        }

        if (product.getContextPath() == null)
        {
            product.setContextPath("/" + handler.getId());
        }
        
        if (product.getDataSources() == null)
        {
            product.setDataSources(Lists.<DataSource>newArrayList());
        }
    }

    private List<ProductArtifact> stringToArtifactList(String val, List<ProductArtifact> artifacts)
    {
        if (val == null || val.trim().length() == 0)
        {
            return artifacts;
        }

        for (String ptn : val.split(","))
        {
            String[] items = ptn.split(":");
            if (items.length < 2 || items.length > 3)
            {
                throw new IllegalArgumentException("Invalid artifact pattern: " + ptn);
            }
            String groupId = items[0];
            String artifactId = items[1];
            String version = (items.length == 3 ? items[2] : "LATEST");
            artifacts.add(new ProductArtifact(groupId, artifactId, version));
        }
        return artifacts;
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        stringToArtifactList(pluginArtifactsString, pluginArtifacts);
        stringToArtifactList(libArtifactsString, libArtifacts);
        stringToArtifactList(bundledArtifactsString, bundledArtifacts);
        systemPropertyVariables.putAll((Map) systemProperties);

        detectDeprecatedVersionOverrides();

        doExecute();
    }

    private void detectDeprecatedVersionOverrides()
    {
        Properties props = getMavenContext().getProject().getProperties();
        for (String deprecatedProperty : new String[] {"sal.version", "rest.version", "web.console.version", "pdk.version"})
        {
            if (props.containsKey(deprecatedProperty))
            {
                getLog().warn("The property '" + deprecatedProperty + "' is no longer usable to override the related bundled plugin." +
                        "  Use <pluginArtifacts> or <libArtifacts> to explicitly override bundled plugins and libraries, respectively.");
            }
        }
    }

    /**
     * Builds the map {instanceId -> Product bean}, based on: <ul>
     * <li>the {@literal <products>} tag</li>
     * <li>the configuration values inherited from the {@literal <configuration>} tag
     * </ul>
     * @throws MojoExecutionException
     */
    Map<String, Product> createProductContexts() throws MojoExecutionException
    {
        Map<String, Product> productMap = Maps.newHashMap();
        MavenContext mavenContext = getMavenContext();
        MavenGoals goals = getMavenGoals();

        // Products in the <products> tag inherit from the upper settings, e.g. when there's a <httpPort> tag for all products
        makeProductsInheritDefaultConfiguration(products, productMap);

        for (Product ctx : Lists.newArrayList(productMap.values()))
        {
            ProductHandler handler = ProductHandlerFactory.create(ctx.getId(), mavenContext, goals);
            setDefaultValues(ctx, handler);

            // If it's a Studio product, check dependent instance are present
            for (String instanceId : StudioProductHandler.getDependantInstances(ctx))
            {
                if (!productMap.containsKey(instanceId))
                {
                    ProductHandler dependantHandler = createProductHandler(instanceId);
                    productMap.put(instanceId, createProductContext(instanceId, instanceId, dependantHandler));
                }
            }
        }

        // Submit the Studio products for configuration
        StudioProductHandler studioProductHandler = (StudioProductHandler) ProductHandlerFactory.create(ProductHandlerFactory.STUDIO, mavenContext, goals);
        studioProductHandler.configureStudioProducts(productMap);

        return productMap;
    }

    /**
     * Returns the map { instanceId -> Product } with initialized values.
     */
    protected Map<String, Product> getProductContexts() throws MojoExecutionException
    {
        if (productMap == null)
        {
            productMap = createProductContexts();
        }
        return productMap;
    }

    /**
     * Puts the list of {@literal <products>} in productMap:
     * <ul>
     * <li>The {@literal <product>} from the maven-amps-plugin configuration (if missing, RefApp is used)</li>
     * <li>The {@literal <products>} from the maven-amps-plugin configuration</li>
     * </ul>
     */
    void makeProductsInheritDefaultConfiguration(List<Product> products, Map<String, Product> productMap) throws MojoExecutionException
    {
        Product defaultProduct = createDefaultProductContext();
        productMap.put(getProductId(), defaultProduct);
        if (!products.isEmpty())
        {
            for (Product product : products)
            {
                Product processedProduct = product.merge(defaultProduct);
                if (ProductHandlerFactory.STUDIO_CROWD.equals(processedProduct.getId()))
                {
                    // This is a temporary fix for StudioCrowd - it requires atlassian.dev.mode=false - see AMPS-556
                    processedProduct.getSystemPropertyVariables().put("atlassian.dev.mode", "false");
                }
                String instanceId = getProductInstanceId(processedProduct);
                productMap.put(instanceId, processedProduct);
            }
        }
    }

    private String getProductInstanceId(Product processedProduct)
    {
        return processedProduct.getInstanceId() == null ? processedProduct.getId() : processedProduct.getInstanceId();
    }


    private Product createProductContext(String productNickname, String instanceId, ProductHandler handler) throws MojoExecutionException
    {
        getLog().info(String.format("Studio (instanceId=%s): No product with name %s is defined in the pom. Using a default product.", instanceId, productNickname));
        Product product;
        product = createDefaultProductContext();
        product.setId(productNickname);
        product.setInstanceId(instanceId);
        setDefaultValues(product, handler);
        if (ProductHandlerFactory.STUDIO_CROWD.equals(product.getId()))
        {
            // This is a temporary fix for StudioCrowd - it requires atlassian.dev.mode=false - see AMPS-556
            product.getSystemPropertyVariables().put("atlassian.dev.mode", "false");
        }
        return product;
    }

    /**
     * Attempts to stop all products. Returns after the timeout or as soon as all products
     * are shut down.
     */
    protected void stopProducts(List<ProductExecution> productExecutions) throws MojoExecutionException
    {
        ExecutorService executor = Executors.newFixedThreadPool(productExecutions.size());
        try
        {
            long before = System.nanoTime();
            for (final ProductExecution execution : Iterables.reverse(productExecutions))
            {
                final Product product = execution.getProduct();
                final ProductHandler productHandler = execution.getProductHandler();

                Future<?> task = executor.submit(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        getLog().info(product.getInstanceId() + ": Shutting down");
                        try
                        {
                            productHandler.stop(product);
                        }
                        catch (MojoExecutionException e)
                        {
                            getLog().error("Exception while trying to stop " + product.getInstanceId(), e);
                        }
                    }
                });

                try
                {
                    task.get(product.getShutdownTimeout(), TimeUnit.MILLISECONDS);
                }
                catch (TimeoutException e)
                {
                    getLog().info(product.getInstanceId() + " shutdown: Didn't return in time");
                    task.cancel(true);
                }
            }
            long after = System.nanoTime();
            getLog().info("amps:stop in " + TimeUnit.NANOSECONDS.toSeconds(after - before) + "s");
        }
        catch (InterruptedException e1)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e)
        {
            throw new MojoExecutionException("Exception while stopping the products", e);
        }

        // If products were launched in parallel, check they are stopped: CodeHaus Cargo returns before
        // products are down.
        if (parallel)
        {
            waitForProducts(productExecutions, false);
        }
    }


    /**
     * Waits until all products are running or stopped
     * @param startingUp true if starting up the products, false if shutting down.
     */
    protected void waitForProducts(List<ProductExecution> productExecutions, boolean startingUp) throws MojoExecutionException
    {
        for (ProductExecution productExecution : productExecutions)
        {
            pingRepeatedly(productExecution.getProduct(), startingUp);
        }
    }

    /**
     * Ping the product until it's up or stopped
     * @param startingUp true if applications are expected to be up; false if applications are expected to be brought down
     * @throws MojoExecutionException if the product didn't have the expected behaviour beofre the timeout
     */
    private void pingRepeatedly(Product product, boolean startingUp) throws MojoExecutionException
    {
        if (product.getHttpPort() != 0)
        {
            String url = "http://" + product.getServer() + ":" + product.getHttpPort();
            if (StringUtils.isNotBlank(product.getContextPath()))
            {
                url = url + product.getContextPath();
            }

            int timeout = startingUp ? product.getStartupTimeout() : product.getShutdownTimeout();
            final long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            boolean interrupted = false;
            boolean success = false;
            String lastMessage = "";

            // keep retrieving from the url until a good response is returned, under a time limit.
            while (!success && !interrupted && System.nanoTime() < end)
            {
                HttpURLConnection connection = null;
                try
                {
                    URL urlToPing = new URL(url);
                    connection = (HttpURLConnection) urlToPing.openConnection();
                    int response = connection.getResponseCode();
                    // Tomcat returns 404 until the webapp is up
                    lastMessage = "Last response code is " + response;
                    if (startingUp)
                    {
                        success = response < 400;
                    }
                    else
                    {
                        success = response >= 400;
                    }
                }
                catch (IOException e)
                {
                    lastMessage = e.getMessage();
                    success = !startingUp;
                }
                finally
                {
                    if (connection != null)
                    {
                        try
                        {
                            connection.getInputStream().close();
                        }
                        catch (IOException e)
                        {
                            // Don't do anything
                        }
                    }
                }

                if (!success)
                {
                    getLog().info("Waiting for " + url + (startingUp ? "" : " to stop"));
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        interrupted = true;
                        break;
                    }
                }
            }

            if (!success)
            {
                throw new MojoExecutionException(String.format("The product %s didn't %s after %ds at %s. %s",
                        product.getInstanceId(), startingUp ? "start" : "stop", TimeUnit.MILLISECONDS.toSeconds(timeout), url, lastMessage));
            }
        }
    }

    /**
     * @return the list of instances for the product 'studio'
     */
    private Iterable<ProductExecution> getStudioExecutions(final List<ProductExecution> productExecutions)
    {
        return Iterables.filter(productExecutions, new Predicate<ProductExecution>(){

            @Override
            public boolean apply(ProductExecution input)
            {
                return input.getProductHandler() instanceof StudioProductHandler;
            }});
    }


    /**
     * If there is any Studio instance, returns a list with all products requested by this instance.
     *
     * Configures both the Studio instance and its dependent products.
     *
     * @param productExecutions the current list of products to run
     * @param goals
     * @return the complete list of products to run
     * @throws MojoExecutionException
     */
    protected List<ProductExecution> includeStudioDependentProducts(final List<ProductExecution> productExecutions, final MavenGoals goals)
            throws MojoExecutionException
    {
        // If one of the products is Studio, ask him/her which other products he/she wants to run
        Iterable<ProductExecution> studioExecutions = getStudioExecutions(productExecutions);
        if (Iterables.isEmpty(studioExecutions))
        {
            return productExecutions;
        }

        // We have studio execution(s), so we need to add all products requested by Studio
        List<ProductExecution> productExecutionsIncludingStudio = Lists.newArrayList(productExecutions);
        Map<String, Product> allContexts = getProductContexts();
        for(ProductExecution execution : studioExecutions)
        {
            for (String dependantProduct : StudioProductHandler.getDependantInstances(execution.getProduct()))
            {
                Product product = allContexts.get(dependantProduct);
                productExecutionsIncludingStudio.add(toProductExecution(product));
            }
        }

        return productExecutionsIncludingStudio;
    }

    protected ProductExecution toProductExecution(Product product)
    {
        return new ProductExecution(product, createProductHandler(product.getId()));
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected void setParallelMode(List<ProductExecution> executions)
    {
        // Apply the configuration of the mojo to the products
        for (ProductExecution execution : executions)
        {
            Product product = execution.getProduct();
            if (parallel)
            {
                if (product.getSynchronousStartup() == null)
                {
                    product.setSynchronousStartup(Boolean.FALSE);
                }
            }
            else
            {
                product.setSynchronousStartup(Boolean.TRUE);
            }
        }
    }
}
