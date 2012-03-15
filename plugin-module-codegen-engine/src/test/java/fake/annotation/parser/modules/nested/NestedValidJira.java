package fake.annotation.parser.modules.nested;

import com.atlassian.plugins.codegen.PluginProjectChangeset;
import com.atlassian.plugins.codegen.annotations.JiraPluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleProperties;

/**
 * @since 3.6
 */
@JiraPluginModuleCreator
public class NestedValidJira implements PluginModuleCreator
{
    public static final String MODULE_NAME = "Nested Valid Jira Module";

    @Override
    public String getModuleName()
    {
        return MODULE_NAME;
    }

    @Override
    public PluginProjectChangeset createModule(PluginModuleProperties props) throws Exception
    {
        return new PluginProjectChangeset();
    }
}
