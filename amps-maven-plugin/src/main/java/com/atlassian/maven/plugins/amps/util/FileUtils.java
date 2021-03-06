package com.atlassian.maven.plugins.amps.util;

import org.apache.commons.io.IOUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.deleteQuietly;

public final class FileUtils
{
    private FileUtils() {}

    public static File file(String parent, String... kids)
    {
        return file(new File(parent), kids);
    }

    public static File file(File parent, String... kids)
    {
        File cur = parent;
        for (String kid : kids)
        {
            cur = new File(cur, kid);
        }
        return cur;
    }

    public static boolean doesFileNameMatchArtifact(String fileName, String artifactId)
    {
        // this is not perfect, but it sure beats fileName.contains(artifactId)        
        String pattern = "^" + artifactId + "-\\d.*$";
        return fileName.matches(pattern);
    }

    public static void deleteDir(File dir)
    {
        if (dir.exists())
        {
            deleteQuietly(dir);
        }
    }

    public static String fixWindowsSlashes(final String path)
    {
        return path.replaceAll("\\\\", "/");
    }

    public static void cleanDirectory(File directory, FileFilter filter) throws IOException
    {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles(filter);
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                org.apache.commons.io.FileUtils.forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    /**
     * Copy all files and directories from one folder to another, preserving structure.
     * This is an unfortunate duplicate of other libraries, which do not preserve the executable status of files,
     * most likely due to Java version restrictions.
     * <p />
     * If you do <i>not</i> have this requirement please use commons-io instead:
     * {@link org.apache.commons.io.FileUtils#copyDirectory(java.io.File, java.io.File)}.
     * @param source source directory from which to copy all files/directories from
     * @param destination destination directory to copy all files/directories to
     * @param preserveFileAttrs indicate whether date and exec should be preserved
     * @throws IOException if the destination files could not be created
     */
    public static void copyDirectory(File source, File destination, boolean preserveFileAttrs) throws IOException
    {
        if (!destination.mkdirs() && !destination.isDirectory())
        {
            throw new IOException("Destination '" + destination + "' directory cannot be created");
        }
        File[] srcFiles = source.listFiles();
        // Who decided that listFiles could return null?!?
        srcFiles = srcFiles != null ? srcFiles : new File[0];
        for (File srcFile : srcFiles)
        {
            File dstFile = new File(destination, srcFile.getName());
            if (srcFile.isDirectory())
            {
                copyDirectory(srcFile, dstFile, preserveFileAttrs);
            }
            else
            {
                copyFile(srcFile, dstFile, preserveFileAttrs);
                if (preserveFileAttrs && srcFile.canExecute())
                {
                    dstFile.setExecutable(true);
                }
            }
        }
    }

    /**
     * Reads the given file to a String.
     *
     * @param name the name of the file to read
     * @param loadingClass the class loading the file
     * @param encoding the encoding to use
     * @return the contents of the file as a String
     * @see Class#getResourceAsStream(java.lang.String)
     */
    @ParametersAreNonnullByDefault
    public static String readFileToString(final String name, final Class<?> loadingClass, final Charset encoding)
    {
        final InputStream fileStream = loadingClass.getResourceAsStream(name);
        requireNonNull(fileStream, format("Could not find '%s' on classpath of %s", name, loadingClass.getName()));
        try
        {
            return IOUtils.toString(fileStream, encoding);
        } catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
