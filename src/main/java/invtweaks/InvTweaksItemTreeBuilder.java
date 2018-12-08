package invtweaks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class InvTweaksItemTreeBuilder {

    private static final Logger log = InvTweaks.log;
    
    public static void buildNewTree() {
        
        //If we don't have the folder, then we don't want to rebuild from the parts.
        if (!InvTweaksConst.INVTWEAKS_CONFIG_DIR.exists()) return;
        
        //Try to get our root tree.
        File base = new File(InvTweaksConst.INVTWEAKS_CONFIG_DIR, "minecraft.tree");
        if (!base.exists())
            base = InvTweaksConst.CONFIG_TREE_FILE;

        //There just isn't any hope left.
        if (!base.exists())
            return;
        
        try {
            log.info("Merging tree files.");
            InvTweaksItemTreeBuilder newTree = new InvTweaksItemTreeBuilder(base);
            
            File[] treeFiles = InvTweaksConst.INVTWEAKS_CONFIG_DIR.listFiles();
            
            for(File tree: treeFiles) {
                //Make sure it is the type of file we want.
                if (tree.getName().endsWith(".tree")) {
                    try {
                        //don't re-load our base.
                        if (!tree.equals(base)) {
                            newTree.add(tree);
                        }
                    } catch (Exception e) {
                        log.error("Unable to process partial Tree: " + tree.getName() + " " + e.getMessage());
                    }
                }
            }
            
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            //This isn't working perfectly, it likes to be off by one indent.

            File treeFile = new File(InvTweaksConst.INVTWEAKS_CONFIG_DIR, "InvTweaksTree.txt");
            Result output = new StreamResult(treeFile);
            Source input = new DOMSource(newTree.newDoc);
            
            transformer.transform(input, output);
            log.info("Merged tree files.");
            
        } catch (Exception e) {
            log.error("Fail Building New Tree: " + e.getMessage());
        }
    }

    private DocumentBuilder docBuilder;
    private Document newDoc;
    
    public InvTweaksItemTreeBuilder(@NotNull File file) throws ParserConfigurationException, SAXException, IOException {
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        newDoc = docBuilder.parse(file);        
    }
    
    public void add(@NotNull File file) throws ParserConfigurationException, SAXException, IOException {
        Document otherDoc = docBuilder.parse(file);
        Element newElement = newDoc.getDocumentElement();
        Element otherElement = otherDoc.getDocumentElement();
        combine(newElement, otherElement);
    }
    
    public static List<Element> getChildrenByTagName(Element parent, String name) {
        List<Element> nodeList = new ArrayList<Element>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                nodeList.add((Element) child);
            }
        }
        return nodeList;
    }
    
    private static boolean matchingAttributes(Element i, Element j) {
        //If both elements have matching ids, classes, dictionary, then it is a match.
        //Or if it lacks them.  We are ignoring merge*, data is only compared if the above matched.
        
        if (!i.hasAttributes() && !j.hasAttributes())
            return true;
        
        String iClass = i.getAttribute(InvTweaksItemTreeLoader.ATTR_CLASS);
        String jClass = j.getAttribute(InvTweaksItemTreeLoader.ATTR_CLASS);
        String iId = i.getAttribute(InvTweaksItemTreeLoader.ATTR_ID);
        String jId = j.getAttribute(InvTweaksItemTreeLoader.ATTR_ID);
        String iDamage = i.getAttribute(InvTweaksItemTreeLoader.ATTR_DAMAGE);
        String jDamage = j.getAttribute(InvTweaksItemTreeLoader.ATTR_DAMAGE);
        String iData = i.getAttribute(InvTweaksItemTreeLoader.ATTR_DATA);
        String jData = j.getAttribute(InvTweaksItemTreeLoader.ATTR_DATA);
        String iOre = i.getAttribute(InvTweaksItemTreeLoader.ATTR_OREDICT_NAME);
        String jOre = j.getAttribute(InvTweaksItemTreeLoader.ATTR_OREDICT_NAME);
        
        if (iClass.equals(jClass) && iId.equals(jId) && iOre.equals(jOre)) {
            if (iClass.length() == 0 && iId.length() == 0) {
                return true;  //This was ore or a category, we are done.
            } else if (iId.length() > 0 && iDamage.equals(jDamage) && iData.equals(jData)) {
                return true;  //The ID has extended data that matches.
            } else if (iClass.length() > 0 && iData.equals(jData)) {
                return true;  //The class has extended data that matches.
            }
        }
        return false;
    }
    
    private void combine(Element existing, Element other) {
        for (Node otherChildNode = other.getFirstChild(); otherChildNode != null; otherChildNode = otherChildNode.getNextSibling()) {
            @Nullable Element newChild = null;
            if (otherChildNode.getNodeType() == Node.ELEMENT_NODE) {
                Element otherChildElement = (Element)otherChildNode;
                List<Element> matches = getChildrenByTagName(existing, otherChildElement.getTagName());
                for(Element candidate: matches) {
                    if (matchingAttributes(candidate, otherChildElement)) {
                        newChild = candidate;
                        break;
                    }                    
                }
                //We failed to find an existing option, so we need a new one.
                if (newChild == null) {
                    newChild = (Element)newDoc.importNode(otherChildElement, true);
                    existing.appendChild(newChild);
                    //We don't need to recurse because we got that for free.
                }
                else 
                {
                    combine(newChild, otherChildElement);
                }
            }            
        }
    }
    
}
