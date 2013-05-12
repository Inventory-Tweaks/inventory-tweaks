package invtweaks;

import invtweaks.api.ContainerSection;
import invtweaks.api.IItemTreeItem;
import invtweaks.forge.InvTweaksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Core of the sorting behaviour. Allows to move items in a container (inventory or chest) with respect to the mod's
 * configuration.
 *
 * @author Jimeo Wan
 */
public class InvTweaksHandlerSorting extends InvTweaksObfuscation {

    private static final Logger log = InvTweaks.log;

    public static final boolean STACK_NOT_EMPTIED = true;
    public static final boolean STACK_EMPTIED = false;

    private static int[] DEFAULT_LOCK_PRIORITIES = null;
    private static boolean[] DEFAULT_FROZEN_SLOTS = null;
    private static final int MAX_CONTAINER_SIZE = 999;

    public static final int ALGORITHM_DEFAULT = 0;
    public static final int ALGORITHM_VERTICAL = 1;
    public static final int ALGORITHM_HORIZONTAL = 2;
    public static final int ALGORITHM_INVENTORY = 3;
    public static final int ALGORITHM_EVEN_STACKS = 4;

    private InvTweaksContainerSectionManager containerMgr;
    private int algorithm;
    private int size;
    private boolean sortArmorParts;

    private InvTweaksItemTree tree;
    private Vector<InvTweaksConfigSortingRule> rules;
    private int[] rulePriority;
    private int[] keywordOrder;
    private int[] lockPriorities;
    private boolean[] frozenSlots;

    public InvTweaksHandlerSorting(Minecraft mc, InvTweaksConfig config,
                                   ContainerSection section, int algorithm, int rowSize) throws Exception {
        super(mc);

        // Init constants

        if(DEFAULT_LOCK_PRIORITIES == null) {
            DEFAULT_LOCK_PRIORITIES = new int[MAX_CONTAINER_SIZE];
            for(int i = 0; i < MAX_CONTAINER_SIZE; i++) {
                DEFAULT_LOCK_PRIORITIES[i] = 0;
            }
        }
        if(DEFAULT_FROZEN_SLOTS == null) {
            DEFAULT_FROZEN_SLOTS = new boolean[MAX_CONTAINER_SIZE];
            for(int i = 0; i < MAX_CONTAINER_SIZE; i++) {
                DEFAULT_FROZEN_SLOTS[i] = false;
            }
        }

        // Init attributes

        this.containerMgr = new InvTweaksContainerSectionManager(mc, section);
        this.size = containerMgr.getSize();
        this.sortArmorParts =
                config.getProperty(InvTweaksConfig.PROP_ENABLE_AUTO_EQUIP_ARMOR).equals(InvTweaksConfig.VALUE_TRUE)
                        && !isGuiInventoryCreative(
                        getCurrentScreen()); // FIXME Armor parts disappear when sorting in creative mode while holding an item

        this.rules = config.getRules();
        this.tree = config.getTree();
        if(section == ContainerSection.INVENTORY) {
            this.lockPriorities = config.getLockPriorities();
            this.frozenSlots = config.getFrozenSlots();
            this.algorithm = ALGORITHM_INVENTORY;
        } else {
            this.lockPriorities = DEFAULT_LOCK_PRIORITIES;
            this.frozenSlots = DEFAULT_FROZEN_SLOTS;
            this.algorithm = algorithm;
            if(algorithm != ALGORITHM_DEFAULT) {
                computeLineSortingRules(rowSize,
                                        algorithm == ALGORITHM_HORIZONTAL);
            }
        }

        this.rulePriority = new int[size];
        this.keywordOrder = new int[size];
        for(int i = 0; i < size; i++) {
            this.rulePriority[i] = -1;
            ItemStack stack = containerMgr.getItemStack(i);
            if(stack != null) {
                this.keywordOrder[i] = getItemOrder(stack);
            } else {
                this.keywordOrder[i] = -1;
            }
        }
    }

    public void sort() throws TimeoutException {
        long timer = System.nanoTime();
        InvTweaksContainerManager globalContainer = new InvTweaksContainerManager(mc);

        // Put hold item down
        if(getHeldStack() != null) {
            int emptySlot = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
            if(emptySlot != -1) {
                globalContainer.putHoldItemDown(ContainerSection.INVENTORY, emptySlot);
            } else {
                return; // Not enough room to work, abort
            }
        }

        if(algorithm != ALGORITHM_DEFAULT) {
            if(algorithm == ALGORITHM_EVEN_STACKS) {
                sortEvenStacks();
            } else if(algorithm == ALGORITHM_INVENTORY) {
                sortInventory(globalContainer);
            }
            sortWithRules();
        }

        //// Sort remaining
        defaultSorting();

        if(log.getLevel() == InvTweaksConst.DEBUG) {
            timer = System.nanoTime() - timer;
            log.info("Sorting done in " + timer + "ns");
        }

        //// Put hold item down, just in case
        if(getHeldStack() != null) {
            int emptySlot = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
            if(emptySlot != -1) {
                globalContainer.putHoldItemDown(ContainerSection.INVENTORY, emptySlot);
            }
        }

        InvTweaksMod.proxy.sortComplete();
    }

    private void sortWithRules() throws TimeoutException {
        //// Apply rules
        log.info("Applying rules.");

        // Sorts rule by rule, themselves being already sorted by decreasing priority
        for(InvTweaksConfigSortingRule rule : rules) {
            int rulePriority = rule.getPriority();

            if(log.getLevel() == InvTweaksConst.DEBUG) {
                log.info("Rule : " + rule.getKeyword() + "(" + rulePriority + ")");
            }

            // For every item in the inventory
            for(int i = 0; i < size; i++) {
                ItemStack from = containerMgr.getItemStack(i);

                // If the rule is strong enough to move the item and it matches the item, move it
                if(hasToBeMoved(i) && lockPriorities[i] < rulePriority) {
                    List<IItemTreeItem> fromItems = tree.getItems(
                            getItemID(from), getItemDamage(from));
                    if(tree.matches(fromItems, rule.getKeyword())) {

                        // Test preffered slots
                        int[] preferredSlots = rule.getPreferredSlots();
                        int stackToMove = i;
                        for(int j = 0; j < preferredSlots.length; j++) {
                            int k = preferredSlots[j];

                            // Move the stack!
                            int moveResult = move(stackToMove, k, rulePriority);
                            if(moveResult != -1) {
                                if(moveResult == k) {
                                    break;
                                } else {
                                    from = containerMgr.getItemStack(moveResult);
                                    fromItems = tree.getItems(getItemID(from), getItemDamage(from));
                                    if(!tree.matches(fromItems, rule.getKeyword())) {
                                        break;
                                    } else {
                                        stackToMove = moveResult;
                                        j = -1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        //// Don't move locked stacks
        log.info("Locking stacks.");

        for(int i = 0; i < size; i++) {
            if(hasToBeMoved(i) && lockPriorities[i] > 0) {
                markAsMoved(i, 1);
            }
        }
    }

    private void sortInventory(InvTweaksContainerManager globalContainer) throws TimeoutException {
        //// Move items out of the crafting slots
        log.info("Handling crafting slots.");
        if(globalContainer.hasSection(ContainerSection.CRAFTING_IN)) {
            List<Slot> craftingSlots = globalContainer.getSlots(ContainerSection.CRAFTING_IN);
            int emptyIndex = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
            if(emptyIndex != -1) {
                for(Slot craftingSlot : craftingSlots) {
                    if(hasStack(craftingSlot)) {
                        globalContainer.move(
                                ContainerSection.CRAFTING_IN,
                                globalContainer.getSlotIndex(getSlotNumber(craftingSlot)),
                                ContainerSection.INVENTORY,
                                emptyIndex);
                        emptyIndex = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
                        if(emptyIndex == -1) {
                            break;
                        }
                    }
                }
            }
        }

        sortMergeArmor(globalContainer);
    }

    private void sortMergeArmor(InvTweaksContainerManager globalContainer) throws TimeoutException {
        //// Merge stacks to fill the ones in locked slots
        //// + Move armor parts to the armor slots
        log.info("Merging stacks.");
        for(int i = size - 1; i >= 0; i--) {
            ItemStack from = containerMgr.getItemStack(i);
            if(from != null) {
                // Move armor parts
                Item fromItem = getItem(from);
                if(isDamageable(fromItem)) {
                    moveArmor(globalContainer, i, from, fromItem);
                }
                // Stackable objects are never damageable
                else {
                    mergeItem(i, from);
                }
            }
        }
    }

    private void mergeItem(int i, ItemStack from) throws TimeoutException {
        int j = 0;
        for(Integer lockPriority : lockPriorities) {
            if(lockPriority > 0) {
                ItemStack to = containerMgr.getItemStack(j);
                if(to != null && areItemsStackable(from, to)) {
                    move(i, j, Integer.MAX_VALUE);
                    markAsNotMoved(j);
                    if(containerMgr.getItemStack(i) == null) {
                        break;
                    }
                }
            }
            j++;
        }
    }

    private void moveArmor(InvTweaksContainerManager globalContainer, int i, ItemStack from, Item fromItem) {
        if(sortArmorParts) {
            if(isItemArmor(fromItem)) {
                ItemArmor fromItemArmor = asItemArmor(fromItem);
                if(globalContainer.hasSection(ContainerSection.ARMOR)) {
                    List<Slot> armorSlots = globalContainer.getSlots(ContainerSection.ARMOR);
                    for(Slot slot : armorSlots) {
                        boolean move = false;
                        if(!hasStack(slot)) {
                            move = true;
                        } else {
                            Item currentArmor = getItem(getStack(slot));
                            if(isItemArmor(currentArmor)) {
                                int armorLevel = getArmorLevel(asItemArmor(currentArmor));
                                if(armorLevel < getArmorLevel(fromItemArmor)
                                        || (armorLevel == getArmorLevel(fromItemArmor)
                                        && getItemDamage(getStack(slot)) < getItemDamage(from))) {
                                    move = true;
                                }
                            } else {
                                move = true;
                            }
                        }
                        if(areSlotAndStackCompatible(slot, from) && move) {
                            globalContainer.move(ContainerSection.INVENTORY, i, ContainerSection.ARMOR,
                                                 globalContainer.getSlotIndex(getSlotNumber(slot)));
                        }
                    }
                }
            }
        }
    }

    private void sortEvenStacks() throws TimeoutException {
        log.info("Distributing items.");

        //item and slot counts for each unique item
        HashMap<List<Integer>, int[]> itemCounts = new HashMap<List<Integer>, int[]>();
        for(int i = 0; i < size; i++) {
            ItemStack stack = containerMgr.getItemStack(i);
            if(stack != null) {
                List<Integer> item = Arrays.asList(getItemID(stack), getItemDamage(stack));
                int[] count = itemCounts.get(item);
                if(count == null) {
                    int[] newCount = {getStackSize(stack), 1};
                    itemCounts.put(item, newCount);
                } else {
                    count[0] += getStackSize(stack); //amount of item
                    count[1]++;                      //slots with item
                }
            }
        }

        //handle each unique item separately
        for(Entry<List<Integer>, int[]> entry : itemCounts.entrySet()) {
            List<Integer> item = entry.getKey();
            int[] count = entry.getValue();
            int numPerSlot = count[0] / count[1];  //totalNumber/numberOfSlots

            //skip hacked itemstacks that are larger than their max size
            //no idea why they would be here, but may as well account for them anyway
            if(numPerSlot <= getMaxStackSize(new ItemStack(item.get(0), 1, 0))) {
                //linkedlists to store which stacks have too many/few items
                LinkedList<Integer> smallStacks = new LinkedList<Integer>();
                LinkedList<Integer> largeStacks = new LinkedList<Integer>();
                for(int i = 0; i < size; i++) {
                    ItemStack stack = containerMgr.getItemStack(i);
                    if(stack != null && Arrays.asList(getItemID(stack), getItemDamage(stack)).equals(item)) {
                        int stackSize = getStackSize(stack);
                        if(stackSize > numPerSlot) {
                            largeStacks.offer(i);
                        } else if(stackSize < numPerSlot) {
                            smallStacks.offer(i);
                        }
                    }
                }

                //move items from stacks with too many to those with too little
                while((!smallStacks.isEmpty())) {
                    int largeIndex = largeStacks.peek();
                    int largeSize = getStackSize(containerMgr.getItemStack(largeIndex));
                    int smallIndex = smallStacks.peek();
                    int smallSize = getStackSize(containerMgr.getItemStack(smallIndex));
                    containerMgr
                            .moveSome(largeIndex, smallIndex, Math.min(numPerSlot - smallSize, largeSize - numPerSlot));

                    //update stack lists
                    largeSize = getStackSize(containerMgr.getItemStack(largeIndex));
                    smallSize = getStackSize(containerMgr.getItemStack(smallIndex));
                    if(largeSize == numPerSlot) {
                        largeStacks.remove();
                    }
                    if(smallSize == numPerSlot) {
                        smallStacks.remove();
                    }
                }

                //put all leftover into one stack for easy removal
                while(largeStacks.size() > 1) {
                    int largeIndex = largeStacks.poll();
                    int largeSize = getStackSize(containerMgr.getItemStack(largeIndex));
                    containerMgr.moveSome(largeIndex, largeStacks.peek(), largeSize - numPerSlot);
                }
            }
        }

        //mark all items as moved. (is there a better way?)
        for(int i = 0; i < size; i++) {
            markAsMoved(i, 1);
        }
    }

    private void defaultSorting() throws TimeoutException {
        log.info("Default sorting.");

        ArrayList<Integer> remaining = new ArrayList<Integer>(), nextRemaining = new ArrayList<Integer>();
        for(int i = 0; i < size; i++) {
            if(hasToBeMoved(i)) {
                remaining.add(i);
                nextRemaining.add(i);
            }
        }

        int iterations = 0;
        while(remaining.size() > 0 && iterations++ < 50) {
            for(int i : remaining) {
                if(hasToBeMoved(i)) {
                    for(int j = 0; j < size; j++) {
                        if(move(i, j, 1) != -1) {
                            nextRemaining.remove((Integer) j);
                            break;
                        }
                    }
                } else {
                    nextRemaining.remove((Integer) i);
                }
            }
            remaining.clear();
            remaining.addAll(nextRemaining);
        }
        if(iterations == 100) {
            log.warning("Sorting takes too long, aborting.");
        }

    }

    private boolean canMove(int i, int j, int priority) {
        ItemStack from = containerMgr.getItemStack(i), to = containerMgr.getItemStack(j);

        if(from == null || frozenSlots[j] || frozenSlots[i] || lockPriorities[i] > priority) {
            return false;
        } else {
            if(i == j) {
                return true;
            }

            if(to == null) {
                return lockPriorities[j] <= priority && !frozenSlots[j];
            }

            return canSwapSlots(i, j, priority) || canMergeStacks(from, to);
        }
    }

    private boolean canMergeStacks(ItemStack from, ItemStack to) {
        if(areItemsStackable(from, to)) {
            if(getStackSize(from) > getMaxStackSize(from)) {
                return false;
            }
            if(getStackSize(to) < getMaxStackSize(to)) {
                return true;
            }
        }
        return false;
    }

    private boolean canSwapSlots(int i, int j, int priority) {
        return lockPriorities[j] <= priority
                && (rulePriority[j] < priority
                || (rulePriority[j] == priority && isOrderedBefore(i, j)));
    }

    /**
     * Tries to move a stack from i to j, and swaps them if j is already occupied but i is of greater priority (even if
     * they are of same ID).
     *
     * @param i        from slot
     * @param j        to slot
     * @param priority The rule priority. Use 1 if the stack was not moved using a rule.
     *
     * @return -1 if it failed, j if the stacks were merged into one, n if the j stack has been moved to the n slot.
     *
     * @throws TimeoutException
     */
    private int move(int i, int j, int priority) throws TimeoutException {
        ItemStack from = containerMgr.getItemStack(i), to = containerMgr.getItemStack(j);

        if(from == null || frozenSlots[j] || frozenSlots[i]) {
            return -1;
        }

        //log.info("Moving " + i + " (" + from + ") to " + j + " (" + to + ") ");

        if(lockPriorities[i] <= priority) {

            if(i == j) {
                markAsMoved(i, priority);
                return j;
            }

            // Move to empty slot
            if(to == null && lockPriorities[j] <= priority && !frozenSlots[j]) {
                rulePriority[i] = -1;
                keywordOrder[i] = -1;
                rulePriority[j] = priority;
                keywordOrder[j] = getItemOrder(from);
                containerMgr.move(i, j);
                return j;
            }

            // Try to swap/merge
            else if(to != null) {
                if(canSwapSlots(i, j, priority) || canMergeStacks(from, to)) {
                    keywordOrder[j] = keywordOrder[i];
                    rulePriority[j] = priority;
                    rulePriority[i] = -1;
                    containerMgr.move(i, j);

                    ItemStack remains = containerMgr.getItemStack(i);

                    if(remains != null) {
                        int dropSlot = i;
                        if(lockPriorities[j] > lockPriorities[i]) {
                            for(int k = 0; k < size; k++) {
                                if(containerMgr.getItemStack(k) == null
                                        && lockPriorities[k] == 0) {
                                    dropSlot = k;
                                    break;
                                }
                            }
                        }
                        if(dropSlot != i) {
                            containerMgr.move(i, dropSlot);
                        }
                        rulePriority[dropSlot] = -1;
                        keywordOrder[dropSlot] = getItemOrder(remains);
                        return dropSlot;
                    } else {
                        return j;
                    }
                }
            }

        }

        return -1;
    }

    private void markAsMoved(int i, int priority) {
        rulePriority[i] = priority;
    }

    private void markAsNotMoved(int i) {
        rulePriority[i] = -1;
    }

    private boolean hasToBeMoved(int slot) {
        return containerMgr.getItemStack(slot) != null
                && rulePriority[slot] == -1;
    }

    private boolean isOrderedBefore(int i, int j) {

        ItemStack iStack = containerMgr.getItemStack(i), jStack = containerMgr.getItemStack(j);

        if(jStack == null) {
            return true;
        } else if(iStack == null || keywordOrder[i] == -1) {
            return false;
        } else {
            if(keywordOrder[i] == keywordOrder[j]) {
                // Items of same keyword orders can have different IDs,
                // in the case of categories defined by a range of IDs
                if(getItemID(iStack) == getItemID(jStack)) {
                    boolean iHasName = iStack.hasDisplayName();
                    boolean jHasName = jStack.hasDisplayName();
                    if(iHasName || jHasName) {
                        if(!iHasName) {
                            return false;
                        } else if(!jHasName) {
                            return true;
                        } else {
                            String iDisplayName = iStack.getDisplayName();
                            String jDisplayName = jStack.getDisplayName();

                            if(!iDisplayName.equals(jDisplayName)) {
                                return iDisplayName.compareTo(jDisplayName) < 0;
                            }
                        }
                    }

                    @SuppressWarnings("unchecked")
                    Map<Integer, Integer> iEnchs = EnchantmentHelper.getEnchantments(iStack);
                    @SuppressWarnings("unchecked")
                    Map<Integer, Integer> jEnchs = EnchantmentHelper.getEnchantments(jStack);
                    if(iEnchs.size() == jEnchs.size()) {
                        int iEnchMaxId = 0, iEnchMaxLvl = 0;
                        int jEnchMaxId = 0, jEnchMaxLvl = 0;

                        for(Map.Entry<Integer, Integer> ench : iEnchs.entrySet()) {
                            if(ench.getValue() > iEnchMaxLvl) {
                                iEnchMaxId = ench.getKey();
                                iEnchMaxLvl = ench.getValue();
                            } else if(ench.getValue() == iEnchMaxLvl && ench.getKey() > iEnchMaxId) {
                                iEnchMaxId = ench.getKey();
                            }
                        }

                        for(Map.Entry<Integer, Integer> ench : jEnchs.entrySet()) {
                            if(ench.getValue() > jEnchMaxLvl) {
                                jEnchMaxId = ench.getKey();
                                jEnchMaxLvl = ench.getValue();
                            } else if(ench.getValue() == jEnchMaxLvl && ench.getKey() > jEnchMaxId) {
                                jEnchMaxId = ench.getKey();
                            }
                        }

                        if(iEnchMaxId == jEnchMaxId) {
                            if(iEnchMaxLvl == jEnchMaxLvl) {
                                if(getItemDamage(iStack) != getItemDamage(jStack)) {
                                    if(isItemStackDamageable(iStack)) {
                                        return getItemDamage(iStack) > getItemDamage(jStack);
                                    } else {
                                        return getItemDamage(iStack) < getItemDamage(jStack);
                                    }
                                } else {
                                    return getStackSize(iStack) > getStackSize(jStack);
                                }
                            } else {
                                return iEnchMaxLvl > jEnchMaxLvl;
                            }
                        } else {
                            return iEnchMaxId > jEnchMaxId;
                        }
                    } else {
                        return iEnchs.size() > jEnchs.size();
                    }
                } else {
                    return getItemID(iStack) > getItemID(jStack);
                }
            } else {
                return keywordOrder[i] < keywordOrder[j];
            }
        }
    }

    private int getItemOrder(ItemStack itemStack) {
        List<IItemTreeItem> items = tree.getItems(
                getItemID(itemStack), getItemDamage(itemStack));
        return (items != null && items.size() > 0)
               ? items.get(0).getOrder()
               : Integer.MAX_VALUE;
    }

    private void computeLineSortingRules(int rowSize, boolean horizontal) {

        rules = new Vector<InvTweaksConfigSortingRule>();


        Map<IItemTreeItem, Integer> stats = computeContainerStats();
        List<IItemTreeItem> itemOrder = new ArrayList<IItemTreeItem>();

        int distinctItems = stats.size();
        int columnSize = getContainerColumnSize(rowSize);
        int spaceWidth;
        int spaceHeight;
        int availableSlots = size;
        int remainingStacks = 0;
        for(Integer stacks : stats.values()) {
            remainingStacks += stacks;
        }

        // No need to compute rules for an empty chest
        if(distinctItems == 0) {
            return;
        }

        // (Partially) sort stats by decreasing item stack count
        List<IItemTreeItem> unorderedItems = new ArrayList<IItemTreeItem>(stats.keySet());
        boolean hasStacksToOrderFirst = true;
        while(hasStacksToOrderFirst) {
            hasStacksToOrderFirst = false;
            for(IItemTreeItem item : unorderedItems) {
                Integer value = stats.get(item);
                if(value > ((horizontal) ? rowSize : columnSize)
                        && !itemOrder.contains(item)) {
                    hasStacksToOrderFirst = true;
                    itemOrder.add(item);
                    unorderedItems.remove(item);
                    break;
                }
            }
        }
        Collections.sort(unorderedItems, Collections.reverseOrder());
        itemOrder.addAll(unorderedItems);

        // Define space size used for each item type.
        if(horizontal) {
            spaceHeight = 1;
            spaceWidth = rowSize / ((distinctItems + columnSize - 1) / columnSize);
        } else {
            spaceWidth = 1;
            spaceHeight = columnSize / ((distinctItems + rowSize - 1) / rowSize);
        }

        char row = 'a', maxRow = (char) (row - 1 + columnSize);
        char column = '1', maxColumn = (char) (column - 1 + rowSize);

        // Create rules
        for(IItemTreeItem item : itemOrder) {

            // Adapt rule dimensions to fit the amount
            int thisSpaceWidth = spaceWidth,
                    thisSpaceHeight = spaceHeight;
            while(stats.get(item) > thisSpaceHeight * thisSpaceWidth) {
                if(horizontal) {
                    if(column + thisSpaceWidth < maxColumn) {
                        thisSpaceWidth = maxColumn - column + 1;
                    } else if(row + thisSpaceHeight < maxRow) {
                        thisSpaceHeight++;
                    } else {
                        break;
                    }
                } else {
                    if(row + thisSpaceHeight < maxRow) {
                        thisSpaceHeight = maxRow - row + 1;
                    } else if(column + thisSpaceWidth < maxColumn) {
                        thisSpaceWidth++;
                    } else {
                        break;
                    }
                }
            }

            // Adjust line/column ends to fill empty space
            if(horizontal && (column + thisSpaceWidth == maxColumn)) {
                thisSpaceWidth++;
            } else if(!horizontal && row + thisSpaceHeight == maxRow) {
                thisSpaceHeight++;
            }

            // Create rule
            String constraint = row + "" + column + "-"
                    + (char) (row - 1 + thisSpaceHeight)
                    + (char) (column - 1 + thisSpaceWidth);
            if(!horizontal) {
                constraint += 'v';
            }
            rules.add(new InvTweaksConfigSortingRule(tree, constraint, item.getName(), size, rowSize));

            // Check if ther's still room for more rules
            availableSlots -= thisSpaceHeight * thisSpaceWidth;
            remainingStacks -= stats.get(item);
            if(availableSlots >= remainingStacks) {
                // Move origin for next rule
                if(horizontal) {
                    if(column + thisSpaceWidth + spaceWidth <= maxColumn + 1) {
                        column += thisSpaceWidth;
                    } else {
                        column = '1';
                        row += thisSpaceHeight;
                    }
                } else {
                    if(row + thisSpaceHeight + spaceHeight <= maxRow + 1) {
                        row += thisSpaceHeight;
                    } else {
                        row = 'a';
                        column += thisSpaceWidth;
                    }
                }
                if(row > maxRow || column > maxColumn) {
                    break;
                }
            } else {
                break;
            }
        }

        String defaultRule;
        if(horizontal) {
            defaultRule = maxRow + "1-a" + maxColumn;
        } else {
            defaultRule = "a" + maxColumn + "-" + maxRow + "1v";
        }
        rules.add(new InvTweaksConfigSortingRule(tree, defaultRule,
                                                 tree.getRootCategory().getName(), size, rowSize));

    }

    private Map<IItemTreeItem, Integer> computeContainerStats() {
        Map<IItemTreeItem, Integer> stats = new HashMap<IItemTreeItem, Integer>();
        Map<Integer, IItemTreeItem> itemSearch = new HashMap<Integer, IItemTreeItem>();

        for(int i = 0; i < size; i++) {
            ItemStack stack = containerMgr.getItemStack(i);
            if(stack != null) {
                int itemSearchKey = getItemID(stack) * 100000 +
                        ((getMaxStackSize(stack) != 1) ? getItemDamage(stack) : 0);
                IItemTreeItem item = itemSearch.get(itemSearchKey);
                if(item == null) {
                    item = tree.getItems(getItemID(stack),
                                         getItemDamage(stack)).get(0);
                    itemSearch.put(itemSearchKey, item);
                    stats.put(item, 1);
                } else {
                    stats.put(item, stats.get(item) + 1);
                }
            }
        }

        return stats;
    }

    private int getContainerColumnSize(int rowSize) {
        return size / rowSize;
    }

}
