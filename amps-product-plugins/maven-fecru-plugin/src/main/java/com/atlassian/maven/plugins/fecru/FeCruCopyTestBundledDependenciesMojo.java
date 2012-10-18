package com.atlassian.maven.plugins.fecru;

import com.atlassian.maven.plugins.amps.CopyTestBundledDependenciesMojo;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "copy-test-bundled-dependencies", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class FeCruCopyTestBundledDependenciesMojo extends CopyTestBundledDependenciesMojo
{

}
