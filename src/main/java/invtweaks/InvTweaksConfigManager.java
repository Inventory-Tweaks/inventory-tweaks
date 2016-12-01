package invtweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.translation.I18n;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Handles the (re)loading of the configuration, and all that is related to file extraction/moves.
 *
 * @author Jimeo Wan
 */
@SuppressWarnings("ResultOfMethodCallIgnored") // Lots of these from file creation
public class InvTweaksConfigManager {

    private static final Logger log = InvTweaks.log;

    private final Minecraft mc;

    /**
     * The mod's configuration.
     */
    private InvTweaksConfig config = null;
    private long storedConfigLastModified = 0;

    private InvTweaksHandlerAutoRefill autoRefillHandler = null;
    private InvTweaksHandlerShortcuts shortcutsHandler = null;

    public InvTweaksConfigManager(Minecraft mc_) {
        mc = mc_;
    }

    private static long computeConfigLastModified() {
        return InvTweaksConst.CONFIG_RULES_FILE.lastModified() + InvTweaksConst.CONFIG_TREE_FILE.lastModified();
    }

    private static void backupFile(File file) {
        File newFile = new File(file.getParentFile(), file.getName() + ".bak");
        if(newFile.exists()) {
            newFile.delete();
        }
        file.renameTo(newFile);
    }

    private static void showConfigErrors(@SuppressWarnings("ParameterHidesMemberVariable") InvTweaksConfig config) {
        List<String> invalid = config.getInvalidKeywords();
        if(invalid.size() > 0) {
            String error = I18n.translateToLocal("invtweaks.loadconfig.invalidkeywords") + ": ";
            for(String keyword : config.getInvalidKeywords()) {
                error += keyword + " ";
            }
            InvTweaks.logInGameStatic(error);
        }
    }

    // TODO Only reload modified file(s)
    public boolean makeSureConfigurationIsLoaded() {

        // Load properties
        try {
            if(config != null && config.refreshProperties()) {
                shortcutsHandler = new InvTweaksHandlerShortcuts(mc, config);

                if(!config.getProperty(InvTweaksConfig.PROP_ENABLE_CONFIG_LOADED_MESSAGE).equals(InvTweaksConfig.VALUE_TRUE)) {
                    InvTweaks.logInGameStatic("invtweaks.propsfile.loaded");
                }
            }
        } catch(IOException e) {
            InvTweaks.logInGameErrorStatic("invtweaks.loadconfig.refresh.error", e);
        }

        // Load rules + tree files
        long configLastModified = computeConfigLastModified();
        if(config != null) {
            // Check time of last edit for both configuration files.
            return storedConfigLastModified == configLastModified || loadConfig();
        } else {
            storedConfigLastModified = configLastModified;
            return loadConfig();
        }
    }

    public InvTweaksConfig getConfig() {
        return config;
    }

    public InvTweaksHandlerAutoRefill getAutoRefillHandler() {
        return autoRefillHandler;
    }

    public InvTweaksHandlerShortcuts getShortcutsHandler() {
        return shortcutsHandler;
    }

    /**
     * Tries to load mod configuration from file, with error handling. If it fails, the config attribute will remain
     * null.
     */
    private boolean loadConfig() {

        // Ensure the config folder exists
        File configDir = InvTweaksConst.MINECRAFT_CONFIG_DIR;
        if(!configDir.exists()) {
            configDir.mkdir();
        }

        // Compatibility: Tree version check
        try {
            if(!(InvTweaksItemTreeLoader.isValidVersion(InvTweaksConst.CONFIG_TREE_FILE))) {
                backupFile(InvTweaksConst.CONFIG_TREE_FILE);
            }
        } catch(Exception e) {
            log.warn("Failed to check item tree version: " + e.getMessage());
        }

        // Compatibility: File names check
        if(InvTweaksConst.OLD_CONFIG_TREE_FILE.exists()) {
            if(InvTweaksConst.CONFIG_RULES_FILE.exists()) {
                backupFile(InvTweaksConst.CONFIG_TREE_FILE);
            }
            InvTweaksConst.OLD_CONFIG_TREE_FILE.renameTo(InvTweaksConst.CONFIG_TREE_FILE);
        } else if(InvTweaksConst.OLDER_CONFIG_RULES_FILE.exists()) {
            if(InvTweaksConst.CONFIG_RULES_FILE.exists()) {
                backupFile(InvTweaksConst.CONFIG_RULES_FILE);
            }
            InvTweaksConst.OLDER_CONFIG_RULES_FILE.renameTo(InvTweaksConst.CONFIG_RULES_FILE);
        }

        // Create missing files

        if(!InvTweaksConst.CONFIG_RULES_FILE.exists() && extractFile(InvTweaksConst.DEFAULT_CONFIG_FILE,
                InvTweaksConst.CONFIG_RULES_FILE)) {
            InvTweaks.logInGameStatic(InvTweaksConst.CONFIG_RULES_FILE + " " +
                    I18n.translateToLocal("invtweaks.loadconfig.filemissing"));
        }
        if(!InvTweaksConst.CONFIG_TREE_FILE.exists() && extractFile(InvTweaksConst.DEFAULT_CONFIG_TREE_FILE,
                InvTweaksConst.CONFIG_TREE_FILE)) {
            InvTweaks.logInGameStatic(InvTweaksConst.CONFIG_TREE_FILE + " " +
                    I18n.translateToLocal("invtweaks.loadconfig.filemissing"));
        }

        storedConfigLastModified = computeConfigLastModified();

        // Load

        String error = null;
        Exception errorException = null;

        try {

            // Configuration creation
            if(config == null) {
                config = new InvTweaksConfig(InvTweaksConst.CONFIG_RULES_FILE, InvTweaksConst.CONFIG_TREE_FILE);
                autoRefillHandler = new InvTweaksHandlerAutoRefill(mc, config);
                shortcutsHandler = new InvTweaksHandlerShortcuts(mc, config);
            }

            // Configuration loading
            config.load();
            shortcutsHandler.loadShortcuts();

            if(!config.getProperty(InvTweaksConfig.PROP_ENABLE_CONFIG_LOADED_MESSAGE).equals(InvTweaksConfig.VALUE_TRUE)) {
                InvTweaks.logInGameStatic("invtweaks.loadconfig.done");
            }
            showConfigErrors(config);
        } catch(FileNotFoundException e) {
            error = "Config file not found";
            errorException = e;
        } catch(Exception e) {
            error = "Error while loading config";
            errorException = e;
        }

        if(error != null) {
            log.error(error);
            InvTweaks.logInGameErrorStatic(error, errorException);

            try {
                // TODO: Refactor this so I'm not just copying the code from above.
                // The purpose of this is to try to deal with any errors in their config files
                // Because things crash if config is null
                backupFile(InvTweaksConst.CONFIG_TREE_FILE);
                backupFile(InvTweaksConst.CONFIG_RULES_FILE);
                backupFile(InvTweaksConst.CONFIG_PROPS_FILE);

                extractFile(InvTweaksConst.DEFAULT_CONFIG_FILE, InvTweaksConst.CONFIG_RULES_FILE);
                extractFile(InvTweaksConst.DEFAULT_CONFIG_TREE_FILE, InvTweaksConst.CONFIG_TREE_FILE);

                config = new InvTweaksConfig(InvTweaksConst.CONFIG_RULES_FILE, InvTweaksConst.CONFIG_TREE_FILE);
                autoRefillHandler = new InvTweaksHandlerAutoRefill(mc, config);
                shortcutsHandler = new InvTweaksHandlerShortcuts(mc, config);

                config.load();
                shortcutsHandler.loadShortcuts();
            } catch(Exception e) {
                // But if this fails too there's not much point in trying again
                config = null;
                autoRefillHandler = null;
                shortcutsHandler = null;

                if(e.getCause() == null) {
                    e.initCause(errorException);
                }

                throw new Error("InvTweaks config load failed", e);
            }

            return false;
        } else {
            return true;
        }
    }

    private boolean extractFile(String resourcePath, File destination) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = classloader.getResourceAsStream(resourcePath)) {
            try {
                FileUtils.copyInputStreamToFile(input, destination);
                return true;
            } catch(IOException e) {
                InvTweaks.logInGameStatic("[16] The mod won't work, because " + destination + " creation failed!");
                log.error("Cannot create " + destination + " file: " + e.getMessage());
                return false;
            }
        } catch(IOException e) {
            InvTweaks.logInGameStatic("[15] The mod won't work, because " + resourcePath + " extraction failed!");
            log.error("Cannot extract " + resourcePath + " file: " + e.getMessage());
            return false;
        }
    }

}
