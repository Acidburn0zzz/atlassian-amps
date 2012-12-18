package com.atlassian.maven.plugins.amps.util;

import static com.atlassian.maven.plugins.amps.util.FileUtils.copyDirectory;
import static com.atlassian.maven.plugins.amps.util.FileUtils.doesFileNameMatchArtifact;
import static com.atlassian.maven.plugins.amps.util.FileUtils.file;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class TestFileUtils extends TestCase
{
    public void testFile()
    {
        File parent = new File("bob");
        assertEquals(new File(parent, "jim").getAbsolutePath(), file(parent, "jim").getAbsolutePath());

        assertEquals(new File(new File(parent, "jim"), "sarah").getAbsolutePath(),
                file(parent, "jim", "sarah").getAbsolutePath());
    }

    public void testDoesFileNameMatcheArtifact()
    {
        assertTrue(doesFileNameMatchArtifact("sal-crowd-plugin-2.0.7.jar", "sal-crowd-plugin"));
        assertFalse(doesFileNameMatchArtifact("sal-crowd-plugin-2.0.7.jar", "crowd-plugin"));
    }

    public void testCopyDirectory() throws IOException
    {
        File src = new File("src");
        File dest = new File("dest");
        try
        {
            src.mkdirs();
            File file = new File(src, "something");
            file.createNewFile();
            file.setExecutable(true);
            new File(src, "a/b").mkdirs();
            new File(src, "a/b/c").createNewFile();
            new File(src, "a/d").createNewFile();
            copyDirectory(src, dest, true);
            assertTrue(new File(dest, "a/b/c").exists());
            assertTrue(new File(dest, "a/d").exists());
            assertFalse(new File(dest, "a/d").canExecute());
            assertTrue(new File(dest, file.getName()).canExecute());
        }
        finally
        {
            FileUtils.deleteDir(src);
            FileUtils.deleteDir(dest);
        }
    }

}
