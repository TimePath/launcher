package com.timepath.launcher;

import com.timepath.launcher.util.XMLUtils;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.logging.*;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

import static com.timepath.launcher.Utils.debug;
import static com.timepath.launcher.Utils.start;

public class Launcher {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    public DownloadManager downloadManager = new DownloadManager();

    private final CompositeClassLoader cl = new CompositeClassLoader();

    private final HashMap<String, Program> libs = new HashMap<String, Program>();

    private final HashMap<Program, Long> running = new HashMap<Program, Long>();

    private Program self;

    private final String updateName = "update.tmp";

    /**
     * Prevent running multiple times within a few seconds
     * <p/>
     * @param p The program
     * <p/>
     * @return true if not started recently
     */
    public boolean canStart(Program p) {
        if(running.containsKey(p)) {
            long c = running.get(p);
            if(System.currentTimeMillis() - c < 10000) {
                return false;
            }
            running.put(p, System.currentTimeMillis());
        }
        return true;
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
     * Check a program for updates
     * <p/>
     * @param p The program
     * <p/>
     * @return false if not up to date, true if up to date or working offline
     */
    public boolean isLatest(Program p) {
        if(debug && p == self) {
            return true;
        }
        LOG.log(Level.INFO, "Checking {0} for updates...", p);
        for(Downloadable d : p.downloads) {
            try {
                LOG.log(Level.INFO, "Version file: {0}", d.file());
                LOG.log(Level.INFO, "Version url: {0}", d.versionURL);

                File f = d.file();
                if(f.getName().equals(updateName)) { // edge case for current file
                    f = Utils.currentFile;
                }

                if(!f.exists()) {
                    return false;
                } else if(d.versionURL == null) {
                    continue; // have unversioned file, skip check
                }
                String checksum = Utils.checksum(f, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    new URL(d.versionURL).openStream()));
                String expected = br.readLine();
                if(!checksum.equals(expected)) {
                    return false;
                }
            } catch(Exception ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }

    /**
     *
     * @return true if self is up to date
     */
    public boolean selfCheck() {
        return isLatest(self);
    }

    public void shutdown() {
        downloadManager.shutdown();
    }

    public void start(final Program program) {
        if(program == null) {
            return;
        }
        if(!canStart(program)) {
            return;
        }
        update(program);
    }

    public Future<?> submitDownload(Downloadable d) {
        return downloadManager.submit(d);
    }

    private ArrayList<Future<?>> download(Program p) {
        ArrayList<Future<?>> arr = new ArrayList<Future<?>>();
        for(Downloadable d : p.downloads) {
            arr.add(submitDownload(d));
        }
        return arr;
    }

    private ListModel<Program> parseXML(InputStream is) {
        DefaultListModel<Program> listM = new DefaultListModel<Program>();
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Node root = docBuilder.parse(new BufferedInputStream(is));

            LOG.log(Level.FINEST, "\n{0}", XMLUtils.printTree(root, 0));

            String[] nodes = {"self", "libs", "programs"};
            for(String n : nodes) {
                ArrayList<Node> programs = XMLUtils.getElements("root/" + n + "/entry", root);
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
                    }

                    Node news = Utils.last(XMLUtils.getElements("newsfeed", entry));
                    if(news != null) {
                        p.newsfeedURL = XMLUtils.getAttribute(news, "url");
                    }

                    ArrayList<Node> downloads = XMLUtils.getElements("download", entry);
                    // downloadURL
                    for(Node download : downloads) {
                        Node checksum = Utils.last(XMLUtils.getElements("checksum", entry));
                        String dlu = XMLUtils.getAttribute(download, "url");
                        if(dlu == null) {
                            continue;
                        }
                        String csu = null;
                        if(checksum != null) {
                            csu = XMLUtils.getAttribute(checksum, "url");
                        }
                        p.downloads.add(new Downloadable(dlu, csu));
                    }

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
        } catch(Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return listM;
    }

    private void update(final Program run) {
        new SwingWorker<Boolean, Void>() {

            final HashMap<Program, List<Future<?>>> downloads
                                                        = new HashMap<Program, List<Future<?>>>();

            /**
             * 
             * @return true if something updated
             */
            @Override
            protected Boolean doInBackground() {
                boolean updated = false;
                Set<Program> ps = run.rdepends();
                LOG.log(Level.INFO, "Download list: {0}", ps.toString());
                for(Program p : ps) {
                    if(!isLatest(p)) {
                        LOG.log(Level.INFO, "{0} is outdated", p);
                        downloads.put(p, download(p));
                        updated = true;
                    } else {
                        LOG.log(Level.INFO, "{0} is up to date", p);
                        if(p.self) {
                            JOptionPane.showMessageDialog(null, "Launcher is up to date",
                                                          "Launcher is up to date",
                                                          JOptionPane.INFORMATION_MESSAGE,
                                                          null);
                        }
                    }
                }
                for(Entry<Program, List<Future<?>>> e : downloads.entrySet()) {
                    Program p = e.getKey();
                    List<Future<?>> futures = e.getValue();
                    try {
                        for(Future<?> future : futures) {
                            future.get(); // wait for download
                        }
                        LOG.log(Level.INFO, "Updated {0}", p);
                    } catch(Exception ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
                return updated;
            }

            @Override
            protected void done() {
                boolean updated = false;
                try {
                    updated = get();
                } catch(Exception ignore) {
                }
                if(run.main == null && updated) {
                    JOptionPane.showMessageDialog(null, "Restart to apply", "Update downloaded",
                                                  JOptionPane.INFORMATION_MESSAGE, null);
                } else {
                    Thread t = run.createThread(cl);
                    t.setDaemon(true);
                    t.start();
                }
            }

        }.execute();
    }

}
