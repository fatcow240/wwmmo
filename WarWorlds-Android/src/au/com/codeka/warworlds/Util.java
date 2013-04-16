
package au.com.codeka.warworlds;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import au.com.codeka.warworlds.api.ApiClient;
import au.com.codeka.warworlds.model.BuildingDesignManager;
import au.com.codeka.warworlds.model.PurchaseManager;
import au.com.codeka.warworlds.model.RealmManager;
import au.com.codeka.warworlds.model.ShipDesignManager;
import au.com.codeka.warworlds.model.SpriteManager;

/**
 * Utility methods for getting the base URL for client-server communication and
 * retrieving shared preferences.
 */
public class Util {
    /**
     * Key for shared preferences.
     */
    private static final String SHARED_PREFS = "WARWORLDS_PREFS";

    private static Properties sProperties;
    private static boolean sWasSetup;

    /**
     * This should be called from every entry-point into the process to make
     * sure the various globals are up and running.
     */
    public static boolean setup(Context context) {
        if (sWasSetup) {
            return false;
        }

        Authenticator.configure(context);
        SpriteManager.getInstance().setup(context);
        BuildingDesignManager.getInstance().setup(context);
        ShipDesignManager.getInstance().setup(context);
        PurchaseManager.getInstance().setup(context);
        RealmManager.i.setup(context);

        sWasSetup = true;
        return true;
    }

    public static boolean isSetup() {
        return sWasSetup;
    }

    /**
     * Must be called before other methods on this class. We load up the initial
     * properties, preferences and settings to make later calls easier (and not
     * require a \c Context parameter)
     */
    public static Properties loadProperties(Context context) {
        if (sProperties != null) {
            // if it's already loaded, don't do it again
            return sProperties;
        }

        // load the warworlds.properties file and populate mProperties.
        AssetManager assetManager = context.getAssets();

        InputStream inputStream = null;
        try {
            inputStream = assetManager.open("warworlds.properties");
            sProperties = new Properties();
            sProperties.load(inputStream);
        } catch (IOException e) {
            sProperties = null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch(IOException e) {
            }
        }

        String impersonateUser = sProperties.getProperty("user.on_behalf_of", null);
        if (impersonateUser != null) {
            ApiClient.impersonate(impersonateUser);
        }

        return sProperties;
    }

    /**
     * Gets the contents of the warworlds.properties as a \c Properties. These are the static
     * properties that govern things like which server to connect to and so on.
     */
    public static Properties getProperties() {
        return sProperties;
    }

    /**
     * Returns true if we are running against a dev mode appengine instance.
     */
    public static boolean isDebug() {
        final String debugValue = sProperties.getProperty("debug");
        return debugValue.equals("true");
    }

    /**
     * Helper method to get a SharedPreferences instance.
     */
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFS, 0);
    }
}
