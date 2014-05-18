package com.timepath.launcher.util;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
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
        Node child = Utils.last(getElements(key, node));
        if(child != null) {
            return child.getNodeValue();
        }
        return ( e.getAttributeNode(key) != null ) ? e.getAttributeNode(key).getValue() : null;
    }

    public static List<Node> getElements(String eval, Node root) {
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
}
