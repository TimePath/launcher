package com.timepath.launcher.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class Utils {

    public static final  String      USER       = MessageFormat.format("{0}@{1}",
                                                                       System.getProperty("user.name"),
                                                                       ManagementFactory.getRuntimeMXBean()
                                                                                        .getName()
                                                                                        .split("@")[1]
                                                                      );
    public static final  boolean     DEBUG      = JARUtils.CURRENT_VERSION == 0;
    public static final  Preferences SETTINGS   = Preferences.userRoot().node("timepath");
    public static final  long        START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final Logger      LOG        = Logger.getLogger(Utils.class.getName());

    private Utils() {}

    public static List<String> argParse(String cmd) {
        if(cmd == null) return null;
        return Arrays.asList(cmd.split(" "));
    }

    public static String pprint(Source xmlInput, int indent) {
        try {
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch(IllegalArgumentException | TransformerException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static String pprint(Map<String, ?> map) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = document.createElement("root");
            document.appendChild(root);
            for(Element e : pprint(map, document)) {
                root.appendChild(e);
            }
            return pprint(new DOMSource(document), 2);
        } catch(ParserConfigurationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static List<Element> pprint(Map<?, ?> map, Document document) {
        List<Element> elems = new LinkedList<>();
        for(Map.Entry<?, ?> entry : map.entrySet()) {
            Element e = document.createElement("entry");
            e.setAttribute("key", String.valueOf(entry.getKey()));
            if(entry.getValue() instanceof Map) {
                for(Element child : pprint((Map<?, ?>) entry.getValue(), document)) {
                    e.appendChild(child);
                }
            } else {
                e.setAttribute("value", String.valueOf(entry.getValue()));
            }
            elems.add(e);
        }
        return elems;
    }
}
