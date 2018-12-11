package invtweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.resources.I18n;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import invtweaks.integration.ItemListSorter;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
    @Nullable
    private InvTweaksConfig config = null;
    private long storedConfigLastModified = 0;

    @Nullable
    private InvTweaksHandlerAutoRefill autoRefillHandler = null;
    @Nullable
    private InvTweaksHandlerShortcuts shortcutsHandler = null;

    public InvTweaksConfigManager(Minecraft mc_) {
        mc = mc_;
    }

    private static long computeConfigLastModified() {
        long sum = Long.MIN_VALUE;
        if (InvTweaksConst.INVTWEAKS_CONFIG_DIR.exists())
        {
            File[] treeFiles = InvTweaksConst.INVTWEAKS_CONFIG_DIR.listFiles();
            
            for(File tree: treeFiles) {
                //Make sure it is the type of file we want.
                if (tree.getName().endsWith(".tree")) {
                    sum += tree.lastModified();
                }
            }
        }
        return sum + InvTweaksConst.CONFIG_RULES_FILE.lastModified() + InvTweaksConst.CONFIG_TREE_FILE.lastModified();
    }

    private static void backupFile(@NotNull File file) {
        @NotNull File newFile = new File(file.getParentFile(), file.getName() + ".bak");
        log.warn("Backing up file: %1$s to %2$s", file.getAbsolutePath(), newFile.getAbsolutePath());
        if(newFile.exists()) {
            log.warn("New file %1$s already exists, deleting old.", newFile.getAbsolutePath());
            newFile.delete();
        }
        file.renameTo(newFile);
    }

    private static void showConfigErrors(@NotNull @SuppressWarnings("ParameterHidesMemberVariable") InvTweaksConfig config) {
        List<String> invalid = config.getInvalidKeywords();
        if(invalid.size() > 0) {
            @NotNull String error = I18n.format("invtweaks.loadconfig.invalidkeywords") + ": ";
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

                if(config.getProperty(InvTweaksConfig.PROP_ENABLE_CONFIG_LOADED_MESSAGE).equals(InvTweaksConfig.VALUE_TRUE)) {
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

    @Nullable
    public InvTweaksConfig getConfig() {
        return config;
    }

    @Nullable
    public InvTweaksHandlerAutoRefill getAutoRefillHandler() {
        return autoRefillHandler;
    }

    @Nullable
    public InvTweaksHandlerShortcuts getShortcutsHandler() {
        return shortcutsHandler;
    }

    /**
     * Tries to load mod configuration from file, with error handling. If it fails, the config attribute will remain
     * null.
     */
    private boolean loadConfig() {

        // Ensure the config folder exists
        @NotNull File configDir = InvTweaksConst.MINECRAFT_CONFIG_DIR;
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
                    I18n.format("invtweaks.loadconfig.filemissing"));
        }
        if(!InvTweaksConst.CONFIG_TREE_FILE.exists() && extractFile(InvTweaksConst.DEFAULT_CONFIG_TREE_FILE,
                InvTweaksConst.CONFIG_TREE_FILE)) {
            InvTweaks.logInGameStatic(InvTweaksConst.CONFIG_TREE_FILE + " " +
                    I18n.format("invtweaks.loadconfig.filemissing"));
        }
        File baseTreeFile = new File(InvTweaksConst.INVTWEAKS_CONFIG_DIR, "minecraft.tree");
        //Extract all of the files if the folder is there but the main tree file is missing.
        //This is so if the user deletes any of the others, they aren't forced to have it.
        if (InvTweaksConst.INVTWEAKS_CONFIG_DIR.exists()) {
            if (!baseTreeFile.exists()) {
                try {
                    //mc.getResourceManager().getAllResources(location)
                    List<ResourceLocation> treeFiles = getResourceNames("trees");
                    for(ResourceLocation srcTreeFile: treeFiles) {
                        //log.info(srcTreeFile.getResourcePath());
                        String fileName = srcTreeFile.getResourcePath().substring(6);
                        File realTreeFile = new File(InvTweaksConst.INVTWEAKS_CONFIG_DIR, fileName);
                        //log.info(realTreeFile.getAbsolutePath());
                        if (!realTreeFile.exists()) {
                            extractFile(srcTreeFile, realTreeFile);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting merge tree files: " + e);
                }
            }
            
            InvTweaksItemTreeBuilder.buildNewTree();
        }

        storedConfigLastModified = computeConfigLastModified();

        // Load

        @Nullable String error = null;
        @Nullable Exception errorException = null;

        try {

            // Configuration creation
            if(config == null) {
                if (InvTweaksConst.MERGED_TREE_FILE.exists()) {
                    config = new InvTweaksConfig(InvTweaksConst.CONFIG_RULES_FILE, InvTweaksConst.MERGED_TREE_FILE);
                } else {
                    config = new InvTweaksConfig(InvTweaksConst.CONFIG_RULES_FILE, InvTweaksConst.CONFIG_TREE_FILE);
                }
                autoRefillHandler = new InvTweaksHandlerAutoRefill(mc, config);
                shortcutsHandler = new InvTweaksHandlerShortcuts(mc, config);
            }

            // Configuration loading
            config.load();
            shortcutsHandler.loadShortcuts();

            if(config.getProperty(InvTweaksConfig.PROP_ENABLE_CONFIG_LOADED_MESSAGE).equals(InvTweaksConfig.VALUE_TRUE)) {
                InvTweaks.logInGameStatic("invtweaks.loadconfig.done");
            }
            showConfigErrors(config);
            ItemListSorter.ReloadItemList();
            
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
                ItemListSorter.ReloadItemList();
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
    
    public void ExtractModdedTreeFile()
    {
        //Create the tree file directory, this enables the merging system.
        if (!InvTweaksConst.INVTWEAKS_CONFIG_DIR.exists()) {
            InvTweaksConst.INVTWEAKS_CONFIG_DIR.mkdir();
        }
        //This file change will trigger the loader to execute.
        extractFile(InvTweaksConst.MODDED_CONFIG_TREE_FILE, InvTweaksConst.CONFIG_TREE_FILE);
    }

    private boolean extractFile(@NotNull ResourceLocation resource, @NotNull File destination) {
        try(@NotNull InputStream input = mc.getResourceManager().getResource(resource).getInputStream()) {
            try {
                FileUtils.copyInputStreamToFile(input, destination);
                return true;
            } catch(IOException e) {
                InvTweaks.logInGameStatic("[16] The mod won't work, because " + destination + " creation failed!");
                log.error("Cannot create " + destination + " file: " + e.getMessage());
                return false;
            }
        } catch(IOException e) {
            InvTweaks.logInGameStatic("[15] The mod won't work, because " + resource + " extraction failed!");
            log.error("Cannot extract " + resource + " file: " + e.getMessage());
            return false;
        }
    }
    
    private List<ResourceLocation> getResourceNames(String inPath) {
        ArrayList<ResourceLocation> fileList = new ArrayList<>();
        String workingPath = "/assets/" + InvTweaksConst.INVTWEAKS_RESOURCE_DOMAIN + "/" + inPath;
        FileSystem filesystem = null;
        URL url = InvTweaks.class.getResource(workingPath);
        try
        {
            if (url != null)
            {
                URI uri = url.toURI();
                Path path = null;

                if ("file".equals(uri.getScheme()))
                {
                    path = Paths.get(InvTweaks.class.getResource(workingPath).toURI());
                }
                else
                {
                    filesystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    path = filesystem.getPath(workingPath);
                }
                
                Iterator<Path> it = Files.walk(path).iterator();
                
                while(it.hasNext()) {
                    Path instance = it.next();
                    if (instance.toFile().isFile())
                        fileList.add(new ResourceLocation(InvTweaksConst.INVTWEAKS_RESOURCE_DOMAIN, inPath + "/" + instance.getFileName()));
                }
            }
        }
        catch (Exception e)
        {
            log.error(e);
        }
        
        return fileList;
    }

}
