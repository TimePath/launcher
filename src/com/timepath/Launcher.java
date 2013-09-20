package com.timepath;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 *
 * @author timepath
 */
public class Launcher extends javax.swing.JFrame {

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    public static final long myVer = getVer();

    private static long getVer() {
        String impl = Launcher.class.getPackage().getImplementationVersion();
        if(impl == null) {
            return 0;
        }
        return Long.parseLong(impl);
    }

    public static File currentFile() {
        String encoded = Launcher.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            return new File(URLDecoder.decode(encoded, "UTF-8"));
        } catch(UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        String ans = System.getProperty("user.dir") + File.separator;
        String cmd = System.getProperty("sun.java.command");
        int idx = cmd.lastIndexOf(File.separator);
        if(idx != -1) {
            cmd = cmd.substring(0, idx + 1);
        } else {
            cmd = "";
        }
        ans += cmd;
        return new File(ans);
    }

    private static String getAttribute(Node n, String key) {
        Element p = (Element) n;
        String val = p.getAttributeNode(key) != null ? p.getAttributeNode(key).getValue().trim() : null;
        NodeList nodes = p.getElementsByTagName(key);
        if(p.getNodeType() == Node.ELEMENT_NODE && nodes.getLength() > 0) {
            Element lastElement = (Element) nodes.item(nodes.getLength() - 1);
            NodeList children = lastElement.getChildNodes();
            val = ((Node) children.item(children.getLength() - 1)).getNodeValue();
        }
        return val;
    }

    private static String hash(File f, String method) throws IOException, NoSuchAlgorithmException {
        FileChannel c = new RandomAccessFile(f, "r").getChannel();
        MappedByteBuffer buf = c.map(FileChannel.MapMode.READ_ONLY, 0, c.size());
        StringBuilder sb = new StringBuilder();
        MessageDigest md = MessageDigest.getInstance(method);
        md.update(buf);
        byte[] b = md.digest();
        for(int i = 0; i < b.length; i++) {
            sb.append(Integer.toString((b[i] & 0xFF) + 256, 16).substring(1));
        }
        return sb.toString();
    }

    private static boolean download(URL u, File f) {
        try {
            f = f.getCanonicalFile();
        } catch(IOException ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
        }
        boolean ret;
        InputStream is = null;
        try {
            LOG.log(Level.INFO, "Downloading {0} to {1}", new Object[] {u, f});
            is = new BufferedInputStream(u.openStream());
            f.mkdirs();
            f.delete();
            f.createNewFile();
            FileOutputStream fos = new FileOutputStream(f);
            byte[] buffer = new byte[10240];
            int read;
            while((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();
            ret = true;
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            ret = false;
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        return ret;
    }

    private DefaultListModel/*
             * <Project>
             */ listModel;

    /**
     * Creates new form Launcher
     */
    public Launcher() {
        long start = System.currentTimeMillis();
        initComponents();

        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(e.getValueIsAdjusting()) {
                    return;
                }
                Project p = (Project) list.getSelectedValue();
                jEditorPane1.setContentType("text/html");
                if(p.changelogData == null) {
                    loadPageForProject(p);
                } else {
                    jEditorPane1.setText(p.changelogData);
                }
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Project p = (Project) list.getSelectedValue();
                if(e.getClickCount() >= 2) {
                    start(p);
                }
            }
        });

        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Project p = (Project) list.getSelectedValue();
                if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    start(p);
                }
            }
        });

        LOG.log(Level.INFO, "Created UI in {0}ms", System.currentTimeMillis() - start);

        new Thread(new Runnable() {
            public void run() {
                InputStream is = null;
                if(currentFile().isDirectory()) {
                    try {
                        is = new FileInputStream(
                                System.getProperty("user.home") + "/Dropbox/Public/projects.xml");
                    } catch(FileNotFoundException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
                if(is == null) {
                    try {
                        LOG.log(Level.INFO, "Connecting...");
                        long start = System.currentTimeMillis();
                        String s = "https://raw.github.com/TimePath/launcher/master/src/projects.xml";
                        URL u = new URL(s);
                        URLConnection c = u.openConnection();
                        c.connect();
                        LOG.log(Level.INFO, "Connected in {0}ms", System.currentTimeMillis() - start);
                        is = c.getInputStream();
                    } catch(IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                        System.exit(1);
                    }
                }
                final DefaultListModel listM = parseXML(is);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        list.setModel(listM);
                    }
                });
            }
        }).start();
    }

    private void loadPageForProject(final Project p) {
        LOG.log(Level.INFO, "Loading {0}", p.changelog);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    jEditorPane1.setEnabled(false);
                    String s = p.changelog;
                    StringBuilder sb = new StringBuilder();

                    URL u = new URL(s);
                    URLConnection c = u.openConnection();
                    InputStream is = c.getInputStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while((line = r.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    r.close();
                    p.changelogData = sb.toString();
                    Launcher.this.jEditorPane1.setEnabled(true);
                    Launcher.this.jEditorPane1.setText(p.changelogData);
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }).start();
    }

    private static void start(Project p) {
        boolean upToDate = false;
        boolean valid = true;
        File f = new File("bin", p.local);
        try {
            f = f.getCanonicalFile();
        } catch(IOException ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
        }
        if(f.exists()) {
            try {
                String md5 = hash(f, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        new URL(p.upstream).openStream()));
                String expectedMd5 = br.readLine();
                upToDate = true;
            } catch(IOException ex) {
                Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            } catch(NoSuchAlgorithmException ex) {
                Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            valid = false;
        }
        if(!upToDate && (!f.exists() || f.canWrite())) {
            try {
                URL u = new URL(p.upstream);
                download(u, f);
                valid = true;
            } catch(MalformedURLException ex) {
                Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if(valid) {
            if(!p.self) {
                LOG.log(Level.SEVERE, "Starting {0}, latest version: {1}",
                        new Object[] {p, upToDate});
                start(f.getAbsolutePath(), p.args, p.main);
            } else {
                JOptionPane.showMessageDialog(null, "Restart to apply", "Update downloaded",
                                              JOptionPane.INFORMATION_MESSAGE, null);
            }
        } else {
            LOG.log(Level.SEVERE, "Unable to start {0}", p);
        }
    }

    private static void start(String s, List<String> args, String main) {
        try {
            final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

            final ArrayList<String> cmd = new ArrayList<String>();
            cmd.add(javaBin);
            if(args != null) {
                cmd.addAll(args);
            } else {
                if(main == null) {
                    cmd.add("-jar");
                    cmd.add(s);
                } else {
                    cmd.add("-cp");
                    File cp = new File(s).getParentFile();
                    String[] jars = cp.list(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            if(name.endsWith(".jar")) {
                                return true;
                            }
                            return false;
                        }
                    });
                    StringBuilder sb = new StringBuilder();
                    for(String jar : jars) {
                        sb.append(File.pathSeparator);
                        sb.append(cp.getPath()).append("/").append(jar);
                    }
                    cmd.add(sb.toString().substring(File.pathSeparator.length()));
                    cmd.add(main);
                }
            }
            String[] exec = new String[cmd.size()];
            cmd.toArray(exec);
            LOG.log(Level.INFO, "Invoking other: {0}", Arrays.toString(exec));
            final ProcessBuilder process = new ProcessBuilder(exec);
            process.start();
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        list = new javax.swing.JList();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jEditorPane1 = new javax.swing.JEditorPane();
        jButton1 = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("TimePath's program hub");

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(list);

        jSplitPane1.setLeftComponent(jScrollPane1);

        jPanel1.setLayout(new java.awt.BorderLayout());

        jEditorPane1.setEditable(false);
        jEditorPane1.setContentType("text/html"); // NOI18N
        jEditorPane1.setText("");
        jScrollPane2.setViewportView(jEditorPane1);

        jPanel1.add(jScrollPane2, java.awt.BorderLayout.CENTER);

        jButton1.setText("Launch");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1, java.awt.BorderLayout.SOUTH);

        jSplitPane1.setRightComponent(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 563, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );

        setBounds(0, 0, 573, 330);
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        Project p = (Project) list.getSelectedValue();
        start(p);
    }//GEN-LAST:event_jButton1ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        long start = System.currentTimeMillis();

        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        LOG.log(Level.INFO, "Current version = {0}", myVer);
        File current = currentFile();
        LOG.log(Level.INFO, "Current file = {0}", current);
        File cwd = current.getParentFile().getAbsoluteFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd);
        File update = new File(cwd, "update.tmp");
        if(update.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", update);
            if(!current.equals(update)) {
                try {
                    File updateChecksum = new File(update.getPath() + ".MD5");
                    BufferedReader is = new BufferedReader(new InputStreamReader(
                            new BufferedInputStream(new FileInputStream(updateChecksum))));
                    String expectedMd5 = is.readLine();
                    is.close();
                    LOG.log(Level.INFO, "Expecting checksum = {0}", expectedMd5);

                    String md5 = hash(update, "MD5");
                    LOG.log(Level.INFO, "Actual checksum = {0}", md5);
                    if(!md5.equals(expectedMd5)) {
                        updateChecksum.delete();
                        update.delete();
                        throw new Exception("Corrupt update file");
                    }
                    ArrayList<String> cmds = new ArrayList<String>();
                    cmds.add("-jar");
                    cmds.add(update.getPath());
                    cmds.add("-u");
                    cmds.add(update.getPath());
                    cmds.add(current.getPath());
                    start(update.getPath(), cmds, null);
                    System.exit(0);
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }

        for(int i = 0; i < args.length; i++) {
            if(args[i].equalsIgnoreCase("-u")) {
                try {
                    File sourceFile = new File(args[i + 1]);
                    File destFile = new File(args[i + 2]);
                    LOG.log(Level.INFO, "Updating {0}", destFile);
                    destFile.delete();
                    destFile.createNewFile();

                    FileChannel source = null;
                    FileChannel destination = null;
                    try {
                        source = new RandomAccessFile(sourceFile, "rw").getChannel();
                        destination = new RandomAccessFile(destFile, "rw").getChannel();
                        long position = 0;
                        long count = source.size();

                        source.transferTo(position, count, destination);
                    } finally {
                        if(source != null) {
                            source.close();
                        }
                        if(destination != null) {
                            destination.force(true);
                            destination.close();
                        }
                    }
                    new File(update.getPath() + ".MD5").delete();
                    sourceFile.deleteOnExit();
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }

        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - start);

        /*
         * Set the Nimbus look and feel
         */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        try {
            for(javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch(ClassNotFoundException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch(InstantiationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch(IllegalAccessException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch(javax.swing.UnsupportedLookAndFeelException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        //</editor-fold>
        try {
            /*
             * Create and display the form
             */
            java.awt.EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    Launcher l = new Launcher();
                    l.pack();
                    l.setLocationRelativeTo(null);
                    l.setVisible(true);
                }
            });
        } catch(InterruptedException ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
        } catch(InvocationTargetException ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JEditorPane jEditorPane1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JList list;
    // End of variables declaration//GEN-END:variables

    private List<String> argParse(String cmd) {
        if(cmd == null) {
            return null;
        }
        return Arrays.asList(cmd.split(" "));
    }

    private DefaultListModel parseXML(InputStream is) {
        DefaultListModel listM = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new BufferedInputStream(is));
            doc.getDocumentElement().normalize();

            listM = new DefaultListModel/*
                     * <Project>
                     */();

            Node self = doc.getElementsByTagName("self").item(0);
            Project s = new Project();
            s.self = true;
            s.name = getAttribute(self, "name");
            s.changelog = getAttribute(self, "changelog");
            s.upstream = getAttribute(self, "upstream");
            s.local = getAttribute(self, "local");
            s.hash = getAttribute(self, "md5");
//            s.args = argParse(getAttribute(self, "args"));
            s.main = getAttribute(self, "main");
            listM.addElement(s);

            NodeList programs = doc.getElementsByTagName("program");
            for(int i = 0; i < programs.getLength(); i++) {
                Node program = programs.item(i);
                Project p = new Project();
                p.name = getAttribute(program, "name");
                p.changelog = getAttribute(program, "changelog");
                if(p.changelog == null) {
                    p.changelogData = "No changelog available";
                }
                p.upstream = getAttribute(program, "upstream");
                p.local = getAttribute(program, "local");
                p.hash = getAttribute(program, "md5");
                p.args = argParse(getAttribute(program, "args"));
                p.main = getAttribute(program, "main");
                listM.addElement(p);
            }
        } catch(SAXParseException err) {
            LOG.log(Level.SEVERE, "** Parsing error" + ", line {0}, uri {1}", new Object[] {
                err.getLineNumber(),
                err.getSystemId()});
            LOG.log(Level.SEVERE, "{0}", err.getMessage());
        } catch(SAXException e) {
            Exception x = e.getException();
            ((x == null) ? e : x).printStackTrace();
        } catch(Throwable t) {
            t.printStackTrace();
        }
        return listM;
    }

    private class Project {

        private String name;

        private String changelog;

        private String local;

        private String upstream;

        private String hash;

        private boolean self;

        private List<String> args;

        private String main;

        private String changelogData;

        @Override
        public String toString() {
            return name;
        }

    }

}
