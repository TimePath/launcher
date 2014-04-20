package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

import static com.timepath.launcher.util.Utils.UPDATE_NAME;
import static com.timepath.launcher.util.Utils.debug;

/**
 *
 * @author TimePath
 */
public class Program extends PackageFile {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());

    public List<String> args;

    public String changelogData;

    public Set<Program> depends = new HashSet<>(0);

    /**
     * A map of downloads to checksums. TODO: allow for versions
     */
    public List<PackageFile> downloads = new LinkedList<>();

    public JEditorPane jEditorPane;

    public String main;

    public String newsfeedType = "text/html";

    public String newsfeedURL;

    public JPanel panel;

    public boolean self;

    public String title;

    public boolean daemon;

    public boolean lock;

    public Set<URI> calculateClassPath() {
        Set<URI> h = new HashSet<>(downloads.size() * depends.size());
        for(PackageFile d : downloads) {
            h.add(d.getFile().toURI());
            for(PackageFile n : d.nested) {
                try {
                    URL u = new URL("jar", "", d.getFile().toURI() + "!/" + n.downloadURL);
                    Utils.extract(u, n.getFile());
                    h.add(n.getFile().toURI());
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        for(Program p : depends) {
            if(p == null) {
                continue;
            }
            h.addAll(p.calculateClassPath());
        }
        File f = getFile();
        if(f != null) {
            h.add(f.toURI());
        }
        return h;
    }

    /**
     * Check a program for updates
     * <p/>
     * @return false if not up to date, true if up to date or working offline
     */
    public boolean isLatest() {
        if(debug && self) {
            return true;
        }
        LOG.log(Level.INFO, "Checking {0} for updates...", this);
        for(PackageFile d : downloads) {
            try {
                LOG.log(Level.INFO, "Version file: {0}", d.getFile());
                LOG.log(Level.INFO, "Version url: {0}", d.versionURL);

                File f = d.getFile();
                if(UPDATE_NAME.equals(f.getName())) { // Edge case for current file
                    f = Utils.currentFile;
                }

                if(!f.exists()) {
                    return false;
                } else if(d.versionURL == null) {
                    continue; // Eave unversioned file, skip check
                }
                String checksum = Utils.checksum(f, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    new URL(d.versionURL).openStream()));
                String expected = br.readLine();
                if(!checksum.equals(expected)) {
                    return false;
                }
            } catch(IOException | NoSuchAlgorithmException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }

    /**
     *
     * @return A list of updates for this program
     */
    public List<Program> getUpdates() {
        List<Program> outdated = new LinkedList<>();
        Set<Program> ps = getDependencies();
        LOG.log(Level.INFO, "Download list: {0}", ps.toString());
        for(Program p : ps) {
            if(p == null) {
                continue;
            }
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
                if(main == null) { // Not executable
                    return;
                }
                LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] {this, main});
                try {
                    String[] argv = null;
                    if(args != null) {
                        argv = args.toArray(new String[args.size()]);
                    }
                    Set<URI> cp = calculateClassPath();
                    cl.start(main, argv, cp.toArray(new URI[cp.size()]));
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        };
    }

    public void setSelf(boolean b) {
        self = b;
        programDirectory = null;
        for(PackageFile d : downloads) {
            d.programDirectory = null;
        }
    }

    /**
     *
     * @return A flattened list of dependencies
     */
    public Set<Program> getDependencies() {
        Set<Program> h = new HashSet<>(0);
        for(Program p : depends) { // Add all parent dependencies first
            if(p == null) {
                continue;
            }
            h.addAll(p.getDependencies());
        }
        if(!downloads.isEmpty()) { // Not just a run configuration
            h.add(this);
        }
        h.remove(null);
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(title);
        if(debug) {
            sb.append(" ").append("(").append(downloads).append(")");
            if(!depends.isEmpty()) {
                sb.append(" ").append(depends);
            }
        }
        return sb.toString();
    }

}
