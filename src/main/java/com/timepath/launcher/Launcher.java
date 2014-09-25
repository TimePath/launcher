package com.timepath.launcher;

import com.timepath.classloader.CompositeClassLoader;
import com.timepath.launcher.data.Program;
import com.timepath.launcher.data.Repository;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import com.timepath.util.Cache;
import com.timepath.util.concurrent.DaemonThreadFactory;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author TimePath
 */
public class Launcher {

    public static final String REPO_MAIN =
            "http://oss.jfrog.org/artifactory/oss-snapshot-local/com/timepath/launcher/config/" + "public.xml";
    public static final Preferences PREFS = Preferences.userNodeForPackage(Launcher.class);
    private static final Preferences PREFS_REPOS = PREFS.node("repositories");
    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());
    private static Map<Package, Boolean> locked = new Cache<Package, Boolean>() {
        @Override
        protected Boolean fill(Package key) {
            return false;
        }
    };
    private final CompositeClassLoader cl = CompositeClassLoader.createPrivileged();
    private final DownloadManager downloadManager = new DownloadManager();
    private Package self;

    public Launcher() {
    }

    public static void addRepository(Repository r) {
        PREFS_REPOS.put(String.valueOf(r.hashCode()), r.getLocation());
    }

    public static void removeRepository(Repository r) {
        PREFS_REPOS.remove(String.valueOf(r.hashCode()));
    }

    private static List<String> getRepositoryLocations() {
        List<String> locations = new LinkedList<>();
        try {
            for (String repo : PREFS_REPOS.keys()) {
                String s = PREFS_REPOS.get(repo, null);
                if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) { // Legacy schema
                    PREFS_REPOS.remove(String.valueOf(s.hashCode()));
                    continue;
                }
                if (s != null) locations.add(s);
            }
        } catch (BackingStoreException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        return locations;
    }

    public static boolean isLocked(Package aPackage) {
        return locked.get(aPackage);
    }

    public static void setLocked(Package aPackage, boolean lock) {
        LOG.log(Level.INFO, (lock ? "L" : "Unl") + "ocking {0}", aPackage);
        locked.put(aPackage, lock);
    }

    /**
     * @return the downloadManager
     */
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    /**
     * @return fetch and return a list of all repositories
     */
    public List<Repository> getRepositories() {
        List<Repository> repositories = new LinkedList<>();
        Repository main = Repository.fromIndex(REPO_MAIN);
        repositories.add(main);
        self = main.getSelf();
        List<String> locations = getRepositoryLocations();
        if (!locations.isEmpty()) {
            ExecutorService pool = Executors.newFixedThreadPool(locations.size(), new DaemonThreadFactory());
            List<Future<Repository>> futures = new LinkedList<>();
            for (final String repo : locations) {
                futures.add(pool.submit(new Callable<Repository>() {
                    @Override
                    public Repository call() throws Exception {
                        return Repository.fromIndex(repo);
                    }
                }));
            }
            for (Future<Repository> future : futures) {
                try {
                    Repository r = future.get();
                    if (r != null) repositories.add(r);
                } catch (InterruptedException | ExecutionException e) {
                    LOG.log(Level.SEVERE, null, e);
                }
            }
        }
        return repositories;
    }

    /**
     * @return true if self is up to date
     */
    public boolean updateRequired() {
        return !UpdateChecker.isLatest(self);
    }

    /**
     * Starts a program. Returns after started; quickly if the target defers to the EDT
     *
     * @param program
     */
    public void start(final Program program) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    program.run(cl);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        t.setName(program.toString());
        t.setDaemon(program.isDaemon());
        t.setContextClassLoader(cl);
        t.start();
    }

    /**
     * @param program
     * @return a Set of updated {@code Package}s, or null if currently updating
     */
    public Set<Package> update(Program program) {
        Package parent = program.getPackage();
        if (isLocked(parent)) {
            LOG.log(Level.INFO, "Package {0} locked, aborting: {1}", new Object[]{parent, program});
            return null;
        }
        try {
            setLocked(parent, true);
            LOG.log(Level.INFO, "Checking for updates");
            Map<Package, Future<?>> downloads = new HashMap<>();
            Set<Package> updates = UpdateChecker.getUpdates(parent);
            LOG.log(Level.INFO, "Updates: {0}", updates);
            for (Package pkg : updates) {
                LOG.log(Level.INFO, "Submitting {0}", pkg);
                downloads.put(pkg, downloadManager.submit(pkg));
            }
            LOG.log(Level.INFO, "Waiting for completion");
            for (Map.Entry<Package, Future<?>> e : downloads.entrySet()) {
                Package pkg = e.getKey();
                Future<?> future = e.getValue();
                try {
                    future.get();
                    LOG.log(Level.INFO, "Updated {0}", pkg);
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            return updates;
        } finally {
            setLocked(parent, false);
        }
    }
}
