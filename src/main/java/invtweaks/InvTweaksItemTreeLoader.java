package invtweaks;

import invtweaks.api.IItemTreeListener;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Loads the item tree by parsing the XML file.
 *
 * @author Jimeo Wan
 */
public class InvTweaksItemTreeLoader extends DefaultHandler {

    private final static String ATTR_ID = "id";
    private final static String ATTR_DAMAGE = "damage";
    private final static String ATTR_RANGE_DMIN = "dmin"; // Damage ranges
    private final static String ATTR_RANGE_DMAX = "dmax";
    private final static String ATTR_OREDICT_NAME = "oreDictName"; // OreDictionary names
    private final static String ATTR_DATA = "data";
    private final static String ATTR_CLASS = "class";
    private final static String ATTR_LAST_ORDER = "mergePrevious";
    private final static String ATTR_TREE_VERSION = "treeVersion";
    private static final List<IItemTreeListener> onLoadListeners = new ArrayList<>();
    private static InvTweaksItemTree tree;
    @Nullable
    private static String treeVersion;
    private static int itemOrder;
    private static LinkedList<String> categoryStack;
    private static boolean treeLoaded = false;

    private static void init() {
        treeVersion = null;
        tree = new InvTweaksItemTree();
        itemOrder = 0;
        categoryStack = new LinkedList<>();
    }

    public synchronized static InvTweaksItemTree load(@NotNull File file) throws Exception {
        init();

        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        SAXParser parser = parserFactory.newSAXParser();
        parser.parse(file, new InvTweaksItemTreeLoader());

        // Tree loaded event
        synchronized(onLoadListeners) {
            treeLoaded = true;
            for(@NotNull IItemTreeListener onLoadListener : onLoadListeners) {
                onLoadListener.onTreeLoaded(tree);
            }
        }

        MinecraftForge.EVENT_BUS.register(tree);

        return tree;
    }

    public synchronized static boolean isValidVersion(@NotNull File file) throws Exception {
        init();

        if(file.exists()) {
            treeVersion = null;
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            SAXParser parser = parserFactory.newSAXParser();

            VersionLoader loader = new VersionLoader();
            parser.parse(file, loader);
            return InvTweaksConst.TREE_VERSION.equals(loader.version);
        } else {
            return false;
        }
    }

    public synchronized static void addOnLoadListener(@NotNull IItemTreeListener listener) {
        onLoadListeners.add(listener);
        if(treeLoaded) {
            // Late event triggering
            listener.onTreeLoaded(tree);
        }
    }

    public synchronized static boolean removeOnLoadListener(IItemTreeListener listener) {
        return onLoadListeners.remove(listener);
    }


    @Override
    public synchronized void startElement(String uri, String localName, String name, @NotNull Attributes attributes)
            throws SAXException {

        String rangeDMinAttr = attributes.getValue(ATTR_RANGE_DMIN);
        String newTreeVersion = attributes.getValue(ATTR_TREE_VERSION);
        String oreDictNameAttr = attributes.getValue(ATTR_OREDICT_NAME);
        String id = attributes.getValue(ATTR_ID);
        String className = attributes.getValue(ATTR_CLASS);
        String lastOrderValue = attributes.getValue(ATTR_LAST_ORDER);
        lastOrderValue = lastOrderValue == null ? "" : lastOrderValue.toLowerCase(Locale.ENGLISH);
        boolean lastOrder = (lastOrderValue == "1" || lastOrderValue == "true" || lastOrderValue == "yes"|| lastOrderValue == "t" || lastOrderValue == "y");
        	

        // Category
        if(attributes.getLength() == 0 || treeVersion == null || rangeDMinAttr != null) {

            // Tree version
            if(treeVersion == null) {
                treeVersion = newTreeVersion;
            }

            if(categoryStack.isEmpty()) {
                // Root category
                tree.setRootCategory(new InvTweaksItemTreeCategory(name));
            } else {
                // Normal category
                tree.addCategory(categoryStack.getLast(), new InvTweaksItemTreeCategory(name));
            }

            // Handle damage ranges
            if(rangeDMinAttr != null) {                
                int rangeDMin = Integer.parseInt(rangeDMinAttr);
                int rangeDMax = Integer.parseInt(attributes.getValue(ATTR_RANGE_DMAX));
                for(int damage = rangeDMin; damage <= rangeDMax; damage++) {
                    tree.addItem(name, new InvTweaksItemTreeItem((name + id + "-" + damage), id, damage, null,
                    		lastOrder ? itemOrder : itemOrder++));
                }
            }

            categoryStack.add(name);
        }

        // Item
        else if(id != null) {
            int damage = InvTweaksConst.DAMAGE_WILDCARD;
            String extraDataAttr = attributes.getValue(ATTR_DATA);
            @Nullable NBTTagCompound extraData = null;
            if(extraDataAttr != null) {
                try {
                    extraData = JsonToNBT.getTagFromJson(extraDataAttr);
                } catch(NBTException e) {
                    throw new RuntimeException("Data attribute failed for tree entry '" + name + "'", e);
                }
            }
            if(attributes.getValue(ATTR_DAMAGE) != null) {
                damage = Integer.parseInt(attributes.getValue(ATTR_DAMAGE));
            }
            tree.addItem(categoryStack.getLast(),
                    new InvTweaksItemTreeItem(name, id, damage, extraData, lastOrder ? itemOrder : itemOrder++));
        } else if(oreDictNameAttr != null) {
            tree.registerOre(categoryStack.getLast(), name, oreDictNameAttr, lastOrder ? itemOrder : itemOrder++);
        } else if(className != null)
        {
        	String extraDataAttr = attributes.getValue(ATTR_DATA);
        	@Nullable NBTTagCompound extraData = null;
        	if(extraDataAttr != null) {
                try {
                	extraData = JsonToNBT.getTagFromJson(extraDataAttr.toLowerCase());
                } catch(NBTException e) {
                    throw new RuntimeException("Data attribute failed for tree entry '" + name + "'", e);
                }
            }
        	tree.registerClass(categoryStack.getLast(), name, className.toLowerCase(), extraData, lastOrder ? itemOrder : itemOrder++);
        }
    }

    @Override
    public synchronized void endElement(String uri, String localName, @NotNull String name) throws SAXException {
        if(!categoryStack.isEmpty() && name.equals(categoryStack.getLast())) {
            categoryStack.removeLast();
        }
    }
    
    @Override
    public void endDocument () throws SAXException {
    	tree.endFileRead();
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        InvTweaks.log.warn("Tree XML Warning: ", e);
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        InvTweaks.log.error("Tree XML Error: ", e);
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        InvTweaks.log.fatal("Tree XML Fatal Error: ", e);
    }

    private static class VersionLoader extends DefaultHandler {
        @Nullable
        String version;

        @Override
        public synchronized void startElement(String uri, String localName, String name, @NotNull Attributes attributes)
                throws SAXException {
            if(version == null) {
                version = attributes.getValue(ATTR_TREE_VERSION);
            }
        }
    }
}
