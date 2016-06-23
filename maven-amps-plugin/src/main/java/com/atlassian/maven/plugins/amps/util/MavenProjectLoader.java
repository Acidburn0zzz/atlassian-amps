package com.atlassian.maven.plugins.amps.util;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.util.Optional;

public class MavenProjectLoader
{
    /**
     * Resolve a given artifact with its dependencies
     * @param session
     * @param pomArtifact
     * @param incudeDependencies
     * @return  Optional.of(MavenProject) if the maven project for the given artifact and session can be resolved.
     *          Optional.empty() otherwise
     */
    public Optional<MavenProject> loadMavenProject(MavenSession session, Artifact pomArtifact, boolean incudeDependencies)
            throws MojoExecutionException
    {
        MavenExecutionRequest executionRequest = session.getRequest();
        try
        {
            ProjectBuilder projectBuilder = session.getContainer().lookup(ProjectBuilder.class);
            ProjectBuildingRequest projectBuildingRequest = executionRequest.getProjectBuildingRequest()
                    // The validation level is currently "VALIDATION_LEVEL_MAVEN_3_0".
                    // Setting it to minimal might speed things up a bit
                    .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                    // ResolveDependencies is currently "false".
                    // If the calling function needs dependencies, we need to tell maven about it
                    .setResolveDependencies(incudeDependencies);
            return Optional.ofNullable(projectBuilder.build(pomArtifact, projectBuildingRequest).getProject());
        } catch (ComponentLookupException e)
        {
            throw new MojoExecutionException("Could not get the ProjectBuilder from the maven session", e);
        } catch (ProjectBuildingException e)
        {
            throw new MojoExecutionException(String.format("Could not build the MavenProject for %s", pomArtifact), e);
        }
    }
}