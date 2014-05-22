package com.timepath.launcher;

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
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Repository {

    private static final Logger LOG = Logger.getLogger(Repository.class.getName());
    Map<String, Program> libs = new HashMap<>(0);
    String        location;
    Program       self;
    String        name;
    List<Program> packages;

    public static Repository get(String location) {
        InputStream is = null;
        if(Utils.DEBUG) {
            try {
                is = new FileInputStream(System.getProperty("user.home") + "/Dropbox/Public/" + JARUtils.name(location));
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
        Repository r = RepositoryParser.parse(findCompatible(is));
        r.location = location;
        r.name = r.name == null ? location : r.name;
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
            Node root = XMLUtils.getElements("root", doc).get(0);
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
            LOG.log(Level.FINE, "\n{0}", XMLUtils.printTree(version, 0));
            return version;
        } catch(IOException | ParserConfigurationException | SAXException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * @return the packages
     */
    public List<Program> getPackages() {
        return Collections.unmodifiableList(packages);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0} ({1})", name, location);
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
}
