package invtweaks;


import invtweaks.api.IItemTree;
import invtweaks.api.IItemTreeCategory;
import invtweaks.api.IItemTreeItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;
import java.util.logging.Logger;

/**
 * Contains the whole hierarchy of categories and items, as defined in the XML item tree. Is used to recognize keywords
 * and store item orders.
 *
 * @author Jimeo Wan
 */
public class InvTweaksItemTree implements IItemTree {

    public static final int MAX_CATEGORY_RANGE = 1000;
    public static final String UNKNOWN_ITEM = "unknown";

    private static final Logger log = InvTweaks.log;

    /**
     * All categories, stored by name
     */
    private Map<String, IItemTreeCategory> categories =
            new HashMap<String, IItemTreeCategory>();

    /**
     * Items stored by ID. A same ID can hold several names.
     */
    private Map<Integer, Vector<IItemTreeItem>> itemsById =
            new HashMap<Integer, Vector<IItemTreeItem>>(500);
    private static Vector<IItemTreeItem> defaultItems = null;

    /**
     * Items stored by name. A same name can match several IDs.
     */
    private Map<String, Vector<IItemTreeItem>> itemsByName =
            new HashMap<String, Vector<IItemTreeItem>>(500);

    private String rootCategory;

    public InvTweaksItemTree() {
        reset();
    }

    public void reset() {

        if(defaultItems == null) {
            defaultItems = new Vector<IItemTreeItem>();
            defaultItems.add(new InvTweaksItemTreeItem(UNKNOWN_ITEM, -1, InvTweaksConst.DAMAGE_WILDCARD,
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
     *
     * @param items
     * @param keyword
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
            log.severe("The root category is missing: " + e.getMessage());
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
                log.severe("The root category is missing: " + e.getMessage());
                return -1;
            }
        }
    }

    /**
     * Checks if the given keyword is valid (i.e. represents either a registered item or a registered category)
     *
     * @param keyword
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
    public IItemTreeCategory getCategory(String keyword) {
        return categories.get(keyword);
    }

    @Override
    public boolean isItemUnknown(int id, int damage) {
        return itemsById.get(id) == null;
    }

    @Override
    public List<IItemTreeItem> getItems(int id, int damage) {
        List<IItemTreeItem> items = itemsById.get(id);
        List<IItemTreeItem> filteredItems = new ArrayList<IItemTreeItem>();
        if(items != null) {
            filteredItems.addAll(items);
        }

        // Filter items of same ID, but different damage value
        if(items != null && !items.isEmpty()) {
            for(IItemTreeItem item : items) {
                if(item.getDamage() != InvTweaksConst.DAMAGE_WILDCARD && item.getDamage() != damage) {
                    filteredItems.remove(item);
                }
            }
        }

        // If there's no matching item, create new ones
        if(filteredItems.isEmpty()) {
            IItemTreeItem newItemId = new InvTweaksItemTreeItem(
                    String.format("%d-%d", id, damage),
                    id, damage, 5000 + id * 16 + damage);
            IItemTreeItem newItemDamage = new InvTweaksItemTreeItem(
                    Integer.toString(id),
                    id, InvTweaksConst.DAMAGE_WILDCARD, 5000 + id * 16);
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
        return (IItemTreeItem) itemsByName.values()
                                          .toArray()[r.nextInt(itemsByName.size())];
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
    public void setRootCategory(IItemTreeCategory category) {
        rootCategory = category.getName();
        categories.put(rootCategory, category);
    }

    @Override
    public IItemTreeCategory addCategory(String parentCategory, String newCategory) throws NullPointerException {
        IItemTreeCategory addedCategory = new InvTweaksItemTreeCategory(newCategory);
        addCategory(parentCategory, addedCategory);
        return addedCategory;
    }

    @Override
    public IItemTreeItem addItem(String parentCategory, String name, int id, int damage, int order)
            throws NullPointerException {
        InvTweaksItemTreeItem addedItem = new InvTweaksItemTreeItem(name, id, damage, order);
        addItem(parentCategory, addedItem);
        return addedItem;
    }

    @Override
    public void addCategory(String parentCategory,
                            IItemTreeCategory newCategory) throws NullPointerException {
        // Build tree
        categories.get(parentCategory.toLowerCase()).addCategory(newCategory);

        // Register category
        categories.put(newCategory.getName(), newCategory);
    }

    @Override
    public void addItem(String parentCategory,
                        IItemTreeItem newItem) throws NullPointerException {
        // Build tree
        categories.get(parentCategory.toLowerCase()).addItem(newItem);

        // Register item
        if(itemsByName.containsKey(newItem.getName())) {
            itemsByName.get(newItem.getName()).add(newItem);
        } else {
            Vector<IItemTreeItem> list = new Vector<IItemTreeItem>();
            list.add(newItem);
            itemsByName.put(newItem.getName(), list);
        }
        if(itemsById.containsKey(newItem.getId())) {
            itemsById.get(newItem.getId()).add(newItem);
        } else {
            Vector<IItemTreeItem> list = new Vector<IItemTreeItem>();
            list.add(newItem);
            itemsById.put(newItem.getId(), list);
        }
    }

    /**
     * For debug purposes. Call log(getRootCategory(), 0) to log the whole tree.
     */
    private void log(IItemTreeCategory category, int indentLevel) {

        String logIdent = "";
        for(int i = 0; i < indentLevel; i++) {
            logIdent += "  ";
        }
        log.info(logIdent + category.getName());

        for(IItemTreeCategory subCategory : category.getSubCategories()) {
            log(subCategory, indentLevel + 1);
        }

        for(List<IItemTreeItem> itemList : category.getItems()) {
            for(IItemTreeItem item : itemList) {
                log.info(logIdent + "  " + item + " " +
                                 item.getId() + " " + item.getDamage());
            }
        }

    }


    private class OreDictInfo {
        String category;
        String name;
        String oreName;
        int order;

        OreDictInfo(String category, String name, String oreName, int order) {
            this.category = category;
            this.name = name;
            this.oreName = oreName;
            this.order = order;
        }
    }

    @Override
    public void registerOre(String category, String name, String oreName, int order) {
        for(ItemStack i : OreDictionary.getOres(oreName)) {
            addItem(category, new InvTweaksItemTreeItem(name,
                                                        i.itemID, i.getItemDamage(), order));
        }
        oresRegistered.add(new OreDictInfo(category, name, oreName, order));
    }

    private List<OreDictInfo> oresRegistered = new ArrayList<OreDictInfo>();

    @ForgeSubscribe
    public void oreRegistered(OreDictionary.OreRegisterEvent ev) {
        for(OreDictInfo ore : oresRegistered) {
            if(ore.oreName.equals(ev.Name)) {
                addItem(ore.category, new InvTweaksItemTreeItem(ore.name,
                                                                ev.Ore.itemID, ev.Ore.getItemDamage(), ore.order));
            }
        }
    }
}
