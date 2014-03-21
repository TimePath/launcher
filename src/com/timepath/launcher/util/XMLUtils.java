package com.timepath.launcher.util;

import com.timepath.launcher.Utils;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author TimePath
 */
public class XMLUtils {

    private static final Logger LOG = Logger.getLogger(XMLUtils.class.getName());

    public static ArrayList<Node> get(Node parent, short nodeType) {
        ArrayList<Node> al = new ArrayList<Node>();
        if(parent.hasChildNodes()) {
            NodeList nodes = parent.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if(n.getNodeType() == nodeType) {
                    al.add(n);
                }
            }
        }
        return al;
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

    public static ArrayList<Node> getElements(String eval, Node root) {
        String[] path = eval.split("/");
        ArrayList<Node> nodes = new ArrayList<Node>();
        nodes.add(root);
        for(String part : path) {
            ArrayList<Node> repl = new ArrayList<Node>();
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
        String str = root.getNodeName();
        String spacing = "";
        if(depth > 0) {
            spacing = String.format("%-" + depth * 4 + "s", "");
        }
        if(root.hasAttributes()) {
            NamedNodeMap attribs = root.getAttributes();
            for(int i = attribs.getLength() - 1; i >= 0; i--) {
                str += " " + attribs.item(i).getNodeName() + "=\"" + attribs.item(i).getNodeValue()
                       + "\"";
            }
        }
        ArrayList<Node> elements = get(root, Node.ELEMENT_NODE);
        sb.append(depth).append(": ").append(spacing).append("<").append(str).append(
            elements.isEmpty() ? "/" : "").append(">\n");
        for(Node n : elements) {
            sb.append(printTree(n, depth + 1));
        }
        if(!elements.isEmpty()) {
            sb.append(depth).append(": ").append(spacing).append("</").append(root.getNodeName())
                .append(
                    ">\n");
        }
        return sb.toString();
    }

    private XMLUtils() {
    }

}
