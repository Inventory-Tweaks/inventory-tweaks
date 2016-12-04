package invtweaks;

import invtweaks.api.IItemTreeItem;
import invtweaks.api.SortingMethod;
import invtweaks.api.container.ContainerSection;
import invtweaks.container.ContainerSectionManager;
import invtweaks.container.IContainerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.Map.Entry;

/**
 * Core of the sorting behaviour. Allows to move items in a container (inventory or chest) with respect to the mod's
 * configuration.
 *
 * @author Jimeo Wan
 */
public class InvTweaksHandlerSorting extends InvTweaksObfuscation {
    private static final Logger log = InvTweaks.log;
    private static final int MAX_CONTAINER_SIZE = 999;
    private static int[] DEFAULT_LOCK_PRIORITIES = null;
    private static boolean[] DEFAULT_FROZEN_SLOTS = null;
    private ContainerSectionManager containerMgr;
    private SortingMethod algorithm;
    private int size;
    private boolean sortArmorParts;

    private InvTweaksItemTree tree;
    private List<InvTweaksConfigSortingRule> rules;
    private int[] rulePriority;
    private int[] keywordOrder;
    private int[] lockPriorities;
    private boolean[] frozenSlots;

    public InvTweaksHandlerSorting(Minecraft mc_, InvTweaksConfig config, ContainerSection section, SortingMethod algorithm_,
                                   int rowSize) throws Exception {
        super(mc_);

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

        containerMgr = new ContainerSectionManager(section);
        size = containerMgr.getSize();
        sortArmorParts = config.getProperty(InvTweaksConfig.PROP_ENABLE_AUTO_EQUIP_ARMOR)
                .equals(InvTweaksConfig.VALUE_TRUE) && !isGuiInventoryCreative(
                getCurrentScreen()); // FIXME Armor parts disappear when sorting in creative mode while holding an item

        rules = config.getRules();
        tree = config.getTree();
        if(section == ContainerSection.INVENTORY) {
            lockPriorities = config.getLockPriorities();
            frozenSlots = config.getFrozenSlots();
            algorithm = SortingMethod.INVENTORY;
        } else {
            lockPriorities = DEFAULT_LOCK_PRIORITIES;
            frozenSlots = DEFAULT_FROZEN_SLOTS;
            algorithm = algorithm_;
            if(algorithm != SortingMethod.DEFAULT) {
                computeLineSortingRules(rowSize, algorithm == SortingMethod.HORIZONTAL);
            }
        }

        rulePriority = new int[size];
        keywordOrder = new int[size];
        for(int i = 0; i < size; i++) {
            rulePriority[i] = -1;
            ItemStack stack = containerMgr.getItemStack(i);
            if(!stack.isEmpty()) {
                keywordOrder[i] = getItemOrder(stack);
            } else {
                keywordOrder[i] = -1;
            }
        }

        // Initialize rule priority for currently matching items
        // TODO: J1.8 streams?
        rules.stream().filter(rule -> rule.getContainerSize() == size).forEach(rule -> {
            int priority = rule.getPriority();
            for(int slot : rule.getPreferredSlots()) {
                ItemStack stack = containerMgr.getItemStack(slot);
                if(!stack.isEmpty()) {
                    List<IItemTreeItem> items = tree
                            .getItems(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getItemDamage(), stack.getTagCompound());
                    if(rulePriority[slot] < priority && tree.matches(items, rule.getKeyword())) {
                        rulePriority[slot] = priority;
                    }
                }
            }
        });
    }

    private static boolean canMergeStacks(ItemStack from, ItemStack to) {
        if(areItemsStackable(from, to)) {
            // We will not merge from a stack that exceeds its maximum size already, as these cannot be normally obtained.
            if(from.getCount() > from.getMaxStackSize()) {
                return false;
            }

            // If the destination stack has any room left, we can add to it.
            if(to.getCount() < to.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public void sort() {
        long timer = System.nanoTime();
        IContainerManager globalContainer = InvTweaks.getCurrentContainerManager();

        // Put hold item down
        if(!getHeldStack().isEmpty()) {
            int emptySlot = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
            if(emptySlot != -1) {
                globalContainer.putHoldItemDown(ContainerSection.INVENTORY, emptySlot);
            } else {
                return; // Not enough room to work, abort
            }
        }

        if(algorithm != SortingMethod.DEFAULT) {
            if(algorithm == SortingMethod.EVEN_STACKS) {
                sortEvenStacks();
            } else if(algorithm == SortingMethod.INVENTORY) {
                sortInventory(globalContainer);
            }
            sortWithRules();
        }

        //// Sort remaining
        defaultSorting();

        if(log.isEnabled(InvTweaksConst.DEBUG)) {
            timer = System.nanoTime() - timer;
            log.info("Sorting done in " + timer + "ns");
        }

        //// Put hold item down, just in case
        if(!getHeldStack().isEmpty()) {
            int emptySlot = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
            if(emptySlot != -1) {
                globalContainer.putHoldItemDown(ContainerSection.INVENTORY, emptySlot);
            }
        }

        globalContainer.applyChanges();
    }

    private void sortWithRules() {
        //// Apply rules
        log.info("Applying rules.");

        // Sorts rule by rule, themselves being already sorted by decreasing priority
        for(InvTweaksConfigSortingRule rule : rules) {
            int priority = rule.getPriority();

            if(log.isEnabled(InvTweaksConst.DEBUG)) {
                log.info("Rule : " + rule.getKeyword() + "(" + priority + ")");
            }

            // For every item in the inventory
            for(int i = 0; i < size; i++) {
                ItemStack from = containerMgr.getItemStack(i);

                // If the rule is strong enough to move the item and it matches the item, move it
                if(hasToBeMoved(i, priority) && lockPriorities[i] < priority) {
                    // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                    List<IItemTreeItem> fromItems = tree
                            .getItems(Item.REGISTRY.getNameForObject(from.getItem()).toString(), from.getItemDamage(), from.getTagCompound());
                    if(tree.matches(fromItems, rule.getKeyword())) {

                        // Test preferred slots
                        int[] preferredSlots = rule.getPreferredSlots();
                        int stackToMove = i;
                        for(int k : preferredSlots) {
                            // Move the stack!
                            int moveResult = move(stackToMove, k, priority);
                            if(moveResult != -1) {
                                if(moveResult == k) {
                                    break;
                                } else {
                                    from = containerMgr.getItemStack(moveResult);
                                    // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                                    fromItems = tree.getItems(Item.REGISTRY.getNameForObject(from.getItem()).toString(), from.getItemDamage(), from.getTagCompound());
                                    if(tree.matches(fromItems, rule.getKeyword())) {
                                        if(i >= moveResult) {
                                            // Current or already-processed slot.
                                            stackToMove = moveResult;
                                            //j = -1; // POSSIBLE INFINITE LOOP. But having this missing may cause sorting to take a few tries to stabilize in specific situations.
                                        } else {
                                            // The item will be processed later
                                            break;
                                        }
                                    } else {
                                        break;
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
            if(hasToBeMoved(i, 1) && lockPriorities[i] > 0) {
                markAsMoved(i, 1);
            }
        }
    }

    private void sortInventory(IContainerManager globalContainer) {
        //// Move items out of the crafting slots
        log.info("Handling crafting slots.");
        if(globalContainer.hasSection(ContainerSection.CRAFTING_IN)) {
            List<Slot> craftingSlots = globalContainer.getSlots(ContainerSection.CRAFTING_IN);
            int emptyIndex = globalContainer.getFirstEmptyIndex(ContainerSection.INVENTORY);
            if(emptyIndex != -1) {
                for(Slot craftingSlot : craftingSlots) {
                    if(craftingSlot.getHasStack()) {
                        globalContainer.move(ContainerSection.CRAFTING_IN,
                                globalContainer.getSlotIndex(getSlotNumber(craftingSlot)),
                                ContainerSection.INVENTORY, emptyIndex);
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

    private void sortMergeArmor(IContainerManager globalContainer) {
        //// Merge stacks to fill the ones in locked slots
        //// + Move armor parts to the armor slots
        log.info("Merging stacks.");
        for(int i = size - 1; i >= 0; i--) {
            ItemStack from = containerMgr.getItemStack(i);
            if(!from.isEmpty()) {
                // Move armor parts
                // Item
                Item fromItem = from.getItem();
                if(fromItem.isDamageable()) {
                    moveArmor(globalContainer, i, from, fromItem);
                }
                // Stackable objects are never damageable
                else {
                    mergeItem(i, from);
                }
            }
        }
    }

    private void mergeItem(int i, ItemStack from) {
        int j = 0;
        for(Integer lockPriority : lockPriorities) {
            if(lockPriority > 0) {
                ItemStack to = containerMgr.getItemStack(j);
                if(!to.isEmpty() && areItemsStackable(from, to)) {
                    move(i, j, Integer.MAX_VALUE);
                    markAsNotMoved(j);
                    if(containerMgr.getItemStack(i).isEmpty()) {
                        break;
                    }
                }
            }
            j++;
        }
    }

    private void moveArmor(IContainerManager globalContainer, int i, ItemStack from, Item fromItem) {
        if(sortArmorParts) {
            if(isItemArmor(fromItem)) {
                // ItemArmor
                ItemArmor fromItemArmor = (ItemArmor) fromItem;
                if(globalContainer.hasSection(ContainerSection.ARMOR)) {
                    List<Slot> armorSlots = globalContainer.getSlots(ContainerSection.ARMOR);
                    for(Slot slot : armorSlots) {
                        boolean move = false;
                        if(!slot.getHasStack()) {
                            move = true;
                        } else {
                            // Item
                            Item currentArmor = slot.getStack().getItem();
                            if(isItemArmor(currentArmor)) {
                                // ItemArmor
                                // ItemArmor
                                int armorLevel = ((ItemArmor) currentArmor).damageReduceAmount;
                                // ItemArmor
                                // ItemArmor
                                if(armorLevel < fromItemArmor.damageReduceAmount || (armorLevel == fromItemArmor.damageReduceAmount && slot
                                        .getStack().getItemDamage() < from.getItemDamage())) {
                                    move = true;
                                }
                            } else {
                                move = true;
                            }
                        }
                        if(slot.isItemValid(from) && move) {
                            globalContainer.move(ContainerSection.INVENTORY, i, ContainerSection.ARMOR,
                                    globalContainer.getSlotIndex(getSlotNumber(slot)));
                        }
                    }
                }
            }
        }
    }

    private void sortEvenStacks() {
        log.info("Distributing items.");

        //item and slot counts for each unique item
        HashMap<Pair<String, Integer>, int[]> itemCounts = new HashMap<>();
        for(int i = 0; i < size; i++) {
            ItemStack stack = containerMgr.getItemStack(i);
            if(!stack.isEmpty()) {
                // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                Pair<String, Integer> item = Pair.of(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getItemDamage());
                int[] count = itemCounts.get(item);
                if(count == null) {
                    int[] newCount = {stack.getCount(), 1};
                    itemCounts.put(item, newCount);
                } else {
                    count[0] += stack.getCount(); //amount of item
                    count[1]++;                      //slots with item
                }
            }
        }

        //handle each unique item separately
        for(Entry<Pair<String, Integer>, int[]> entry : itemCounts.entrySet()) {
            Pair<String, Integer> item = entry.getKey();
            int[] count = entry.getValue();
            int numPerSlot = count[0] / count[1];  //totalNumber/numberOfSlots

            //skip hacked itemstacks that are larger than their max size
            //no idea why they would be here, but may as well account for them anyway
            // TODO: ResourceLocation
            if(numPerSlot <= new ItemStack(Item.REGISTRY.getObject(new ResourceLocation(item.getLeft())), 1, 0).getMaxStackSize()) {
                //linkedlists to store which stacks have too many/few items
                LinkedList<Integer> smallStacks = new LinkedList<>();
                LinkedList<Integer> largeStacks = new LinkedList<>();
                for(int i = 0; i < size; i++) {
                    ItemStack stack = containerMgr.getItemStack(i);
                    // TODO: ResourceLocation
                    if(!stack.isEmpty() && Pair.of(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getItemDamage())
                            .equals(item)) {
                        int stackSize = stack.getCount();
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
                    int largeSize = containerMgr.getItemStack(largeIndex).getCount();
                    int smallIndex = smallStacks.peek();
                    int smallSize = containerMgr.getItemStack(smallIndex).getCount();
                    containerMgr
                            .moveSome(largeIndex, smallIndex, Math.min(numPerSlot - smallSize, largeSize - numPerSlot));

                    //update stack lists
                    largeSize = containerMgr.getItemStack(largeIndex).getCount();
                    smallSize = containerMgr.getItemStack(smallIndex).getCount();
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
                    int largeSize = containerMgr.getItemStack(largeIndex).getCount();
                    containerMgr.moveSome(largeIndex, largeStacks.peek(), largeSize - numPerSlot);
                }
            }
        }

        //mark all items as moved. (is there a better way?)
        for(int i = 0; i < size; i++) {
            markAsMoved(i, 1);
        }
    }

    private void defaultSorting() {
        log.info("Default sorting.");

        ArrayList<Integer> remaining = new ArrayList<>(), nextRemaining = new ArrayList<>();
        for(int i = 0; i < size; i++) {
            if(hasToBeMoved(i, 1)) {
                remaining.add(i);
                nextRemaining.add(i);
            }
        }

        int iterations = 0;
        while(remaining.size() > 0 && iterations++ < 50) {
            for(int i : remaining) {
                if(hasToBeMoved(i, 1)) {
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
            log.warn("Sorting takes too long, aborting.");
        }

    }

    private boolean canSwapSlots(int i, int j, int priority) {
        return lockPriorities[j] <= priority && (rulePriority[j] < priority || (rulePriority[j] == priority && isOrderedBefore(
                i, j)));
    }

    /**
     * Tries to move a stack from i to j, and swaps them if j is already occupied but i is of greater priority (even if
     * they are of same ID).
     *
     * @param i        from slot
     * @param j        to slot
     * @param priority The rule priority. Use 1 if the stack was not moved using a rule.
     * @return -1 if it failed, j if the stacks were merged into one, n if the j stack has been moved to the n slot.
     */
    private int move(int i, int j, int priority) {
        ItemStack from = containerMgr.getItemStack(i), to = containerMgr.getItemStack(j);

        if(from.isEmpty()|| frozenSlots[j] || frozenSlots[i]) {
            return -1;
        }

        //log.info("Moving " + i + " (" + from + ") to " + j + " (" + to + ") ");

        if(lockPriorities[i] <= priority) {

            if(i == j) {
                markAsMoved(i, priority);
                return j;
            }

            // Move to empty slot
            if(to.isEmpty() && lockPriorities[j] <= priority && !frozenSlots[j]) {
                rulePriority[i] = -1;
                keywordOrder[i] = -1;
                rulePriority[j] = priority;
                keywordOrder[j] = getItemOrder(from);
                if(containerMgr.move(i, j)) {
                    return j;
                } else {
                    return -1;
                }
            }

            // Try to swap/merge
            else if(!to.isEmpty()) {
                if(canSwapSlots(i, j, priority) || canMergeStacks(from, to)) {
                    keywordOrder[j] = keywordOrder[i];
                    rulePriority[j] = priority;
                    rulePriority[i] = -1;
                    boolean success = containerMgr.move(i, j);

                    if(success) {
                        ItemStack remains = containerMgr.getItemStack(i);

                        if(!remains.isEmpty()) {
                            int dropSlot = i;
                            if(lockPriorities[j] > lockPriorities[i]) {
                                for(int k = 0; k < size; k++) {
                                    if(containerMgr.getItemStack(k).isEmpty()&& lockPriorities[k] == 0) {
                                        dropSlot = k;
                                        break;
                                    }
                                }
                            }
                            if(dropSlot != i) {
                                if(!containerMgr.move(i, dropSlot)) {
                                    // TODO: This is a potentially bad situation: One move succeeded, then the rest failed.
                                    return -1;
                                }
                            }
                            rulePriority[dropSlot] = -1;
                            keywordOrder[dropSlot] = getItemOrder(remains);
                            return dropSlot;
                        } else {
                            return j;
                        }
                    } else {
                        return -1;
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

    private boolean hasToBeMoved(int slot, int priority) {
        return !containerMgr.getItemStack(slot).isEmpty() && rulePriority[slot] <= priority;
    }

    private boolean isOrderedBefore(int i, int j) {
        ItemStack iStack = containerMgr.getItemStack(i), jStack = containerMgr.getItemStack(j);

        return InvTweaks.getInstance().compareItems(iStack, jStack, keywordOrder[i], keywordOrder[j]) < 0;
    }

    private int getItemOrder(ItemStack itemStack) {
        // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
        List<IItemTreeItem> items = tree.getItems(Item.REGISTRY.getNameForObject(itemStack.getItem()).toString(), itemStack.getItemDamage(), itemStack.getTagCompound());
        return (items != null && items.size() > 0) ? items.get(0).getOrder() : Integer.MAX_VALUE;
    }

    private void computeLineSortingRules(int rowSize, boolean horizontal) {

        rules = new ArrayList<>();


        Map<IItemTreeItem, Integer> stats = computeContainerStats();
        List<IItemTreeItem> itemOrder = new ArrayList<>();

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
        List<IItemTreeItem> unorderedItems = new ArrayList<>(stats.keySet());
        boolean hasStacksToOrderFirst = true;
        while(hasStacksToOrderFirst) {
            hasStacksToOrderFirst = false;
            for(IItemTreeItem item : unorderedItems) {
                Integer value = stats.get(item);
                if(value > ((horizontal) ? rowSize : columnSize) && !itemOrder.contains(item)) {
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
            String constraint = String.format("%c%c-%c%c", row, column, (char) (row - 1 + thisSpaceHeight), (char) (column - 1 + thisSpaceWidth));
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
        rules.add(new InvTweaksConfigSortingRule(tree, defaultRule, tree.getRootCategory().getName(), size, rowSize));

    }

    private Map<IItemTreeItem, Integer> computeContainerStats() {
        Map<IItemTreeItem, Integer> stats = new HashMap<>();
        Map<Integer, IItemTreeItem> itemSearch = new HashMap<>();

        for(int i = 0; i < size; i++) {
            ItemStack stack = containerMgr.getItemStack(i);
            if(!stack.isEmpty()) {
                // TODO: ID Changes (Leaving as-is for now because WHY)
                int itemSearchKey = Item.getIdFromItem(stack.getItem()) * 100000 + ((stack
                        .getMaxStackSize() != 1) ? stack.getItemDamage() : 0);
                IItemTreeItem item = itemSearch.get(itemSearchKey);
                if(item == null) {
                    // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                    item = tree.getItems(Item.REGISTRY.getNameForObject(stack.getItem()).toString(), stack.getItemDamage(), stack.getTagCompound()).get(0);
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
