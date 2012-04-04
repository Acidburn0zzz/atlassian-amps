package fake.jar.annotation.parser.modules.dependencies;

import com.atlassian.plugins.codegen.ArtifactDependency.Scope;
import com.atlassian.plugins.codegen.PluginProjectChangeset;
import com.atlassian.plugins.codegen.annotations.JiraPluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleProperties;

import static com.atlassian.plugins.codegen.ArtifactDependency.dependency;

/**
 * @since 3.5
 */
@JiraPluginModuleCreator
public class JARValidJiraWithMissingScopeDependency implements PluginModuleCreator<PluginModuleProperties> {
    public static final String MODULE_NAME = "Valid Jira Module With Missing Scope Dependency";

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public PluginProjectChangeset createModule(PluginModuleProperties props) throws Exception {
        return new PluginProjectChangeset()
            .with(dependency("javax.servlet", "servlet-api", "2.4", Scope.DEFAULT));
    }
}
