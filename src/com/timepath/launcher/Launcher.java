package com.timepath.launcher;

import java.io.*;
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.*;

import static com.timepath.launcher.util.Utils.debug;
import static com.timepath.launcher.util.Utils.start;

public class Launcher {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    private final CompositeClassLoader cl = AccessController.doPrivileged(
        new PrivilegedAction<CompositeClassLoader>() {
            @Override
            public CompositeClassLoader run() {
                return new CompositeClassLoader();
            }
        });

    private final DownloadManager downloadManager = new DownloadManager();

    private Program self;

    /**
     * @return the downloadManager
     */
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    public List<List<Program>> getListings() {
        List<List<Program>> lists = new LinkedList<>();

        lists.add(getListing("http://dl.dropboxusercontent.com/u/42745598/public.xml"));
        Preferences repos = Preferences.userNodeForPackage(getClass()).node("repositories");
        try {
            for(String repo : repos.keys()) {
                if(repos.getBoolean(repo, false)) {
                    lists.add(getListing(repo));
                }
            }
        } catch(BackingStoreException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return lists;
    }

    /**
     *
     * @return true if self is up to date
     */
    public boolean selfCheck() {
        return self.isLatest();
    }

    public void shutdown() {
        getDownloadManager().shutdown();
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

        List<Program> updates = program.getUpdates();

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

    private List<Future<?>> download(Program p) {
        List<Future<?>> arr = new LinkedList<>();
        for(PackageFile d : p.downloads) {
            arr.add(getDownloadManager().submit(d));
        }
        return arr;
    }

    private List<Program> getListing(String s) {
        InputStream is = null;
        if(debug) {
            try {
                is = new FileInputStream(System.getProperty("user.home")
                                         + "/Dropbox/Public/projects.xml");
            } catch(FileNotFoundException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        if(is == null) {
            try {
                long listingStart = System.currentTimeMillis();
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
        Repository r = new Repository(is);
        if(self == null) {
            self = r.self;
        }
        LOG.log(Level.INFO, "Parsed at {0}ms", System.currentTimeMillis() - start);
        return r.getPackages();
    }

}
