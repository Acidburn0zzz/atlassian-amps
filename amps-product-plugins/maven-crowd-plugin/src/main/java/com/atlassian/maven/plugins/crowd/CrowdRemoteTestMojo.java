package com.atlassian.maven.plugins.crowd;

import com.atlassian.maven.plugins.amps.RemoteTestMojo;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "remote-test", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class CrowdRemoteTestMojo extends RemoteTestMojo
{

}
