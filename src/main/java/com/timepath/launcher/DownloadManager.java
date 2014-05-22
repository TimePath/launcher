package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.Utils.DaemonThreadFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadManager {

    private static final Logger                LOG      = Logger.getLogger(DownloadManager.class.getName());
    private final        List<DownloadMonitor> monitors = Collections.synchronizedList(new LinkedList<DownloadMonitor>());
    private final        ExecutorService       pool     = Executors.newCachedThreadPool(new DaemonThreadFactory());

    public DownloadManager() {
    }

    public void addListener(DownloadMonitor downloadMonitor) {
        monitors.add(downloadMonitor);
    }

    public void removeListener(DownloadMonitor downloadMonitor) {
        monitors.remove(downloadMonitor);
    }

    public void shutdown() {
        pool.shutdown();
    }

    public Future<?> submit(Package pkgFile) {
        synchronized(monitors) {
            for(DownloadMonitor monitor : monitors) {
                monitor.submit(pkgFile);
            }
        }
        return pool.submit(new DownloadTask(pkgFile));
    }

    public interface DownloadMonitor {

        void submit(Package pkgFile);

        void update(Package pkgFile);
    }

    private class DownloadTask implements Runnable {

        private final Package pkgFile;

        private DownloadTask(Package pkgFile) {
            this.pkgFile = pkgFile;
        }

        @Override
        public void run() {
            String[] dl = { pkgFile.getDownloadURL(), pkgFile.getChecksumURL() };
            try {
                for(String s : dl) {
                    if(s == null) {
                        continue;
                    }
                    URL u = new URI(s).toURL();
                    File file = ( s == dl[0] ) ? pkgFile.getFile() : pkgFile.getChecksumFile();
                    download(u, file);
                }
            } catch(IOException | URISyntaxException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }

        private void download(URL u, File file) throws IOException {
            URLConnection connection = u.openConnection();
            pkgFile.size = connection.getContentLengthLong();
            LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[] { u, file });
            Utils.createFile(file);
            byte[] buffer = new byte[81920];
            try(InputStream is = new BufferedInputStream(connection.getInputStream(), buffer.length);
                OutputStream fos = new BufferedOutputStream(new FileOutputStream(file), buffer.length)) {
                int read;
                long total = 0;
                while(( read = is.read(buffer) ) > -1) {
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
