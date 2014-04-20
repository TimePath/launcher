package com.timepath.launcher;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadManager {

    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());

    private final List<DownloadMonitor> monitors = Collections.synchronizedList(
        new LinkedList<DownloadMonitor>());

    private final ExecutorService pool = Executors.newCachedThreadPool(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    });

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

    public Future<?> submit(Downloadable d) {
        synchronized(monitors) {
            Iterator<DownloadMonitor> i = monitors.iterator();
            while(i.hasNext()) {
                i.next().submit(d);
            }
        }
        return pool.submit(new DownloadThread(d));
    }

    private class DownloadThread implements Runnable {

        private final Downloadable d;

        private DownloadThread(Downloadable d) {
            this.d = d;
        }

        @Override
        public void run() {
            String[] dl = {d.downloadURL, d.versionURL};
            try {
                for(String s : dl) {
                    if(s == null) {
                        continue;
                    }
                    URL u = new URI(s).toURL();
                    File f;
                    if(s == dl[0]) {
                        f = d.file();
                    } else {
                        f = d.versionFile();
                    }
                    if(f == null) {
                        f = new File(Downloadable.PROGRAM_DIRECTORY, Downloadable.name(u));
                    }
                    download(u, f);
                }
            } catch(IOException | URISyntaxException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }

        private void download(URL u, File f) throws IOException {
            URLConnection c = u.openConnection();
            long size = c.getContentLengthLong();
            d.size = size;
            LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[] {u, f});
            f.mkdirs();
            f.delete();
            f.createNewFile();
            byte[] buffer = new byte[8192];

            try(InputStream is = new BufferedInputStream(c.getInputStream(), buffer.length);
                OutputStream fos = new BufferedOutputStream(new FileOutputStream(f),
                                                            buffer.length)) {
                int read;
                long total = 0;
                while((read = is.read(buffer)) > -1) {
                    fos.write(buffer, 0, read);
                    total += read;
                    d.progress = total;
                    synchronized(monitors) {
                        Iterator<DownloadMonitor> i = monitors.iterator();
                        while(i.hasNext()) {
                            i.next().update(d);
                        }
                    }
                }
                fos.flush();
            }
        }

    }

}
