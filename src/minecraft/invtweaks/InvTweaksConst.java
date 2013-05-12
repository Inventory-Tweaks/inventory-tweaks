package invtweaks;

import net.minecraft.client.Minecraft;
import net.minecraftforge.oredict.OreDictionary;

import java.io.File;
import java.util.logging.Level;

public class InvTweaksConst {

    // Mod version
    public static final String MOD_VERSION = "@VERSION@";

    // Mod tree version
    // Change only when the tree evolves significantly enough to need to override all configs
    public static final String TREE_VERSION = "1.5.1";

    // Network Protocol version
    public static final byte PROTOCOL_VERSION = 1;
    public static final byte PACKET_LOGIN = 0x55;
    public static final byte PACKET_CLICK = 0x01;
    public static final byte PACKET_SORTCOMPLETE = 0x02;

    // Timing constants
    public static final int RULESET_SWAP_DELAY = 1000;
    public static final int AUTO_REFILL_DELAY = 200;
    public static final int POLLING_DELAY = 3;
    public static final int POLLING_TIMEOUT = 1500;
    public static final int CHEST_ALGORITHM_SWAP_MAX_INTERVAL = 2000;
    public static final int TOOLTIP_DELAY = 800;

    // File constants
    public static final String MINECRAFT_DIR = getMinecraftDir();
    public static final String MINECRAFT_CONFIG_DIR = MINECRAFT_DIR + "config" + File.separatorChar;
    public static final String CONFIG_PROPS_FILE = MINECRAFT_CONFIG_DIR + "InvTweaks.cfg";
    public static final String CONFIG_RULES_FILE = MINECRAFT_CONFIG_DIR + "InvTweaksRules.txt";
    public static final String CONFIG_TREE_FILE = MINECRAFT_CONFIG_DIR + "InvTweaksTree.txt";
    public static final String OLD_CONFIG_TREE_FILE = MINECRAFT_CONFIG_DIR + "InvTweaksTree.xml";
    public static final String OLDER_CONFIG_RULES_FILE = MINECRAFT_DIR + "InvTweaksRules.txt";
    public static final String OLDER_CONFIG_TREE_FILE = MINECRAFT_DIR + "InvTweaksTree.txt";
    public static final String DEFAULT_CONFIG_FILE = "DefaultConfig.dat";
    public static final String DEFAULT_CONFIG_TREE_FILE = "DefaultTree.dat";
    public static final String HELP_URL = "http://modding.kalam-alami.net/invtweaks";

    // Global mod constants
    public static final String INGAME_LOG_PREFIX = "InvTweaks: ";
    public static final Level DEFAULT_LOG_LEVEL = Level.WARNING;
    public static final Level DEBUG = Level.INFO;
    public static final int JIMEOWAN_ID = 54696386; // Used in GUIs

    // Minecraft constants
    public static final int INVENTORY_SIZE = 36;
    public static final int INVENTORY_ROW_SIZE = 9;
    public static final int CHEST_ROW_SIZE = INVENTORY_ROW_SIZE;
    public static final int DISPENSER_ROW_SIZE = 3;
    public static final int INVENTORY_HOTBAR_SIZE = INVENTORY_ROW_SIZE;
    public static final int DAMAGE_WILDCARD = OreDictionary.WILDCARD_VALUE;

    /**
     * Returns the Minecraft folder ensuring: - It is an absolute path - It ends with a folder separator
     */
    public static String getMinecraftDir() {
        String absolutePath = Minecraft.getMinecraftDir().getAbsolutePath();
        if(absolutePath.endsWith(".")) {
            return absolutePath.substring(0, absolutePath.length() - 1);
        }
        if(absolutePath.endsWith(File.separator)) {
            return absolutePath;
        } else {
            return absolutePath + File.separatorChar;
        }
    }

}
