package invtweaks;


import invtweaks.api.IItemTree;
import invtweaks.api.IItemTreeCategory;
import invtweaks.api.IItemTreeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Contains the whole hierarchy of categories and items, as defined in the XML item tree. Is used to recognize keywords
 * and store item orders.
 *
 * @author Jimeo Wan
 */
public class InvTweaksItemTree implements IItemTree {
    public static final String UNKNOWN_ITEM = "unknown";

    private static final Logger log = InvTweaks.log;
    private static List<IItemTreeItem> defaultItems = null;
    /**
     * All categories, stored by name
     */
    private Map<String, IItemTreeCategory> categories = new HashMap<>();
    /**
     * Items stored by ID. A same ID can hold several names.
     */
    private Map<String, List<IItemTreeItem>> itemsById = new HashMap<>(500);
    /**
     * Items stored by name. A same name can match several IDs.
     */
    private Map<String, List<IItemTreeItem>> itemsByName = new HashMap<>(500);

    private String rootCategory;
    private List<OreDictInfo> oresRegistered = new ArrayList<>();

    public InvTweaksItemTree() {
        reset();
    }

    public void reset() {

        if(defaultItems == null) {
            defaultItems = new ArrayList<>();
            defaultItems.add(new InvTweaksItemTreeItem(UNKNOWN_ITEM, null, InvTweaksConst.DAMAGE_WILDCARD,
                    Integer.MAX_VALUE));
        }

        // Reset tree
        categories.clear();
        itemsByName.clear();
        itemsById.clear();

    }

    /**
     * Checks if given item ID matches a given keyword (either the item's name is the keyword, or it is in the keyword
     * category)
     */
    @Override
    public boolean matches(List<IItemTreeItem> items, String keyword) {

        if(items == null) {
            return false;
        }

        // The keyword is an item
        for(IItemTreeItem item : items) {
            if(item.getName() != null && item.getName().equals(keyword)) {
                return true;
            }
        }

        // The keyword is a category
        IItemTreeCategory category = getCategory(keyword);
        if(category != null) {
            for(IItemTreeItem item : items) {
                if(category.contains(item)) {
                    return true;
                }
            }
        }

        // Everything is stuff
        return keyword.equals(rootCategory);

    }

    @Override
    public int getKeywordDepth(String keyword) {
        try {
            return getRootCategory().findKeywordDepth(keyword);
        } catch(NullPointerException e) {
            log.error("The root category is missing: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public int getKeywordOrder(String keyword) {
        List<IItemTreeItem> items = getItems(keyword);
        if(items != null && items.size() != 0) {
            return items.get(0).getOrder();
        } else {
            try {
                return getRootCategory().findCategoryOrder(keyword);
            } catch(NullPointerException e) {
                log.error("The root category is missing: " + e.getMessage());
                return -1;
            }
        }
    }

    /**
     * Checks if the given keyword is valid (i.e. represents either a registered item or a registered category)
     */
    @Override
    public boolean isKeywordValid(String keyword) {

        // Is the keyword an item?
        if(containsItem(keyword)) {
            return true;
        }

        // Or maybe a category ?
        else {
            IItemTreeCategory category = getCategory(keyword);
            return category != null;
        }
    }

    /**
     * Returns a reference to all categories.
     */
    @Override
    public Collection<IItemTreeCategory> getAllCategories() {
        return categories.values();
    }

    @Override
    public IItemTreeCategory getRootCategory() {
        return categories.get(rootCategory);
    }

    @Override
    public void setRootCategory(IItemTreeCategory category) {
        rootCategory = category.getName();
        categories.put(rootCategory, category);
    }

    @Override
    public IItemTreeCategory getCategory(String keyword) {
        return categories.get(keyword);
    }

    @Override
    public boolean isItemUnknown(String id, int damage) {
        return itemsById.get(id) == null;
    }

    @Override
    public List<IItemTreeItem> getItems(String id, int damage) {
        if(id == null) {
            return new ArrayList<>();
        }

        List<IItemTreeItem> items = itemsById.get(id);
        List<IItemTreeItem> filteredItems = new ArrayList<>();
        if(items != null) {
            filteredItems.addAll(items);
        }

        // Filter items of same ID, but different damage value
        if(items != null && !items.isEmpty()) {
            items.stream().filter(item -> item.getDamage() != InvTweaksConst.DAMAGE_WILDCARD && item.getDamage() != damage).forEach(filteredItems::remove);
        }

        // If there's no matching item, create new ones
        if(filteredItems.isEmpty()) {
            IItemTreeItem newItemId = new InvTweaksItemTreeItem(String.format("%s-%d", id, damage), id, damage,
                    5000/*TODO: What to do here with non-int IDs + id * 16 + damage*/);
            IItemTreeItem newItemDamage = new InvTweaksItemTreeItem(id, id,
                    InvTweaksConst.DAMAGE_WILDCARD, 5000/*TODO: What to do here with non-int IDs + id * 16*/);
            addItem(getRootCategory().getName(), newItemId);
            addItem(getRootCategory().getName(), newItemDamage);
            filteredItems.add(newItemId);
            filteredItems.add(newItemDamage);
        }

        Iterator<IItemTreeItem> it = filteredItems.iterator();
        while(it.hasNext()) {
            if(it.next() == null) {
                it.remove();
            }
        }

        return filteredItems;
    }

    @Override
    public List<IItemTreeItem> getItems(String name) {
        return itemsByName.get(name);
    }

    @Override
    public IItemTreeItem getRandomItem(Random r) {
        return (IItemTreeItem) itemsByName.values().toArray()[r.nextInt(itemsByName.size())];
    }

    @Override
    public boolean containsItem(String name) {
        return itemsByName.containsKey(name);
    }

    @Override
    public boolean containsCategory(String name) {
        return categories.containsKey(name);
    }

    @Override
    public IItemTreeCategory addCategory(String parentCategory, String newCategory) throws NullPointerException {
        IItemTreeCategory addedCategory = new InvTweaksItemTreeCategory(newCategory);
        addCategory(parentCategory, addedCategory);
        return addedCategory;
    }

    @Override
    public IItemTreeItem addItem(String parentCategory, String name, String id, int damage, int order)
            throws NullPointerException {
        InvTweaksItemTreeItem addedItem = new InvTweaksItemTreeItem(name, id, damage, order);
        addItem(parentCategory, addedItem);
        return addedItem;
    }

    @Override
    public void addCategory(String parentCategory, IItemTreeCategory newCategory) throws NullPointerException {
        // Build tree
        categories.get(parentCategory).addCategory(newCategory);

        // Register category
        categories.put(newCategory.getName(), newCategory);
    }

    @Override
    public void addItem(String parentCategory, IItemTreeItem newItem) throws NullPointerException {
        // Build tree
        categories.get(parentCategory).addItem(newItem);

        // Register item
        if(itemsByName.containsKey(newItem.getName())) {
            itemsByName.get(newItem.getName()).add(newItem);
        } else {
            List<IItemTreeItem> list = new ArrayList<>();
            list.add(newItem);
            itemsByName.put(newItem.getName(), list);
        }
        if(itemsById.containsKey(newItem.getId())) {
            itemsById.get(newItem.getId()).add(newItem);
        } else {
            List<IItemTreeItem> list = new ArrayList<>();
            list.add(newItem);
            itemsById.put(newItem.getId(), list);
        }
    }

    @Override
    public void registerOre(String category, String name, String oreName, int order) {
        for(ItemStack i : OreDictionary.getOres(oreName)) {
            if(i != null) {
                // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                addItem(category,
                        new InvTweaksItemTreeItem(name, i.getItem().getRegistryName().toString(), i.getItemDamage(), order));
            } else {
                log.warn(String.format("An OreDictionary entry for %s is null", oreName));
            }
        }
        oresRegistered.add(new OreDictInfo(category, name, oreName, order));
    }

    @SubscribeEvent
    public void oreRegistered(OreDictionary.OreRegisterEvent ev) {
        // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
        oresRegistered.stream().filter(ore -> ore.oreName.equals(ev.getName())).forEach(ore -> {
            if(ev.getOre().getItem() != null) {
                // TODO: It looks like Mojang changed the internal name type to ResourceLocation. Evaluate how much of a pain that will be.
                addItem(ore.category, new InvTweaksItemTreeItem(ore.name, ev.getOre().getItem().getRegistryName().toString(),
                        ev.getOre().getItemDamage(), ore.order));
            } else {
                log.warn(String.format("An OreDictionary entry for %s is null", ev.getName()));
            }
        });
    }

    private static class OreDictInfo {
        String category;
        String name;
        String oreName;
        int order;

        OreDictInfo(String category_, String name_, String oreName_, int order_) {
            category = category_;
            name = name_;
            oreName = oreName_;
            order = order_;
        }
    }
}
