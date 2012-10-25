package com.atlassian.maven.plugins.amps.osgi;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.maven.plugins.amps.AbstractAmpsMojo;

import com.google.common.collect.ImmutableMap;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import aQute.lib.osgi.Constants;

import static com.atlassian.maven.plugins.amps.util.FileUtils.file;

@Mojo(name = "generate-test-manifest")
public class GenerateTestManifestMojo extends AbstractAmpsMojo
{
    private static final String BUILD_DATE_ATTRIBUTE = "Atlassian-Build-Date";

    private static final DateFormat BUILD_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    /**
     * The BND instructions for the bundle.
     */
    @Parameter
    private Map<String, String> testInstructions = new HashMap<String, String>();

    @Parameter(property = "project.build.finalName")
    private String finalName;
    
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if(shouldBuildTestPlugin())
        {
            final MavenProject project = getMavenContext().getProject();
    
            // The Atlassian-Build-Date manifest attribute is used by the Atlassian licensing framework to determine
            // chronological order of bundle versions.
            final String buildDateStr = String.valueOf(BUILD_DATE_FORMAT.format(new Date()));
            final Map<String, String> basicAttributes = ImmutableMap.of(BUILD_DATE_ATTRIBUTE, buildDateStr);
    
            if (!testInstructions.isEmpty())
            {
                getLog().info("Generating a manifest for this test plugin");

                if (!testInstructions.containsKey(Constants.BUNDLE_SYMBOLICNAME))
                {
                    testInstructions.put(Constants.BUNDLE_SYMBOLICNAME, finalName + "-tests");
                }
                else if(!testInstructions.get(Constants.BUNDLE_SYMBOLICNAME).endsWith("-tests"))
                {
                    testInstructions.put(Constants.BUNDLE_SYMBOLICNAME, testInstructions.get(Constants.BUNDLE_SYMBOLICNAME) + "-tests");
                }

                if (!testInstructions.containsKey(Constants.BUNDLE_NAME))
                {
                    testInstructions.put(Constants.BUNDLE_NAME, getMavenContext().getProject().getName() + " Tests");
                }
                
                if (!testInstructions.containsKey(Constants.EXPORT_PACKAGE))
                {
                    testInstructions.put(Constants.EXPORT_PACKAGE, "");
                }
    
                File metainfLib = file(project.getBuild().getTestOutputDirectory(), "META-INF", "lib");
                if (metainfLib.exists())
                {
                    StringBuilder sb = new StringBuilder(".");
                    for (File lib : metainfLib.listFiles())
                    {
                        sb.append(",").append("META-INF/lib/" + lib.getName());
                    }
                    testInstructions.put(Constants.BUNDLE_CLASSPATH, sb.toString());
                }
                getMavenGoals().generateTestBundleManifest(testInstructions, basicAttributes);
            }
            else
            {
                if (OsgiHelper.isAtlassianPlugin(project))
                {
                    getLog().warn("Atlassian plugin detected as the organisation name includes the string 'Atlassian'.  If " +
                                  "this is meant for production, you should add bundle " +
                                  "instructions specifically configuring what packages are imported and exported.  This " +
                                  "helps catch manifest generation bugs during the build rather than upon install.  The " +
                                  "bundle generation configuration can be specified " +
                                  "via the <testInstructions> element in the maven-" + getPluginInformation().getId()+"-plugin configuration.  For example:\n" +
                                  "    <configuration>\n" +
                                  "        <Import-Package>\n" +
                                  "            com.atlassian.myplugin*,\n" +
                                  "            com.library.optional.*;resolution:=optional,\n" +
                                  "            *\n" +
                                  "        </Import-Package>\n" +
                                  "    </configuration>\n\n" +
                                  "See the Maven bundle plugin (which is used under the covers) for more info: " +
                                  "http://felix.apache.org/site/apache-felix-maven-bundle-plugin-bnd.html#ApacheFelixMavenBundlePlugin%28BND%29-Instructions");
                }
                else
                {
                    getLog().info("No manifest instructions found, adding only non-OSGi manifest attributes");
                }
                getMavenGoals().generateTestMinimalManifest(basicAttributes);
            }
        }
    }
}
