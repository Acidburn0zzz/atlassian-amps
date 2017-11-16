package com.atlassian.maven.plugins.amps.util;


import org.junit.Assert;
import org.junit.Test;

public class JvmArgsFixTest
{

    private JvmArgsFix testDefaults = JvmArgsFix.empty().with("-Xmx", "512m").with("-XX:MaxPermSize=", "256m");

    @Test
    public void testWithNullArgs() throws Exception
    {
        Assert.assertEquals("-Xmx512m -XX:MaxPermSize=256m", testDefaults.apply(null));
    }

    @Test
    public void testWithEmptyArgs() throws Exception
    {
        Assert.assertEquals("-Xmx512m -XX:MaxPermSize=256m", testDefaults.apply(""));
    }

    @Test
    public void testWithExistingUnrelated() throws Exception
    {
        Assert.assertEquals("-XmsSOMETHING -Xmx512m -XX:MaxPermSize=256m", testDefaults.apply("-XmsSOMETHING"));
    }

    @Test
    public void testWithMx() throws Exception
    {
        Assert.assertEquals("-XmxSOMETHING -XX:MaxPermSize=256m", testDefaults.apply("-XmxSOMETHING"));
    }

    @Test
    public void testWithBoth() throws Exception
    {
        Assert.assertEquals("-XmxSOMETHING -XX:MaxPermSize=SOMETHING", testDefaults.apply("-XmxSOMETHING -XX:MaxPermSize=SOMETHING"));
    }

    @Test
    public void testDefaults() throws Exception
    {
        // testing if what is put in defaults() is what is actually meant - plain text here
         Assert.assertEquals("-Xmx768m -XX:MaxPermSize=256m -Xms384m", JvmArgsFix.defaults().apply(null));
    }

    @Test
    public void testDefaultsOverride() throws Exception
    {
         Assert.assertEquals("-XmxSOMETHING -XX:MaxPermSize=STH -Xms384m", JvmArgsFix.defaults().with("-XX:MaxPermSize=", "STH").apply("-XmxSOMETHING"));
    }
}
