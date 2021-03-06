package com.atlassian.maven.plugins.amps.cli;

import com.atlassian.maven.plugins.amps.AbstractAmpsMojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "idea", requiresProject = false)
public class IdeaMojo extends AbstractAmpsMojo
{
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        getMavenGoals().installIdeaPlugin();
    }
}