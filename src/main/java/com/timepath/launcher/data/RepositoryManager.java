package com.timepath.launcher.data;

import com.timepath.launcher.Launcher;
import com.timepath.util.concurrent.DaemonThreadFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author TimePath
 */
public class RepositoryManager {

    public static final Preferences PREFS_REPOS = Launcher.PREFS.node("repositories");
    public static final String KEY_URL = "url";
    public static final String KEY_ENABLED = "enabled";
    private static final Logger LOG = Logger.getLogger(RepositoryManager.class.getName());

    public static void addRepository(Repository r) {
        getNode(r).put(KEY_URL, r.getLocation());
    }

    private static Preferences getNode(Repository r) {
        return PREFS_REPOS.node(getNodeName(r));
    }

    private static String getNodeName(Repository r) {
        return String.valueOf(r.hashCode());
    }

    public static void setRepositoryEnabled(Repository r, boolean flag) {
        getNode(r).putBoolean(KEY_ENABLED, flag);
    }

    public static void removeRepository(Repository r) {
        getNode(r).remove(KEY_URL);
    }

    public static List<Repository> loadCustom() {
        List<Repository> repositories = new LinkedList<>();
        List<Future<Repository>> futures = new LinkedList<>();
        try {
            ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            for (final String s : PREFS_REPOS.childrenNames()) {
                Preferences repo = PREFS_REPOS.node(s);
                final String url = repo.get(KEY_URL, null);
                if (url == null) { // Dead
                    repo.removeNode();
                    repo.flush();
                    continue;
                }
                final boolean enabled = repo.getBoolean(KEY_ENABLED, true);
                futures.add(pool.submit(new Callable<Repository>() {
                    @Override
                    public Repository call() throws Exception {
                        final Repository r = Repository.fromIndex(url);
                        if (r == null) return null;
                        if (!s.equals(getNodeName(r))) return null; // Node name needs update
                        r.setEnabled(enabled);
                        return r;
                    }
                }));
            }
        } catch (BackingStoreException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        for (Future<Repository> future : futures) {
            try {
                Repository r = future.get();
                if (r != null) repositories.add(r);
            } catch (InterruptedException | ExecutionException e) {
                LOG.log(Level.SEVERE, null, e);
            }
        }
        return repositories;
    }
}
