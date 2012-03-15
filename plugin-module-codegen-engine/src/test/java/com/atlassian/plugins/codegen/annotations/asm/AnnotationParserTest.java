package com.atlassian.plugins.codegen.annotations.asm;

import java.util.Map;

import com.atlassian.plugins.codegen.PluginModuleCreatorRegistryImpl;
import com.atlassian.plugins.codegen.modules.PluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleCreatorRegistry;

import org.junit.Before;
import org.junit.Test;

import fake.annotation.parser.modules.InheritedValidJira;
import fake.annotation.parser.modules.JiraAndConfluenceCreator;
import fake.annotation.parser.modules.JiraAnnotatedWithoutInterface;
import fake.annotation.parser.modules.ValidJiraModuleCreator;
import fake.annotation.parser.modules.nested.NestedValidJira;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @since 3.6
 */
public class AnnotationParserTest
{

    private static final String MODULES_PACKAGE = "fake.annotation.parser.modules";

    private PluginModuleCreatorRegistry registry;
    private ModuleCreatorAnnotationParser parser;

    @Before
    public void setup()
    {
        registry = new PluginModuleCreatorRegistryImpl();
        parser = new ModuleCreatorAnnotationParser(registry);
    }

    @Test
    public void hasJiraModule() throws Exception
    {

        parser.parse(MODULES_PACKAGE);

        Map<Class, PluginModuleCreator> modules = registry.getModuleCreatorsForProduct(PluginModuleCreatorRegistry.JIRA);
        assertNotNull("module map is null", modules);
        assertTrue("no testmodules registered", modules.size() > 0);
        assertTrue("jira module not found", modules.containsKey(ValidJiraModuleCreator.class));
    }

    @Test
    public void annotatedWithoutInterfaceIsNotRegistered() throws Exception
    {
        parser.parse(MODULES_PACKAGE);

        Map<Class, PluginModuleCreator> modules = registry.getModuleCreatorsForProduct(PluginModuleCreatorRegistry.JIRA);
        assertNotNull("module map is null", modules);
        assertTrue("no testmodules registered", modules.size() > 0);
        assertTrue("non-module found", !modules.containsKey(JiraAnnotatedWithoutInterface.class));
    }

    @Test
    public void nestedCreatorsAreRegistered() throws Exception
    {
        parser.parse(MODULES_PACKAGE);

        Map<Class, PluginModuleCreator> modules = registry.getModuleCreatorsForProduct(PluginModuleCreatorRegistry.JIRA);
        assertNotNull("module map is null", modules);
        assertTrue("no testmodules registered", modules.size() > 0);
        assertTrue("nested jira module not found", modules.containsKey(NestedValidJira.class));
    }

    @Test
    public void inheritedCreatorsAreRegistered() throws Exception
    {
        parser.parse(MODULES_PACKAGE);

        Map<Class, PluginModuleCreator> modules = registry.getModuleCreatorsForProduct(PluginModuleCreatorRegistry.JIRA);
        assertNotNull("module map is null", modules);
        assertTrue("no testmodules registered", modules.size() > 0);
        assertTrue("inherited jira module not found", modules.containsKey(InheritedValidJira.class));
    }

    @Test
    public void muiltipleProductsHaveSameCreator() throws Exception
    {
        parser.parse(MODULES_PACKAGE);

        Map<Class, PluginModuleCreator> jiraModules = registry.getModuleCreatorsForProduct(PluginModuleCreatorRegistry.JIRA);
        Map<Class, PluginModuleCreator> confluenceModules = registry.getModuleCreatorsForProduct(PluginModuleCreatorRegistry.CONFLUENCE);

        assertTrue("jiraAndConfluence not found for jira", jiraModules.containsKey(JiraAndConfluenceCreator.class));
        assertTrue("jiraAndConfluence not found for confluence", confluenceModules.containsKey(JiraAndConfluenceCreator.class));
    }
}
