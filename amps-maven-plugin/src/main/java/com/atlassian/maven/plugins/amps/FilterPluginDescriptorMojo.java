package com.atlassian.maven.plugins.amps;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "filter-plugin-descriptor")
public class FilterPluginDescriptorMojo extends AbstractAmpsMojo
{
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        getMavenGoals().filterPluginDescriptor();
    }
}
