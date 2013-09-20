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
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
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

    private static long start = System.currentTimeMillis();

    public static void main(String... args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - start);
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
        boolean customLaf = false;
        //<editor-fold defaultstate="collapsed" desc="Look and feel setting code">
        if(customLaf) {
            LOG.log(Level.INFO, "Setting theme...");
            long theme = System.currentTimeMillis();
            try {
                for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
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
            LOG.log(Level.INFO, "Set theme in {0}ms", System.currentTimeMillis() - theme);
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch(ClassNotFoundException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(InstantiationException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(IllegalAccessException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(UnsupportedLookAndFeelException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>
        try {
            java.awt.EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {                    
                    Launcher l = new Launcher();
                    l.pack();
                    l.setLocationRelativeTo(null);
                    l.setVisible(true);
                    LOG.log(Level.INFO, "DONE: {0}ms", System.currentTimeMillis() - start);
                }
            });
        } catch(InterruptedException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch(InvocationTargetException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Creates new form Launcher
     */
    public Launcher() {
        initComponents();

        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(e.getValueIsAdjusting()) {
                    return;
                }
                Project p = (Project) list.getSelectedValue();
                displayChangelog(p);
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
                try {
                    long start = System.currentTimeMillis();
                    String s = "http://dl.dropboxusercontent.com/u/42745598/projects.xml";
                    LOG.log(Level.INFO, "Resolving...");
                    URL u = new URL(s);
                    long resolved = System.currentTimeMillis();
                    LOG.log(Level.INFO, "Resolved in {0}ms", resolved - start);
                    LOG.log(Level.INFO, "Opening...");
                    URLConnection c = u.openConnection();
                    long opened = System.currentTimeMillis();
                    LOG.log(Level.INFO, "Opened in {0}ms", opened - resolved);
                    is = c.getInputStream();
                    LOG.log(Level.INFO, "Streaming...");
                    long streaming = System.currentTimeMillis();
                    LOG.log(Level.INFO, "Streaming in {0}ms", streaming - opened);
                    long parsing = System.currentTimeMillis();
                    LOG.log(Level.INFO, "Parsing in {0}ms", parsing - streaming);
                    final DefaultListModel listM = parseXML(is);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            list.setModel(listM);
                        }
                    });
                    LOG.log(Level.INFO, "Total: {0}ms", System.currentTimeMillis() - start);
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }).start();
    }

    //<editor-fold defaultstate="collapsed" desc="Updating">
    //<editor-fold defaultstate="collapsed" desc="Self updating">
    private static long getVer() {
        String impl = Launcher.class.getPackage().getImplementationVersion();
        if(impl == null) {
            return 0;
        }
        return Long.parseLong(impl);
    }

    public static final long myVer = getVer();

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
    //</editor-fold>

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
            LOG.log(Level.SEVERE, null, ex);
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

    private static void start(Project p) {
        boolean upToDate = false;
        boolean valid = true;
        File f = new File("bin", p.local);
        try {
            f = f.getCanonicalFile();
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        if(f.exists()) {
            try {
                String md5 = hash(f, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        new URL(p.upstream).openStream()));
                String expectedMd5 = br.readLine();
                upToDate = true;
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(NoSuchAlgorithmException ex) {
                LOG.log(Level.SEVERE, null, ex);
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
                LOG.log(Level.SEVERE, null, ex);
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
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Changelog">
    private HashMap<String, String> initPageCache() {
        HashMap<String, String> m = new HashMap<String, String>();
        m.put(null, "No changelog available");
        return m;
    }

    private HashMap<String, String> pages = initPageCache();

    private String loadPage(String s) throws IOException {
        if(!pages.containsKey(s)) {
            LOG.log(Level.INFO, "Loading {0}", s);
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
            pages.put(s, sb.toString());
        }
        return pages.get(s);
    }

    private void displayChangelog(final Project p) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(p.jEditorPane == null) {
                        p.changelogData = loadPage(p.changelog);
                        p.jEditorPane = new JEditorPane("text/html", p.changelogData);
                        p.jEditorPane.setEditable(false);
                    }
                    Runnable r = new Runnable() {
                        public void run() {
                            textScrollPane.setViewportView(p.jEditorPane);
                            textScrollPane.getVerticalScrollBar().setValue(0);
                        }
                    };
                    try {
                        SwingUtilities.invokeAndWait(r);
                    } catch(Exception ex) {
                        LOG.log(Level.SEVERE, null, ex);
                        SwingUtilities.invokeLater(r);
                    }
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }).start();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Parsing">
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

        private JEditorPane jEditorPane;

        @Override
        public String toString() {
            return name;
        }

    }
    //</editor-fold>

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        splitPane = new javax.swing.JSplitPane();
        listScrollPane = new javax.swing.JScrollPane();
        list = new javax.swing.JList();
        sidePanel = new javax.swing.JPanel();
        textScrollPane = new javax.swing.JScrollPane();
        launchButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("TimePath's program hub");

        splitPane.setContinuousLayout(true);

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listScrollPane.setViewportView(list);

        splitPane.setLeftComponent(listScrollPane);

        sidePanel.setLayout(new java.awt.BorderLayout());
        sidePanel.add(textScrollPane, java.awt.BorderLayout.CENTER);

        launchButton.setText("Launch");
        launchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                launchButtonActionPerformed(evt);
            }
        });
        sidePanel.add(launchButton, java.awt.BorderLayout.SOUTH);

        splitPane.setRightComponent(sidePanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(splitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 563, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(splitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );

        setBounds(0, 0, 573, 330);
    }// </editor-fold>//GEN-END:initComponents

    private void launchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_launchButtonActionPerformed
        Project p = (Project) list.getSelectedValue();
        start(p);
    }//GEN-LAST:event_launchButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton launchButton;
    private javax.swing.JList list;
    private javax.swing.JScrollPane listScrollPane;
    private javax.swing.JPanel sidePanel;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JScrollPane textScrollPane;
    // End of variables declaration//GEN-END:variables

}
