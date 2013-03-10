package invtweaks;

import invtweaks.InvTweaksConst;
import invtweaks.InvTweaksItemTreeLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.IllegalFormatException;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.Minecraft;

/**
 * Handles the (re)loading of the configuration,
 * and all that is related to file extraction/moves.
 * 
 * @author Jimeo Wan
 *
 */
public class InvTweaksConfigManager {

    private static final Logger log = InvTweaks.log;

    private Minecraft mc;
    
    /**
     * The mod's configuration.
     */
    private InvTweaksConfig config = null;
    private long storedConfigLastModified = 0;

    private InvTweaksHandlerAutoRefill autoRefillHandler = null;
    private InvTweaksHandlerShortcuts shortcutsHandler = null;
    
    public InvTweaksConfigManager(Minecraft mc) {
        this.mc = mc;
    }
    
    // TODO Only reload modified file(s)
    public boolean makeSureConfigurationIsLoaded() {
    
        // Load properties
        try {
            if (config != null && config.refreshProperties()) {
                shortcutsHandler = new InvTweaksHandlerShortcuts(mc, config);
                InvTweaks.logInGameStatic("invtweaks.propsfile.loaded");
            }
        } catch (IOException e) {
            InvTweaks.logInGameErrorStatic("invtweaks.loadconfig.refresh.error", e);
        }
    
        // Load rules + tree files
        long configLastModified = computeConfigLastModified();
        if (config != null) {
            // Check time of last edit for both configuration files.
            if (storedConfigLastModified != configLastModified) {
                return loadConfig(); // Reload
            } else {
                return true;
            }
        } else {
            storedConfigLastModified = configLastModified;
            if (loadConfig()) { // Reload
                return true;
            }
            else {
                return false;
            }
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
    
    private long computeConfigLastModified() {
        return new File(InvTweaksConst.CONFIG_RULES_FILE).lastModified()
        + new File(InvTweaksConst.CONFIG_TREE_FILE).lastModified();
    }

    /**
     * Tries to load mod configuration from file, with error handling. If it
     * fails, the config attribute will remain null.
     * 
     * @param config
     */
    private boolean loadConfig() {
        
        // Ensure the config folder exists
        File configDir = new File(InvTweaksConst.MINECRAFT_CONFIG_DIR);
        if (!configDir.exists()) {
           configDir.mkdir();
        }

        // Compatibility: Tree version check
        try {
            if (!(InvTweaksItemTreeLoader.isValidVersion(InvTweaksConst.CONFIG_TREE_FILE))) {
                backupFile(new File(InvTweaksConst.CONFIG_TREE_FILE), InvTweaksConst.CONFIG_TREE_FILE);
            }
        } catch (Exception e) {
            log.warning("Failed to check item tree version: " + e.getMessage());
        }

        // Compatibility: File names check
        if (new File(InvTweaksConst.OLDER_CONFIG_RULES_FILE).exists()) {
            if (new File(InvTweaksConst.CONFIG_RULES_FILE).exists()) {
                backupFile(new File(InvTweaksConst.CONFIG_RULES_FILE), InvTweaksConst.CONFIG_RULES_FILE);
            }
            new File(InvTweaksConst.OLDER_CONFIG_RULES_FILE).renameTo(new File(InvTweaksConst.CONFIG_RULES_FILE));
        }
        if (new File(InvTweaksConst.OLDER_CONFIG_TREE_FILE).exists()) {
            backupFile(new File(InvTweaksConst.OLDER_CONFIG_TREE_FILE), InvTweaksConst.CONFIG_TREE_FILE);
        }
        if (new File(InvTweaksConst.OLD_CONFIG_TREE_FILE).exists()) {
            new File(InvTweaksConst.OLD_CONFIG_TREE_FILE).renameTo(new File(InvTweaksConst.CONFIG_TREE_FILE));
        }
        
        // Create missing files

        if (!new File(InvTweaksConst.CONFIG_RULES_FILE).exists() && 
                extractFile(InvTweaksConst.DEFAULT_CONFIG_FILE, InvTweaksConst.CONFIG_RULES_FILE)) {
            InvTweaks.logInGameStatic(InvTweaksConst.CONFIG_RULES_FILE + " " + InvTweaksLocalization.get("invtweaks.loadconfig.invalidkeywords"));
        }
        if (!new File(InvTweaksConst.CONFIG_TREE_FILE).exists() && 
                extractFile(InvTweaksConst.DEFAULT_CONFIG_TREE_FILE, InvTweaksConst.CONFIG_TREE_FILE)) {
            InvTweaks.logInGameStatic(InvTweaksConst.CONFIG_TREE_FILE + " " + InvTweaksLocalization.get("invtweaks.loadconfig.invalidkeywords"));
        }

        storedConfigLastModified = computeConfigLastModified();

        // Load

        String error = null;
        Exception errorException = null;

        try {
            
            // Configuration creation
            if (config == null) {
                config = new InvTweaksConfig(InvTweaksConst.CONFIG_RULES_FILE, InvTweaksConst.CONFIG_TREE_FILE);
                autoRefillHandler = new InvTweaksHandlerAutoRefill(mc, config);
                shortcutsHandler = new InvTweaksHandlerShortcuts(mc, config);
            }
            
            // Configuration loading
            config.load();
            shortcutsHandler.loadShortcuts();
            
            log.setLevel(config.getLogLevel());
            InvTweaks.logInGameStatic("invtweaks.loadconfig.done");
            showConfigErrors(config);
        } catch (FileNotFoundException e) {
            error = "Config file not found";
        } catch (Exception e) {
            error = "Error while loading config";
            errorException = e;
        }

        if (error != null) {
            InvTweaks.logInGameErrorStatic(error, errorException);
            log.severe(error);
            config = null;
            return false;
        } else {
            return true;
        }
    }

    private void backupFile(File file, String name) {
        File newFile = new File(name + ".bak");
        if (newFile.exists()) {
            newFile.delete();
        }
        file.renameTo(newFile);
    }

    private boolean extractFile(String resource, String destination) {

        String resourceContents = "";
        URL resourceUrl = InvTweaks.class.getResource(resource);

        // Extraction from minecraft.jar
        if (resourceUrl != null) {
            InputStream content = null;
            try {
                Object o = resourceUrl.getContent();
                if (o instanceof InputStream) {
                    content = (InputStream) o;
                    while (content.available() > 0) {
                        byte[] bytes = new byte[content.available()];
                        content.read(bytes);
                        resourceContents += new String(bytes);
                    }
                }
            } catch (IOException e) {
                resourceUrl = null;
            }
            finally {
                if (content != null) {
                    try {
                        content.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Extraction from mods folder
        if (resourceUrl == null) {

            File modFolder = new File(InvTweaksConst.MINECRAFT_DIR + File.separatorChar + "mods");

            File[] zips = modFolder.listFiles();
            if (zips != null && zips.length > 0) {
                for (File zip : zips) {
                    try {
                        ZipFile invTweaksZip = new ZipFile(zip);
                        ZipEntry zipResource = invTweaksZip.getEntry(resource);
                        if (zipResource != null) {
                            InputStream content = invTweaksZip.getInputStream(zipResource);
                            while (content.available() > 0) {
                                byte[] bytes = new byte[content.available()];
                                content.read(bytes);
                                resourceContents += new String(bytes);
                            }
                            invTweaksZip.close();
                            break;
                        }
                        else                        
                            invTweaksZip.close();                        
                    } catch (Exception e) {
                        log.warning("Failed to extract " + resource + " from mod: " + e.getMessage());
                    }
                }
            }
        }

        // Write to destination
        if (!resourceContents.isEmpty()) {
            try {
                FileWriter f = new FileWriter(destination);
                f.write(resourceContents);
                f.close();
                return true;
            } catch (IOException e) {
            	try {
            		InvTweaks.logInGameStatic(String.format(InvTweaksLocalization.get("invtweaks.extract.create.error"), destination));
            	}
            	catch (IllegalFormatException e2) {
            		InvTweaks.logInGameStatic("[16] The mod won't work, because " + destination + " creation failed!");
            	}
                log.severe("Cannot create " + destination + " file: " + e.getMessage());
                return false;
            }
        } else {
        	try {
        		InvTweaks.logInGameStatic(String.format(InvTweaksLocalization.get("invtweaks.extract.find.error"), resource));
        	}
        	catch (IllegalFormatException e2) {
        		InvTweaks.logInGameStatic("[15] The mod won't work, because " + resource + " creation failed!");
        	}
            log.severe("Cannot create " + destination + " file: " + resource + " not found");
            return false;
        }
    }

    private void showConfigErrors(InvTweaksConfig config) {
        Vector<String> invalid = config.getInvalidKeywords();
        if (invalid.size() > 0) {
            String error = InvTweaksLocalization.get("invtweaks.loadconfig.invalidkeywords") + ": ";
            for (String keyword : config.getInvalidKeywords()) {
                error += keyword + " ";
            }
            InvTweaks.logInGameStatic(error);
        }
    }

}
