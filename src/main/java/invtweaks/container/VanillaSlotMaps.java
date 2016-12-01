package invtweaks.container;

import invtweaks.InvTweaksConst;
import invtweaks.api.container.ContainerSection;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.passive.AbstractChestHorse;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerHorseInventory;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class VanillaSlotMaps {
    public static Map<ContainerSection, List<Slot>> containerPlayerSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.CRAFTING_OUT, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.CRAFTING_IN, container.inventorySlots.subList(1, 5));
        slotRefs.put(ContainerSection.ARMOR, container.inventorySlots.subList(5, 9));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(9, 45));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(9, 36));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(36, 45));

        return slotRefs;
    }

    @SideOnly(Side.CLIENT)
    public static boolean containerCreativeIsInventory(GuiContainerCreative.ContainerCreative container) {
        GuiScreen currentScreen = FMLClientHandler.instance().getClient().currentScreen;
        return currentScreen instanceof GuiContainerCreative && ((GuiContainerCreative) currentScreen).getSelectedTabIndex() == CreativeTabs.INVENTORY.getTabIndex();
    }

    @SideOnly(Side.CLIENT)
    public static Map<ContainerSection, List<Slot>> containerCreativeSlots(GuiContainerCreative.ContainerCreative container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.ARMOR, container.inventorySlots.subList(5, 9));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(9, 45));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(9, 36));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(36, 45));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerChestDispenserSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        int size = container.inventorySlots.size();

        slotRefs.put(ContainerSection.CHEST, container.inventorySlots.subList(0, size - InvTweaksConst.INVENTORY_SIZE));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(size - InvTweaksConst.INVENTORY_SIZE, size));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(size - InvTweaksConst.INVENTORY_SIZE, size - InvTweaksConst.HOTBAR_SIZE));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(size - InvTweaksConst.HOTBAR_SIZE, size));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerHorseSlots(ContainerHorseInventory container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        int size = container.inventorySlots.size();

        if (container.theHorse instanceof AbstractChestHorse && ((AbstractChestHorse)container.theHorse).hasChest()) { // Chest slots are only added if chest is added. Saddle/armor slots always exist.
            slotRefs.put(ContainerSection.CHEST, container.inventorySlots.subList(2, size - InvTweaksConst.INVENTORY_SIZE));
        }
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(size - InvTweaksConst.INVENTORY_SIZE, size));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(size - InvTweaksConst.INVENTORY_SIZE, size - InvTweaksConst.HOTBAR_SIZE));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(size - InvTweaksConst.HOTBAR_SIZE, size));

        return slotRefs;
    }

    public static boolean containerHorseIsInventory(ContainerHorseInventory container) {
        return container.theHorse instanceof AbstractChestHorse && ((AbstractChestHorse)container.theHorse).hasChest();
    }

    public static Map<ContainerSection, List<Slot>> containerFurnaceSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.FURNACE_IN, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.FURNACE_FUEL, container.inventorySlots.subList(1, 2));
        slotRefs.put(ContainerSection.FURNACE_OUT, container.inventorySlots.subList(2, 3));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(3, 39));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(3, 30));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(30, 39));
        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerWorkbenchSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.CRAFTING_OUT, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.CRAFTING_IN, container.inventorySlots.subList(1, 10));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(10, 46));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(10, 37));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(37, 46));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerEnchantmentSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.ENCHANTMENT, container.inventorySlots.subList(0, 1));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(2, 38));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(2, 29));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(29, 38));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerBrewingSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.BREWING_BOTTLES, container.inventorySlots.subList(0, 3));
        slotRefs.put(ContainerSection.BREWING_INGREDIENT, container.inventorySlots.subList(3, 4));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(4, 40));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(4, 31));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(31, 40));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> containerRepairSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        slotRefs.put(ContainerSection.CRAFTING_IN, container.inventorySlots.subList(0, 2));
        slotRefs.put(ContainerSection.CRAFTING_OUT, container.inventorySlots.subList(2, 3));
        slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(3, 39));
        slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(3, 30));
        slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(30, 39));

        return slotRefs;
    }

    public static Map<ContainerSection, List<Slot>> unknownContainerSlots(Container container) {
        Map<ContainerSection, List<Slot>> slotRefs = new HashMap<>();

        int size = container.inventorySlots.size();

        if(size >= InvTweaksConst.INVENTORY_SIZE) {
            // Assuming the container ends with the inventory, just like all vanilla containers.
            slotRefs.put(ContainerSection.CHEST,
                    container.inventorySlots.subList(0, size - InvTweaksConst.INVENTORY_SIZE));
            slotRefs.put(ContainerSection.INVENTORY, container.inventorySlots.subList(size - InvTweaksConst.INVENTORY_SIZE, size));
            slotRefs.put(ContainerSection.INVENTORY_NOT_HOTBAR, container.inventorySlots.subList(size - InvTweaksConst.INVENTORY_SIZE, size - InvTweaksConst.HOTBAR_SIZE));
            slotRefs.put(ContainerSection.INVENTORY_HOTBAR, container.inventorySlots.subList(size - InvTweaksConst.HOTBAR_SIZE, size));
        } else {
            slotRefs.put(ContainerSection.CHEST, container.inventorySlots.subList(0, size));
        }

        return slotRefs;
    }
}
