package com.atlassian.plugins.codegen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import com.atlassian.plugins.codegen.modules.PluginModuleLocation;
import com.atlassian.plugins.codegen.util.FileUtil;
import com.atlassian.plugins.codegen.util.PluginXmlHelper;

import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.io.IOUtils.closeQuietly;

/**
 * Applies the changes from a {@link PluginProjectChangeset} that involve creating
 * source or resource files.
 */
public class ProjectFilesRewriter implements ProjectRewriter
{
    private PluginModuleLocation location;
    
    public ProjectFilesRewriter(PluginModuleLocation location)
    {
        this.location = checkNotNull(location, "location");
    }

    @Override
    public void applyChanges(PluginProjectChangeset changes) throws Exception
    {
        // We use a PluginXmlHelper to read some information from atlassian-plugin.xml if needed
        PluginXmlHelper xmlHelper = new PluginXmlHelper(location);
        
        for (SourceFile sourceFile : changes.getItems(SourceFile.class))
        {
            File baseDir = sourceFile.getSourceGroup() == SourceFile.SourceGroup.TESTS ?
                location.getTestDirectory() : location.getSourceDirectory();
            File newFile = FileUtil.dotDelimitedFilePath(baseDir, sourceFile.getClassId().getFullName(), ".java");
            Files.createParentDirs(newFile);
            FileUtils.writeStringToFile(newFile, sourceFile.getContent());
        }
        for (ResourceFile resourceFile : changes.getItems(ResourceFile.class))
        {
            File resourceDir = location.getResourcesDir();
            if (!resourceFile.getRelativePath().equals(""))
            {
                resourceDir = new File(resourceDir, resourceFile.getRelativePath());
            }
            File newFile = new File(resourceDir, resourceFile.getName());
            Files.createParentDirs(newFile);
            FileUtils.writeStringToFile(newFile, resourceFile.getContent());
        }
        if (changes.hasItems(I18nString.class))
        {
            File i18nFile = FileUtil.dotDelimitedFilePath(location.getResourcesDir(), xmlHelper.getDefaultI18nLocation(), ".properties");
            Files.createParentDirs(i18nFile);
            if (!i18nFile.exists())
            {
                i18nFile.createNewFile();
            }
            Properties props = new Properties();
            InputStream is = new FileInputStream(i18nFile);
            props.load(is);
            closeQuietly(is);
            for (I18nString s : changes.getItems(I18nString.class))
            {
                props.put(s.getName(), s.getValue());
            }
            OutputStream os = new FileOutputStream(i18nFile);
            props.store(os, "");
            closeQuietly(os);
        }
    }
}
