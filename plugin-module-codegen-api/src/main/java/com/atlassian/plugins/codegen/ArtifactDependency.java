package com.atlassian.plugins.codegen;

import com.atlassian.fugue.Option;

import static com.atlassian.plugins.codegen.ArtifactId.artifactId;

import static com.atlassian.fugue.Option.none;
import static com.atlassian.fugue.Option.some;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Describes a dependency on an external artifact that should be added to the POM.
 */
public final class ArtifactDependency
{
    public enum Scope
    {
        DEFAULT,
        COMPILE,
        PROVIDED,
        TEST
    };

    private ArtifactId artifactId;
    private String version;
    private Option<String> propertyName;
    private Scope scope;
    
    /**
     * Creates a dependency descriptor whose version string is provided explicitly.
     * @param groupId  Maven group ID
     * @param artifactId  Maven artifact ID
     * @param version  the version string
     * @param scope  dependency scope (use DEfAULT to omit scope declaration)
     */
    public static ArtifactDependency dependency(String groupId, String artifactId, String version, Scope scope)
    {
        return new ArtifactDependency(artifactId(some(groupId), artifactId), version, none(String.class), scope);
    }

    /**
     * Creates a dependency descriptor whose version string is provided explicitly.
     * @param groupAndArtifactId  Maven group and artifact ID
     * @param version  the version string
     * @param scope  dependency scope (use DEfAULT to omit scope declaration)
     */
    public static ArtifactDependency dependency(ArtifactId groupAndArtifactId, String version, Scope scope)
    {
        return new ArtifactDependency(groupAndArtifactId, version, none(String.class), scope);
    }

    /**
     * Creates a dependency descriptor that uses a property placeholder for the version string.
     * For instance, if the version string is "1.0" and the property name is "foo.version", the
     * dependency in the POM will say "&lt;version&gt;${foo.version}&lt;/version&gt;", and
     * "&lt;foo.version&gt;1.0&lt;/foo.version&gt;" will be added to the &lt;properties&gt;
     * section of the POM if that property was not already defined.
     * @param groupId  Maven group ID
     * @param artifactId  Maven artifact ID
     * @param version  the default value to give to the property, if it does not already exist in the POM
     * @param propertyName  name of the property to use and/or create
     * @param scope  dependency scope (use DEfAULT to omit scope declaration)
     */
    public static ArtifactDependency dependency(String groupId, String artifactId, String version, String propertyName, Scope scope)
    {
        return new ArtifactDependency(artifactId(some(groupId), artifactId), version, some(propertyName), scope);
    }
    
    /**
     * Creates a dependency descriptor that uses a property placeholder for the version string.
     * @param groupAndArtifactId  Maven group and artifact ID
     * @param version  the default value to give to the property, if it does not already exist in the POM
     * @param propertyName  name of the property to use and/or create
     * @param scope  dependency scope (use DEfAULT to omit scope declaration)
     */
    public static ArtifactDependency dependency(ArtifactId groupAndArtifactId, String version, String propertyName, Scope scope)
    {
        return new ArtifactDependency(groupAndArtifactId, version, some(propertyName), scope);
    }
    
    private ArtifactDependency(ArtifactId artifactId, String version, Option<String> propertyName, Scope scope)
    {
        this.artifactId = checkNotNull(artifactId, "artifactId");
        if (!artifactId.getGroupId().isDefined())
        {
            throw new IllegalArgumentException("Group ID must be specified for dependency");
        }
        this.version = checkNotNull(version, "version");
        this.propertyName = checkNotNull(propertyName, "propertyName");
        this.scope = checkNotNull(scope, "scope");
    }
    
    public ArtifactId getGroupAndArtifactId()
    {
        return artifactId;
    }
    
    public String getVersion()
    {
        return version;
    }
    
    public Option<String> getPropertyName()
    {
        return propertyName;
    }
    
    public Scope getScope()
    {
        return scope;
    }
    
    @Override
    public String toString()
    {
        StringBuilder ret = new StringBuilder();
        ret.append("(");
        ret.append(artifactId.getCombinedId());
        ret.append(",");
        for (String p : propertyName)
        {
            ret.append("${");
            ret.append(p);
            ret.append("}=");
        }
        ret.append(version);
        ret.append(",");
        ret.append(scope.name());
        ret.append(")");
        return ret.toString();
    }
}
