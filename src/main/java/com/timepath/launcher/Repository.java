package com.timepath.launcher;

import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A repository contains a list of multiple {@code Package}s and their {@code Program}s
 *
 * @author TimePath
 */
public class Repository {

    private static final Logger LOG = Logger.getLogger(Repository.class.getName());
    /**
     * URL to the index file
     */
    private String        location;
    /**
     * The package representing this repository. Mostly only relevant to the main repository so that the main launcher has a way
     * of updating itself
     */
    private Package       self;
    /**
     * The name of this repository
     */
    private String        name;
    /**
     * A list of all program entry points
     */
    private List<Program> executions;

    private Repository() {}

    /**
     * Constructs a repository from a compatible node within a larger document
     *
     * @param location
     *
     * @return
     */
    public static Repository fromIndex(String location) {
        InputStream is = null;
        if(Utils.DEBUG) { // try loading from local file
            try {
                is = new FileInputStream(System.getProperty("user.home") + "/Dropbox/Public/" + IOUtils.name(location));
            } catch(FileNotFoundException ignored) {
            }
        }
        if(is == null) {
            try {
                URL u = new URL(location);
                URLConnection connection = u.openConnection();
                is = connection.getInputStream();
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        Repository r = parse(findCompatible(is));
        r.location = location;
        return r;
    }

    /**
     * Constructs a repository from a root node
     *
     * @param root
     *
     * @return
     */
    private static Repository parse(Node root) {
        if(root == null) {
            throw new IllegalArgumentException("The root node must not be null");
        }
        Repository r = new Repository();
        r.name = XMLUtils.get(root, "name");
        r.self = new Package(root);
        r.self.setSelf(true);
        r.executions = new LinkedList<>(r.self.getExecutions());
        for(Node entry : XMLUtils.getElements(root, "programs/program")) {
            r.executions.addAll(new Package(entry).getExecutions());
        }
        return r;
    }

    /**
     * @return a compatible configuration node
     */
    private static Node findCompatible(InputStream is) {
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new BufferedInputStream(is));
            Node root = XMLUtils.getElements(doc, "root").get(0);
            Node version = null;
            Node iter = null;
            NodeList versions = root.getChildNodes();
            for(int i = 0; i < versions.getLength(); iter = versions.item(i++)) {
                if(( iter == null ) || !iter.hasAttributes()) {
                    continue;
                }
                NamedNodeMap attributes = iter.getAttributes();
                Node versionAttribute = attributes.getNamedItem("version");
                if(versionAttribute == null) {
                    continue;
                }
                String v = versionAttribute.getNodeValue();
                if(v != null) {
                    try {
                        if(Utils.DEBUG || ( JARUtils.CURRENT_VERSION >= Long.parseLong(v) )) {
                            version = iter;
                        }
                    } catch(NumberFormatException ignore) {
                    }
                }
            }
            LOG.log(Level.FINE, "\n{0}", Utils.pprint(new DOMSource(version), 2));
            return version;
        } catch(IOException | ParserConfigurationException | SAXException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * @return the executions
     */
    public List<Program> getExecutions() {
        return Collections.unmodifiableList(executions);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0} ({1})", name, location);
    }

    /**
     * @return the name
     */
    public String getName() {
        return name == null ? location : name;
    }

    public Package getSelf() {
        return self;
    }

    public String getLocation() {
        return location;
    }
}
