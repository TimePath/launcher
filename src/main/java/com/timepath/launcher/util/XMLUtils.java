package com.timepath.launcher.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class XMLUtils {

    private static final Logger LOG = Logger.getLogger(XMLUtils.class.getName());

    private XMLUtils() {
    }

    /**
     * Attempts to get the last text node by key
     *
     * @param root
     * @param key
     *
     * @return the text, or null
     */
    public static String get(Node root, String key) {
        try {
            return last(getElements(root, key)).getFirstChild().getNodeValue();
        } catch(NullPointerException ignored) {
            return null;
        }
    }

    /**
     * Get a list of nodes from the '/' delimited expression
     *
     * @param root
     * @param expression
     *
     * @return
     */
    public static List<Node> getElements(Node root, String expression) {
        String[] path = expression.split("/");
        List<Node> nodes = new LinkedList<>();
        nodes.add(root);
        for(String part : path) {
            List<Node> temp = new LinkedList<>();
            for(Node scan : nodes) {
                for(Node node : get(scan, Node.ELEMENT_NODE)) {
                    if(node.getNodeName().equals(part)) {
                        temp.add(node);
                    }
                }
            }
            nodes = temp;
        }
        return nodes;
    }

    /**
     * Get direct descendants by type
     *
     * @param parent
     * @param nodeType
     *
     * @return
     */
    public static List<Node> get(Node parent, short nodeType) {
        List<Node> list = new LinkedList<>();
        if(parent.hasChildNodes()) {
            NodeList nodes = parent.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if(node.getNodeType() == nodeType) {
                    list.add(node);
                }
            }
        }
        return list;
    }

    /** Get the last item in a list, or null */
    public static <E> E last(List<E> list) {
        return ( ( list == null ) || list.isEmpty() ) ? null : list.get(list.size() - 1);
    }

    /**
     * Fetch the first root by name from the given stream
     *
     * @param is
     * @param name
     *
     * @return
     *
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public static Node rootNode(final InputStream is, String name)
            throws ParserConfigurationException, IOException, SAXException
    {
        LOG.log(Level.INFO, "Getting root {0} node", name);
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(is);
        return getElements(doc, name).get(0);
    }
}
