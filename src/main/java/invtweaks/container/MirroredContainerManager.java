package invtweaks.container;

import invtweaks.InvTweaks;
import invtweaks.InvTweaksObfuscation;
import invtweaks.api.container.ContainerSection;
import invtweaks.forge.InvTweaksMod;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MirroredContainerManager implements IContainerManager {
    private ItemStack[] slotItems;
    private ItemStack heldItem;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // Partially implemented
    private List<ItemStack> droppedItems = new ArrayList<>();
    private Container container;
    private Map<ContainerSection, List<Integer>> itemRefs;
    private Map<ContainerSection, List<Slot>> slotRefs;

    public MirroredContainerManager(Container cont) {
        container = cont;

        slotRefs = InvTweaksObfuscation.getContainerSlotMap(container);
        if(slotRefs == null) {
            slotRefs = new HashMap<>();
        }

        // TODO: Detect if there is a big enough unassigned section for inventory.
        List<Slot> slots = container.inventorySlots;
        int size = slots.size();

        itemRefs = new HashMap<>();
        for(Map.Entry<ContainerSection, List<Slot>> section : slotRefs.entrySet()) {
            List<Integer> slotIndices = section.getValue().stream().map(slots::indexOf).collect(Collectors.toList());

            itemRefs.put(section.getKey(), slotIndices);
        }

        slotItems = new ItemStack[size];
        for(int i = 0; i < size; ++i) {
            slotItems[i] = slots.get(i).getStack().copy();
        }

        heldItem = InvTweaks.getInstance().getHeldStack();
    }

    @Override
    public boolean move(ContainerSection srcSection, int srcIndex, ContainerSection destSection, int destIndex) {
        int srcSlotIdx = slotPositionToIndex(srcSection, srcIndex);

        if(destIndex == DROP_SLOT) {
            droppedItems.add(slotItems[srcSlotIdx]);
            slotItems[srcSlotIdx] = null;
        }

        int destSlotIdx = slotPositionToIndex(destSection, destIndex);

        Slot srcSlot = getSlot(srcSection, srcIndex);
        Slot destSlot = getSlot(destSection, destIndex);

        ItemStack srcItem = slotItems[srcSlotIdx];
        ItemStack destItem = slotItems[destSlotIdx];

        if(srcItem != null && !destSlot.isItemValid(srcItem)) {
            return false;
        }

        if(destItem != null && !srcSlot.isItemValid(destItem)) {
            // TODO: Behavior says move dest to empty valid slot in this case.
            return false;
        }

        // TODO: Attempt to stack instead of always swapping?
        slotItems[srcSlotIdx] = destItem;
        slotItems[destSlotIdx] = srcItem;

        return true;
    }

    @Override
    public boolean moveSome(ContainerSection srcSection, int srcIndex, ContainerSection destSection, int destIndex, int amount) {
        // TODO: Can't currently do partial movements
        return false;
    }

    @Override
    public boolean putHoldItemDown(ContainerSection destSection, int destIndex) {
        int destSlotIdx = slotPositionToIndex(destSection, destIndex);

        if(slotItems[destSlotIdx] != null) {
            return false;
        }

        slotItems[destSlotIdx] = heldItem;
        heldItem = null;

        return true;
    }

    @Override
    public void click(ContainerSection section, int index, boolean rightClick) {
        // TODO: Currently unused externally -- consider if it needs to really be in the interface.
    }

    @Override
    public boolean hasSection(ContainerSection section) {
        return itemRefs.containsKey(section);
    }

    @Override
    public List<Slot> getSlots(ContainerSection section) {
        return slotRefs.get(section);
    }

    @Override
    public int getSize() {
        return slotItems.length;
    }

    @Override
    public int getSize(ContainerSection section) {
        return itemRefs.get(section).size();
    }

    @Override
    public int getFirstEmptyIndex(ContainerSection section) {
        int i = 0;
        for(int slot : itemRefs.get(section)) {
            if(slotItems[slot].isEmpty()) {
                return i;
            }
            i++;
        }
        return -1;
    }

    @Override
    public boolean isSlotEmpty(ContainerSection section, int slot) {
        return getItemStack(section, slot).isEmpty()
                ;
    }

    @Override
    public Slot getSlot(ContainerSection section, int index) {
        return container.getSlot(slotPositionToIndex(section, index));
    }

    @Override
    public int getSlotIndex(int slotNumber, boolean preferInventory) {
        // TODO Caching with getSlotSection
        for(ContainerSection section : slotRefs.keySet()) {
            if(!preferInventory && section != ContainerSection.INVENTORY || (preferInventory && section != ContainerSection.INVENTORY_NOT_HOTBAR && section != ContainerSection.INVENTORY_HOTBAR)) {
                int i = 0;
                for(Slot slot : slotRefs.get(section)) {
                    if(InvTweaksObfuscation.getSlotNumber(slot) == slotNumber) {
                        return i;
                    }
                    i++;
                }
            }
        }
        return -1;
    }

    @Override
    public ContainerSection getSlotSection(int slotNumber) {
        // TODO Caching with getSlotIndex
        for(ContainerSection section : slotRefs.keySet()) {
            if(section != ContainerSection.INVENTORY) {
                for(Slot slot : slotRefs.get(section)) {
                    if(InvTweaksObfuscation.getSlotNumber(slot) == slotNumber) {
                        return section;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public ItemStack getItemStack(ContainerSection section, int index) {
        return slotItems[slotPositionToIndex(section, index)];
    }

    @Override
    public Container getContainer() {
        return container;
    }

    @Override
    public void applyChanges() {
        // TODO: Figure out what is needed to match container with virtual inventory.
        InvTweaksMod.proxy.sortComplete();
    }

    /**
     * Converts section/index values to slot ID.
     *
     * @return -1 if not found
     */
    private int slotPositionToIndex(ContainerSection section, int index) {
        if(index == DROP_SLOT) {
            return DROP_SLOT;
        } else if(index < 0) {
            return -1;
        } else if(hasSection(section)) {
            return itemRefs.get(section).get(index);
        } else {
            return -1;
        }
    }
}
