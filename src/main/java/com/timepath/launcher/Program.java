package com.timepath.launcher;

import com.timepath.launcher.util.Utils;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.timepath.launcher.util.Utils.UPDATE_NAME;
import static com.timepath.launcher.util.Utils.debug;

/**
 * @author TimePath
 */
public class Program extends PackageFile {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());
    public List<String> args;
    public String       changelogData;
    public Set<Program>      depends   = new HashSet<>(0);
    /**
     * A map of downloads to checksums. TODO: allow for versions
     */
    public List<PackageFile> downloads = new LinkedList<>();
    public JEditorPane jEditorPane;
    public String      main;
    public String newsfeedType = "text/html";
    public String  newsfeedURL;
    public JPanel  panel;
    public boolean self;
    public String  title;
    public boolean daemon;
    public boolean lock;

    public Program() {}

    public boolean isSelf() {
        return self;
    }

    public void setSelf(boolean self) {
        this.self = self;
        programDirectory = null;
        for(PackageFile pkgFile : downloads) {
            pkgFile.programDirectory = null;
        }
    }

    public Set<URI> calculateClassPath() {
        Set<URI> h = new HashSet<>(downloads.size() * depends.size());
        for(PackageFile download : downloads) {
            h.add(download.getFile().toURI());
            for(PackageFile extract : download.nested) {
                try {
                    URL u = new URL("jar", "", download.getFile().toURI() + "!/" + extract.downloadURL);
                    Utils.extract(u, extract.getFile());
                    h.add(extract.getFile().toURI());
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
        File file = getFile();
        if(file != null) {
            h.add(file.toURI());
        }
        return h;
    }

    /**
     * Check a program for updates
     *
     * @return false if not up to date, true if up to date or working offline
     */
    public boolean isLatest() {
        LOG.log(Level.INFO, "Checking {0} for updates...", this);
        for(PackageFile pkgFile : downloads) {
            try {
                File file = pkgFile.getFile();
                if(UPDATE_NAME.equals(file.getName())) { // Edge case for current file
                    file = Utils.currentFile;
                }
                LOG.log(Level.INFO, "Version file: {0}", file);
                LOG.log(Level.INFO, "Version url: {0}", pkgFile.checksumURL);
                if(!file.exists()) {
                    LOG.log(Level.INFO, "Don't have {0}, not latest", file);
                    return false;
                }
                if(pkgFile.checksumURL == null) {
                    LOG.log(Level.INFO, "{0} not versioned, skipping", file);
                    continue; // Have unversioned file, skip check
                }
                String checksum = Utils.checksum(file, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(new URL(pkgFile.checksumURL).openStream()));
                String expected = br.readLine();
                if(!checksum.equals(expected)) {
                    LOG.log(Level.INFO, "Checksum mismatch for {0}, not latest", file);
                    return false;
                }
            } catch(IOException | NoSuchAlgorithmException ex) {
                LOG.log(Level.SEVERE, null, ex);
                return false;
            }
        }
        LOG.log(Level.INFO, "{0} doesn't need updating", this);
        return true;
    }

    /**
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
            if(p.isLatest()) {
                LOG.log(Level.INFO, "{0} is up to date", p);
            } else {
                LOG.log(Level.INFO, "{0} is outdated", p);
                outdated.add(p);
            }
        }
        return outdated;
    }

    public Thread createThread(final CompositeClassLoader cl) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                if(main == null) { // Not executable
                    return;
                }
                LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] { this, main });
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
        });
        t.setContextClassLoader(cl);
        t.setDaemon(daemon);
        return t;
    }

    /**
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
            sb.append(' ').append('(').append(downloads).append(')');
            if(!depends.isEmpty()) {
                sb.append(' ').append(depends);
            }
        }
        return sb.toString();
    }
}
