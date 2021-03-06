package com.atlassian.maven.plugins.amps.product;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.amps.MavenContext;
import com.atlassian.maven.plugins.amps.MavenGoals;
import com.atlassian.maven.plugins.amps.Product;
import com.atlassian.maven.plugins.amps.ProductArtifact;
import com.atlassian.maven.plugins.amps.util.VersionUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import org.apache.maven.artifact.factory.ArtifactFactory;

public class RefappProductHandler extends AbstractWebappProductHandler
{

    @VisibleForTesting
    static final String ATLASSIAN_BUNDLED_PLUGINS_ZIP = "WEB-INF/classes/atlassian-bundled-plugins.zip";
    @VisibleForTesting
    static final String ATLASSIAN_BUNDLED_PLUGINS_DIR = "WEB-INF/atlassian-bundled-plugins";

    public RefappProductHandler(MavenContext context, MavenGoals goals, ArtifactFactory artifactFactory)
    {
        super(context, goals, new RefappPluginProvider(),artifactFactory);
    }
    @Override
    public String getDefaultContainerId()
    {
        return "tomcat7x";
    }

    public String getId()
    {
        return "refapp";
    }

    @Override
    public int getDefaultHttpPort()
    {
        return 5990;
    }

    @Override
    public int getDefaultHttpsPort()
    {
        return 8445;
    }

    @Override
    protected File getUserInstalledPluginsDirectory(final Product product, final File webappDir, File homeDir)
    {
        return null;
    }

    @Override
    protected List<ProductArtifact> getExtraContainerDependencies()
    {
        return Collections.emptyList();
    }

    @Override
    protected File getBundledPluginPath(Product ctx, File appDir)
    {
        // if the bundled plugin directory exists, use it, otherwise fallback to the old zip behaviour.
        final File bundleDir = new File(appDir, ATLASSIAN_BUNDLED_PLUGINS_DIR);

        if (bundleDir.exists() && bundleDir.isDirectory())
        {
            return bundleDir;
        }
        return new File(appDir, ATLASSIAN_BUNDLED_PLUGINS_ZIP);
    }

    @Override
    protected List<ProductArtifact> getDefaultLibPlugins()
    {
        return Collections.emptyList();
    }

    @Override
    protected List<ProductArtifact> getDefaultBundledPlugins()
    {
        return Collections.emptyList();
    }

    @Override
    protected Map<String, String> getSystemProperties(Product ctx)
    {
        ImmutableMap.Builder<String, String> properties = ImmutableMap.<String, String>builder();
        properties.putAll(super.getSystemProperties(ctx));
        properties.put("refapp.home", getHomeDirectory(ctx).getPath());
        properties.put("osgi.cache", getHomeDirectory(ctx).getPath()+ "/osgi-cache");
        properties.put("bundledplugins.cache", getHomeDirectory(ctx).getPath()+ "/bundled-plugins");
        properties.put("cargo.servlet.uriencoding", "UTF-8");
        return properties.build();
    }

    @Override
    public ProductArtifact getArtifact()
    {
        return new ProductArtifact("com.atlassian.refapp", "atlassian-refapp", VersionUtils.getVersion());
    }

    @Override
    public ProductArtifact getTestResourcesArtifact()
    {
        return null;
    }

    private static class RefappPluginProvider extends AbstractPluginProvider
    {

        @Override
        protected Collection<ProductArtifact> getSalArtifacts(String salVersion)
        {
            return Arrays.asList(
                new ProductArtifact("com.atlassian.sal", "sal-api", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-appproperties-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-component-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-executor-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-lifecycle-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-message-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-net-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-pluginsettings-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-project-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-search-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-transaction-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-upgrade-plugin", salVersion),
                new ProductArtifact("com.atlassian.sal", "sal-refimpl-user-plugin", salVersion));
        }

        @Override
        protected Collection<ProductArtifact> getPdkInstallArtifacts(String pdkInstallVersion)
        {
            return Collections.emptyList();
        }
    }
}
