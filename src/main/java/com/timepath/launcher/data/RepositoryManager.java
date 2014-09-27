package com.timepath.launcher.data;

import com.timepath.launcher.Launcher;
import com.timepath.util.concurrent.DaemonThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public static void addRepository(@NotNull Repository r) {
        getNode(r).put(KEY_URL, r.getLocation());
    }

    private static Preferences getNode(@NotNull Repository r) {
        return PREFS_REPOS.node(getNodeName(r));
    }

    @NotNull
    private static String getNodeName(@NotNull Repository r) {
        return String.valueOf(r.hashCode());
    }

    public static void setRepositoryEnabled(@NotNull Repository r, boolean flag) {
        getNode(r).putBoolean(KEY_ENABLED, flag);
    }

    public static void removeRepository(@NotNull Repository r) {
        getNode(r).remove(KEY_URL);
    }

    @NotNull
    public static List<Repository> loadCustom() {
        @NotNull List<Repository> repositories = new LinkedList<>();
        @NotNull List<Future<Repository>> futures = new LinkedList<>();
        try {
            @NotNull ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            for (@NotNull final String s : PREFS_REPOS.childrenNames()) {
                Preferences repo = PREFS_REPOS.node(s);
                final String url = repo.get(KEY_URL, null);
                if (url == null) { // Dead
                    repo.removeNode();
                    repo.flush();
                    continue;
                }
                final boolean enabled = repo.getBoolean(KEY_ENABLED, true);
                futures.add(pool.submit(new Callable<Repository>() {
                    @Nullable
                    @Override
                    public Repository call() throws Exception {
                        @Nullable final Repository r = Repository.fromIndex(url);
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
        for (@NotNull Future<Repository> future : futures) {
            try {
                Repository r = future.get();
                if (r != null) repositories.add(r);
            } catch (@NotNull InterruptedException | ExecutionException e) {
                LOG.log(Level.SEVERE, null, e);
            }
        }
        return repositories;
    }
}
