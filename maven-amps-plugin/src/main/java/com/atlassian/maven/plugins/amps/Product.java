package com.atlassian.maven.plugins.amps;

import com.atlassian.maven.plugins.amps.product.studio.StudioProperties;
import com.atlassian.maven.plugins.amps.util.ArtifactRetriever;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

public class Product
{
    /**
     * Container to run in
     */
    protected String containerId;

    /**
     * HTTP port for the servlet containers
     */
    private int httpPort = 0;

    /**
     * Application context path
     */
    protected String contextPath;

    /**
     * Application server
     */
    protected String server;

    /**
     * Webapp version
     */
    protected String version;

    /**
     * JVM arguments to pass to cargo
     */
    protected String jvmArgs = null;

    /**
     * A log4j properties file
     */
    protected File log4jProperties;

    /**
     * The test resources version
     */
    protected String productDataVersion;

    /**
     * The path to a custom test resources zip
     */
    protected String productDataPath = "";

    /**
     */
    private List<ProductArtifact> pluginArtifacts = new ArrayList<ProductArtifact>();

    /**
     */
    private List<ProductArtifact> libArtifacts = new ArrayList<ProductArtifact>();

    /**
     */
    private List<ProductArtifact> bundledArtifacts = new ArrayList<ProductArtifact>();

    /**
     * SAL version
     */
    private String salVersion;

    /**
     * Atlassian Plugin Development Kit (PDK) version
     */
    private String pdkVersion;

    /**
     * Atlassian REST module version
     */
    private String restVersion;

    /**
     * Version of the Felix OSGi web console
     */
    private String webConsoleVersion;

    /**
     * Product id - nickname of the product to run
     */
    private String id;

    /**
     * The name of the instance of the product
     */
    private String instanceId;

    private ArtifactRetriever artifactRetriever;

    /**
     * Flag to indicate whether or not to install the plugin
     */
    private Boolean installPlugin;

    /**
     * The system properties to set for the product
     */
    private Map<String,Object> systemProperties = new HashMap<String,Object>();

    /**
     * File the container should log to.
     */
    private String output;

    /**
     * Port for debugging
     */
    private int jvmDebugPort;

    /**
     * How long to wait for product startup, in milliseconds; if not specified, default is determined by AbstractProductHandlerMojo
     */
    private int startupTimeout = 0;

    /**
     * How long to wait for product shutdown, in milliseconds; if not specified, default is determined by AbstractProductHandlerMojo
     */
    private int shutdownTimeout = 0;
    
    /**
     * An optional override of the webapp's groupId
     */
    private String groupId;
    
    /**
     * An optional override of the webapp's artifactId
     */
    private String artifactId;
    

    
    /**
     * The studio configuration which is shared for all products in the same
     * studio instance. Null if products are not studio or not yet configured.
     * <p>
     * {@link StudioProductHandler#configure(Product, List)} will set this value.
     * It must be called before Studio products are launched.  
     */
    protected StudioProperties studioProperties;


    /**
     * Only applies to Studio
     * List of 'sub'-products that are managed by this Studio instance.
     * Optional. Default value is: studio-crowd, studio-confluence, studio-fecru, studio-bamboo, studio-jira.
     */
    protected List<String> instanceIds = new ArrayList<String>();

    /**
     * Only applies to Studio.
     * The data to use to initialize Studio Home. Folder or zip.
     */
    protected String studioHomeData;

    /**
     * Only applies to Studio.
     * The data to use to initialize the svn root. Folder or zip.
     */
    protected String svnRootData;

    /**
     * Only applies to Studio.
     * The data to use to initialize the webdav home. Folder or zip.
     */
    protected String webDavData;
    
    /**
     * Only applies to Studio
     * Set 'true' if GApps is enabled. Default is 'false'
     */
    protected String gappsEnabled;

    /**
     * Only applies to Studio
     * The GApps domain, if GApps is enabled
     */
    protected String gappsDomain;


    
    /**
     * Creates a new product that is merged with this one, where the properties in this one override the passed
     * in product.
     * @param product The product to merge with
     * @return A new product
     */
    public Product merge(Product product)
    {
        Product prod = new Product();
        prod.setOutput(output == null ? product.getOutput() : output);

        Map<String,Object> sysProps = new HashMap<String,Object>();
        sysProps.putAll(product.getSystemPropertyVariables());
        sysProps.putAll(systemProperties);
        prod.setSystemPropertyVariables(sysProps);

        prod.setInstallPlugin(installPlugin == null ? product.isInstallPlugin() : installPlugin);
        prod.setArtifactRetriever(artifactRetriever == null ? product.getArtifactRetriever() : artifactRetriever);
        prod.setId(id == null ? product.getId() : id);
        prod.setInstanceId(instanceId == null ? product.getInstanceId() : instanceId);
        prod.setWebConsoleVersion(webConsoleVersion == null ? product.getWebConsoleVersion() : webConsoleVersion);
        prod.setRestVersion(restVersion == null ? product.getRestVersion() : restVersion);
        prod.setPdkVersion(pdkVersion == null ? product.getPdkVersion() : pdkVersion);
        prod.setSalVersion(salVersion == null ? product.getSalVersion() : salVersion);
        
        prod.setBundledArtifacts(bundledArtifacts.isEmpty() ? product.getBundledArtifacts() : bundledArtifacts);
        prod.setPluginArtifacts(pluginArtifacts.isEmpty() ? product.getPluginArtifacts() : pluginArtifacts);
        prod.setLibArtifacts(libArtifacts.isEmpty() ? product.getLibArtifacts() : libArtifacts);

        prod.setDataPath(productDataPath.length() == 0 ? product.getDataPath() : productDataPath);
        prod.setDataVersion(productDataVersion == null ? product.getDataVersion() : productDataVersion);
        prod.setLog4jProperties(log4jProperties == null ? product.getLog4jProperties() : log4jProperties);
        prod.setJvmArgs(jvmArgs == null ? product.getJvmArgs() : jvmArgs);
        prod.setVersion(groupId == null ? product.getGroupId() : groupId);
        prod.setVersion(artifactId == null ? product.getArtifactId() : artifactId);
        prod.setVersion(version == null ? product.getVersion() : version);

        prod.setServer(server == null ? product.getServer() : server);
        prod.setContextPath(contextPath == null ? product.getContextPath() : contextPath);
        prod.setContainerId(containerId == null ? product.getContainerId() : containerId);
        prod.setHttpPort(httpPort == 0 ? product.getHttpPort() : httpPort);
        prod.setJvmDebugPort(jvmDebugPort == 0 ? product.getJvmDebugPort() : jvmDebugPort);

        prod.setStartupTimeout(startupTimeout == 0 ? product.getStartupTimeout() : startupTimeout);
        prod.setShutdownTimeout(shutdownTimeout == 0 ? product.getShutdownTimeout() : shutdownTimeout);
        
        // Studio-related properties
        prod.setStudioProperties(studioProperties == null ? product.getStudioProperties() : studioProperties);
        prod.setInstanceIds(instanceIds == null ? product.getInstanceIds() : instanceIds);

        prod.setSvnRootData(svnRootData == null ? product.getSvnRootData() : svnRootData);
        prod.setWebDavData(webDavData == null ? product.getWebDavData() : webDavData);
        prod.setStudioHomeData(studioHomeData == null ? product.getStudioHomeData() : studioHomeData);
        
        return prod;
    }

    public String getContainerId()
    {
        return containerId;
    }

    public void setContainerId(String containerId)
    {
        this.containerId = containerId;
    }

    public String getServer()
    {
        return server;
    }

    public void setServer(String server)
    {
        this.server = server;
    }

    public int getHttpPort()
    {
        return httpPort;
    }

    public void setHttpPort(int httpPort)
    {
        this.httpPort = httpPort;
    }

    public String getContextPath()
    {
        return contextPath;
    }

    public void setContextPath(String contextPath)
    {
        this.contextPath = contextPath;
    }

    public String getJvmArgs()
    {
        return jvmArgs;
    }

    public void setJvmArgs(String jvmArgs)
    {
        this.jvmArgs = jvmArgs;
    }

    public ArtifactRetriever getArtifactRetriever()
    {
        return artifactRetriever;
    }

    public void setArtifactRetriever(ArtifactRetriever artifactRetriever)
    {
        this.artifactRetriever = artifactRetriever;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getDataVersion()
    {
        return productDataVersion;
    }

    public void setDataVersion(String productDataVersion)
    {
        this.productDataVersion = productDataVersion;
    }

    /**
     * @deprecated since 3.2
     */
    public String getProductDataVersion()
    {
        return productDataVersion;
    }

    /**
     * @deprecated since 3.2
     */
    public void setProductDataVersion(String productDataVersion)
    {
        this.productDataVersion = productDataVersion;
    }

    public String getDataPath()
    {
        return productDataPath;
    }

    public void setDataPath(String productDataPath)
    {
        this.productDataPath = productDataPath;
    }

    /**
     * @deprecated since 3.2
     */
    public String getProductDataPath()
    {
        return productDataPath;
    }

    /**
     * @deprecated since 3.2
     */
    public void setProductDataPath(String productDataPath)
    {
        this.productDataPath = productDataPath;
    }

    public List<ProductArtifact> getPluginArtifacts()
    {
        return pluginArtifacts;
    }

    public void setPluginArtifacts(List<ProductArtifact> pluginArtifacts)
    {
        this.pluginArtifacts = pluginArtifacts;
    }

    public List<ProductArtifact> getLibArtifacts()
    {
        return libArtifacts;
    }

    public void setLibArtifacts(List<ProductArtifact> libArtifacts)
    {
        this.libArtifacts = libArtifacts;
    }

    public List<ProductArtifact> getBundledArtifacts()
    {
        return bundledArtifacts;
    }

    public void setBundledArtifacts(List<ProductArtifact> bundledArtifacts)
    {
        this.bundledArtifacts = bundledArtifacts;
    }

    public File getLog4jProperties()
    {
        return log4jProperties;
    }

    public void setLog4jProperties(File log4jProperties)
    {
        this.log4jProperties = log4jProperties;
    }

    public String getRestVersion()
    {
        return restVersion;
    }

    public void setRestVersion(String restVersion)
    {
        this.restVersion = restVersion;
    }

    public String getSalVersion()
    {
        return salVersion;
    }

    public void setSalVersion(String salVersion)
    {
        this.salVersion = salVersion;
    }

    public String getPdkVersion()
    {
        return pdkVersion;
    }

    public void setPdkVersion(String pdkVersion)
    {
        this.pdkVersion = pdkVersion;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(String instanceId)
    {
        this.instanceId = instanceId;
    }

    public Boolean isInstallPlugin()
    {
        return installPlugin;
    }

    public void setInstallPlugin(final Boolean installPlugin)
    {
        this.installPlugin = installPlugin;
    }

    public String getWebConsoleVersion()
    {
        return webConsoleVersion;
    }

    public void setWebConsoleVersion(String webConsoleVersion)
    {
        this.webConsoleVersion = webConsoleVersion;
    }

    /**
     * @deprecated Since 3.2, use systemPropertyVariables
     */
    public void setSystemProperties(Properties systemProperties)
    {
        this.systemProperties.putAll((Map) systemProperties);
    }

    /**
     * @deprecated Since 3.2, use systemPropertyVariables
     */
    public Properties getSystemProperties()
    {
        Properties props = new Properties();
        props.putAll(systemProperties);
        return props;
    }

    public void setSystemPropertyVariables(Map<String,Object> systemProperties)
    {
        this.systemProperties = systemProperties;
    }

    public Map<String,Object> getSystemPropertyVariables()
    {
        return systemProperties;
    }

    public String getOutput()
    {
        return output;
    }
    
    public void setOutput(String output)
    {
        this.output = output;
    }

    public int getJvmDebugPort()
    {
        return jvmDebugPort;
    }

    public void setJvmDebugPort(int jvmDebugPort)
    {
        this.jvmDebugPort = jvmDebugPort;
    }
    
    public int getStartupTimeout()
    {
        return startupTimeout;
    }
    
    public void setStartupTimeout(int startupTimeout)
    {
        this.startupTimeout = startupTimeout;
    }
    
    public int getShutdownTimeout()
    {
        return shutdownTimeout;
    }
    
    public void setShutdownTimeout(int shutdownTimeout)
    {
        this.shutdownTimeout = shutdownTimeout;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public void setGroupId(String groupId)
    {
        this.groupId = groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public void setArtifactId(String artifactId)
    {
        this.artifactId = artifactId;
    }

    public StudioProperties getStudioProperties()
    {
        return studioProperties;
    }

    public void setStudioProperties(StudioProperties studioProperties)
    {
        this.studioProperties = studioProperties;
    }

    public List<String> getInstanceIds()
    {
        return instanceIds;
    }

    public void setInstanceIds(List<String> instanceIds)
    {
        this.instanceIds = instanceIds;
    }

    public String getStudioHomeData()
    {
        return studioHomeData;
    }

    public void setStudioHomeData(String studioHomeData)
    {
        this.studioHomeData = studioHomeData;
    }

    public String getSvnRootData()
    {
        return svnRootData;
    }

    public void setSvnRootData(String svnRootData)
    {
        this.svnRootData = svnRootData;
    }

    public String getWebDavData()
    {
        return webDavData;
    }

    public void setWebDavData(String webDavData)
    {
        this.webDavData = webDavData;
    }

    public String getGappsEnabled()
    {
        return gappsEnabled;
    }

    public void setGappsEnabled(String gappsEnabled)
    {
        this.gappsEnabled = gappsEnabled;
    }

    public String getGappsDomain()
    {
        return gappsDomain;
    }

    public void setGappsDomain(String gappsDomain)
    {
        this.gappsDomain = gappsDomain;
    }

    public void setSystemProperties(Map<String, Object> systemProperties)
    {
        this.systemProperties = systemProperties;
    }

    @Override
    public String toString()
    {
        return "Product " + id + " [instanceId=" + instanceId + ", localhost:" + httpPort + "/" + contextPath + "]";
    }
    
}
