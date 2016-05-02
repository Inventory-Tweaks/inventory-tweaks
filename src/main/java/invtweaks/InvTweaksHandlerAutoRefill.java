package invtweaks;

import invtweaks.api.IItemTreeItem;
import invtweaks.api.container.ContainerSection;
import invtweaks.container.ContainerSectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles the auto-refilling of the hotbar.
 *
 * @author Jimeo Wan
 */
public class InvTweaksHandlerAutoRefill extends InvTweaksObfuscation {

    private static final Logger log = InvTweaks.log;

    private InvTweaksConfig config = null;

    public InvTweaksHandlerAutoRefill(Minecraft mc_, InvTweaksConfig config_) {
        super(mc_);
        config = config_;
    }

    public void setConfig(InvTweaksConfig config_) {
        config = config_;
    }

    /**
     * Auto-refill
     *
     * @throws Exception
     */
    public void autoRefillSlot(int slot, String wantedId, int wantedDamage) throws Exception {

        ContainerSectionManager container = new ContainerSectionManager(
                ContainerSection.INVENTORY);
        ItemStack candidateStack, replacementStack = null;
        int replacementStackSlot = -1;
        boolean refillBeforeBreak = config.getProperty(InvTweaksConfig.PROP_AUTO_REFILL_BEFORE_BREAK)
                .equals(InvTweaksConfig.VALUE_TRUE);
        boolean hasSubtypes = false;

        // TODO: ResourceLocation
        Item original = ForgeRegistries.ITEMS.getValue(new ResourceLocation(wantedId));
        if(original != null) {
            hasSubtypes = original.getHasSubtypes();
        }

        List<InvTweaksConfigSortingRule> matchingRules = new ArrayList<>();
        List<InvTweaksConfigSortingRule> rules = config.getRules();
        InvTweaksItemTree tree = config.getTree();

        // Check that the item is in the tree
        if(!tree.isItemUnknown(wantedId, wantedDamage)) {

            //// Search replacement

            List<IItemTreeItem> items = tree.getItems(wantedId, wantedDamage);

            // Find rules that match the slot
            for(IItemTreeItem item : items) {
                if(!hasSubtypes || ((item.getDamage() == wantedDamage) || (item.getDamage() == InvTweaksConst.DAMAGE_WILDCARD))) {
                    // Since we search a matching item using rules,
                    // create a fake one that matches the exact item first
                    matchingRules.add(new InvTweaksConfigSortingRule(tree, "D" + (slot - 26), item.getName(),
                            InvTweaksConst.INVENTORY_SIZE,
                            InvTweaksConst.INVENTORY_ROW_SIZE));
                }
            }
            for(InvTweaksConfigSortingRule rule : rules) {
                if(rule.getType() == InvTweaksConfigSortingRuleType.SLOT || rule
                        .getType() == InvTweaksConfigSortingRuleType.COLUMN) {
                    for(int preferredSlot : rule.getPreferredSlots()) {
                        if(slot == preferredSlot) {
                            matchingRules.add(rule);
                            break;
                        }
                    }
                }
            }

            // Look only for a matching stack
            // First, look for the same item,
            // else one that matches the slot's rules
            for(InvTweaksConfigSortingRule rule : matchingRules) {
                for(int i = 0; i < InvTweaksConst.INVENTORY_SIZE; i++) {
                    candidateStack = container.getItemStack(i);
                    if(candidateStack != null) {
                        // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                        List<IItemTreeItem> candidateItems = tree
                                .getItems(candidateStack.getItem().getRegistryName().toString(), candidateStack.getItemDamage());
                        if(tree.matches(candidateItems, rule.getKeyword())) {
                            // Choose tool of highest damage value
                            if(candidateStack.getMaxStackSize() == 1) {
                                // Item
                                if((replacementStack == null || candidateStack.getItemDamage() > replacementStack
                                        .getItemDamage()) && (!refillBeforeBreak || candidateStack.getMaxDamage() - candidateStack
                                        .getItemDamage() > config
                                        .getIntProperty(InvTweaksConfig.PROP_AUTO_REFILL_DAMAGE_THRESHHOLD))) {
                                    replacementStack = candidateStack;
                                    replacementStackSlot = i;
                                }
                            }
                            // Choose stack of lowest size
                            else if(replacementStack == null || candidateStack.stackSize < replacementStack.stackSize) {
                                replacementStack = candidateStack;
                                replacementStackSlot = i;
                            }
                        }
                    }
                }
            }
        }

        // If item is unknown, look for exact same item
        else {
            for(int i = 0; i < InvTweaksConst.INVENTORY_SIZE; i++) {
                candidateStack = container.getItemStack(i);
                // TODO: ResourceLocation
                if(candidateStack != null &&
                        Objects.equals(candidateStack.getItem().getRegistryName().toString(), wantedId) &&
                        candidateStack.getItemDamage() == wantedDamage) {
                    replacementStack = candidateStack;
                    replacementStackSlot = i;
                    break;
                }
            }
        }

        //// Proceed to replacement

        if(replacementStack != null || (refillBeforeBreak && container.getSlot(slot).getStack() != null)) {

            log.info("Automatic stack replacement.");

		    /*
             * This allows to have a short feedback
		     * that the stack/tool is empty/broken.
		     */
            InvTweaks.getInstance().addScheduledTask(new Runnable() {

                private ContainerSectionManager containerMgr;
                private int targetedSlot;
                private int i;
                private String expectedItemId;
                private boolean refillBeforeBreak;

                public Runnable init(int i_, int currentItem, boolean refillBeforeBreak_) throws Exception {
                    containerMgr = new ContainerSectionManager(ContainerSection.INVENTORY);
                    targetedSlot = currentItem;
                    if(i_ != -1) {
                        i = i_;
                        // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                        expectedItemId = containerMgr.getItemStack(i).getItem().getRegistryName().toString();
                    } else {
                        i = containerMgr.getFirstEmptyIndex();
                        expectedItemId = null;
                    }
                    refillBeforeBreak = refillBeforeBreak_;
                    return this;
                }

                public void run() {
                    if(i < 0 || targetedSlot < 0) {
                        return;
                    }

                    // TODO: Look for better update detection now that this runs tick-based. It'll probably fail a bit if latency is > 50ms (1 tick)
                    // Since last tick, things might have changed
                    ItemStack stack = containerMgr.getItemStack(i);

                    // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                    if(stack != null && StringUtils.equals(stack.getItem().getRegistryName().toString(),
                            expectedItemId) || this.refillBeforeBreak) {
                        if(containerMgr.move(targetedSlot, i) || containerMgr.move(i, targetedSlot)) {
                            if(!config.getProperty(InvTweaksConfig.PROP_ENABLE_SOUNDS)
                                    .equals(InvTweaksConfig.VALUE_FALSE)) {
                                mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(
                                        SoundEvents.ENTITY_CHICKEN_EGG, 1.0F));
                            }
                            // If item are swapped (like for mushroom soups),
                            // put the item back in the inventory if it is in the hotbar
                            if(containerMgr.getItemStack(i) != null && i >= 27) {
                                for(int j = 0; j < InvTweaksConst.INVENTORY_SIZE; j++) {
                                    if(containerMgr.getItemStack(j) == null) {
                                        containerMgr.move(i, j);
                                        break;
                                    }
                                }
                            }

                            // Make sure the inventory resyncs
                            containerMgr.applyChanges();
                        } else {
                            log.warn("Failed to move stack for autoreplace, despite of prior tests.");
                        }
                    }
                }

            }.init(replacementStackSlot, slot, refillBeforeBreak));

        }
    }

}
