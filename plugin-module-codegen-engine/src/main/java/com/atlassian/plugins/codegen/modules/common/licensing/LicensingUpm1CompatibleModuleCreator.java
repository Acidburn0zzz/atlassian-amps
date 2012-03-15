package com.atlassian.plugins.codegen.modules.common.licensing;

import com.atlassian.plugins.codegen.ArtifactDependency;
import com.atlassian.plugins.codegen.BundleInstruction;
import com.atlassian.plugins.codegen.ClassId;
import com.atlassian.plugins.codegen.ComponentDeclaration;
import com.atlassian.plugins.codegen.PluginArtifact;
import com.atlassian.plugins.codegen.PluginProjectChangeset;
import com.atlassian.plugins.codegen.VersionId;
import com.atlassian.plugins.codegen.annotations.BambooPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.ConfluencePluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.CrowdPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.FeCruPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.JiraPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.RefAppPluginModuleCreator;
import com.atlassian.plugins.codegen.modules.AbstractPluginModuleCreator;
import com.atlassian.plugins.codegen.modules.common.servlet.ServletModuleCreator;
import com.atlassian.plugins.codegen.modules.common.servlet.ServletProperties;

import static com.atlassian.fugue.Option.some;
import static com.atlassian.plugins.codegen.AmpsSystemPropertyVariable.ampsSystemPropertyVariable;
import static com.atlassian.plugins.codegen.ArtifactDependency.dependency;
import static com.atlassian.plugins.codegen.ArtifactDependency.Scope.COMPILE;
import static com.atlassian.plugins.codegen.ArtifactDependency.Scope.PROVIDED;
import static com.atlassian.plugins.codegen.ArtifactId.artifactId;
import static com.atlassian.plugins.codegen.BundleInstruction.dynamicImportPackage;
import static com.atlassian.plugins.codegen.BundleInstruction.importPackage;
import static com.atlassian.plugins.codegen.BundleInstruction.privatePackage;
import static com.atlassian.plugins.codegen.ClassId.fullyQualified;
import static com.atlassian.plugins.codegen.ComponentImport.componentImport;
import static com.atlassian.plugins.codegen.MavenPlugin.mavenPlugin;
import static com.atlassian.plugins.codegen.PluginArtifact.pluginArtifact;
import static com.atlassian.plugins.codegen.PluginArtifact.ArtifactType.BUNDLED_ARTIFACT;
import static com.atlassian.plugins.codegen.PluginParameter.pluginParameter;
import static com.atlassian.plugins.codegen.VersionId.noVersion;
import static com.atlassian.plugins.codegen.VersionId.version;
import static com.atlassian.plugins.codegen.VersionId.versionProperty;

/**
 * @since 3.8
 */
@RefAppPluginModuleCreator
@JiraPluginModuleCreator
@ConfluencePluginModuleCreator
@BambooPluginModuleCreator
@FeCruPluginModuleCreator
@CrowdPluginModuleCreator
public class LicensingUpm1CompatibleModuleCreator extends AbstractPluginModuleCreator<LicensingProperties>
{
    public static final String MODULE_NAME = "License Management (pre-UPM 2 Compatibility)";

    public static final String LICENSE_SERVLET_TEMPLATE = "templates/common/licensing/LicenseServlet.java.vtl";
    public static final String HELLO_WORLD_SERVLET_TEMPLATE = "templates/common/licensing/HelloWorldServlet.java.vtl";
    
    public static final ClassId LICENSE_STORAGE_MANAGER_CLASS = fullyQualified("com.atlassian.upm.license.storage.lib.ThirdPartyPluginLicenseStorageManagerImpl");
    public static final ClassId PLUGIN_INSTALLER_CLASS = fullyQualified("com.atlassian.upm.license.storage.lib.PluginLicenseStoragePluginInstaller");
    public static final ClassId LICENSE_SERVLET_CLASS = fullyQualified("com.example.plugins.tutorial.compatibility.servlet.LicenseServlet");
    public static final ClassId HELLO_WORLD_SERVLET_CLASS = fullyQualified("com.example.plugins.tutorial.compatibility.servlet.HelloWorldServlet");
    
    public static final String LICENSE_SERVLET_URL_PATTERN = "/licenseservlet";
    public static final String HELLO_WORLD_SERVLET_URL_PATTERN = "/helloworldservlet";
    
    public static final String LICENSE_API_VERSION = "2.1-m5";
    public static final VersionId LICENSE_API_VERSION_PROPERTY = versionProperty("upm.license.compatibility.version", LICENSE_API_VERSION);

    public static final ArtifactDependency[] DEPENDENCIES = {
        dependency("com.atlassian.upm", "plugin-license-storage-lib", LICENSE_API_VERSION_PROPERTY, COMPILE),
        dependency("com.atlassian.upm", "plugin-license-storage-plugin", LICENSE_API_VERSION_PROPERTY, PROVIDED),
        dependency("com.atlassian.upm", "licensing-api", LICENSE_API_VERSION_PROPERTY, PROVIDED),
        dependency("com.atlassian.upm", "upm-api", LICENSE_API_VERSION_PROPERTY, PROVIDED),
        dependency("com.atlassian.sal", "sal-api", "2.4.0", PROVIDED),
        dependency("com.atlassian.templaterenderer", "atlassian-template-renderer-api", versionProperty("atlassian.templaterenderer.version", "1.0.5"), PROVIDED),
        dependency("org.springframework.osgi", "spring-osgi-core", "1.1.3", PROVIDED),
        dependency("commons-lang", "commons-lang", "2.4", PROVIDED)
    };
    
    public static final PluginArtifact[] BUNDLED_ARTIFACTS = {
        pluginArtifact(BUNDLED_ARTIFACT, artifactId("com.atlassian.upm", "atlassian-universal-plugin-manager-plugin"), version("1.6.3")),
        pluginArtifact(BUNDLED_ARTIFACT, artifactId("com.atlassian.upm", "plugin-license-storage-plugin"), LICENSE_API_VERSION_PROPERTY)
    };
    
    public static final BundleInstruction[] BUNDLE_INSTRUCTIONS = {
        importPackage("com.atlassian.plugin*", "0.0"),
        importPackage("com.atlassian.sal*", "0.0"),
        importPackage("com.atlassian.templaterenderer*", "0.0"),
        importPackage("com.google.common*", "1.0"),
        importPackage("javax.servlet*", "0.0"),
        importPackage("org.apache.commons*", "0.0"),
        importPackage("org.osgi.framework*", "0.0"),
        importPackage("org.osgi.util*", "0.0"),
        importPackage("org.slf4j*", "1.5"),
        importPackage("org.springframework.beans*", "0.0"),
        importPackage("org.springframework.context*", "0.0"),
        importPackage("org.springframework.osgi*", "0.0"),
        privatePackage("com.atlassian.upm.license.storage.lib*"),
        dynamicImportPackage("com.atlassian.upm.api.license", "2.0.1"),
        dynamicImportPackage("com.atlassian.upm.api.license.entity", "2.0.1"),
        dynamicImportPackage("com.atlassian.upm.api.util", "2.0.1"),
        dynamicImportPackage("com.atlassian.upm.license.storage.plugin", "2.0.1")
    };
    
    public static final String MAVEN_DEPENDENCY_PLUGIN_ID = "maven-dependency-plugin";
    public static final String MAVEN_DEPENDENCY_PLUGIN_CONFIG = "<executions>" +
            "<execution>" +
                "<id>copy-storage-plugin</id>" +
                "<phase>process-resources</phase>" +
                "<goals><goal>copy-dependencies</goal></goals>" +
                "<configuration>" +
                    "<outputDirectory>${project.build.outputDirectory}</outputDirectory>" +
                    "<includeArtifactIds>plugin-license-storage-plugin</includeArtifactIds>" +
                    "<stripVersion>true</stripVersion>" +
                "</configuration>" +
            "</execution>" +
        "</executions>";
    
    @Override
    public PluginProjectChangeset createModule(LicensingProperties props) throws Exception
    {
        ComponentDeclaration licenseStorageManagerComponent = ComponentDeclaration.builder(LICENSE_STORAGE_MANAGER_CLASS, "thirdPartyPluginLicenseStorageManager").build();
        ComponentDeclaration pluginInstallerComponent = ComponentDeclaration.builder(PLUGIN_INSTALLER_CLASS, "pluginLicenseStoragePluginInstaller").build();
        
        PluginProjectChangeset ret = new PluginProjectChangeset()
            .with(DEPENDENCIES)
            .with(BUNDLE_INSTRUCTIONS)
            .with(BUNDLED_ARTIFACTS)
            .with(mavenPlugin(artifactId(MAVEN_DEPENDENCY_PLUGIN_ID), noVersion(), MAVEN_DEPENDENCY_PLUGIN_CONFIG))
            .with(pluginParameter("atlassian-licensing-enabled", "true"))
            .with(componentImport(fullyQualified("com.atlassian.plugin.PluginAccessor")),
                  componentImport(fullyQualified("com.atlassian.plugin.PluginController")),
                  componentImport(fullyQualified("com.atlassian.sal.api.transaction.TransactionTemplate")).key(some("txTemplate")),
                  componentImport(fullyQualified("com.atlassian.sal.api.ApplicationProperties")))
            .with(pluginInstallerComponent, licenseStorageManagerComponent)
            .with(ampsSystemPropertyVariable("mac.baseurl", "https://intsys-staging.atlassian.com/my"));
        
        if (props.includeExamples())
        {
            ServletProperties licenseServletProps = new ServletProperties(LICENSE_SERVLET_CLASS.getFullName());
            licenseServletProps.setUrlPattern(LICENSE_SERVLET_URL_PATTERN);
            licenseServletProps.setCreateClass(false);
            ret = ret.with(new ServletModuleCreator().createModule(licenseServletProps))
                .with(createClass(licenseServletProps, LICENSE_SERVLET_TEMPLATE));
            
            ServletProperties helloWorldServletProps = new ServletProperties(HELLO_WORLD_SERVLET_CLASS.getFullName());
            helloWorldServletProps.setUrlPattern(HELLO_WORLD_SERVLET_URL_PATTERN);
            helloWorldServletProps.setCreateClass(false);
            ret = ret.with(new ServletModuleCreator().createModule(helloWorldServletProps))
                .with(createClass(helloWorldServletProps, HELLO_WORLD_SERVLET_TEMPLATE));
        }
        
        return ret;
    }

    @Override
    public String getModuleName()
    {
        return MODULE_NAME;
    }
}
