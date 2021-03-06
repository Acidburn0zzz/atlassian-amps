package com.atlassian.maven.plugins.amps.codegen.prompter.confluence.blueprint;

import com.atlassian.maven.plugins.amps.codegen.prompter.AbstractPrompterTest;
import com.atlassian.maven.plugins.amps.codegen.prompter.PluginModulePrompter;
import com.atlassian.plugins.codegen.modules.confluence.blueprint.BlueprintBuilder;
import com.atlassian.plugins.codegen.modules.confluence.blueprint.BlueprintPromptEntries;
import com.atlassian.plugins.codegen.modules.confluence.blueprint.BlueprintPromptEntry;
import com.atlassian.plugins.codegen.modules.confluence.blueprint.BlueprintProperties;
import com.atlassian.plugins.codegen.modules.confluence.blueprint.BlueprintStringer;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.atlassian.plugins.codegen.modules.confluence.blueprint.BlueprintPromptEntry.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The logic for creating the {@link BlueprintProperties} object is in the {@link BlueprintBuilder} class, so this
 * test class only checks that the correct prompts exists and that the simple Properties object is filled correctly.
 *
 * @since 4.1.8
 */
public class BlueprintPrompterTest extends AbstractPrompterTest
{
    private String webItemName = "FooPrint";
    private String webItemDesc = "There's no Blueprint like my FooPrint.";
    private String templateModuleKey = "foo-plate";
    private String blueprintIndexKey = "foo-print";

    private BlueprintPrompter modulePrompter;

    @Before
    public void setup() throws PrompterException
    {
        modulePrompter = new BlueprintPrompter(prompter);

        BlueprintStringer stringer = new BlueprintStringer(blueprintIndexKey, "com.foo.plugin");

        when(prompter.prompt(WEB_ITEM_NAME_PROMPT.message(), WEB_ITEM_NAME_PROMPT.defaultValue())).thenReturn(webItemName);
        when(prompter.prompt(eq(WEB_ITEM_DESC_PROMPT.message()), anyString())).thenReturn(webItemDesc);
        when(prompter.prompt(eq(INDEX_KEY_PROMPT.message()), anyString())).thenReturn(blueprintIndexKey);
        when(prompter.prompt(CONTENT_TEMPLATE_KEYS_PROMPT.message(), stringer.makeContentTemplateKey(0))).thenReturn(templateModuleKey);

        mockBooleanPromptResponse(ANOTHER_CONTENT_TEMPLATE_KEY_PROMPT, "N");
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "N");
        mockBooleanPromptResponse(HOW_TO_USE_PROMPT, "N");
        mockBooleanPromptResponse(DIALOG_WIZARD_PROMPT, "N");
        mockBooleanPromptResponse(CONTEXT_PROVIDER_PROMPT, "N");
        mockBooleanPromptResponse(SKIP_PAGE_EDITOR_PROMPT, "N");
        mockBooleanPromptResponse(EVENT_LISTENER_PROMPT, "N");
        mockBooleanPromptResponse(INDEX_PAGE_TEMPLATE_PROMPT, "N");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void basicPropertiesAreValid() throws PrompterException
    {
        BlueprintPromptEntries props = modulePrompter.promptForProps();

        // Assert that all of the things are good things.
        assertEquals(blueprintIndexKey, props.get(INDEX_KEY_PROMPT));
        assertEquals(webItemName, props.get(WEB_ITEM_NAME_PROMPT));
        assertEquals(webItemDesc, props.get(WEB_ITEM_DESC_PROMPT));
        assertFalse((Boolean) props.get(HOW_TO_USE_PROMPT));

        List<String> templateKeys = (List<String>) props.get(CONTENT_TEMPLATE_KEYS_PROMPT);
        assertEquals(1, templateKeys.size());
        assertEquals(templateModuleKey, templateKeys.get(0));

        assertEquals("example", props.getPluginKey());
        assertEquals("com.example", props.getDefaultBasePackage());
    }

    @Test
    public void howToUseTemplateAdded() throws PrompterException
    {
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "Y");
        mockBooleanPromptResponse(HOW_TO_USE_PROMPT, "Y");

        BlueprintPromptEntries props = modulePrompter.promptForProps();

        assertTrue((Boolean) props.get(HOW_TO_USE_PROMPT));
    }

    @Test
    public void pageEditorSkipped() throws PrompterException
    {
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "Y");
        mockBooleanPromptResponse(SKIP_PAGE_EDITOR_PROMPT, "Y");

        BlueprintPromptEntries props = modulePrompter.promptForProps();

        assertTrue((Boolean) props.get(SKIP_PAGE_EDITOR_PROMPT));
    }

    @Test
    public void dialogWizardAdded() throws PrompterException
    {
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "Y");
        mockBooleanPromptResponse(DIALOG_WIZARD_PROMPT, "Y");

        BlueprintPromptEntries props = modulePrompter.promptForProps();

        assertTrue((Boolean) props.get(DIALOG_WIZARD_PROMPT));
    }

    @Test
    public void contextProviderAdded() throws PrompterException
    {
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "Y");
        mockBooleanPromptResponse(CONTEXT_PROVIDER_PROMPT, "Y");

        BlueprintPromptEntries props = modulePrompter.promptForProps();

        assertTrue((Boolean) props.get(CONTEXT_PROVIDER_PROMPT));
    }
    @Test
    public void customIndexPageTemplateAdded() throws PrompterException
    {
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "Y");
        mockBooleanPromptResponse(INDEX_PAGE_TEMPLATE_PROMPT, "Y");

        BlueprintPromptEntries props = modulePrompter.promptForProps();

        assertTrue((Boolean) props.get(INDEX_PAGE_TEMPLATE_PROMPT));
    }

    @Test
    public void eventListenerAdded() throws PrompterException
    {
        mockBooleanPromptResponse(ADVANCED_BLUEPRINT_PROMPT, "Y");
        mockBooleanPromptResponse(EVENT_LISTENER_PROMPT, "Y");

        BlueprintPromptEntries props = modulePrompter.promptForProps();

        assertTrue((Boolean) props.get(EVENT_LISTENER_PROMPT));
    }

    private void mockBooleanPromptResponse(BlueprintPromptEntry prompt, String response) throws PrompterException
    {
        when(prompter.prompt(prompt.message(), PluginModulePrompter.YN_ANSWERS, prompt.defaultValue())).thenReturn(response);
    }
}
