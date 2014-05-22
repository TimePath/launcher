package com.timepath.launcher;

import com.timepath.classloader.CompositeClassLoader;

import javax.swing.*;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static java.security.AccessController.doPrivileged;

public class Launcher {

    public static final  Preferences          PREFS           = Preferences.userNodeForPackage(Launcher.class);
    public static final  String               REPO_MAIN       = "public.xml";
    private static final Logger               LOG             = Logger.getLogger(Launcher.class.getName());
    private final        CompositeClassLoader cl              = doPrivileged(new PrivilegedAction<CompositeClassLoader>() {
        @Override
        public CompositeClassLoader run() {
            return new CompositeClassLoader();
        }
    });
    private final        DownloadManager      downloadManager = new DownloadManager();
    private Program self;

    public Launcher() {}

    public static void addRepository(Repository r) {
        PREFS.node("repositories").putBoolean(r.location, true);
    }

    public static void removeRepository(Repository r) {
        PREFS.node("repositories").putBoolean(r.location, false);
    }

    /**
     * @return the downloadManager
     */
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    public List<Repository> getRepositories() {
        List<Repository> lists = new LinkedList<>();
        Repository main = Repository.get("http://dl.dropboxusercontent.com/u/42745598/" + REPO_MAIN);
        self = main.self;
        lists.add(main);
        Preferences repos = PREFS.node("repositories");
        try {
            for(String repo : repos.keys()) {
                if(repos.getBoolean(repo, false)) {
                    lists.add(Repository.get(repo));
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

    public void shutdown() {
        downloadManager.shutdown();
    }

    public void start(Program program) {
        if(program == null) {
            return;
        }
        if(program.lock) {
            LOG.log(Level.INFO, "Program locked, aborting: {0}", program);
            return;
        }
        LOG.log(Level.INFO, "Locking {0}", program);
        program.lock = true;
        List<Program> updates = program.getUpdates();
        if(program.self && !updates.contains(program)) {
            JOptionPane.showMessageDialog(null,
                                          "Launcher is up to date",
                                          "Launcher is up to date",
                                          JOptionPane.INFORMATION_MESSAGE,
                                          null);
        }
        Map<Program, List<Future<?>>> downloads = new HashMap<>(updates.size());
        for(Program p : updates) {
            downloads.put(p, download(p));
        }
        boolean selfupdated = false;
        for(Map.Entry<Program, List<Future<?>>> e : downloads.entrySet()) {
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
        if(( program.main == null ) && selfupdated) {
            JOptionPane.showMessageDialog(null, "Restart to apply", "Update downloaded", JOptionPane.INFORMATION_MESSAGE, null);
        } else {
            program.createThread(cl).start();
            program.lock = false;
        }
    }

    private List<Future<?>> download(Program p) {
        List<Future<?>> arr = new LinkedList<>();
        for(PackageFile pkgFile : p.downloads) {
            arr.add(downloadManager.submit(pkgFile));
        }
        return arr;
    }
}
