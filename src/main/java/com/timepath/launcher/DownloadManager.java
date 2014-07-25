package com.timepath.launcher;

import com.timepath.launcher.util.DaemonThreadFactory;
import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.maven.Package;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class DownloadManager {

    private static final Logger                  LOG      = Logger.getLogger(DownloadManager.class.getName());
    private final        List<DownloadMonitor>   monitors = new LinkedList<>();
    private final        ExecutorService         pool     = Executors.newCachedThreadPool(new DaemonThreadFactory());
    private final        Map<Package, Future<?>> tasks    = new HashMap<>();

    public DownloadManager() { }

    public void addListener(DownloadMonitor downloadMonitor) {
        synchronized(monitors) {
            monitors.add(downloadMonitor);
        }
    }

    public void removeListener(DownloadMonitor downloadMonitor) {
        synchronized(monitors) {
            monitors.remove(downloadMonitor);
        }
    }

    public void shutdown() { pool.shutdown(); }

    public synchronized Future<?> submit(Package pkgFile) {
        Future<?> future = tasks.get(pkgFile);
        if(future == null) { // Not already submitted
            synchronized(monitors) {
                for(DownloadMonitor monitor : monitors) {
                    monitor.submit(pkgFile);
                }
            }
            future = pool.submit(new DownloadTask(pkgFile));
            tasks.put(pkgFile, future);
        }
        return future;
    }

    public static interface DownloadMonitor {

        void submit(Package pkgFile);

        void update(Package pkgFile);
    }

    private class DownloadTask implements Runnable {

        private final Package pkgFile;

        private DownloadTask(Package pkgFile) { this.pkgFile = pkgFile; }

        @Override
        public void run() {
            try {
                File downloadFile = pkgFile.getFile();
                File checksumFile = pkgFile.getChecksumFile();
                if(downloadFile.equals(JARUtils.CURRENT_FILE)) { // Edge case for updating current file
                    downloadFile = new File(JARUtils.UPDATE_NAME);
                    checksumFile = new File(JARUtils.UPDATE_NAME + ".sha1");
                }
                download(new URI(pkgFile.getChecksumURL()).toURL(), checksumFile);
                download(new URI(pkgFile.getDownloadURL()).toURL(), downloadFile);
            } catch(IOException | URISyntaxException e) {
                LOG.log(Level.SEVERE, "run", e);
            }
        }

        private void download(URL u, File file) throws IOException {
            LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[] { u, file });
            URLConnection connection = u.openConnection();
            pkgFile.size = connection.getContentLengthLong();
            IOUtils.createFile(file);
            byte[] buffer = new byte[8192];
            try(InputStream is = new BufferedInputStream(connection.getInputStream());
                OutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
                long total = 0;
                for(int read; ( read = is.read(buffer) ) > -1; ) {
                    fos.write(buffer, 0, read);
                    total += read;
                    pkgFile.progress = total;
                    synchronized(monitors) {
                        for(DownloadMonitor monitor : monitors) {
                            monitor.update(pkgFile);
                        }
                    }
                }
                fos.flush();
            }
        }
    }
}
