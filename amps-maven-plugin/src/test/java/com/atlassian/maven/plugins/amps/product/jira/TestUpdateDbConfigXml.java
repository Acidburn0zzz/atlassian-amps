package com.atlassian.maven.plugins.amps.product.jira;

import com.atlassian.maven.plugins.amps.MavenContext;
import com.atlassian.maven.plugins.amps.MavenGoals;
import com.atlassian.maven.plugins.amps.product.JiraProductHandler;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasXPath;
import static org.mockito.Mockito.mock;

public class TestUpdateDbConfigXml {
    public static final String SAMPLE_DB_CONFIG = "/com/atlassian/maven/plugins/amps/product/jira/sample.config.db.xml";
    protected JiraProductHandler jiraProductHandler;

    protected static File getTempDir(final String subPath) {
        return new File(System.getProperty("java.io.tmpdir"), subPath);
    }

    protected static File getConfigFile() {
        return getTempDir("test/dbconfig.xml");
    }

    protected static File getH2DbFile() {
        return getTempDir("test/database/h2db");
    }

    protected static Document getDocumentFrom(File f) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(getConfigFile());
    }


    protected void copySampleDbConfig() throws IOException {
        InputStream is = TestUpdateDbConfigXml.class.getResourceAsStream(SAMPLE_DB_CONFIG);
        copyInputStreamToFile(is, getConfigFile());
    }

    @Before
    public void setUp() throws IOException {
        jiraProductHandler = new JiraProductHandler(
                mock(MavenContext.class),
                mock(MavenGoals.class),
                mock(ArtifactFactory.class)
        );

        deleteDirectory(getTempDir("test"));
        getTempDir("test").mkdir();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        deleteDirectory(getTempDir("test"));
    }

    @Test
    public void givenH2DatabaseWhenConfigIsModifiedThenPathIsChangedToCorrectH2DbLocation() throws Exception {
        // given
        String correctJDBCURL = String.format("jdbc:h2:file:%s;MV_STORE=FALSE;MVCC=TRUE", getH2DbFile().toPath());
        copySampleDbConfig();

        // when
        jiraProductHandler.updateDbConfigXml(getTempDir("test"), JiraDatabaseType.H2, "PUBLIC");

        // then
        Document doc = getDocumentFrom(getConfigFile());
        assertThat(doc, hasXPath("//jira-database-config/jdbc-datasource/url", containsString(correctJDBCURL)));
    }

    @Test
    public void givenNotConsistentDatabaseTypesWhenConfigIsModifiedThenDatabaseTypeIsUpdated() throws Exception {
        // given
        copySampleDbConfig();

        // when
        jiraProductHandler.updateDbConfigXml(getTempDir("test"), JiraDatabaseType.POSTGRES, "PUBLIC");

        // then
        Document doc = getDocumentFrom(getConfigFile());
        assertThat(doc, hasXPath(
                "//jira-database-config/database-type",
                containsString(JiraDatabaseType.POSTGRES.getDbType())
        ));
    }





}
