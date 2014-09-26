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

    private static final Logger LOG = Logger.getLogger(RepositoryManager.class.getName());

    public static final Preferences PREFS_REPOS = Launcher.PREFS.node("repositories");
    public static final String KEY_URL = "url";
    public static final String KEY_ENABLED = "enabled";

    public static void addRepository(Repository r) {
        PREFS_REPOS.node(String.valueOf(r.hashCode())).put(KEY_URL, r.getLocation());
    }

    public static void setRepositoryEnabled(Repository r, boolean flag) {
        PREFS_REPOS.node(String.valueOf(r.hashCode())).putBoolean(KEY_ENABLED, flag);
    }

    public static void removeRepository(Repository r) {
        PREFS_REPOS.node(String.valueOf(r.hashCode())).remove(KEY_URL);
    }

    public static List<Repository> loadCustom() {
        List<Repository> repositories = new LinkedList<>();
        List<Future<Repository>> futures = new LinkedList<>();
        try {
            ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            for (String s : PREFS_REPOS.childrenNames()) {
                Preferences repo = PREFS_REPOS.node(s);
                final String url = repo.get(KEY_URL, null);
                if (url == null) { // Dead
                    repo.removeNode();
                    continue;
                }
                final boolean enabled = repo.getBoolean(KEY_ENABLED, true);
                futures.add(pool.submit(new Callable<Repository>() {
                    @Override
                    public Repository call() throws Exception {
                        final Repository r = Repository.fromIndex(url);
                        if (r != null) r.setEnabled(enabled);
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
