package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

import static com.timepath.launcher.util.Utils.debug;

/**
 *
 * @author TimePath
 */
public class Program extends Downloadable {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());

    public List<String> args;

    public String changelogData;

    public Set<Program> depends = new HashSet<>();

    /**
     * A map of downloads to checksums. TODO: allow for versions
     */
    public List<Downloadable> downloads = new LinkedList<>();

    public JEditorPane jEditorPane;

    public String main;

    public String newsfeedType = "text/html";

    public String newsfeedURL;

    public JPanel panel;

    public boolean self;

    public String title;

    public boolean daemon;

    public boolean lock;

    public Set<URI> classPath() {
        Set<URI> h = new HashSet<>(downloads.size() * depends.size());
        for(Downloadable d : downloads) {
            h.add(d.file().toURI());
            for(Downloadable n : d.nested) {
                try {
                    URL u = new URL("jar", "", d.file().toURI() + "!/" + n.downloadURL);
                    Utils.extract(u, n.file());
                    h.add(n.file().toURI());
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        for(Program p : depends) {
            if(p == null) {
                continue;
            }
            h.addAll(p.classPath());
        }
        File f = file();
        if(f != null) {
            h.add(f.toURI());
        }
        return h;
    }

    /**
     * Check a program for updates
     * <p/>
     * @param p The program
     * <p/>
     * @return false if not up to date, true if up to date or working offline
     */
    public boolean isLatest() {
        if(debug && self) {
            return true;
        }
        LOG.log(Level.INFO, "Checking {0} for updates...", this);
        for(Downloadable d : downloads) {
            try {
                LOG.log(Level.INFO, "Version file: {0}", d.file());
                LOG.log(Level.INFO, "Version url: {0}", d.versionURL);

                File f = d.file();
                if(f.getName().equals(Launcher.updateName)) { // edge case for current file
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
    
    public List<Program> updates() {
        List<Program> outdated = new LinkedList<>();
        Set<Program> ps = rdepends();
        LOG.log(Level.INFO, "Download list: {0}", ps.toString());
        for(Program p : ps) {
            if(!p.isLatest()) {
                LOG.log(Level.INFO, "{0} is outdated", p);
                outdated.add(p);
            } else {
                LOG.log(Level.INFO, "{0} is up to date", p);
            }
        }
        return outdated;
    }
    
    public Thread createThread(final CompositeClassLoader cl) {
        return new Thread() {

            {
                setDaemon(Program.this.daemon);
            }

            @Override
            public void run() {
                if(main == null) {
                    return; // Not executable
                }
                LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] {this, main});
                try {
                    String[] argv = null;
                    if(args != null) {
                        argv = args.toArray(new String[args.size()]);
                    }
                    Set<URI> cp = classPath();
                    cl.start(main, argv, cp.toArray(new URI[cp.size()]));
//                    for(Window w : Window.getWindows()) { // TODO: This will probably come back to haunt me later
//                        LOG.log(Level.INFO, "{0}  {1}", new Object[] {w, w.isDisplayable()});
//                        if(!w.isVisible()) {
//                            w.dispose();
//                        }
//                    }
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        };
    }

    public void setSelf(boolean b) {
        self = b;
        programDirectory = null;
        for(Downloadable d : downloads) {
            d.programDirectory = null;
        }
    }

    public Set<Program> rdepends() {
        Set<Program> h = new HashSet<>();
        // Add all parent dependencies first
        for(Program p : depends) {
            if(p == null) {
                continue;
            }
            h.addAll(p.rdepends());
        }
        // Not just a run configuration
        if(!downloads.isEmpty()) {
            h.add(this);
        }
        h.remove(null);
        return h;
    }

    @Override
    public String toString() {
        return title + (debug ? ("(" + downloads + ")" + (!depends.isEmpty() ? (" " + depends
                                                                                .toString()) : ""))
                        : "");
    }

}
