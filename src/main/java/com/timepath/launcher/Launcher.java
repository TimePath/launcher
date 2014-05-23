package com.timepath.launcher;

import com.timepath.classloader.CompositeClassLoader;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class Launcher {

    public static final  Preferences          PREFS           = Preferences.userNodeForPackage(Launcher.class);
    public static final  String               REPO_MAIN       = "public.xml";
    private static final Logger               LOG             = Logger.getLogger(Launcher.class.getName());
    private final        CompositeClassLoader cl              = CompositeClassLoader.createPrivileged();
    private final        DownloadManager      downloadManager = new DownloadManager();
    private Package self;

    public Launcher() {}

    public static void addRepository(Repository r) {
        PREFS.node("repositories").putBoolean(r.getLocation(), true);
    }

    public static void removeRepository(Repository r) {
        PREFS.node("repositories").putBoolean(r.getLocation(), false);
    }

    /**
     * @return the downloadManager
     */
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    public List<Repository> getRepositories() {
        List<Repository> lists = new LinkedList<>();
        Repository main = Repository.fromIndex("http://dl.dropboxusercontent.com/u/42745598/" + REPO_MAIN);
        self = main.getSelf();
        lists.add(main);
        Preferences repos = PREFS.node("repositories");
        try {
            for(String repo : repos.keys()) {
                if(repos.getBoolean(repo, false)) {
                    lists.add(Repository.fromIndex(repo));
                }
            }
        } catch(BackingStoreException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return lists;
    }

    /**
     * @return true if self is up to date
     */
    public boolean updateRequired() {
        return !self.isLatest();
    }

    /**
     * Starts a program on another {@code Thread}. Returns after started
     *
     * @param program
     */
    public void start(Program program) {
        program.createThread(cl).start();
    }

    /**
     * @param program
     *
     * @return a Set of updated {@code Package}s, or null if currently updating
     */
    public Set<Package> update(Program program) {
        Package parent = program.getPackage();
        if(parent.isLocked()) {
            LOG.log(Level.INFO, "Package {0} locked, aborting: {1}", new Object[] { parent, program });
            return null;
        }
        parent.setLocked(true);
        // check what needs updating
        Set<Package> updates = parent.getUpdates();
        // map to futures
        Map<Package, List<Future<?>>> downloads = new HashMap<>(updates.size());
        for(Package pkg : updates) {
            List<Future<?>> pkgDownloads = new LinkedList<>();
            for(Package pkgDownload : pkg.getDownloads()) {
                pkgDownloads.add(downloadManager.submit(pkgDownload));
            }
            downloads.put(pkg, pkgDownloads);
        }
        // wait for completion
        for(Map.Entry<Package, List<Future<?>>> e : downloads.entrySet()) {
            Package pkg = e.getKey();
            try {
                for(Future<?> future : e.getValue()) {
                    future.get();
                }
                LOG.log(Level.INFO, "Updated {0}", pkg);
            } catch(InterruptedException | ExecutionException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        parent.setLocked(false);
        return downloads.keySet();
    }
}
