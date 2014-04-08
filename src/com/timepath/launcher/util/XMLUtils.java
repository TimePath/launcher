package com.timepath.launcher.util;

import com.timepath.launcher.util.XMLUtils;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Logger;
import org.w3c.dom.*;

/**
 *
 * @author TimePath
 */
public class XMLUtils {

    private static final Logger LOG = Logger.getLogger(XMLUtils.class.getName());

    public static List<Node> get(Node parent, short nodeType) {
        List<Node> list = new LinkedList<>();
        if(parent.hasChildNodes()) {
            NodeList nodes = parent.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if(n.getNodeType() == nodeType) {
                    list.add(n);
                }
            }
        }
        return list;
    }

    public static String getAttribute(Node n, String key) {
        Element e = (Element) n;
        Node child = Utils.last(getElements(key, n));
        if(child != null) {
            return child.getNodeValue();
        } else if(e.getAttributeNode(key) != null) {
            return e.getAttributeNode(key).getValue();
        } else {
            return null;
        }
    }

    public static List<Node> getElements(String eval, Node root) {
        String[] path = eval.split("/");
        List<Node> nodes = new LinkedList<>();
        nodes.add(root);
        for(String part : path) {
            List<Node> repl = new LinkedList<>();
            for(Node scan : nodes) {
                for(Node n : get(scan, Node.ELEMENT_NODE)) {
                    if(n.getNodeName().equals(part)) {
                        repl.add(n);
                    }
                }
            }
            nodes = repl;
        }
        return nodes;
    }

    public static String printTree(Node root, int depth) {
        StringBuilder sb = new StringBuilder();
        String spacing = "";
        if(depth > 0) {
            spacing = String.format("%-" + (depth * 4) + "s", "");
        }

        StringBuilder sb2 = new StringBuilder(root.getNodeName());
        if(root.hasAttributes()) {
            NamedNodeMap attribs = root.getAttributes();
            for(int i = attribs.getLength() - 1; i >= 0; i--) {
                sb2.append(MessageFormat.format(" {0}=\"{1}\"",
                                                attribs.item(i).getNodeName(),
                                                attribs.item(i).getNodeValue()));
            }
        }
        List<Node> elements = get(root, Node.ELEMENT_NODE);
        sb.append(MessageFormat.format("{0}: {1}<{2}{3}>\n", depth, spacing, sb2.toString(),
                                       elements.isEmpty() ? '/' : ""));
        for(Node n : elements) {
            sb.append(printTree(n, depth + 1));
        }
        if(!elements.isEmpty()) {
            sb.append(MessageFormat.format("{0}: {1}</{2}>\n", depth, spacing, root.getNodeName()));
        }
        return sb.toString();
    }

    private XMLUtils() {
    }

}
