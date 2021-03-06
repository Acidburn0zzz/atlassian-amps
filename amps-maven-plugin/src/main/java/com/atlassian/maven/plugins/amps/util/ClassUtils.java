package com.atlassian.maven.plugins.amps.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.objectweb.asm.ClassReader;

public class ClassUtils
{
    public static String getClassnameFromFile(File classFile, String removePrefix)
    {
        String regex = "/";

        if(SystemUtils.IS_OS_WINDOWS)
        {
            regex = "\\\\";
        }
        return StringUtils.removeEnd(
                StringUtils.removeStart(
                        StringUtils.removeStart(
                                classFile.getAbsolutePath(), removePrefix)
                                   .replaceAll(regex, ".")
                        , ".")
                , ".class");
    }
    
    public static WiredTestInfo getWiredTestInfo(File classFile)
    {
        boolean isWiredClass;
        String applicationFilter;

        try (FileInputStream fis = new FileInputStream(classFile))
        {
            TestClassVisitor visitor = new TestClassVisitor();
            ClassReader reader = new ClassReader(fis);
            
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            isWiredClass = visitor.isWiredTest();
            applicationFilter = visitor.getApplicationFilter();
        }
        catch (IOException e)
        {
            isWiredClass = false;
            applicationFilter = "";
        }

        return new WiredTestInfo(isWiredClass, applicationFilter);
    }
}