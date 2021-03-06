package com.atlassian.maven.plugins.jira;

import com.atlassian.maven.plugins.amps.CopyBundledDependenciesMojo;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "copy-bundled-dependencies", requiresDependencyResolution = ResolutionScope.TEST)
public class JiraCopyBundledDependenciesMojo extends CopyBundledDependenciesMojo
{
}
