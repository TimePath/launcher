package com.timepath.launcher;

import com.timepath.classloader.CompositeClassLoader;
import com.timepath.launcher.data.DownloadManager;
import com.timepath.launcher.data.Program;
import com.timepath.launcher.data.Repository;
import com.timepath.launcher.data.RepositoryManager;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import com.timepath.util.Cache;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * @author TimePath
 */
public class Launcher {

    public static final String REPO_MAIN =
            "http://oss.jfrog.org/artifactory/oss-snapshot-local/com/timepath/launcher/config/" + "public.xml";
    public static final Preferences PREFS = Preferences.userNodeForPackage(Launcher.class);
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

    private List<Repository> repositories;

    /**
     * @return fetch and return a list of all repositories
     */
    public List<Repository> getRepositories(boolean override) {
        if (!override && repositories != null) return repositories;
        repositories = new LinkedList<>();
        Repository main = Repository.fromIndex(REPO_MAIN);
        main.setEnabled(true);
        repositories.add(main);
        self = main.getSelf();
        repositories.addAll(RepositoryManager.loadCustom());
        return repositories;
    }

    public List<Repository> getRepositories() {
        return getRepositories(false);
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
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
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
