package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static com.timepath.launcher.util.Utils.UPDATE_NAME;

public class Repository {

    private static final Logger LOG = Logger.getLogger(Repository.class.getName());

    private final Map<String, Program> libs = new HashMap<>(0);

    private List<Program> packages;

    Program self;

    public Repository(InputStream is) {
        packages = new LinkedList<>();
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Node root = docBuilder.parse(new BufferedInputStream(is));

            LOG.log(Level.FINEST, "\n{0}", XMLUtils.printTree(root, 0));

            String[] nodes = {"self", "libs", "programs"};
            for(String n : nodes) {
                List<Node> programs = XMLUtils.getElements("root/" + n + "/entry", root);
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

    private Repository() {
    }

    /**
     * @return the packages
     */
    public List<Program> getPackages() {
        return Collections.unmodifiableList(packages);
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

}
