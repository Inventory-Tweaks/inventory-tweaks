package invtweaks.forge;

import invtweaks.InvTweaks;
import invtweaks.InvTweaksConst;
import invtweaks.InvTweaksItemTreeCategory;
import invtweaks.InvTweaksItemTreeItem;
import invtweaks.api.IItemTree;
import invtweaks.api.IItemTreeListener;
import net.minecraft.item.Item;
import net.minecraftforge.common.ForgeHooks;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.*;

public class ForgeItemTreeListener implements IItemTreeListener {
    @SuppressWarnings("unchecked")
    @Override
    public void onTreeLoaded(IItemTree tree) {
        try {
            Field toolClassesField = ForgeHooks.class.getDeclaredField("toolClasses");
            toolClassesField.setAccessible(true);
            Map<Item, List> toolClasses = (Map<Item, List>) toolClassesField.get(null);

            Map<String, Map> toolClassesByName = new HashMap<String, Map>();
            for(Item i : toolClasses.keySet()) {
                List entry = toolClasses.get(i);
                String className = (String) entry.get(0);
                int level = (Integer) entry.get(1);

                if(!toolClassesByName.containsKey(className)) {
                    Map<Item, Integer> map = new HashMap<Item, Integer>();
                    map.put(i, level);
                    toolClassesByName.put(className, map);
                } else {
                    toolClassesByName.get(className).put(i, level);
                }
            }

            for(String name : toolClassesByName.keySet()) {
                tree.addCategory(tree.getRootCategory().getName(),
                                 new InvTweaksItemTreeCategory("forge_toolClasses_" + name));

                Map itemsByPriority = toolClassesByName.get(name);
                List<Item> itemList = new ArrayList<Item>(itemsByPriority.keySet());
                Collections.sort(itemList, new ItemPriorityComparator(itemsByPriority));

                for(Item i : itemList) {
                    tree.addItem("forge_toolClasses_" + name,
                                 new InvTweaksItemTreeItem(Integer.toString(i.itemID), i.itemID,
                                                           InvTweaksConst.DAMAGE_WILDCARD,
                                                           (Integer) itemsByPriority.get(i)));
                }
            }
        } catch(Exception e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            stringWriter.flush();
            InvTweaks.log.warning(stringWriter.toString());
        }
    }

    private class ItemPriorityComparator implements Comparator<Item> {
        Map<Item, Integer> priorities;

        ItemPriorityComparator(Map<Item, Integer> prios) {
            priorities = prios;
        }

        @Override
        public int compare(Item o1, Item o2) {
            return priorities.get(o1) - priorities.get(o2);
        }
    }
}
