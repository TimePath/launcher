package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import java.io.*;
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.*;
import javax.swing.*;
import javax.xml.parsers.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static com.timepath.launcher.util.Utils.debug;
import static com.timepath.launcher.util.Utils.start;

public class Launcher {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    public DownloadManager downloadManager = new DownloadManager();

    private final CompositeClassLoader cl;

    private final Map<String, Program> libs = new HashMap<>();

    private Program self;

    public static final String updateName = "update.tmp";

    public Launcher() {
        cl = AccessController.doPrivileged(new PrivilegedAction<CompositeClassLoader>() {
            @Override
            public CompositeClassLoader run() {
                return new CompositeClassLoader();
            }
        });
    }

    public ListModel<Program> getListing() {
        InputStream is = null;
        File f = new File(System.getProperty("user.home") + "/Dropbox/Public/projects.xml");
        if(debug && f.exists()) {
            try {
                is = new FileInputStream(f);
            } catch(FileNotFoundException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        if(is == null) {
            try {
                long listingStart = System.currentTimeMillis();
                String s = "http://dl.dropboxusercontent.com/u/42745598/projects.xml";
                LOG.log(Level.INFO, "Resolving...");
                URL u = new URL(s);
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
        ListModel<Program> l = parseXML(is);
        LOG.log(Level.INFO, "Parsed at {0}ms", System.currentTimeMillis() - start);
        return l;
    }

    /**
     *
     * @return true if self is up to date
     */
    public boolean selfCheck() {
        return self.isLatest();
    }

    public void shutdown() {
        downloadManager.shutdown();
    }

    public void start(final Program program) {
        if(program == null) {
            return;
        }
        if(program.lock) {
            LOG.log(Level.INFO, "Program locked, aborting: {0}", program);
            return;
        } else {
            LOG.log(Level.INFO, "Locking {0}", program);
            program.lock = true;
        }
        
        List<Program> updates = program.updates();
        
        if(program.self && !updates.contains(program)) {
            JOptionPane.showMessageDialog(null, "Launcher is up to date",
                                          "Launcher is up to date",
                                          JOptionPane.INFORMATION_MESSAGE,
                                          null);
        }
        
        Map<Program, List<Future<?>>> downloads = new HashMap<>(updates.size());
        for(Program p : updates) {
            downloads.put(p, download(p));
        }
        boolean selfupdated = false;
        for(Entry<Program, List<Future<?>>> e : downloads.entrySet()) {
            Program p = e.getKey();
            List<Future<?>> futures = e.getValue();
            try {
                for(Future<?> future : futures) {
                    future.get(); // Wait for download
                }
                LOG.log(Level.INFO, "Updated {0}", p);
                if(p.self) {
                    selfupdated = true;
                }
            } catch(InterruptedException | ExecutionException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        if(program.main == null && selfupdated) {
            JOptionPane.showMessageDialog(null, "Restart to apply", "Update downloaded",
                                          JOptionPane.INFORMATION_MESSAGE, null);
        } else {
            program.createThread(cl).start();
            program.lock = false;
        }
    }

    public Future<?> submitDownload(Downloadable d) {
        return downloadManager.submit(d);
    }

    private List<Future<?>> download(Program p) {
        List<Future<?>> arr = new LinkedList<>();
        for(Downloadable d : p.downloads) {
            arr.add(submitDownload(d));
        }
        return arr;
    }

    private List<Downloadable> getDownloads(Node entry) {
        List<Downloadable> downloads = new LinkedList<>();
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
            Downloadable d = new Downloadable(dlu, csu);
            d.nested = getDownloads(n);
            downloads.add(d);
        }
        return downloads;
    }

    private ListModel<Program> parseXML(InputStream is) {
        DefaultListModel<Program> listM = new DefaultListModel<>();
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
                        for(Downloadable d : p.downloads) {
                            d.filename = updateName;
                        }
                        p.setSelf(true);
                        self = p;
                        listM.addElement(p);
                    } else if(n.equals(nodes[1])) {
                        libs.put(p.title, p);
                    } else if(n.equals(nodes[2])) {
                        listM.addElement(p);
                    }
                }
            }
        } catch(IOException | ParserConfigurationException | SAXException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return listM;
    }

}
