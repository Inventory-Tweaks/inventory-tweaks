package invtweaks;

import invtweaks.api.container.ContainerSection;
import invtweaks.container.IContainerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * @author Jimeo Wan
 */
public class InvTweaksHandlerShortcuts extends InvTweaksObfuscation {
    private static final int DROP_SLOT = -999;
    private InvTweaksConfig config;
    private IContainerManager container;
    /**
     * Stores all pressed keys (only the one that are related to shortcuts)
     */
    private Map<Integer, Boolean> pressedKeys;
    /**
     * Stores the shortcuts mappings
     */
    private Map<InvTweaksShortcutType, List<InvTweaksShortcutMapping>> shortcuts;

    public InvTweaksHandlerShortcuts(Minecraft mc_, InvTweaksConfig config_) {
        super(mc_);
        config = config_;
        pressedKeys = new HashMap<>();
        shortcuts = new HashMap<>();
    }

    public void loadShortcuts() {
        pressedKeys.clear();
        shortcuts.clear();

        // Register shortcut mappings
        Map<String, String> keys = config.getProperties(InvTweaksConfig.PROP_SHORTCUT_PREFIX);
        for(String key : keys.keySet()) {
            String[] keyMappings = keys.get(key).split("[ ]*,[ ]*");
            InvTweaksShortcutType shortcutType = InvTweaksShortcutType.fromConfigKey(key);
            if(shortcutType != null) {
                for(String keyMapping : keyMappings) {
                    String[] keysToHold = keyMapping.split("\\+");
                    registerShortcutMapping(shortcutType, new InvTweaksShortcutMapping(keysToHold));
                }
            }
        }

        // Add Minecraft's Up & Down mappings
        int upKeyCode = getKeyBindingForwardKeyCode(),
                downKeyCode = getKeyBindingBackKeyCode();

        registerShortcutMapping(InvTweaksShortcutType.MOVE_UP, new InvTweaksShortcutMapping(upKeyCode));
        registerShortcutMapping(InvTweaksShortcutType.MOVE_DOWN, new InvTweaksShortcutMapping(downKeyCode));

        // Add hotbar shortcuts (1-9) mappings
        int[] hotbarKeys = {
                Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5, Keyboard.KEY_6,
                Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9, Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2,
                Keyboard.KEY_NUMPAD3, Keyboard.KEY_NUMPAD4, Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6,
                Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9
        };
        for(int i : hotbarKeys) {
            registerShortcutMapping(InvTweaksShortcutType.MOVE_TO_SPECIFIC_HOTBAR_SLOT,
                    new InvTweaksShortcutMapping(i));
        }

        // Register (L/R)SHIFT to allow to filter them
        pressedKeys.put(Keyboard.KEY_LSHIFT, false);
        pressedKeys.put(Keyboard.KEY_RSHIFT, false);
    }

    private void registerShortcutMapping(InvTweaksShortcutType type, InvTweaksShortcutMapping mapping) {
        // Register shortcut
        if(shortcuts.containsKey(type)) {
            shortcuts.get(type).add(mapping);
        } else {
            List<InvTweaksShortcutMapping> newMappingList = new LinkedList<>();
            newMappingList.add(mapping);
            shortcuts.put(type, newMappingList);
        }
        // Register key status listeners
        for(int keyCode : mapping.getKeyCodes()) {
            pressedKeys.put(keyCode, false);
        }
    }

    public void handleShortcut() {
        try {
            // Init shortcut
            ShortcutConfig shortcutToTrigger = computeShortcutToTrigger();
            if(shortcutToTrigger != null) {
                int ex = Mouse.getEventX(), ey = Mouse.getEventY();

                // GO!
                runShortcut(shortcutToTrigger);

                // Reset mouse status to prevent default action.
                // TODO Find a better solution, like 'anticipate' default action?
                Mouse.destroy();
                Mouse.create();

                // Fixes a tiny glitch (Steve looks for a short moment
                // at [0, 0] because of the mouse reset).
                Mouse.setCursorPosition(ex, ey);
            }

        } catch(Exception e) {
            InvTweaks.logInGameErrorStatic("invtweaks.shortcut.error", e);
        }
    }

    public ShortcutSpecification computeCurrentShortcut() {
        ShortcutSpecification.Action action = ShortcutSpecification.Action.MOVE;
        ShortcutSpecification.Target target = ShortcutSpecification.Target.UNSPECIFIED;
        ShortcutSpecification.Scope scope = ShortcutSpecification.Scope.ONE_STACK;

        updatePressedKeys();

        boolean validAction = false;
        if(isShortcutDown(InvTweaksShortcutType.DROP) != null) {
            action = ShortcutSpecification.Action.DROP;
            validAction = true;
        }

        if(action != ShortcutSpecification.Action.DROP) {
            if(isShortcutDown(InvTweaksShortcutType.MOVE_TO_SPECIFIC_HOTBAR_SLOT) != null) {
                target = ShortcutSpecification.Target.HOTBAR_SLOT;
                validAction = true;
            } else if(isShortcutDown(InvTweaksShortcutType.MOVE_UP) != null) {
                target = ShortcutSpecification.Target.UP;
                validAction = true;
            } else if(isShortcutDown(InvTweaksShortcutType.MOVE_DOWN) != null) {
                target = ShortcutSpecification.Target.DOWN;
                validAction = true;
            }
        }

        if(isShortcutDown(InvTweaksShortcutType.MOVE_ALL_ITEMS) != null) {
            scope = ShortcutSpecification.Scope.ALL_ITEMS;
            validAction = true;
        } else if(isShortcutDown(InvTweaksShortcutType.MOVE_EVERYTHING) != null) {
            scope = ShortcutSpecification.Scope.EVERYTHING;
            validAction = true;
        } else if(isShortcutDown(InvTweaksShortcutType.MOVE_ONE_ITEM) != null) {
            scope = ShortcutSpecification.Scope.ONE_ITEM;
            validAction = true;
        }

        if(validAction) {
            return new ShortcutSpecification(action, target, scope);
        } else {
            return null;
        }
    }

    private ShortcutConfig computeShortcutToTrigger() {
        ShortcutSpecification shortcut = computeCurrentShortcut();

        ShortcutConfig shortcutConfig = new ShortcutConfig();

        container = InvTweaks.getCurrentContainerManager();
        Slot slot = InvTweaksObfuscation.getSlotAtMousePosition((GuiContainer) getCurrentScreen());
        // If a valid and not empty slot is clicked
        if(shortcut != null && slot != null && (slot.getHasStack() || getHeldStack() != null)) {
            int slotNumber = getSlotNumber(slot);

            // Set shortcut origin
            shortcutConfig.fromSection = container.getSlotSection(slotNumber);
            shortcutConfig.fromIndex = container.getSlotIndex(slotNumber);
            shortcutConfig.fromStack = slot.getHasStack() ? slot.getStack().copy() : getHeldStack().copy();

            // Compute shortcut type
            // Ensure the item currently in the slot can be placed back into it for one-item shortcuts.
            if(!slot.isItemValid(slot.getStack()) && shortcut.getScope() == ShortcutSpecification.Scope.ONE_ITEM) {
                shortcut.setScope(ShortcutSpecification.Scope.ONE_STACK);
            }

            if(shortcutConfig.fromSection != null && shortcutConfig.fromIndex != -1) {
                if(shortcut.getAction() != ShortcutSpecification.Action.DROP) {
                    // Compute shortcut target
                    if(shortcut.getTarget() == ShortcutSpecification.Target.HOTBAR_SLOT) {
                        shortcutConfig.toSection = ContainerSection.INVENTORY_HOTBAR;
                        InvTweaksShortcutMapping hotbarShortcut = isShortcutDown(
                                InvTweaksShortcutType.MOVE_TO_SPECIFIC_HOTBAR_SLOT);
                        if(hotbarShortcut != null && !hotbarShortcut.getKeyCodes().isEmpty()) {
                            String keyName = Keyboard.getKeyName(hotbarShortcut.getKeyCodes().get(0));
                            shortcutConfig.toIndex = -1 + Integer.parseInt(keyName.replace("NUMPAD", ""));
                        }
                    } else {
                        // Compute targetable sections in order
                        List<ContainerSection> orderedSections = new ArrayList<>();

                        // (Top part)
                        if(container.hasSection(ContainerSection.CHEST)) {
                            orderedSections.add(ContainerSection.CHEST);
                        } else if(container.hasSection(ContainerSection.CRAFTING_IN)) {
                            orderedSections.add(ContainerSection.CRAFTING_IN);
                        } else if(container.hasSection(ContainerSection.CRAFTING_IN_PERSISTENT)) {
                            orderedSections.add(ContainerSection.CRAFTING_IN_PERSISTENT);
                        } else if(container.hasSection(ContainerSection.FURNACE_IN)) {
                            orderedSections.add(ContainerSection.FURNACE_IN);
                        } else if(container.hasSection(ContainerSection.BREWING_INGREDIENT)) {
                            if(shortcutConfig.fromStack != null) {
                                // TODO: ResourceLocation
                                if(shortcutConfig.fromStack.getItem() == Item.itemRegistry.getObject(new ResourceLocation("potion"))) {
                                    orderedSections.add(ContainerSection.BREWING_BOTTLES);
                                } else {
                                    orderedSections.add(ContainerSection.BREWING_INGREDIENT);
                                }
                            }
                        } else if(container.hasSection(ContainerSection.ENCHANTMENT)) {
                            orderedSections.add(ContainerSection.ENCHANTMENT);
                        }

                        // (Inventory part)
                        orderedSections.add(ContainerSection.INVENTORY_NOT_HOTBAR);
                        orderedSections.add(ContainerSection.INVENTORY_HOTBAR);

                        // Choose target section
                        if(shortcut
                                .getTarget() != ShortcutSpecification.Target.UNSPECIFIED) { // Explicit section (up/down shortcuts)
                            int sectionOffset = 0;
                            if(shortcut.getTarget() == ShortcutSpecification.Target.UP) {
                                sectionOffset--;
                            } else if(shortcut.getTarget() == ShortcutSpecification.Target.DOWN) {
                                sectionOffset++;
                            }
                            int fromSectionIndex = orderedSections.indexOf(shortcutConfig.fromSection);
                            if(fromSectionIndex != -1) {
                                shortcutConfig.toSection = orderedSections.get((orderedSections
                                        .size() + fromSectionIndex + sectionOffset) % orderedSections.size());
                            } else {
                                shortcutConfig.toSection = ContainerSection.INVENTORY;
                            }
                        } else { // Implicit section
                            switch(shortcutConfig.fromSection) {
                                case CHEST:
                                    shortcutConfig.toSection = ContainerSection.INVENTORY;
                                    break;
                                case INVENTORY_HOTBAR:
                                    if(orderedSections.contains(ContainerSection.CHEST)) {
                                        shortcutConfig.toSection = ContainerSection.CHEST;
                                    } else {
                                        shortcutConfig.toSection = ContainerSection.INVENTORY_NOT_HOTBAR;
                                    }
                                    break;
                                case CRAFTING_IN:
                                case CRAFTING_IN_PERSISTENT:
                                case FURNACE_IN:
                                    shortcutConfig.toSection = ContainerSection.INVENTORY_NOT_HOTBAR;
                                    break;
                                default:
                                    if(orderedSections.contains(ContainerSection.CHEST)) {
                                        shortcutConfig.toSection = ContainerSection.CHEST;
                                    } else {
                                        shortcutConfig.toSection = ContainerSection.INVENTORY_HOTBAR;
                                    }
                            }
                        }
                    }
                }

                // Shortcut modifiers
                shortcutConfig.forceEmptySlot = Mouse.isButtonDown(1);
                shortcutConfig.action = shortcut.getAction();
                shortcutConfig.scope = shortcut.getScope();

                return shortcutConfig;
            }
        }

        return null;
    }

    // XXX Bad API
    public void updatePressedKeys() {
        if(haveControlsChanged()) {
            loadShortcuts(); // Reset mappings
        }
        for(int keyCode : pressedKeys.keySet()) {
            if(keyCode > 0 && Keyboard.isKeyDown(keyCode)) {
                if(!pressedKeys.get(keyCode)) {
                    pressedKeys.put(keyCode, true);
                }
            } else {
                pressedKeys.put(keyCode, false);
            }
        }
    }

    /**
     * Checks if the Up/Down controls that are listened are outdated
     *
     * @return true if the shortuts listeners have to be reset
     */
    private boolean haveControlsChanged() {
        return (!pressedKeys.containsKey(getKeyBindingForwardKeyCode()) || !pressedKeys
                .containsKey(getKeyBindingBackKeyCode()));
    }

    private void runShortcut(ShortcutConfig shortcut) throws TimeoutException {
        // Try to put held item down
        if(getHeldStack() != null) {
            Slot slot = InvTweaksObfuscation.getSlotAtMousePosition((GuiContainer) getCurrentScreen());
            if(slot != null) {
                int slotNumber = getSlotNumber(slot);
                container.putHoldItemDown(container.getSlotSection(slotNumber), container.getSlotIndex(slotNumber));
                if(getHeldStack() != null) {
                    return;
                }
            } else {
                return;
            }
        }

        synchronized(this) {
            if(shortcut.toSection == ContainerSection.INVENTORY_HOTBAR && shortcut.toIndex != -1) {
                container.move(shortcut.fromSection, shortcut.fromIndex, shortcut.toSection, shortcut.toIndex);
            } else {
                switch(shortcut.action) {
                    case DROP: {
                        switch(shortcut.scope) {
                            case ONE_ITEM:
                                container.dropSome(shortcut.fromSection, shortcut.fromIndex, 1);
                                break;
                            case ONE_STACK:
                                container.drop(shortcut.fromSection, shortcut.fromIndex);
                                break;
                            case ALL_ITEMS:
                                dropAll(shortcut, shortcut.fromStack);
                                break;
                            case EVERYTHING:
                                dropAll(shortcut, null);
                                break;
                        }
                    }
                    case MOVE: {
                        int toIndex;
                        boolean success;
                        int newIndex;

                        switch(shortcut.scope) {
                            case ONE_STACK: {
                                Slot slot = container.getSlot(shortcut.fromSection, shortcut.fromIndex);
                                if(slot.getHasStack()) {
                                    toIndex = getNextTargetIndex(shortcut, slot.getStack());
                                    if(shortcut.fromSection != ContainerSection.CRAFTING_OUT && shortcut.toSection != ContainerSection.ENCHANTMENT) {
                                        while(slot.getHasStack() && toIndex != -1) {
                                            success = container
                                                    .move(shortcut.fromSection, shortcut.fromIndex, shortcut.toSection,
                                                            toIndex);
                                            newIndex = getNextTargetIndex(shortcut, slot.getStack());
                                            toIndex = (success ||
                                                    (shortcut.action == ShortcutSpecification.Action.DROP) ||
                                                    newIndex != toIndex) ? newIndex : -1; // Needed when we can't put items in the target slot
                                        }
                                    } else {
                                        // Move only once, since the crafting output might be refilled
                                        container.move(shortcut.fromSection, shortcut.fromIndex, shortcut.toSection,
                                                toIndex);
                                    }
                                }
                                break;

                            }

                            case ONE_ITEM: {
                                Slot slot = container.getSlot(shortcut.fromSection, shortcut.fromIndex);
                                if(slot.getHasStack()) {
                                    toIndex = getNextTargetIndex(shortcut, slot.getStack());
                                    container.moveSome(shortcut.fromSection, shortcut.fromIndex, shortcut.toSection,
                                            toIndex, 1);
                                }
                                break;
                            }

                            case ALL_ITEMS: {
                                moveAll(shortcut, shortcut.fromStack);
                                if(shortcut.fromSection == ContainerSection.INVENTORY_NOT_HOTBAR && shortcut.toSection == ContainerSection.CHEST) {
                                    shortcut.fromSection = ContainerSection.INVENTORY_HOTBAR;
                                    moveAll(shortcut, shortcut.fromStack);
                                }
                                break;
                            }

                            case EVERYTHING: {
                                moveAll(shortcut, null);
                                if(shortcut.fromSection == ContainerSection.INVENTORY_HOTBAR && shortcut.toSection == ContainerSection.CHEST) {
                                    shortcut.fromSection = ContainerSection.INVENTORY_HOTBAR;
                                    moveAll(shortcut, null);
                                }
                                break;
                            }
                        }
                    }
                }
            }

        }
    }

    private void dropAll(ShortcutConfig shortcut, ItemStack stackToMatch) {
        container.getSlots(shortcut.fromSection).stream().filter(slot -> slot.getHasStack() && (stackToMatch == null || areSameItemType(stackToMatch, slot.getStack()))).forEach(slot -> {
            int fromIndex = container.getSlotIndex(getSlotNumber(slot));
            while(slot.getHasStack()) {
                container.drop(shortcut.fromSection, fromIndex);
            }
        });
    }

    private void moveAll(ShortcutConfig shortcut, ItemStack stackToMatch) {
        int toIndex = Integer.MIN_VALUE; // This will always get overwritten before being used, but -1 caused a rapid break.
        int newIndex;

        boolean success;

        for(Slot slot : container.getSlots(shortcut.fromSection)) {
            if(slot.getHasStack() && (stackToMatch == null || areSameItemType(stackToMatch, slot.getStack()))) {
                int fromIndex = container.getSlotIndex(getSlotNumber(slot));
                toIndex = getNextTargetIndex(shortcut, slot.getStack());

                // Move while current slot has item, and there is a valid target that is not the same slot we're trying
                // to move from.
                while(slot.getHasStack() && toIndex != -1 &&
                        !(shortcut.fromSection == shortcut.toSection && fromIndex == toIndex)) {
                    success = container.move(shortcut.fromSection, fromIndex, shortcut.toSection, toIndex);
                    newIndex = getNextTargetIndex(shortcut, slot.getStack());

                    // Continue if movement succeeded, there is another slot to try, or we're dropping items.
                    // In reverse: fail if movement failed, AND there are no other slots AND we're not dropping.
                    if (newIndex == toIndex) {
                        break;
                    } else if (success || shortcut.action == ShortcutSpecification.Action.DROP) {
                        toIndex = newIndex;
                    } else {
                        toIndex = -1;
                    }
                }
            }
//             if(toIndex == -1) {
//                 break;
//             }
        }
    }

    private int getNextTargetIndex(ShortcutConfig shortcut, ItemStack current) {

        if(shortcut.action == ShortcutSpecification.Action.DROP) {
            return DROP_SLOT;
        }

        int result = -1;

        // Try to merge with existing slot
        if(!shortcut.forceEmptySlot) {
            int i = 0;
            for(Slot slot : container.getSlots(shortcut.toSection)) {
                if(slot.getHasStack()) {
                    ItemStack stack = slot.getStack();
                    if(InvTweaksObfuscation.areItemsStackable(current, stack) && stack.stackSize < stack
                            .getMaxStackSize()) {
                        result = i;
                        break;
                    }
                }
                i++;
            }
        }

        // Else find empty slot
        if(result == -1) {
            result = container.getFirstEmptyIndex(shortcut.toSection);
        }

        // Switch from FURNACE_IN to FURNACE_FUEL if the slot is taken
        // TODO Better furnace shortcuts
        if(result == -1 && shortcut.toSection == ContainerSection.FURNACE_IN) {
            shortcut.toSection = ContainerSection.FURNACE_FUEL;
            result = container.getFirstEmptyIndex(shortcut.toSection);
        }

        return result;
    }

    /**
     * Checks if shortcut has been triggered
     *
     * @return The mapping that triggered the shortcut
     */
    public InvTweaksShortcutMapping isShortcutDown(InvTweaksShortcutType type) {
        List<InvTweaksShortcutMapping> mappings = shortcuts.get(type);
        if(mappings != null) {
            for(InvTweaksShortcutMapping mapping : mappings) {
                if(mapping.isTriggered(pressedKeys)) {
                    return mapping;
                }
            }
        }
        return null;
    }

    private static class ShortcutConfig {
        public ShortcutSpecification.Action action = null;
        public ShortcutSpecification.Scope scope = null;
        public ContainerSection fromSection = null;
        public int fromIndex = -1;
        public ItemStack fromStack = null;
        public ContainerSection toSection = null;
        public int toIndex = -1;
        public boolean forceEmptySlot = false;
    }

}
