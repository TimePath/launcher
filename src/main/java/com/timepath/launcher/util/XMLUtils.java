package com.timepath.launcher.util;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
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

    public static String getAttribute(Node node, String key) {
        Element e = (Element) node;
        Node child = last(getElements(node, key));
        if(child != null) {
            return child.getNodeValue();
        }
        return ( e.getAttributeNode(key) != null ) ? e.getAttributeNode(key).getValue() : null;
    }

    public static List<Node> getElements(Node root, String eval) {
        String[] path = eval.split("/");
        List<Node> nodes = new LinkedList<>();
        nodes.add(root);
        for(String part : path) {
            List<Node> repl = new LinkedList<>();
            for(Node scan : nodes) {
                for(Node node : get(scan, Node.ELEMENT_NODE)) {
                    if(node.getNodeName().equals(part)) {
                        repl.add(node);
                    }
                }
            }
            nodes = repl;
        }
        return nodes;
    }

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

    public static <E> E last(List<E> arr) {
        return ( ( arr == null ) || arr.isEmpty() ) ? null : arr.get(arr.size() - 1);
    }

    public static String printTree(Node root, int depth) {
        StringBuilder sb = new StringBuilder();
        String spacing = "";
        if(depth > 0) {
            spacing = String.format("%-" + ( depth * 4 ) + 's', "");
        }
        StringBuilder sb2 = new StringBuilder(root.getNodeName());
        if(root.hasAttributes()) {
            NamedNodeMap attribs = root.getAttributes();
            for(int i = attribs.getLength() - 1; i >= 0; i--) {
                sb2.append(MessageFormat.format(" {0}=\"{1}\"", attribs.item(i).getNodeName(), attribs.item(i).getNodeValue()));
            }
        }
        List<Node> elements = get(root, Node.ELEMENT_NODE);
        sb.append(MessageFormat.format("{0}: {1}<{2}{3}>\n", depth, spacing, sb2.toString(), elements.isEmpty() ? '/' : ""));
        for(Node node : elements) {
            sb.append(printTree(node, depth + 1));
        }
        if(!elements.isEmpty()) {
            sb.append(MessageFormat.format("{0}: {1}</{2}>\n", depth, spacing, root.getNodeName()));
        }
        return sb.toString();
    }

    public static String get(Node root, String key) {
        try {
            return last(XMLUtils.getElements(root, key)).getFirstChild().getNodeValue();
        } catch(NullPointerException ignored) {
            return null;
        }
    }

    public static Node rootNode(final InputStream is, String name)
    throws ParserConfigurationException, IOException, SAXException
    {
        LOG.log(Level.INFO, "Getting root {0} node", name);
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(is);
        return XMLUtils.getElements(doc, name).get(0);
    }
}
