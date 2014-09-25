package com.timepath.launcher.data;

import com.timepath.IOUtils;
import com.timepath.StringUtils;
import com.timepath.XMLUtils;
import com.timepath.launcher.LauncherUtils;
import com.timepath.maven.Package;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private String location;
    /**
     * The package representing this repository. Mostly only relevant to the main repository so that the main launcher
     * has a way of updating itself
     */
    private com.timepath.maven.Package self;
    /**
     * The name of this repository
     */
    private String name;
    /**
     * A list of all program entry points
     */
    private List<Program> executions;

    private Repository() {
    }

    /**
     * Constructs a repository from a compatible node within a larger document
     *
     * @param location
     * @return
     */
    public static Repository fromIndex(String location) {
        String page = IOUtils.requestPage(location);
        if (page == null) return null;
        byte[] data = page.getBytes(StandardCharsets.UTF_8);
        Repository r = parse(findCompatible(new ByteArrayInputStream(data)));
        r.location = location;
        return r;
    }

    /**
     * Constructs a repository from a root node
     *
     * @param root
     * @return
     */
    private static Repository parse(Node root) {
        if (root == null) throw new IllegalArgumentException("The root node must not be null");
        Repository r = new Repository();
        r.name = XMLUtils.get(root, "name");
        r.self = Package.parse(root, null);
        if (r.self != null) Package.setSelf(r.self, true);
        r.executions = new LinkedList<>();
        for (Node entry : XMLUtils.getElements(root, "programs/program")) {
            Package pkg = Package.parse(entry, null);
            // extended format with execution data
            for (Node execution : XMLUtils.getElements(entry, "executions/execution")) {
                Node cfg = XMLUtils.last(XMLUtils.getElements(execution, "configuration"));
                Program p = new Program(pkg,
                        XMLUtils.get(execution, "name"),
                        XMLUtils.get(execution, "url"),
                        XMLUtils.get(cfg, "main"),
                        StringUtils.argParse(XMLUtils.get(cfg, "args")));
                r.executions.add(p);
                String daemonStr = XMLUtils.get(cfg, "daemon");
                if (daemonStr != null) p.setDaemon(Boolean.parseBoolean(daemonStr));
            }
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
            for (int i = 0; i < versions.getLength(); iter = versions.item(i++)) {
                if ((iter == null) || !iter.hasAttributes()) continue;
                NamedNodeMap attributes = iter.getAttributes();
                Node versionAttribute = attributes.getNamedItem("version");
                if (versionAttribute == null) continue;
                String v = versionAttribute.getNodeValue();
                if (v != null) {
                    try {
                        if (LauncherUtils.DEBUG || (LauncherUtils.CURRENT_VERSION >= Long.parseLong(v))) {
                            version = iter;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            LOG.log(Level.FINE, "\n{0}", XMLUtils.pprint(new DOMSource(version), 2));
            return version;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
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
