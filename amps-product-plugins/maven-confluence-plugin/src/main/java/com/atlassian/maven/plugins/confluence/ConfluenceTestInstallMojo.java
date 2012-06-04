package com.atlassian.maven.plugins.confluence;

import com.atlassian.maven.plugins.amps.pdk.TestInstallMojo;
import com.atlassian.maven.plugins.amps.product.ProductHandlerFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "test-install")
public class ConfluenceTestInstallMojo extends TestInstallMojo
{
    @Override
    protected String getDefaultProductId() throws MojoExecutionException
    {
        return ProductHandlerFactory.CONFLUENCE;
    }
}
