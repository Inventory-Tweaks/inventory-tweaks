package invtweaks;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;


/**
 * The global mod's configuration.
 *
 * @author Jimeo Wan
 */
public class InvTweaksLocalization {

    private static final Logger log = InvTweaks.log;
    private static final String LANG_RESOURCES_LOCATION = "/invtweaks/lang/";
    private static final String DEFAULT_LANGUAGE = "en_US";
    private static Properties defaultMappings = new Properties();
    private static Properties mappings = new Properties();
    private static String loadedLanguage = null;

    public synchronized static String get(String key) {

        String currentLanguage = InvTweaksObfuscation.getCurrentLanguage();
        if(!currentLanguage.equals(loadedLanguage)) {
            loadedLanguage = load(currentLanguage);
        }

        return mappings.getProperty(key,
                                    defaultMappings.getProperty(key, key));

    }

    private static String load(String currentLanguage) {

        defaultMappings.clear();
        mappings.clear();

        try {
            InputStream langStream = InvTweaksLocalization.class
                    .getResourceAsStream(LANG_RESOURCES_LOCATION + currentLanguage + ".properties");
            InputStream defaultLangStream = InvTweaksLocalization.class
                    .getResourceAsStream(LANG_RESOURCES_LOCATION + DEFAULT_LANGUAGE + ".properties");

            mappings.load((langStream == null) ? defaultLangStream : langStream);
            defaultMappings.load(defaultLangStream);

            if(langStream != null) {
                langStream.close();
            }
            defaultLangStream.close();
        } catch(Exception e) {
            e.printStackTrace();
        }

        return currentLanguage;
    }

}