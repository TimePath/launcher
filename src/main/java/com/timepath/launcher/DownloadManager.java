package com.timepath.launcher;

import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import com.timepath.maven.Utils;
import com.timepath.util.concurrent.DaemonThreadFactory;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());
    protected final List<DownloadMonitor> monitors = new LinkedList<>();
    protected final ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
    protected final Map<Package, Future<?>> tasks = new HashMap<>();

    public DownloadManager() {
    }

    public void addListener(DownloadMonitor downloadMonitor) {
        synchronized (monitors) {
            monitors.add(downloadMonitor);
        }
    }

    public void removeListener(DownloadMonitor downloadMonitor) {
        synchronized (monitors) {
            monitors.remove(downloadMonitor);
        }
    }

    public void shutdown() {
        pool.shutdown();
    }

    public synchronized Future<?> submit(Package pkgFile) {
        Future<?> future = tasks.get(pkgFile);
        if (future == null) { // Not already submitted
            fireSubmitted(pkgFile);
            future = pool.submit(new DownloadTask(pkgFile));
            tasks.put(pkgFile, future);
        }
        return future;
    }

    protected void fireSubmitted(Package pkgFile) {
        synchronized (monitors) {
            for (DownloadMonitor monitor : monitors) {
                monitor.onSubmit(pkgFile);
            }
        }
    }

    protected void fireUpdated(Package pkgFile) {
        synchronized (monitors) {
            for (DownloadMonitor monitor : monitors) {
                monitor.onUpdate(pkgFile);
            }
        }
    }

    protected void fireFinished(Package pkgFile) {
        synchronized (monitors) {
            for (DownloadMonitor monitor : monitors) {
                monitor.onFinish(pkgFile);
            }
        }
    }

    public static interface DownloadMonitor {

        void onSubmit(Package pkgFile);

        void onUpdate(Package pkgFile);

        void onFinish(Package pkgFile);
    }

    private class DownloadTask implements Runnable {

        private final Package pkgFile;

        private DownloadTask(Package pkgFile) {
            this.pkgFile = pkgFile;
        }

        @Override
        public void run() {
            try {
                int retryCount = 10;
                for (int i = 0; i < retryCount + 1; i++) {
                    // TODO: download resuming
                    try {
                        File downloadFile = UpdateChecker.getFile(pkgFile);
                        File checksumFile = UpdateChecker.getChecksumFile(pkgFile, UpdateChecker.ALGORITHM);
                        if (downloadFile.equals(JARUtils.CURRENT_FILE)) { // Edge case for updating current file
                            downloadFile = new File(JARUtils.UPDATE_NAME);
                            checksumFile = new File(JARUtils.UPDATE_NAME + '.' + UpdateChecker.ALGORITHM);
                        }
                        File temp = download(pkgFile);
                        // Get the checksum before the package is moved into place
                        Files.createDirectories(checksumFile.getParentFile().toPath());
                        try (FileOutputStream checksumOutputStream = new FileOutputStream(checksumFile)) {
                            checksumOutputStream.write(UpdateChecker.getChecksum(pkgFile, UpdateChecker.ALGORITHM).getBytes("UTF-8"));
                        }
                        Path move = Files.move(temp.toPath(), downloadFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        LOG.log(Level.INFO, "Complete: {0} > {1}", new Object[]{UpdateChecker.getDownloadURL(pkgFile), move});
                        return;
                    } catch (SocketTimeoutException ignored) {
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "DownloadTask", e);
                    }
                }
                LOG.log(Level.WARNING, "Failed all attempts: {0}", UpdateChecker.getDownloadURL(pkgFile));
            } finally {
                fireFinished(pkgFile);
            }
        }

        private File download(Package p) throws IOException {
            File file = File.createTempFile("jar", "");
            String s = UpdateChecker.getDownloadURL(p);
            URLConnection connection = Utils.requestConnection(s);
            pkgFile.size = connection.getContentLengthLong();
            p.associate(connection);
            IOUtils.createFile(file);
            LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[]{s, file});
            byte[] buffer = new byte[8192];
            try (InputStream is = Utils.openStream(connection);
                 OutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
                long total = 0;
                for (int read; (read = is.read(buffer)) > -1; ) {
                    fos.write(buffer, 0, read);
                    total += read;
                    pkgFile.progress = total;
                    fireUpdated(pkgFile);
                }
                fos.flush();
            }
            return file;
        }
    }
}
