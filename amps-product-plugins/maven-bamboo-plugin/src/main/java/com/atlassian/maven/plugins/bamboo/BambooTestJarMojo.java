package com.atlassian.maven.plugins.bamboo;

import com.atlassian.maven.plugins.amps.TestJarMojo;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "test-jar",requiresDependencyResolution = ResolutionScope.TEST)
public class BambooTestJarMojo extends TestJarMojo
{
}
