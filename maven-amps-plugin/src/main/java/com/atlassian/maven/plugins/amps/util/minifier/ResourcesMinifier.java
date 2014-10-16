package com.atlassian.maven.plugins.amps.util.minifier;

import java.io.*;
import java.util.Collections;
import java.util.List;

import com.googlecode.htmlcompressor.compressor.XmlCompressor;
import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.DirectoryScanner;


/**
 * @since version
 */
public class ResourcesMinifier
{
    private static ResourcesMinifier INSTANCE;

    private ResourcesMinifier()
    {
    }

    public static void minify(List<Resource> resources, File outputDir, boolean compressJs, boolean compressCss, boolean useClosureForJs, Log log) throws MojoExecutionException
    {
        if(null == INSTANCE)
        {
            INSTANCE = new ResourcesMinifier();
        }
        
        for(Resource resource : resources)
        {
            INSTANCE.processResource(resource,outputDir,compressJs, compressCss, useClosureForJs,log);
        }
    }
    
    public void processResource(Resource resource, File outputDir, boolean compressJs, boolean compressCss, boolean useClosureForJs, Log log) throws MojoExecutionException
    {
        File destDir = outputDir;
        if(StringUtils.isNotBlank(resource.getTargetPath()))
        {
            destDir = new File(outputDir,resource.getTargetPath());
        }
        
        File resourceDir = new File(resource.getDirectory());
        
        if(null == resourceDir || !resourceDir.exists())
        {
            return;
        }

        if(compressJs)
        {
            processJs(resourceDir,destDir,resource.getIncludes(),resource.getExcludes(),useClosureForJs,log);
        }
        
        if(compressCss)
        {
            processCss(resourceDir,destDir,resource.getIncludes(),resource.getExcludes(), log);
        }

        processXml(resourceDir, destDir, resource.getIncludes(), resource.getExcludes(), log);
    }

    public void processXml(final File resourceDir, final File destDir, List<String> includes, final List<String> excludes, final Log log) throws MojoExecutionException
    {
        log.info("Compressing XML files");

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(resourceDir);
        if(null == includes || includes.isEmpty())
        {
            includes = Collections.singletonList("**/*.xml");
        }
        scanner.setIncludes(includes.toArray(new String[includes.size()]));

        if(null != excludes && !excludes.isEmpty())
        {
            scanner.setExcludes(excludes.toArray(new String[0]));
        }

        scanner.addDefaultExcludes();
        scanner.scan();

        XmlCompressor compressor = new XmlCompressor();

        for (String name : scanner.getIncludedFiles())
        {
            File sourceFile = new File(resourceDir,name);
            // double check xml file
            if(!sourceFile.getName().endsWith(".xml"))
            {
                continue;
            }
            File destFile = new File(destDir,name);

            if(sourceFile.exists() && sourceFile.canRead())
            {
                if(destFile.exists() && destFile.lastModified() > sourceFile.lastModified())
                {
                    log.info("Nothing to do, " + destFile.getAbsolutePath() + " is younger than the original");
                    continue;
                }

                log.info("compressing to " + destFile.getAbsolutePath());

                try
                {
                    FileUtils.forceMkdir(destFile.getParentFile());
                    String source = FileUtils.readFileToString(sourceFile);
                    String min = compressor.compress(source);
                    FileUtils.writeStringToFile(destFile,min);
                }
                catch (IOException e)
                {
                    throw new MojoExecutionException("IOException when compiling JS", e);
                }
            }
        }
    }

    public void processJs(File resourceDir, File destDir, List<String> includes, List<String> excludes, boolean useClosure, Log log) throws MojoExecutionException
    {
        if(useClosure)
        {
            log.info("Compiling javascript using Closure");
        }
        else
        {
            log.info("Compiling javascript using YUI");
        }
        
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(resourceDir);
        if (null == includes || includes.isEmpty())
        {
            includes = Collections.singletonList("**/*.js");
        }
        scanner.setIncludes(includes.toArray(new String[includes.size()]));

        if(null != excludes && !excludes.isEmpty())
        {
            scanner.setExcludes(excludes.toArray(new String[0]));
        }

        scanner.addDefaultExcludes();
        scanner.scan();
        
        for(String name : scanner.getIncludedFiles())
        {
            File sourceFile = new File(resourceDir,name);
            // double check javascript file
            if (!sourceFile.getName().endsWith(".js")) {
                continue;
            }
            String baseName = FilenameUtils.removeExtension(name);
            File destFile = new File(destDir,baseName + "-min.js");
            if(sourceFile.exists() && sourceFile.canRead())
            {
                if(destFile.exists() && destFile.lastModified() > sourceFile.lastModified())
                {
                    log.info("Nothing to do, " + destFile.getAbsolutePath() + " is younger than the original");
                    continue;
                }

                log.info("compressing to " + destFile.getAbsolutePath());
                if(useClosure)
                {
                    closureJsCompile(sourceFile,destFile);
                }
                else
                {
                    yuiJsCompile(sourceFile,destFile,log);
                }
            }
        }
    }

    public void processCss(File resourceDir, File destDir, List<String> includes, List<String> excludes, Log log) throws MojoExecutionException
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(resourceDir);
        if(null == includes || includes.isEmpty())
        {
            includes = Collections.singletonList("**/*.css");
        }
        scanner.setIncludes(includes.toArray(new String[includes.size()]));

        if(null != excludes && !excludes.isEmpty())
        {
            scanner.setExcludes(excludes.toArray(new String[0]));
        }

        scanner.addDefaultExcludes();
        scanner.scan();

        for(String name : scanner.getIncludedFiles())
        {
            File sourceFile = new File(resourceDir,name);
            // double check css file
            if(!sourceFile.getName().endsWith(".css"))
            {
                continue;
            }
            String baseName = FilenameUtils.removeExtension(name);
            File destFile = new File(destDir,baseName + "-min.css");

            if(sourceFile.exists() && sourceFile.canRead())
            {
                if(destFile.exists() && destFile.lastModified() > sourceFile.lastModified())
                {
                    log.info("Nothing to do, " + destFile.getAbsolutePath() + " is younger than the original");
                    continue;
                }
                log.info("compressing to " + destFile.getAbsolutePath());
                yuiCssCompile(sourceFile,destFile);
            }
        }
    }

    private void closureJsCompile(File sourceFile, File destFile) throws MojoExecutionException
    {
        try
        {
            FileUtils.forceMkdir(destFile.getParentFile());
            String source = FileUtils.readFileToString(sourceFile);
            String min = GoogleClosureJSMinifier.compile(source);
            FileUtils.writeStringToFile(destFile,min);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("IOException when compiling JS", e);
        }
    }
    
    private void yuiJsCompile(File sourceFile, File destFile, Log log) throws MojoExecutionException
    {
        InputStreamReader in = null;
        OutputStreamWriter out = null;
        try
        {
            FileUtils.forceMkdir(destFile.getParentFile());
            in = new InputStreamReader(new FileInputStream(sourceFile));
            out = new OutputStreamWriter(new FileOutputStream(destFile));
            
            JavaScriptCompressor yui = new JavaScriptCompressor(in,new YUIErrorReporter(log));
            yui.compress(out,-1,true,false,false,false);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("IOException when compiling JS", e);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    private void yuiCssCompile(File sourceFile, File destFile) throws MojoExecutionException
    {
        InputStreamReader in = null;
        OutputStreamWriter out = null;
        try
        {
            FileUtils.forceMkdir(destFile.getParentFile());
            in = new InputStreamReader(new FileInputStream(sourceFile));
            out = new OutputStreamWriter(new FileOutputStream(destFile));

            CssCompressor yui = new CssCompressor(in);
            yui.compress(out,-1);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("IOException when compiling JS", e);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    
}
