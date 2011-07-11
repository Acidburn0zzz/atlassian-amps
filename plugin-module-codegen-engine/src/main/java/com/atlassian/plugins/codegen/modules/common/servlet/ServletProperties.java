package com.atlassian.plugins.codegen.modules.common.servlet;

import com.atlassian.plugins.codegen.modules.BasicClassModuleProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @since version
 */
public class ServletProperties extends BasicClassModuleProperties {
    private static final String KEY_PREFIX = "jira.servlet.";
    public static final String URL_PATTERN = "URL_PATTERN";
    public static final String INIT_PARAMS = "INIT_PARAMS";

    public ServletProperties() {
        this("MyServlet");
    }

    public ServletProperties(String fqClassName) {
        super(fqClassName);
        put(INIT_PARAMS, new HashMap<String, String>());

        //sane defaults
        setUrlPattern("/" + getProperty(CLASSNAME).toLowerCase());
        setDescription("The " + getProperty(MODULE_NAME) + " Servlet");
        setDescriptionKey(KEY_PREFIX + getProperty(MODULE_KEY) + ".description");
        addI18nProperty(getProperty(DESCRIPTION_KEY), getProperty(DESCRIPTION));

        setNameKey(KEY_PREFIX + getProperty(MODULE_KEY) + ".name");
        addI18nProperty(getProperty(NAME_KEY), getProperty(MODULE_NAME));
    }

    public void setUrlPattern(String pattern) {
        setProperty(URL_PATTERN, pattern);
    }

    public void setInitParams(Map<String, String> params) {
        put(INIT_PARAMS, params);
    }

    @SuppressWarnings(value = "unchecked")
    public void addInitParam(String name, String value) {
        Map<String, String> params = (Map<String, String>) get(INIT_PARAMS);
        if (params == null) {
            params = new HashMap<String, String>();
            setInitParams(params);
        }

        params.put(name, value);
    }
}
