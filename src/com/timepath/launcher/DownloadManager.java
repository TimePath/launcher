package com.timepath.launcher;

import com.timepath.swing.table.ObjectBasedTableModel;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadManager {

    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());

    private final ExecutorService pool = Executors.newCachedThreadPool();

    private final ObjectBasedTableModel<Downloadable> tableModel;

    public DownloadManager(ObjectBasedTableModel<Downloadable> tm) {
        this.tableModel = tm;
    }

    public void shutdown() {
        pool.shutdown();
    }

    public Future<?> submit(Downloadable d) {
        tableModel.add(d);
        return pool.submit(new DownloadThread(tableModel, d));
    }

    private static class DownloadThread implements Runnable {

        private final Downloadable d;

        private final ObjectBasedTableModel<Downloadable> m;

        private DownloadThread(ObjectBasedTableModel<Downloadable> m, Downloadable d) {
            this.m = m;
            this.d = d;
        }

        public void run() {
            InputStream is = null;
            String[] dl = {d.downloadURL, d.versionURL};
            try {
                for(String s : dl) {
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
                    URLConnection c = u.openConnection();
                    String len = c.getHeaderField("content-length");
                    long size = -1;
                    try {
                        size = Long.parseLong(len);
                    } catch(Exception e) {
                    }
                    d.size = size;
                    LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[] {u, f});
                    f.mkdirs();
                    f.delete();
                    f.createNewFile();
                    byte[] buffer = new byte[8192];
                    is = new BufferedInputStream(c.getInputStream(), buffer.length);

                    OutputStream fos = new BufferedOutputStream(new FileOutputStream(f),
                                                                buffer.length);
                    int read;
                    long total = 0;
                    while((read = is.read(buffer)) > -1) {
                        fos.write(buffer, 0, read);
                        total += read;
                        d.progress = total;
                        m.update(d);
                    }
                    fos.flush();
                    fos.close();
                }
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(URISyntaxException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } finally {
                if(is != null) {
                    try {
                        is.close();
                    } catch(IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

    }

}
