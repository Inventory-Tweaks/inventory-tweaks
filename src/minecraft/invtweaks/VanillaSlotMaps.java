package invtweaks;

import invtweaks.api.ContainerSection;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VanillaSlotMaps {
    public static Map<ContainerSection, List<Slot>> containerPlayerSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.CRAFTING_OUT, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.CRAFTING_IN, container.inventorySlots.subList(1, 5));
        slotRefs.put(ContainerSection.ARMOR, container.inventorySlots.subList(5, 9));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerCreativeSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.ARMOR, container.inventorySlots.subList(5, 9));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerChestDispenserSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.CHEST, container.inventorySlots.subList(0, container.inventorySlots.size() -
                InvTweaksConst.INVENTORY_SIZE));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerFurnaceSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.FURNACE_IN, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.FURNACE_FUEL, container.inventorySlots.subList(1, 2));
        slotRefs.put(ContainerSection.FURNACE_OUT, container.inventorySlots.subList(2, 3));
        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerWorkbenchSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.CRAFTING_OUT, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.CRAFTING_IN, container.inventorySlots.subList(1, 10));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerEnchantmentSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.ENCHANTMENT, container.inventorySlots.subList(0, 1));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerBrewingSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        slotRefs.put(ContainerSection.BREWING_BOTTLES, container.inventorySlots.subList(0, 3));
        slotRefs.put(ContainerSection.BREWING_INGREDIENT, container.inventorySlots.subList(3, 4));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> unknownContainerSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<ContainerSection, List<Slot>>();

        int size = container.inventorySlots.size();

        if(size >= InvTweaksConst.INVENTORY_SIZE) {
            // Assuming the container ends with the inventory, just like all vanilla containers.
            slotRefs.put(ContainerSection.CHEST,
                         container.inventorySlots.subList(0, size - InvTweaksConst.INVENTORY_SIZE));
        } else {
            slotRefs.put(ContainerSection.CHEST, container.inventorySlots.subList(0, size));
        }

        return slotRefs;
    }
}
