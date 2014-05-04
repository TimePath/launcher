package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static com.timepath.launcher.util.Utils.UPDATE_NAME;
import static com.timepath.launcher.util.Utils.debug;
import static com.timepath.launcher.util.Utils.start;

public class Repository {

    private static final Logger LOG = Logger.getLogger(Repository.class.getName());

    private boolean enabled;

    private final Map<String, Program> libs = new HashMap<>(0);

    private String name;

    private List<Program> packages;

    final String location;

    Program self;

    public Repository(String s) {
        location = s;
        name = s;
        this.enabled = true;
        if(enabled) {
            connect();
        }
    }

    public void connect() {
        InputStream is = null;
        if(debug) {
            try {
                is = new FileInputStream(System.getProperty("user.home") + "/Dropbox/Public/"
                                             + Utils.name(location));
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
                URLConnection c = u.openConnection();
                LOG.log(Level.INFO, "Connected in {0}ms", System.currentTimeMillis() - listingStart);
                LOG.log(Level.INFO, "Streaming...");
                is = c.getInputStream();
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        LOG.log(Level.INFO, "Stream opened at {0}ms", System.currentTimeMillis() - start);
        LOG.log(Level.INFO, "Parsing...");
        parse(is);
        LOG.log(Level.INFO, "Parsed at {0}ms", System.currentTimeMillis() - start);
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
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
     * @param enabled the enabled to set
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return getName();
    }

    private List<PackageFile> getDownloads(Node entry) {
        List<PackageFile> downloads = new LinkedList<>();
        // downloadURL
        for(Node n : XMLUtils.getElements("download", entry)) {
            Node checksum = Utils.last(XMLUtils.getElements("checksum", entry));
            String dlu = XMLUtils.getAttribute(n, "url");
            if(dlu == null) {
                continue;
            }
            String csu = null;
            if(checksum != null) {
                csu = XMLUtils.getAttribute(checksum, "url");
            }
            PackageFile d = new PackageFile(dlu, csu);
            d.nested = getDownloads(n);
            downloads.add(d);
        }
        return downloads;
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
                if(iter == null || !iter.hasAttributes()) {
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
                        if(Utils.debug || Utils.currentVersion >= Long.parseLong(v)) {
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
            
            String[] nodes = {"self", "libs", "programs"};
            for(String n : nodes) {
                List<Node> programs = XMLUtils.getElements(n + "/entry", version);
                for(Node entry : programs) {
                    //<editor-fold defaultstate="collapsed" desc="Parse">
                    Program p = new Program();

                    p.title = XMLUtils.getAttribute(entry, "name");

                    String depends = XMLUtils.getAttribute(entry, "depends");
                    if(depends != null) {
                        String[] dependencies = depends.split(",");
                        for(String s : dependencies) {
                            p.depends.add(libs.get(s.trim()));
                        }
                    }

                    p.filename = XMLUtils.getAttribute(entry, "file");

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

                    //</editor-fold>
                    if(n.equals(nodes[0])) {
                        for(PackageFile d : p.downloads) {
                            d.filename = UPDATE_NAME;
                        }
                        p.setSelf(true);
                        self = p;
                        packages.add(p);
                    } else if(n.equals(nodes[1])) {
                        libs.put(p.title, p);
                    } else if(n.equals(nodes[2])) {
                        packages.add(p);
                    }
                }
            }
        } catch(IOException | ParserConfigurationException | SAXException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

}
