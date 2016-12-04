package invtweaks;

import invtweaks.api.IItemTree;
import invtweaks.api.IItemTreeItem;
import invtweaks.api.SortingMethod;
import invtweaks.api.container.ContainerSection;
import invtweaks.container.ContainerSectionManager;
import invtweaks.container.DirectContainerManager;
import invtweaks.container.IContainerManager;
import invtweaks.forge.InvTweaksMod;
import invtweaks.integration.ItemListChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;


/**
 * Main class for Inventory Tweaks, which maintains various hooks and dispatches the events to the correct handlers.
 *
 * @author Jimeo Wan
 *         <p>
 *         Contact: jimeo.wan (at) gmail (dot) com Website: <a href="https://inventory-tweaks.readthedocs.org/">https://inventory-tweaks.readthedocs.org/</a>
 *         Source code: <a href="https://github.com/kobata/inventory-tweaks">GitHub</a> License: MIT
 */
public class InvTweaks extends InvTweaksObfuscation {
    public static Logger log;

    private static InvTweaks instance;

    /**
     * The configuration loader.
     */
    private InvTweaksConfigManager cfgManager = null;

    /**
     * Attributes to remember the status of chest sorting while using middle clicks.
     */
    private SortingMethod chestAlgorithm = SortingMethod.DEFAULT;
    private long chestAlgorithmClickTimestamp = 0;
    private boolean chestAlgorithmButtonDown = false;

    /**
     * Various information concerning the context, stored on each tick to allow for certain features (auto-refill,
     * sorting on pick up...)
     */
    private ItemStack storedStack = null;
    private String storedStackId = null;
    private int storedStackDamage = InvTweaksConst.DAMAGE_WILDCARD, storedFocusedSlot = -1;
    private ItemStack[] hotbarClone = new ItemStack[InvTweaksConst.INVENTORY_HOTBAR_SIZE];
    private boolean hadFocus = true, mouseWasDown = false;

    private boolean wasInGUI = false;
    /**
     * Allows to trigger some logic only every Const.POLLING_DELAY.
     */
    private int tickNumber = 0, lastPollingTickNumber = -InvTweaksConst.POLLING_DELAY;

    /**
     * Stores when the sorting key was last pressed (allows to detect long key holding)
     */
    private long sortingKeyPressedDate = 0;
    private boolean sortKeyDown = false;
    private boolean sortKeyEnabled = true;
    private boolean textboxMode = false;

    private boolean itemPickupPending = false;
    private int itemPickupTimeout = 0;

    private List<String> queuedMessages = new ArrayList<>();

    private final ItemListChecker itemListChecker = new ItemListChecker();

    /**
     * Creates an instance of the mod, and loads the configuration from the files, creating them if necessary.
     */
    public InvTweaks(Minecraft mc_) {
        super(mc_);

        //log.setLevel(InvTweaksConst.DEFAULT_LOG_LEVEL);

        // Store instance
        instance = this;

        // Load config files
        cfgManager = new InvTweaksConfigManager(mc);
        if(cfgManager.makeSureConfigurationIsLoaded()) {
            log.info("Mod initialized");
        } else {
            log.error("Mod failed to initialize!");
        }
    }

    public static void logInGameStatic(String message) {
        InvTweaks.getInstance().logInGame(message);
    }

    public static void logInGameErrorStatic(String message, Exception e) {
        InvTweaks.getInstance().logInGameError(message, e);
    }

    /**
     * @return InvTweaks instance
     */
    public static InvTweaks getInstance() {
        return instance;
    }

    public static Minecraft getMinecraftInstance() {
        return instance.mc;
    }

    public static InvTweaksConfigManager getConfigManager() {
        return instance.cfgManager;
    }

    public static IContainerManager getContainerManager(Container container) {
        // TODO: Reenable when it doesn't just break everything
        //if(getConfigManager().getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_CONTAINER_MIRRORING).equals(InvTweaksConfig.VALUE_TRUE)) {
        //    return new MirroredContainerManager(container);
        //} else {
            return new DirectContainerManager(container);
        //}
    }

    public static IContainerManager getCurrentContainerManager() {
        return getContainerManager(InvTweaksObfuscation.getCurrentContainer());
    }

    private static int getContainerRowSize(GuiContainer guiContainer) {
        return getSpecialChestRowSize(guiContainer.inventorySlots);
    }

    private static String buildLogString(Level level, String message, Exception e) {
        if(e != null) {
            StackTraceElement[] trace = e.getStackTrace();

            if(trace.length == 0) {
                return buildLogString(level, message) + ": " + e.getMessage();
            }

            StackTraceElement exceptionLine = trace[0];
            if(exceptionLine != null && exceptionLine.getFileName() != null) {
                return buildLogString(level, message) + ": " + e.getMessage() + " (l" + exceptionLine.getLineNumber() +
                        " in " + exceptionLine.getFileName().replace("InvTweaks", "") + ")";
            } else {
                return buildLogString(level, message) + ": " + e.getMessage();
            }
        } else {
            return buildLogString(level, message);
        }
    }

    private static String buildLogString(Level level, String message) {
        return InvTweaksConst.INGAME_LOG_PREFIX + ((level.equals(Level.SEVERE)) ? "[ERROR] " : "") + message;
    }

    public void addScheduledTask(Runnable task) {
        InvTweaksMod.proxy.addClientScheduledTask(task);
    }

    /**
     * To be called on each tick during the game (except when in a menu). Handles the auto-refill.
     */
    public void onTickInGame() {
        synchronized(this) {
            if(!onTick()) {
                return;
            }
            handleAutoRefill();
            if(wasInGUI) {
                wasInGUI = false;
                textboxMode = false;
            }
        }
    }

    /**
     * To be called on each tick when a menu is open. Handles the GUI additions and the middle clicking.
     */
    public void onTickInGUI(GuiScreen guiScreen) {
        synchronized(this) {
            handleMiddleClick(guiScreen); // Called before the rest to be able to trigger config reload
            if(!onTick()) {
                return;
            }
            if(isTimeForPolling()) {
                unlockKeysIfNecessary();
            }
            if(isGuiContainer(guiScreen)) {
                handleGUILayout((GuiContainer) guiScreen);
            }
            if(!wasInGUI) {
                // Right-click is always true on initial open of GUI.
                // Ignore it to prevent erroneous trigger of shortcuts.
                mouseWasDown = true;
            }
            if(isGuiContainer(guiScreen)) {
                handleShortcuts((GuiContainer) guiScreen);
            }

            // Copy some info about current selected stack for auto-refill
            ItemStack currentStack = getFocusedStack();

            // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
            storedStackId = (currentStack == null) ? null : Item.REGISTRY.getNameForObject(currentStack.getItem()).toString();
            storedStackDamage = (currentStack == null) ? 0 : currentStack.getItemDamage();
            if(!wasInGUI) {
                wasInGUI = true;
            }
        }
    }

    /**
     * To be called every time the sorting key is pressed. Sorts the inventory.
     */
    public final void onSortingKeyPressed() {
        synchronized(this) {

            // Check config loading success
            if(!cfgManager.makeSureConfigurationIsLoaded()) {
                return;
            }

            // Check current GUI
            GuiScreen guiScreen = getCurrentScreen();
            if(guiScreen == null || (isGuiContainer(guiScreen) && (isValidChest(
                    ((GuiContainer) guiScreen).inventorySlots) || isValidInventory(
                    ((GuiContainer) guiScreen).inventorySlots)))) {
                // Sorting!
                handleSorting(guiScreen);
            }
        }
    }

    /**
     * To be called everytime a stack has been picked up. Moves the picked up item in another slot that matches best the
     * current configuration.
     */
    public void onItemPickup() {

        if(!cfgManager.makeSureConfigurationIsLoaded()) {
            return;
        }
        InvTweaksConfig config = cfgManager.getConfig();
        // Handle option to disable this feature
        if(cfgManager.getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_SORTING_ON_PICKUP).equals("false")) {
            itemPickupPending = false;
            return;
        }

        try {
            ContainerSectionManager containerMgr = new ContainerSectionManager(
                    ContainerSection.INVENTORY);

            // Find stack slot (look in hotbar only).
            // We're looking for a brand new stack in the hotbar
            // (not an existing stack whose amount has been increased)
            int currentSlot = -1;
            for(int i = 0; i < InvTweaksConst.INVENTORY_HOTBAR_SIZE; i++) {
                ItemStack currentHotbarStack = containerMgr.getItemStack(i + 27);
                // Don't move already started stacks
                if(!currentHotbarStack.isEmpty() && currentHotbarStack.getAnimationsToGo() > 0 && hotbarClone[i] == null) {
                    currentSlot = i + 27;
                }
            }

            if(currentSlot != -1) {
                itemPickupPending = false;

                // Find preferred slots
                IItemTree tree = config.getTree();
                ItemStack stack = containerMgr.getItemStack(currentSlot);

                // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                List<IItemTreeItem> items = tree.getItems(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getItemDamage());

                List<Integer> preferredPositions = config.getRules().stream().filter(rule -> tree.matches(items, rule.getKeyword()))
                        .flatMapToInt(e -> Arrays.stream(e.getPreferredSlots()))
                        .boxed()
                        .collect(Collectors.toList());

                // Find best slot for stack
                boolean hasToBeMoved = true;
                for(int newSlot : preferredPositions) {
                    // Already in the best slot!
                    if(newSlot == currentSlot) {
                        hasToBeMoved = false;
                        break;
                    }
                    // Is the slot available?
                    else if(containerMgr.getItemStack(newSlot).isEmpty()) {
                        // TODO: Check rule level before to move
                        if(containerMgr.move(currentSlot, newSlot)) {
                            break;
                        }
                    }
                }

                // Else, put the slot anywhere
                if(hasToBeMoved) {
                    for(int i = 0; i < containerMgr.getSize(); i++) {
                        if(containerMgr.getItemStack(i).isEmpty()) {
                            if(containerMgr.move(currentSlot, i)) {
                                break;
                            }
                        }
                    }
                }

                // Sync after pickup movements.
                containerMgr.applyChanges();

            } else {
                if(--itemPickupTimeout == 0) {
                    itemPickupPending = false;
                }
            }

        } catch(Exception e) {
            logInGameError("Failed to move picked up stack", e);
            itemPickupPending = false;
        }
    }

    public int compareItems(ItemStack i, ItemStack j) {
        return compareItems(i, j, getItemOrder(i), getItemOrder(j));
    }

    int compareItems(ItemStack i, ItemStack j, int orderI, int orderJ) {
        if(j == null) {
            return -1;
        } else if(i == null || orderI == -1) {
            return 1;
        } else {
            if(orderI == orderJ) {
                // Items of same keyword orders can have different IDs,
                // in the case of categories defined by a range of IDs
                if(i.getItem() == j.getItem()) {
                    boolean iHasName = i.hasDisplayName();
                    boolean jHasName = j.hasDisplayName();
                    if(iHasName || jHasName) {
                        if(!iHasName) {
                            return -1;
                        } else if(!jHasName) {
                            return 1;
                        } else {
                            String iDisplayName = i.getDisplayName();
                            String jDisplayName = j.getDisplayName();

                            if(!iDisplayName.equals(jDisplayName)) {
                                return iDisplayName.compareTo(jDisplayName);
                            }
                        }
                    }

                    Map<Enchantment, Integer> iEnchs = EnchantmentHelper.getEnchantments(i);
                    Map<Enchantment, Integer> jEnchs = EnchantmentHelper.getEnchantments(j);
                    if(iEnchs.size() == jEnchs.size()) {
                        int iEnchMaxId = 0, iEnchMaxLvl = 0;
                        int jEnchMaxId = 0, jEnchMaxLvl = 0;

                        // TODO: This is really arbitrary but there's not really a good way to do this generically.
                        for(Map.Entry<Enchantment, Integer> ench : iEnchs.entrySet()) {
                            int enchId = Enchantment.getEnchantmentID(ench.getKey());
                            if(ench.getValue() > iEnchMaxLvl) {
                                iEnchMaxId = enchId;
                                iEnchMaxLvl = ench.getValue();
                            } else if(ench.getValue() == iEnchMaxLvl && enchId > iEnchMaxId) {
                                iEnchMaxId = enchId;
                            }
                        }

                        for(Map.Entry<Enchantment, Integer> ench : jEnchs.entrySet()) {
                            int enchId = Enchantment.getEnchantmentID(ench.getKey());
                            if(ench.getValue() > jEnchMaxLvl) {
                                jEnchMaxId = enchId;
                                jEnchMaxLvl = ench.getValue();
                            } else if(ench.getValue() == jEnchMaxLvl && enchId > jEnchMaxId) {
                                jEnchMaxId = enchId;
                            }
                        }

                        if(iEnchMaxId == jEnchMaxId) {
                            if(iEnchMaxLvl == jEnchMaxLvl) {
                                if(i.getItemDamage() != j.getItemDamage()) {
                                    if(i.isItemStackDamageable() && !getConfigManager().getConfig().getProperty(InvTweaksConfig.PROP_INVERT_TOOL_DAMAGE).equals(InvTweaksConfig.VALUE_TRUE)) {
                                        return j.getItemDamage() - i.getItemDamage();
                                    } else {
                                        return i.getItemDamage() - j.getItemDamage();
                                    }
                                } else {
                                    return j.getCount() - i.getCount();
                                }
                            } else {
                                return jEnchMaxLvl - iEnchMaxLvl;
                            }
                        } else {
                            return jEnchMaxId - iEnchMaxId;
                        }
                    } else {
                        return jEnchs.size() - iEnchs.size();
                    }
                } else {
                    // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                    return ObjectUtils.compare(Item.REGISTRY.getNameForObject(i.getItem()).toString(),
                            Item.REGISTRY.getNameForObject(j.getItem()).toString());
                }
            } else {
                return orderI - orderJ;
            }
        }
    }

    public void setItemPickupPending(boolean value) {
        itemPickupPending = value;
        itemPickupTimeout = 5;
    }

    public void setSortKeyEnabled(boolean enabled) {
        sortKeyEnabled = enabled;
    }

    public void setTextboxMode(boolean enabled) {
        textboxMode = enabled;
    }

    public void logInGame(String message) {
        logInGame(message, false);
    }

    public void printQueuedMessages() {
        if(mc.ingameGUI != null && !queuedMessages.isEmpty()) {
            queuedMessages.forEach(this::addChatMessage);
            queuedMessages.clear();
        }
    }

    public void logInGame(String message, boolean alreadyTranslated) {
        String formattedMsg = buildLogString(Level.INFO,
                (alreadyTranslated) ? message : I18n.translateToLocal(message));

        if(mc.ingameGUI == null) {
            queuedMessages.add(formattedMsg);
        } else {
            addChatMessage(formattedMsg);
        }

        log.info(formattedMsg);
    }

    public void logInGameError(String message, Exception e) {
        e.printStackTrace();
        String formattedMsg = buildLogString(Level.SEVERE, I18n.translateToLocal(message), e);

        if(mc.ingameGUI == null) {
            queuedMessages.add(formattedMsg);
        } else {
            addChatMessage(formattedMsg);
        }
    }

    private boolean onTick() {
        printQueuedMessages();

        tickNumber++;

        // Not calling "cfgManager.makeSureConfigurationIsLoaded()" for performance reasons
        InvTweaksConfig config = cfgManager.getConfig();
        if(config == null) {
            return false;
        }

        // Clone the hotbar to be able to monitor changes on it
        if(itemPickupPending) {
            onItemPickup();
        }
        GuiScreen currentScreen = getCurrentScreen();
        if(currentScreen == null || isGuiInventory(currentScreen)) {
            cloneHotbar();
        }

        // Handle sort key
        if(isSortingShortcutDown()) {
            if(!sortKeyDown) {
                sortKeyDown = true;
                onSortingKeyPressed();
            }
        } else {
            sortKeyDown = false;
        }

        // Handle config switch
        handleConfigSwitch();

        return true;

    }

    private void handleConfigSwitch() {

        InvTweaksConfig config = cfgManager.getConfig();
        GuiScreen currentScreen = getCurrentScreen();

        // Switch between configurations (shortcut)
        cfgManager.getShortcutsHandler().updatePressedKeys();
        InvTweaksShortcutMapping switchMapping = cfgManager.getShortcutsHandler().isShortcutDown(
                InvTweaksShortcutType.MOVE_TO_SPECIFIC_HOTBAR_SLOT);
        if(isSortingShortcutDown() && switchMapping != null) {
            String newRuleset = null;
            int pressedKey = switchMapping.getKeyCodes().get(0);
            if(pressedKey >= Keyboard.KEY_1 && pressedKey <= Keyboard.KEY_9) {
                newRuleset = config.switchConfig(pressedKey - Keyboard.KEY_1);
            } else {
                switch(pressedKey) {
                    case Keyboard.KEY_NUMPAD1:
                        newRuleset = config.switchConfig(0);
                        break;
                    case Keyboard.KEY_NUMPAD2:
                        newRuleset = config.switchConfig(1);
                        break;
                    case Keyboard.KEY_NUMPAD3:
                        newRuleset = config.switchConfig(2);
                        break;
                    case Keyboard.KEY_NUMPAD4:
                        newRuleset = config.switchConfig(3);
                        break;
                    case Keyboard.KEY_NUMPAD5:
                        newRuleset = config.switchConfig(4);
                        break;
                    case Keyboard.KEY_NUMPAD6:
                        newRuleset = config.switchConfig(5);
                        break;
                    case Keyboard.KEY_NUMPAD7:
                        newRuleset = config.switchConfig(6);
                        break;
                    case Keyboard.KEY_NUMPAD8:
                        newRuleset = config.switchConfig(7);
                        break;
                    case Keyboard.KEY_NUMPAD9:
                        newRuleset = config.switchConfig(8);
                        break;
                }
            }

            if(newRuleset != null) {
                logInGame(String.format(I18n.translateToLocal("invtweaks.loadconfig.enabled"), newRuleset),
                        true);
                // Hack to prevent 2nd way to switch configs from being enabled
                sortingKeyPressedDate = Integer.MAX_VALUE;
            }
        }

        // Switch between configurations (by holding the sorting key)
        if(isSortingShortcutDown()) {
            long currentTime = System.currentTimeMillis();
            if(sortingKeyPressedDate == 0) {
                sortingKeyPressedDate = currentTime;
            } else if(currentTime - sortingKeyPressedDate > InvTweaksConst.RULESET_SWAP_DELAY && sortingKeyPressedDate != Integer.MAX_VALUE) {
                String previousRuleset = config.getCurrentRulesetName();
                String newRuleset = config.switchConfig();
                // Log only if there is more than 1 ruleset
                if(previousRuleset != null && newRuleset != null && !previousRuleset.equals(newRuleset)) {
                    logInGame(String.format(I18n.translateToLocal("invtweaks.loadconfig.enabled"), newRuleset),
                            true);
                    handleSorting(currentScreen);
                }
                sortingKeyPressedDate = currentTime;
            }
        } else {
            sortingKeyPressedDate = 0;
        }

    }

    @SuppressWarnings("unused")
    private void handleSorting(GuiScreen guiScreen) {

        ItemStack selectedItem = null;
        int focusedSlot = getFocusedSlot();
        NonNullList<ItemStack> mainInventory = getMainInventory();
        if(focusedSlot < mainInventory.size() && focusedSlot >= 0) {
            selectedItem = mainInventory.get(focusedSlot);
        }

        // Sorting
        try {
            new InvTweaksHandlerSorting(mc, cfgManager.getConfig(), ContainerSection.INVENTORY,
                    SortingMethod.INVENTORY, InvTweaksConst.INVENTORY_ROW_SIZE)
                    .sort();
        } catch(Exception e) {
            logInGameError("invtweaks.sort.inventory.error", e);
            e.printStackTrace();
        }

        playClick();
    }

    private void handleAutoRefill() {

        ItemStack currentStack = getFocusedStack();
        ItemStack offhandStack = getOffhandStack();

        // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
        String currentStackId = (currentStack == null) ? null : Item.REGISTRY.getNameForObject(
                currentStack.getItem()).toString();

        int currentStackDamage = (currentStack == null) ? 0 : currentStack.getItemDamage();
        int focusedSlot = getFocusedSlot() + 27; // Convert to container slots index
        InvTweaksConfig config = cfgManager.getConfig();


        if(storedFocusedSlot != focusedSlot) { // Filter selection change
            storedFocusedSlot = focusedSlot;
        } else if(!ItemStack.areItemsEqual(currentStack, storedStack) && storedStackId != null) {
            if (!ItemStack.areItemStacksEqual(offhandStack, storedStack)) { // Checks not switched to offhand
                if (currentStack == null || (currentStack.getItem() == Items.BOWL && Objects.equals(storedStackId, "minecraft:mushroom_stew"))
                    // Handle eaten mushroom soup
                        && (getCurrentScreen() == null || // Filter open inventory or other window
                        isGuiEditSign(
                                getCurrentScreen()))) { // TODO: This should be more expandable on 'equivalent' items (API?) and allowed GUIs

                    if (config.isAutoRefillEnabled(storedStackId, storedStackDamage)) {
                        try {
                            cfgManager.getAutoRefillHandler().autoRefillSlot(focusedSlot, storedStackId, storedStackDamage);
                        } catch(Exception e) {
                            logInGameError("invtweaks.sort.autorefill.error", e);
                        }
                    }
                } else {
                    // Item
                    int itemMaxDamage = currentStack.getMaxDamage();
                    int autoRefillThreshhold = config.getIntProperty(InvTweaksConfig.PROP_AUTO_REFILL_DAMAGE_THRESHHOLD);
                    if (canToolBeReplaced(currentStackDamage, itemMaxDamage, autoRefillThreshhold) && config
                            .getProperty(InvTweaksConfig.PROP_AUTO_REFILL_BEFORE_BREAK)
                            .equals(InvTweaksConfig.VALUE_TRUE) && config
                            .isAutoRefillEnabled(storedStackId, storedStackDamage)) {
                        // Trigger auto-refill before the tool breaks
                        try {
                            cfgManager.getAutoRefillHandler().autoRefillSlot(focusedSlot, storedStackId, storedStackDamage);
                        } catch (Exception e) {
                            logInGameError("invtweaks.sort.autorefill.error", e);
                        }
                    }
                }
            }
        }

        // Copy some info about current selected stack for auto-refill
        storedStack = currentStack;
        storedStackId = currentStackId;
        storedStackDamage = currentStackDamage;

    }

    private boolean canToolBeReplaced(int currentStackDamage, int itemMaxDamage, int autoRefillThreshhold) {
        return itemMaxDamage != 0 && itemMaxDamage - currentStackDamage < autoRefillThreshhold && itemMaxDamage - storedStackDamage >= autoRefillThreshhold;
    }

    private void handleMiddleClick(GuiScreen guiScreen) {
        if(Mouse.isButtonDown(2)) {

            if(!cfgManager.makeSureConfigurationIsLoaded()) {
                return;
            }
            InvTweaksConfig config = cfgManager.getConfig();

            // Check that middle click sorting is allowed
            if(config.getProperty(InvTweaksConfig.PROP_ENABLE_MIDDLE_CLICK)
                    .equals(InvTweaksConfig.VALUE_TRUE) && isGuiContainer(guiScreen)) {

                GuiContainer guiContainer = (GuiContainer) guiScreen;
                Container container = guiContainer.inventorySlots;

                if(!chestAlgorithmButtonDown) {
                    chestAlgorithmButtonDown = true;

                    IContainerManager containerMgr = getContainerManager(container);
                    Slot slotAtMousePosition = InvTweaksObfuscation
                            .getSlotAtMousePosition((GuiContainer) getCurrentScreen());
                    ContainerSection target = null;
                    if(slotAtMousePosition != null) {
                        target = containerMgr.getSlotSection(getSlotNumber(slotAtMousePosition));
                    }

                    if(isValidChest(container)) {

                        // Check if the middle click target the chest or the inventory
                        // (copied GuiContainer.getSlotAtPosition algorithm)

                        if(ContainerSection.CHEST.equals(target)) {

                            // Play click
                            playClick();

                            long timestamp = System.currentTimeMillis();
                            if(timestamp - chestAlgorithmClickTimestamp > InvTweaksConst.CHEST_ALGORITHM_SWAP_MAX_INTERVAL) {
                                chestAlgorithm = SortingMethod.DEFAULT;
                            }
                            try {
                                new InvTweaksHandlerSorting(mc, cfgManager.getConfig(), ContainerSection.CHEST,
                                        chestAlgorithm, getContainerRowSize(guiContainer)).sort();
                            } catch(Exception e) {
                                logInGameError("invtweaks.sort.chest.error", e);
                                e.printStackTrace();
                            }
                            // TODO: Better replacement for this.
                            chestAlgorithm = SortingMethod.values()[(chestAlgorithm.ordinal() + 1) % 3];
                            chestAlgorithmClickTimestamp = timestamp;

                        } else if(ContainerSection.CRAFTING_IN.equals(target) || ContainerSection.CRAFTING_IN_PERSISTENT
                                .equals(target)) {
                            try {
                                new InvTweaksHandlerSorting(mc, cfgManager.getConfig(), target,
                                        SortingMethod.EVEN_STACKS,
                                        (containerMgr.getSize(target) == 9) ? 3 : 2).sort();
                            } catch(Exception e) {
                                logInGameError("invtweaks.sort.crafting.error", e);
                                e.printStackTrace();
                            }

                        } else if(ContainerSection.INVENTORY_HOTBAR.equals(target) || (ContainerSection
                                .INVENTORY_NOT_HOTBAR.equals(target))) {
                            handleSorting(guiScreen);
                        }

                    } else if(isValidInventory(container)) {
                        if(ContainerSection.CRAFTING_IN.equals(target) || ContainerSection.CRAFTING_IN_PERSISTENT
                                .equals(target)) {
                            // Crafting stacks evening
                            try {
                                new InvTweaksHandlerSorting(mc, cfgManager.getConfig(), target,
                                        SortingMethod.EVEN_STACKS,
                                        (containerMgr.getSize(target) == 9) ? 3 : 2).sort();
                            } catch(Exception e) {
                                logInGameError("invtweaks.sort.crafting.error", e);
                                e.printStackTrace();
                            }
                        } else {
                            // Sorting
                            handleSorting(guiScreen);
                        }
                    }
                }
            }
        } else {
            chestAlgorithmButtonDown = false;
        }
    }

    private void handleGUILayout(GuiContainer guiContainer) {

        InvTweaksConfig config = cfgManager.getConfig();

        Container container = guiContainer.inventorySlots;

        boolean isValidChest = isValidChest(container);

        if(showButtons(container)) {
            int w = 10, h = 10;

            // Re-layout when NEI/JEI changes states.
            final boolean isItemListVisible = itemListChecker.isVisible();
            final boolean wasItemListVisible = itemListChecker.wasVisible();
            final boolean relayout = isItemListVisible != wasItemListVisible;

            // Look for the mods buttons
            boolean customButtonsAdded = false;

            List<GuiButton> controlList = guiContainer.buttonList;
            List<GuiButton> toRemove = new ArrayList<>();
            for(GuiButton button : controlList) {
                if(button.id >= InvTweaksConst.JIMEOWAN_ID && button.id < (InvTweaksConst.JIMEOWAN_ID + 4)) {
                    if(relayout) {
                        toRemove.add(button);
                    } else {
                        customButtonsAdded = true;
                        break;
                    }
                }
            }
            controlList.removeAll(toRemove);
            guiContainer.buttonList = controlList;

            if(!customButtonsAdded) {

                // Check for custom button texture
                boolean customTextureAvailable = hasTexture(
                        new ResourceLocation("inventorytweaks", "textures/gui/button10px.png"));

                // Inventory button
                if(!isValidChest) {
                    controlList.add(new InvTweaksGuiSettingsButton(cfgManager, InvTweaksConst.JIMEOWAN_ID,
                            guiContainer.guiLeft + guiContainer.xSize - 15,
                            guiContainer.guiTop + 5, w, h, "...",
                            I18n.translateToLocal(
                                    "invtweaks.button.settings.tooltip"),
                            customTextureAvailable));
                }

                // Chest buttons
                else {
                    // Reset sorting algorithm selector
                    chestAlgorithmClickTimestamp = 0;

                    int id = InvTweaksConst.JIMEOWAN_ID,
                            x = guiContainer.guiLeft + guiContainer.xSize - 16,
                            y = guiContainer.guiTop + 5;
                    boolean isChestWayTooBig = isLargeChest(guiContainer.inventorySlots);

                    // NotEnoughItems/JustEnoughItems compatibility
                    if(isChestWayTooBig && isItemListVisible) {
                        x = guiContainer.guiLeft + guiContainer.xSize - 35;
                        y += 50;
                    }

                    // Settings button
                    controlList
                            .add(new InvTweaksGuiSettingsButton(cfgManager, id++, (isChestWayTooBig) ? x + 22 : x - 1,
                                    (isChestWayTooBig) ? y - 3 : y, w, h, "...",
                                    I18n.translateToLocal(
                                            "invtweaks.button.settings.tooltip"),
                                    customTextureAvailable));

                    // Sorting buttons
                    if(!config.getProperty(InvTweaksConfig.PROP_SHOW_CHEST_BUTTONS).equals("false")) {

                        int rowSize = getContainerRowSize(guiContainer);

                        GuiButton button = new InvTweaksGuiSortingButton(cfgManager, id++,
                                (isChestWayTooBig) ? x + 22 : x - 13,
                                (isChestWayTooBig) ? y + 12 : y, w, h, "h",
                                I18n.translateToLocal(
                                        "invtweaks.button.chest3.tooltip"),
                                SortingMethod.HORIZONTAL,
                                rowSize, customTextureAvailable);
                        controlList.add(button);

                        button = new InvTweaksGuiSortingButton(cfgManager, id++, (isChestWayTooBig) ? x + 22 : x - 25,
                                (isChestWayTooBig) ? y + 25 : y, w, h, "v", I18n
                                .translateToLocal("invtweaks.button.chest2.tooltip"),
                                SortingMethod.VERTICAL, rowSize,
                                customTextureAvailable);
                        controlList.add(button);

                        //noinspection UnusedAssignment (Using ++ for extensibility)
                        button = new InvTweaksGuiSortingButton(cfgManager, id++, (isChestWayTooBig) ? x + 22 : x - 37,
                                (isChestWayTooBig) ? y + 38 : y, w, h, "s", I18n
                                .translateToLocal("invtweaks.button.chest1.tooltip"),
                                SortingMethod.DEFAULT, rowSize,
                                customTextureAvailable);
                        controlList.add(button);

                    }
                }
            }
        } else {
            // Remove "..." button from non-survival tabs of the creative screen
            if(isGuiInventoryCreative(guiContainer)) {
                List<GuiButton> controlList = guiContainer.buttonList;
                GuiButton buttonToRemove = null;
                for(GuiButton o : controlList) {
                    if(o.id == InvTweaksConst.JIMEOWAN_ID) {
                        buttonToRemove = o;
                        break;
                    }
                }
                if(buttonToRemove != null) {
                    controlList.remove(buttonToRemove);
                }

            }
        }

    }

    private void handleShortcuts(GuiContainer guiScreen) {

        // Check open GUI
        if(!(isValidChest(guiScreen.inventorySlots) || isValidInventory(guiScreen.inventorySlots))) {
            return;
        }

        // Configurable shortcuts
        if(Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            if(!mouseWasDown) {
                mouseWasDown = true;

                // The mouse has just been clicked,
                // trigger a shortcut according to the pressed keys.
                if(cfgManager.getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_SHORTCUTS).equals("true")) {
                    cfgManager.getShortcutsHandler().handleShortcut();
                }
            }
        } else {
            mouseWasDown = false;
        }

    }

    private int getItemOrder(ItemStack itemStack) {
        // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
        List<IItemTreeItem> items = cfgManager.getConfig().getTree().getItems(Item.REGISTRY.getNameForObject(itemStack.getItem()).toString(),
                itemStack.getItemDamage(), itemStack.getTagCompound());
        return (items != null && items.size() > 0) ? items.get(0).getOrder() : Integer.MAX_VALUE;
    }

    private boolean isSortingShortcutDown() {
        if(sortKeyEnabled && !textboxMode) {
            int keyCode = cfgManager.getConfig().getSortKeyCode();
            if(keyCode > 0) {
                return Keyboard.isKeyDown(keyCode);
            } else {
                return Mouse.isButtonDown(100 + keyCode);
            }
        } else {
            return false;
        }
    }

    private boolean isTimeForPolling() {
        if(tickNumber - lastPollingTickNumber >= InvTweaksConst.POLLING_DELAY) {
            lastPollingTickNumber = tickNumber;
        }
        return tickNumber - lastPollingTickNumber == 0;
    }

    /**
     * When Minecraft gains focus, reset all pressed keys to avoid the "stuck keys" bug.
     */
    private void unlockKeysIfNecessary() {
        boolean hasFocus = Display.isActive();
        if(!hadFocus && hasFocus) {
            Keyboard.destroy();
            boolean firstTry = true;
            while(!Keyboard.isCreated()) {
                try {
                    Keyboard.create();
                } catch(LWJGLException e) {
                    if(firstTry) {
                        logInGameError("invtweaks.keyboardfix.error", e);
                        firstTry = false;
                    }
                }
            }
            if(!firstTry) {
                logInGame("invtweaks.keyboardfix.recover");
            }
        }
        hadFocus = hasFocus;
    }

    /**
     * Allows to maintain a clone of the hotbar contents to track changes (especially needed by the "on pickup"
     * features).
     */
    private void cloneHotbar() {
        NonNullList<ItemStack> mainInventory = getMainInventory();
        for(int i = 0; i < 9; i++) {
            hotbarClone[i] = mainInventory.get(i).copy();
        }
    }

    private void playClick() {
        if(!cfgManager.getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_SOUNDS)
                .equals(InvTweaksConfig.VALUE_FALSE)) {
            mc.getSoundHandler()
                    .playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

}
