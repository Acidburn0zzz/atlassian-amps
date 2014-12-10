package com.atlassian.maven.plugins.amps.product;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import com.atlassian.maven.plugins.amps.MavenContext;
import com.atlassian.maven.plugins.amps.Product;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import static com.atlassian.maven.plugins.amps.product.JiraProductHandler.BUNDLED_PLUGINS_FROM_4_1;
import static com.atlassian.maven.plugins.amps.product.JiraProductHandler.BUNDLED_PLUGINS_UNZIPPED;
import static com.atlassian.maven.plugins.amps.product.JiraProductHandler.BUNDLED_PLUGINS_UPTO_4_0;
import static com.atlassian.maven.plugins.amps.product.JiraProductHandler.FILENAME_DBCONFIG;
import static com.atlassian.maven.plugins.amps.product.JiraProductHandler.INSTALLED_PLUGINS_DIR;
import static com.atlassian.maven.plugins.amps.product.JiraProductHandler.PLUGINS_DIR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestJiraProductHandler
{
    static File tempHome;

    private static File createTempDir(final String subPath)
    {
        return new File(System.getProperty("java.io.tmpdir"), subPath);
    }
    
    @Before
    public void createTemporaryHomeDirectory() throws IOException
    {
        File f = File.createTempFile("temp-jira-", "-home");
        if (!f.delete())
        {
            throw new IOException();
        }
        
        if (!f.mkdir())
        {
            throw new IOException();
        }
        
        tempHome = f;
    }
    
    @After
    public void deleteTemporaryHomeDirectoryAndContents() throws Exception
    {
        if (tempHome != null)
        {
            FileUtils.deleteDirectory(tempHome);
            tempHome = null;
        }
    }

    @Test
    public void dbconfigXmlCreatedWithCorrectPath() throws Exception
    {
        JiraProductHandler.createDbConfigXmlIfNecessary(tempHome);

        File f = new File(tempHome, FILENAME_DBCONFIG);
        assertTrue("The config file is created: " + FILENAME_DBCONFIG, f.exists());
        assertTrue("And it's a regular file", f.isFile());

        File dbFile = new File(tempHome, "database");

        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);

        XPathExpression xpe = XPathFactory.newInstance().newXPath().compile("/jira-database-config/jdbc-datasource/url");

        String x = xpe.evaluate(d);
        assertEquals("The JDBC URI for the embedded database is as expected",
                "jdbc:hsqldb:file:" + dbFile.toURI().getPath(), x);
    }

    @Test
    public void updateDBConfigXmlForOracle() throws Exception
    {
        testUpdateDbConfigXml(JiraDatabaseType.ORACLE);
    }

    @Test
    public void updateDBConfigXmlForMysql() throws Exception
    {
        testUpdateDbConfigXml(JiraDatabaseType.MYSQL);
    }

    @Test
    public void updateDBConfigXmlForPostgres() throws Exception
    {
        testUpdateDbConfigXml(JiraDatabaseType.POSTGRESQL);
    }

    @Test
    public void updateDBConfigXmlForMssql() throws Exception
    {
        testUpdateDbConfigXml(JiraDatabaseType.MSSQL);
    }

    @Test
    public void updateDBConfigXmlForMssqlJTDS() throws Exception
    {
        testUpdateDbConfigXml(JiraDatabaseType.MSSQL_JTDS);
    }

    private void testUpdateDbConfigXml(JiraDatabaseType dbType) throws Exception
    {
        // Create default dbconfig.xml
        JiraProductHandler.createDbConfigXmlIfNecessary(tempHome);
        // Setup
        final File f = new File(tempHome, FILENAME_DBCONFIG);
        final String schema = "test-schema";
        final SAXReader reader = new SAXReader();
        org.dom4j.Document dbConfigXml = reader.read(f);
        final MavenContext mockMavenContext = mock(MavenContext.class);
        final JiraProductHandler productHandler = new JiraProductHandler(mockMavenContext, null, null);
        // Check default db type
        assertEquals("hsql", getDbType(dbConfigXml));
        assertEquals("PUBLIC", getDbSchema(dbConfigXml));
        // Invoke: update dbconfig.xml
        productHandler.updateDbConfigXml(tempHome, dbType, schema);
        dbConfigXml = reader.read(f);
        // Check
        assertEquals(dbType.getDbType(), getDbType(dbConfigXml));
        if (dbType.hasSchema())
        {
            assertThat("Schema has to update", schema.equals(getDbSchema(dbConfigXml)), is(true));
        }
        else
        {
            assertThat("Schema has not to update", schema.equals(getDbSchema(dbConfigXml)), is(false));
        }
    }

    private String getDbType(org.dom4j.Document dbConfigXml) throws Exception
    {

        final Node dbTypeNode = dbConfigXml.selectSingleNode("//jira-database-config/database-type");
        return dbTypeNode == null ? "" : dbTypeNode.getStringValue();
    }

    private String getDbSchema(org.dom4j.Document dbConfigXml) throws Exception
    {
        final Node schemaNode = dbConfigXml.selectSingleNode("//jira-database-config/schema-name");
        return schemaNode == null ? "" : schemaNode.getStringValue();
    }

    @Test
    public void getDatabaseTypeWithNull() throws Exception
    {
        final JiraDatabaseType dbType = JiraDatabaseType.getDatabaseType(null, null);
        assertNull(dbType);


    }
    @Test
    public void getDatabaseTypePostgresUriAndDriver() throws Exception
    {
        final JiraDatabaseType dbType = JiraDatabaseType.getDatabaseType("jdbc:postgresql://localhost:5432/amps-test", "org.postgresql.Driver");
        assertNotNull(dbType);
        assertEquals(dbType.getDbType(), JiraDatabaseType.POSTGRESQL.getDbType());
    }

    @Test
    public void getDatabaseTypeMssqlUriAndDriver() throws Exception
    {
        final JiraDatabaseType dbType = JiraDatabaseType.getDatabaseType("jdbc:sqlserver://amps-test", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        assertNotNull(dbType);
        assertEquals(dbType.getDbType(), JiraDatabaseType.MSSQL.getDbType());
    }

    @Test
    public void getDatabaseTypeMssqlUriAndAcrossDriver() throws Exception
    {
        final JiraDatabaseType dbType = JiraDatabaseType.getDatabaseType("jdbc:sqlserver://amps-test;user=MyUserName;password=*****;", "net.sourceforge.jtds.jdbc.Driver");
        assertNull(dbType);
    }

    @Test
    public void dbconfigXmlNotCreatedWhenAlreadyExists() throws MojoExecutionException, IOException
    {
        File f = new File(tempHome, FILENAME_DBCONFIG);
        FileUtils.writeStringToFile(f, "Original contents");
        JiraProductHandler.createDbConfigXmlIfNecessary(tempHome);
        
        String after = FileUtils.readFileToString(f);
        assertEquals("Original contents", after);
    }

    @Test
    public void pluginsShouldGoIntoLocalHomeIfNoSharedHomeIsSpecified()
    {
        final File localHome = createTempDir("jira-local");
        assertUserInstalledPluginsDirectory(localHome, null, localHome);
    }

    @Test
    public void pluginsShouldGoIntoSharedHomeIfOneIsSpecified()
    {
        final File sharedHome = createTempDir("jira-shared");
        assertUserInstalledPluginsDirectory(null, sharedHome.getPath(), sharedHome);
    }

    private void assertUserInstalledPluginsDirectory(
            final File localHome, final String sharedHomePath, final File expectedParentDir)
    {
        // Set up
        final MavenProject mockMavenProject = mock(MavenProject.class);
        final MavenContext mockMavenContext = mock(MavenContext.class);
        when(mockMavenContext.getProject()).thenReturn(mockMavenProject);
        final JiraProductHandler productHandler = new JiraProductHandler(mockMavenContext, null, null);
        final Product mockProduct = mock(Product.class);
        when(mockProduct.getSharedHome()).thenReturn(sharedHomePath);

        // Invoke
        final File userInstalledPluginsDirectory =
                productHandler.getUserInstalledPluginsDirectory(mockProduct, null, localHome);

        // Check
        assertNotNull(userInstalledPluginsDirectory);
        assertEquals(new File(new File(expectedParentDir, PLUGINS_DIR), INSTALLED_PLUGINS_DIR), userInstalledPluginsDirectory);
    }

    @Test
    public void bundledPluginsShouldBeUnzippedIfPresent() throws MojoExecutionException
    {
        final File bundledPluginsDir = new File(tempHome, BUNDLED_PLUGINS_UNZIPPED);
        bundledPluginsDir.mkdirs();
        assertTrue(bundledPluginsDir.exists());
        assertBundledPluginPath("6.3", tempHome, bundledPluginsDir);
    }

    @Test
    public void bundledPluginsLocationCorrectFor41() throws MojoExecutionException
    {
        final File bundledPluginsZip = new File(tempHome, BUNDLED_PLUGINS_FROM_4_1);
        assertBundledPluginPath("4.1", tempHome, bundledPluginsZip);
    }

    @Test
    public void bundledPluginsLocationCorrectFor40() throws MojoExecutionException
    {
        final File bundledPluginsZip = new File(tempHome, BUNDLED_PLUGINS_UPTO_4_0);
        assertBundledPluginPath("4.0", tempHome, bundledPluginsZip);
    }

    @Test
    public void bundledPluginsLocationCorrectForFallback() throws MojoExecutionException
    {
        final File bundledPluginsZip = new File(tempHome, BUNDLED_PLUGINS_FROM_4_1);
        assertBundledPluginPath("not.a.version", tempHome, bundledPluginsZip);
    }

    private void assertBundledPluginPath(final String version, final File appDir, final File expectedPath)
            throws MojoExecutionException
    {
        // Set up

        final Log mockLog = mock(Log.class);
        final MavenContext mockMavenContext = mock(MavenContext.class);
        when(mockMavenContext.getLog()).thenReturn(mockLog);
        final JiraProductHandler productHandler = new JiraProductHandler(mockMavenContext, null, null);
        final Product mockProduct = mock(Product.class);
        when(mockProduct.getVersion()).thenReturn(version);

        // Invoke
        final File bundledPluginPath = productHandler.getBundledPluginPath(mockProduct, appDir);

        // Check
        assertNotNull(bundledPluginPath);
        assertEquals(expectedPath, bundledPluginPath);
    }
}
