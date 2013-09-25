package com.timepath.launcher;

import com.timepath.swing.table.ObjectBasedTableModel;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author TimePath
 */
public class DownloadManager extends JPanel {

    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());

    public static class Download {

        public Download(String s, URL u, File f) {
            this.name = s;
            this.url = u;
            this.file = f;
        }

        final String name;

        final URL url;

        final File file;

        long progress, size = -1;

    }

    private static class DownloadThread implements Runnable {

        private final ObjectBasedTableModel m;

        private final Download d;

        private DownloadThread(ObjectBasedTableModel m, Download d) {
            this.m = m;
            this.d = d;
        }

        public void run() {
            URL u = d.url;
            File f = d.file;
            InputStream is = null;
            try {
                URLConnection c = u.openConnection();
                String len = c.getHeaderField("content-length");
                long size = -1;
                try {
                    size = Long.parseLong(len);
                } catch(Exception e) {
                }
                d.size = size;
                LOG.log(Level.INFO, "Downloading {0}", u);
                f.mkdirs();
                f.delete();
                f.createNewFile();
                byte[] buffer = new byte[8192];
                is = new BufferedInputStream(c.getInputStream(), buffer.length);

                OutputStream fos = new BufferedOutputStream(new FileOutputStream(f), buffer.length);
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
            } catch(IOException ex) {
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
    
    public Future<?> submit(Download d) {
        tableModel.add(d);
        return pool.submit(new DownloadThread(tableModel, d));
    }
    
    public void shutdown() {
        pool.shutdown();
    }

    ExecutorService pool = Executors.newCachedThreadPool();
    
    ObjectBasedTableModel<Download> tableModel;

    public DownloadManager() {
        initComponents();
        DefaultTableModel m;
        tableModel = new ObjectBasedTableModel<Download>() {
            @Override
            public String[] columns() {
                return new String[] {"Name", "Progress"};
            }

            @Override
            public Object get(Download o, int columnIndex) {
                switch(columnIndex) {
                    case 0:
                        return o.name;
                    case 1:
                        float percent = (o.progress * 100) / o.size;
                        return (percent >= 0 ? percent + "%" : '?');
                    case 2:
                        return o.size;
                    default:
                        return null;
                }
            }
        };
        jTable1.setModel(tableModel);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jScrollPane1.setViewportView(jTable1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    // End of variables declaration//GEN-END:variables

}
