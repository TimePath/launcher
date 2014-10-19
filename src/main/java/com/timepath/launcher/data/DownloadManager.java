package com.timepath.launcher.data;

import com.timepath.IOUtils;
import com.timepath.launcher.LauncherUtils;
import com.timepath.maven.Constants;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import com.timepath.util.concurrent.DaemonThreadFactory;
import org.jetbrains.annotations.NotNull;

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
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class DownloadManager {

    public static final int MAX_CONCURRENT_CONNECTIONS = 10;
    @NotNull
    private Semaphore semaphore = new Semaphore(MAX_CONCURRENT_CONNECTIONS, true);
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
            for (@NotNull DownloadMonitor monitor : monitors) {
                monitor.onSubmit(pkgFile);
            }
        }
    }

    protected void fireUpdated(Package pkgFile) {
        synchronized (monitors) {
            for (@NotNull DownloadMonitor monitor : monitors) {
                monitor.onUpdate(pkgFile);
            }
        }
    }

    protected void fireFinished(Package pkgFile) {
        synchronized (monitors) {
            for (@NotNull DownloadMonitor monitor : monitors) {
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
        private File temp;

        private DownloadTask(Package pkgFile) {
            this.pkgFile = pkgFile;
            try {
                this.temp = File.createTempFile("pkg", "");
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Unable to create temp file for download", e);
            }
        }

        @Override
        public void run() {
            try {
                semaphore.acquireUninterruptibly();
                int retryCount = 10;
                for (int i = 0; i < retryCount + 1; i++) {
                    try {
                        File downloadFile, checksumFile;
                        if (pkgFile.isSelf()) { // Special case
                            downloadFile = LauncherUtils.UPDATE;
                            checksumFile = new File(LauncherUtils.UPDATE.getName() + '.' + Constants.ALGORITHM);
                        } else {
                            downloadFile = UpdateChecker.getFile(pkgFile);
                            checksumFile = UpdateChecker.getChecksumFile(pkgFile, Constants.ALGORITHM);
                        }
                        download(pkgFile);
                        // Get the checksum before the package is moved into place
                        LOG.log(Level.INFO, "Saving checksum: {0}", checksumFile);
                        Files.createDirectories(checksumFile.getAbsoluteFile().getParentFile().toPath());
                        try (@NotNull FileOutputStream checksumOutputStream = new FileOutputStream(checksumFile)) {
                            checksumOutputStream.write(UpdateChecker.getChecksum(pkgFile, Constants.ALGORITHM).getBytes("UTF-8"));
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
                semaphore.release();
            }
        }

        private void download(@NotNull Package p) throws IOException {
            @NotNull String s = UpdateChecker.getDownloadURL(p);
            URLConnection connection = IOUtils.requestConnection(s, new IOUtils.ConnectionSettings() {
                @Override
                public void apply(@NotNull URLConnection u) {
                    u.setRequestProperty("Range", "bytes=" + pkgFile.getProgress() + "-");
                }
            });
            boolean partial = "bytes".equals(connection.getHeaderField("Accept-Ranges"));
            pkgFile.setSize(connection.getContentLengthLong());
            p.associate(connection);
            IOUtils.createFile(temp);
            LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[]{s, temp});
            @NotNull byte[] buffer = new byte[8192];
            try (InputStream is = IOUtils.openStream(connection);
                 @NotNull OutputStream fos = new BufferedOutputStream(new FileOutputStream(temp, partial))) {
                long total = 0;
                for (int read; (read = is.read(buffer)) > -1; ) {
                    fos.write(buffer, 0, read);
                    total += read;
                    pkgFile.setProgress(total);
                    fireUpdated(pkgFile);
                }
                fos.flush();
            }
        }
    }
}
