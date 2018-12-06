package invtweaks.forge;

import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import invtweaks.InvTweaks;
import invtweaks.InvTweaksConfig;
import invtweaks.InvTweaksConfigManager;
import invtweaks.api.IItemTreeCategory;
import invtweaks.api.IItemTreeItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = "inventorytweaks")
public class ToolTipEvent {

    @SubscribeEvent
    public static void tTipEvent(ItemTooltipEvent event) {
        ItemStack current = event.getItemStack();
        if (current.isEmpty())
        {
            return;
        }
        InvTweaksConfigManager cfgManager = InvTweaks.getConfigManager();
        if (cfgManager == null)
            return;
        
        if(cfgManager.getConfig().getProperty(InvTweaksConfig.PROP_TOOLTIP_PATH).equals("true")) {        
            List<IItemTreeItem> items = cfgManager.getConfig().getTree().getItems(current.getItem().getRegistryName().toString(),
                    current.getItemDamage(), current.getTagCompound());
            if (items.isEmpty())
                return;

            Set<String> paths = new HashSet<>();
            IItemTreeCategory root = cfgManager.getConfig().getTree().getRootCategory();
            int unsortedZone = cfgManager.getConfig().getTree().getLastTreeOrder();
            int minOrder = Integer.MAX_VALUE;
            for(IItemTreeItem item: items) {
                String path = item.getPath();
                String altPath = root.findKeywordPath(item.getName());
                int itemOrder = item.getOrder();
                minOrder = Integer.min(minOrder, itemOrder);
                //event.getToolTip().add( + "");

                //If we have reported a match in the tree, don't report the unsorted matches.
                //This can happen if a damage id is filtered and not all of the items have been
                //placed in the tree.
                if (minOrder <= unsortedZone && itemOrder > unsortedZone)
                    break;
                
                if (path.equals(altPath) || itemOrder > unsortedZone) {
                    if (!paths.contains(path)) {
                        event.getToolTip().add(path + " (" + item.getOrder() + ")");
                        paths.add(path);
                    }                    
                } else {
                    if (!paths.contains(path)) {
                        event.getToolTip().add("T:" + path + " (" + item.getOrder() + ")");
                        paths.add(path);
                    }
                    if (!paths.contains(altPath)) {
                        event.getToolTip().add("M:" + altPath + " (" + item.getOrder() + ")");
                        paths.add(altPath);
                    }
                }
            }
        }
    }
}
