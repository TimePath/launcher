package com.timepath.launcher;

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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Repository {

    private static final Logger LOG = Logger.getLogger(Repository.class.getName());
    final String location;
    private final Map<String, Program> libs = new HashMap<>(0);
    Program self;
    private boolean       enabled;
    private String        name;
    private List<Program> packages;

    public Repository(String s) {
        location = s;
        name = s;
        enabled = true;
        if(enabled) {
            connect();
        }
    }

    private static List<PackageFile> getDownloads(Node entry) {
        List<PackageFile> downloads = new LinkedList<>();
        // downloadURL
        for(Node node : XMLUtils.getElements("download", entry)) {
            Node checksum = Utils.last(XMLUtils.getElements("checksum", entry));
            String dlu = XMLUtils.getAttribute(node, "url");
            if(dlu == null) {
                continue;
            }
            String csu = null;
            if(checksum != null) {
                csu = XMLUtils.getAttribute(checksum, "url");
            }
            PackageFile file = new PackageFile(dlu, csu);
            file.nested = getDownloads(node);
            downloads.add(file);
        }
        return downloads;
    }

    public void connect() {
        InputStream is = null;
        if(Utils.debug) {
            try {
                is = new FileInputStream(System.getProperty("user.home") + "/Dropbox/Public/" + Utils.name(location));
            } catch(FileNotFoundException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        if(is == null) {
            try {
                long listingStart = System.currentTimeMillis();
                LOG.log(Level.INFO, "Resolving...");
                URL u = new URL(location);
                LOG.log(Level.INFO, "Resolved in {0}ms", System.currentTimeMillis() - listingStart);
                LOG.log(Level.INFO, "Connecting...");
                URLConnection connection = u.openConnection();
                LOG.log(Level.INFO, "Connected in {0}ms", System.currentTimeMillis() - listingStart);
                LOG.log(Level.INFO, "Streaming...");
                is = connection.getInputStream();
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        LOG.log(Level.INFO, "Stream opened at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
        LOG.log(Level.INFO, "Parsing...");
        parse(is);
        LOG.log(Level.INFO, "Parsed at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
    }

    /**
     * @return the packages
     */
    public List<Program> getPackages() {
        if(packages == null) {
            connect();
        }
        return Collections.unmodifiableList(packages);
    }

    /**
     * @return the enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled
     *         the enabled to set
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    private void parse(InputStream is) {
        packages = new LinkedList<>();
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
                        if(Utils.debug || ( Utils.currentVersion >= Long.parseLong(v) )) {
                            version = iter;
                        }
                    } catch(NumberFormatException ignore) {
                    }
                }
            }
            LOG.log(Level.FINE, "\n{0}", XMLUtils.printTree(version, 0));
            List<Node> meta = XMLUtils.getElements("meta", version);
            String nameCandidate = XMLUtils.getAttribute(meta.get(0), "name");
            name = nameCandidate != null ? nameCandidate : name;
            String[] nodes = { "self", "libs", "programs" };
            for(String s1 : nodes) {
                List<Node> programs = XMLUtils.getElements(s1 + "/entry", version);
                for(Node entry : programs) {
                    Program p = new Program();
                    p.title = XMLUtils.getAttribute(entry, "name");
                    String depends = XMLUtils.getAttribute(entry, "depends");
                    if(depends != null) {
                        String[] dependencies = depends.split(",");
                        for(String s : dependencies) {
                            p.depends.add(libs.get(s.trim()));
                        }
                    }
                    p.fileName = XMLUtils.getAttribute(entry, "file");
                    Node java = Utils.last(XMLUtils.getElements("java", entry));
                    if(java != null) {
                        p.main = XMLUtils.getAttribute(java, "main");
                        p.args = Utils.argParse(XMLUtils.getAttribute(java, "args"));
                        String daemon = XMLUtils.getAttribute(java, "daemon");
                        if(daemon != null) {
                            p.daemon = Boolean.parseBoolean(daemon);
                        }
                    }
                    Node news = Utils.last(XMLUtils.getElements("newsfeed", entry));
                    if(news != null) {
                        p.newsfeedURL = XMLUtils.getAttribute(news, "url");
                    }
                    p.downloads = getDownloads(entry);
                    if(s1.equals(nodes[0])) {
                        for(PackageFile file : p.downloads) {
                            file.fileName = Utils.UPDATE_NAME;
                        }
                        p.setSelf(true);
                        self = p;
                        packages.add(p);
                    } else if(s1.equals(nodes[1])) {
                        libs.put(p.title, p);
                    } else if(s1.equals(nodes[2])) {
                        packages.add(p);
                    }
                }
            }
        } catch(IOException | ParserConfigurationException | SAXException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }
}
