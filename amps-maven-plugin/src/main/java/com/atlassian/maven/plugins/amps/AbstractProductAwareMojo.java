package com.atlassian.maven.plugins.amps;

import java.util.prefs.Preferences;

import com.atlassian.maven.plugins.amps.product.ProductHandlerFactory;
import com.atlassian.maven.plugins.amps.util.GoogleAmpsTracker;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractProductAwareMojo extends AbstractAmpsMojo
{
    private static final String PREF_FIRSTRUN_PREFIX = "sdk-firstrun";

    /**
     * Product id
     */
    @Parameter(property = "product")
    private String product;

    /**
     * Instance to run. If provided, used to determine the product to use, instead of
     * using the product ID.
     */
    @Parameter(property = "instanceId")
    protected String instanceId;


    /**
     * <p>Flag to enable Google tracking.</p>
     *
     * <p>AMPS sends basic usage events to Google analytics by default. To disable tracking, either:</p>
     * <ol>
     * <li>Add <code>&lt;allow.google.tracking>false&lt;/allow.google.tracking></code> to the
     *  <code>&lt;properties></code> section of your <code>.m2/settings.xml</code> file</li>
     * <li>Include <code>&lt;allowGoogleTracking>false&lt;/allowGoogleTracking></code> in
     * the amps plugin configuration in your <code>pom.xml</code></li>
     * <li>or pass <code>-Dallow.google.tracking=false</code> on the command line.
     * </ol>
     */
    @Parameter(property = "allow.google.tracking", defaultValue = "true")
    protected boolean allowGoogleTracking;

    private GoogleAmpsTracker googleTracker;

    protected String getDefaultProductId() throws MojoExecutionException
    {
        // If maven-[product]-plugin didn't override this method, we fetch the
        // name of the plugin
        String nameOfTheCurrentMavenPlugin = getPluginInformation().getId();
        if (ProductHandlerFactory.getIds().contains(nameOfTheCurrentMavenPlugin))
        {
            return nameOfTheCurrentMavenPlugin;
        }
        return null;
    }

    protected final String getProductId() throws MojoExecutionException
    {
        if (product == null)
        {
            product = getDefaultProductId();
            if (product == null)
            {
                product = ProductHandlerFactory.REFAPP;
            }
        }
        return product;
    }

    protected GoogleAmpsTracker getGoogleTracker() throws MojoExecutionException
    {
        if(null == googleTracker && googleTrackingAllowed())
        {
            googleTracker = new GoogleAmpsTracker(getProductId(), getAmpsPluginVersion(),getPluginInformation().getVersion(),getLog());

            if(googleTrackingAllowed()) {
                getLog().info("Google Analytics Tracking is enabled to collect AMPS usage statistics.");
                getLog().info("Although no personal information is sent, you may disable tracking by adding <allowGoogleTracking>false</allowGoogleTracking> to the amps plugin configuration in your pom.xml");
            }

            googleTracker.setEnabled(googleTrackingAllowed());
        } 
        else
        {
            //no-arg constructor is a NOOP tracker
            googleTracker = new GoogleAmpsTracker();
        }

        

        return googleTracker;
    }

    protected boolean googleTrackingAllowed() {
        return allowGoogleTracking;
    }
    
    protected void trackFirstRunIfNeeded() throws MojoExecutionException
    {
        boolean runningShellScript = (null != System.getenv("ATLAS_VERSION"));
        
        if(googleTrackingAllowed() && runningShellScript)
        {
            String firstRunKey = PREF_FIRSTRUN_PREFIX + "-" + getAmpsPluginVersion();
            Preferences prefs = Preferences.userNodeForPackage(getClass());
            String alreadyRan = prefs.get(firstRunKey, null);
            
            if(null == alreadyRan)
            {
                getGoogleTracker().track(GoogleAmpsTracker.SDK_FIRST_RUN, getAmpsPluginVersion());
                prefs.put(firstRunKey,"true");
            }
        }

    }

}
