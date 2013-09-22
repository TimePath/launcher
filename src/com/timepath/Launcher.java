package com.timepath;

import java.awt.EventQueue;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author TimePath
 */
public class Launcher extends JFrame {

    private static long start = System.currentTimeMillis();

    private static final Logger LOG = Logger.getLogger(Launcher.class.getName());

    private static final Preferences prefs = Preferences.userRoot().node("timepath");

    private static final String progDir = "bin";

    private static String updateFile = "update.tmp";

    private static boolean runningTemp = false;

    //<editor-fold defaultstate="collapsed" desc="Debugging">
    private static final File logFile;

    private static Level consoleLevel = Level.INFO;

    private static Level logfileLevel = Level.INFO;

    static {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable thrwbl) {
                Logger.getLogger(thread.getName()).log(Level.SEVERE, "Uncaught Exception", thrwbl);
            }
        });

        try {
            consoleLevel = Level.parse(prefs.get("consoleLevel", "FINE"));
        } catch(IllegalArgumentException ex) {
        }
        try {
            logfileLevel = Level.parse(prefs.get("logfileLevel", "FINE"));
        } catch(IllegalArgumentException ex) {
        }
        Level packageLevel = consoleLevel;
        if(consoleLevel != Level.OFF && logfileLevel != Level.OFF) {
            if(logfileLevel.intValue() > consoleLevel.intValue()) {
                packageLevel = logfileLevel;
            }
        }
        Logger.getLogger("com.timepath").setLevel(packageLevel);

        SimpleFormatter consoleFormatter = new SimpleFormatter();
        SimpleFormatter fileFormatter = new SimpleFormatter();

        if(consoleLevel != Level.OFF) {
            Handler[] hs = Logger.getLogger("").getHandlers();
            for(Handler h : hs) {
                if(h instanceof ConsoleHandler) {
                    h.setLevel(consoleLevel);
                    h.setFormatter(consoleFormatter);
                }
            }
        }

        if(logfileLevel != Level.OFF) {
            logFile = new File(currentFile().getParentFile(),
                               "logs/log_" + System.currentTimeMillis() / 1000 + ".txt");
            try {
                logFile.getParentFile().mkdirs();
                FileHandler fh = new FileHandler(logFile.getPath(), 0, 1, false);
                fh.setLevel(logfileLevel);
                fh.setFormatter(fileFormatter);
                Logger.getLogger("").addHandler(fh);
                LOG.log(Level.INFO, "Logging to {0}", logFile.getPath());
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(SecurityException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        } else {
            logFile = null;
        }
        LOG.log(Level.INFO, "Console level: {0}", consoleLevel);
        LOG.log(Level.INFO, "Logfile level: {0}", logfileLevel);
    }
    //</editor-fold>

    public static void main(String... args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - start);
        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        LOG.log(Level.INFO, "Current version = {0}", myVer);
        File current = currentFile();
        LOG.log(Level.INFO, "Current file = {0}", current);
        File cwd = current.getParentFile().getAbsoluteFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd);
        File update = new File(cwd, updateFile);
        if(update.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", update);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if(!current.equals(update)) {
                try {
                    File updateChecksum = new File(update.getPath() + ".MD5");
                    BufferedReader is = new BufferedReader(new InputStreamReader(
                            new BufferedInputStream(new FileInputStream(updateChecksum))));
                    String expectedMd5 = is.readLine();
                    is.close();
                    LOG.log(Level.INFO, "Expecting checksum = {0}", expectedMd5);

                    String md5 = checksum(update, "MD5");
                    LOG.log(Level.INFO, "Actual checksum = {0}", md5);
                    if(!md5.equals(expectedMd5)) {
                        updateChecksum.delete();
                        update.delete();
                        throw new Exception("Corrupt update file");
                    } else {
                        ArrayList<String> cmds = new ArrayList<String>();
                        cmds.add("-jar");
                        cmds.add(update.getPath());
                        cmds.add("-u");
                        cmds.add(update.getPath());
                        cmds.add(current.getPath());
                        fork(update.getPath(), cmds, null);
                        System.exit(0);
                    }
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            //</editor-fold>
        }

        //<editor-fold defaultstate="collapsed" desc="on update detected restart">
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
                    updateFile = destFile.getName();// Can continue running from temp file
                    runningTemp = true;
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        //</editor-fold>

        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - start);
        boolean customLaf = false;
        //<editor-fold defaultstate="collapsed" desc="Look and feel setting code">
        if(customLaf) {
            LOG.log(Level.INFO, "Setting theme...");
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
            LOG.log(Level.INFO, "Set theme at {0}ms", System.currentTimeMillis() - start);
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
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Launcher l = new Launcher();
                l.pack();
                l.setLocationRelativeTo(null);
                l.setVisible(true);
            }
        };
        try {
            EventQueue.invokeAndWait(r);
        } catch(Exception ex) {
            Logger.getLogger(Launcher.class.getName()).log(Level.SEVERE, null, ex);
            EventQueue.invokeLater(r);
        }
    }

    private DefaultListModel listM = null;

    public Launcher() {
        //<editor-fold defaultstate="collapsed" desc="UI">
        initComponents();

        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(!e.getValueIsAdjusting()) {
                    return;
                }
                Project p = (Project) list.getSelectedValue();
                displayChangelog(p);
            }
        });

        launchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Project p = (Project) list.getSelectedValue();
                start(p);
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

        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - start);
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Load list">
        new Thread(new Runnable() {
            public void run() {
                InputStream is = null;
                File f = new File(System.getProperty("user.home") + "/Dropbox/Public/projects.xml");
                if(f.exists()) {
                    try {
                        is = new FileInputStream(f);
                    } catch(FileNotFoundException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
                if(is == null) {
                    try {
                        long start = System.currentTimeMillis();
                        String s = "http://dl.dropboxusercontent.com/u/42745598/projects.xml";
                        LOG.log(Level.INFO, "Resolving...");
                        URL u = new URL(s);
                        LOG.log(Level.INFO, "Resolved at {0}ms", System.currentTimeMillis() - start);
                        LOG.log(Level.INFO, "Opening...");
                        URLConnection c = u.openConnection();
                        LOG.log(Level.INFO, "Opened at {0}ms", System.currentTimeMillis() - start);
                        LOG.log(Level.INFO, "Streaming...");
                        is = c.getInputStream();
                        LOG.log(Level.INFO, "Stream opened at {0}ms",
                                System.currentTimeMillis() - start);
                    } catch(IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
                LOG.log(Level.INFO, "Parsing...");
                listM = parseXML(is);
                LOG.log(Level.INFO, "Parsed at {0}ms", System.currentTimeMillis() - start);
            }
        }).start();
        //</editor-fold>
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        LOG.log(Level.INFO, "Visible at {0}ms", System.currentTimeMillis() - start);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                list.setModel(listM);
                LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - start);
            }
        });
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

    private static final long myVer = getVer();

    private static File currentFile() {
        String encoded = Launcher.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            return new File(URLDecoder.decode(encoded, "UTF-8"));
        } catch(UnsupportedEncodingException ex) {
            LOG.log(Level.WARNING, null, ex);
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

    private static String checksum(File f, String method) throws IOException,
                                                                 NoSuchAlgorithmException {
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
        boolean ret;
        InputStream is = null;
        try {
            LOG.log(Level.INFO, "Downloading {0} to {1}", new Object[] {u, f});
            is = new BufferedInputStream(u.openStream());
            f.mkdirs();
            f.delete();
            f.createNewFile();
            FileOutputStream fos = new FileOutputStream(f);
            byte[] buffer = new byte[10240]; // 10K
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

    private static HashSet<Project> depends(Project parent) {
        HashSet<Project> h = new HashSet<Project>();
        for(Project p : parent.depends) {
            h.addAll(depends(p));
        }
        if(parent.downloadURL != null) {
            h.add(parent);
        }
        return h;
    }

    private static void start(Project run) {
        if(run == null) { // List hasn't loaded yet
            return;
        }
        HashSet<Project> ps = depends(run);
        LOG.log(Level.INFO, "Download list: {0}", ps.toString());
        for(Project p : ps) {
            File f = p.getFile();
            boolean latest = false;
            if(f.exists()) {
                latest = p.isLatest();
            }
            if(!latest) {
                LOG.log(Level.INFO, "Fetching {0}", p);
                try {
                    URL u = new URL(p.downloadURL);
                    download(u, f);
                    if(p.self && !runningTemp) {
                        download(new URL(p.checksumURL), new File(f.getPath() + ".MD5"));
                    }
                    LOG.log(Level.INFO, "{0} is up to date", p);
                } catch(MalformedURLException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        if(run.self) {
            JOptionPane.showMessageDialog(null, "Restart to apply", "Update downloaded",
                                          JOptionPane.INFORMATION_MESSAGE, null);
        } else {
            LOG.log(Level.INFO, "Starting {0} ({1})",
                    new Object[] {run, run.main});
            if(run.main == null) { // TODO
                LOG.log(Level.SEVERE, "Manifest detection not implemented ({0})", run);
                return;
            }
            try {
                URL[] urls = run.classPath().toArray(new URL[0]);
                LOG.log(Level.INFO, "Classpath = {0}", Arrays.toString(urls));
                URLClassLoader loader = new URLClassLoader(urls, Launcher.class.getClassLoader());
                Class clazz = loader.loadClass(run.main);
                Method m = clazz.getMethod("main", String[].class);
                String[] ar = run.args.toArray(new String[0]);
                m.invoke(clazz.newInstance(), (Object) ar);
            } catch(Exception ex) {
                LOG.log(Level.SEVERE, "Unable to start {0}:", run);
                LOG.log(Level.SEVERE, null, ex);
            }
        }
    }

    private static void fork(String mainJar, List<String> args, String main) {
        try {
            String javaBin = System.getProperty("java.home") + File.separator + progDir + File.separator + "java";

            ArrayList<String> cmd = new ArrayList<String>();
            cmd.add(javaBin);
            if(args != null) {
                cmd.addAll(args);
            } else {
                if(main == null) {
                    cmd.add("-jar");
                    cmd.add(mainJar);
                } else {
                    cmd.add("-cp");
                    File cp = new File(mainJar).getParentFile();
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
            ProcessBuilder process = new ProcessBuilder(exec);
            process.start();
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Changelog">
    private static HashMap<String, String> initPageCache() {
        HashMap<String, String> m = new HashMap<String, String>();
        m.put(null, "No changelog available");
        return m;
    }

    private static HashMap<String, String> pages = initPageCache();

    private static String loadPage(String s) throws IOException {
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
        if(p == null) {
            return;
        }
        if(p.jEditorPane == null) {
            p.jEditorPane = new JEditorPane("text/html", "");
            p.jEditorPane.setEditable(false);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        p.changelogData = loadPage(p.newsfeedURL);
                        p.jEditorPane.setText(p.changelogData);
                    } catch(IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        }
        textScrollPane.setViewportView(p.jEditorPane);
    }
//</editor-fold>
//<editor-fold defaultstate="collapsed" desc="Parsing">
    private static String getAttribute(Node n, String key) {
        Element e = (Element) n;
        Node child = last(getElements(key, n));
        if(child != null) {
            return child.getNodeValue();
        } else if(e.getAttributeNode(key) != null) {
            return e.getAttributeNode(key).getValue();
        } else {
            return null;
        }
    }

    private static <E> E last(ArrayList<E> arr) {
        if(arr == null || arr.isEmpty()) {
            return null;
        } else {
            return arr.get(arr.size() - 1);
        }
    }

    private static ArrayList<Node> get(Node parent, short nodeType) {
        ArrayList<Node> al = new ArrayList<Node>();
        if(parent.hasChildNodes()) {
            NodeList nodes = parent.getChildNodes();
            for(int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if(n.getNodeType() == nodeType) {
                    al.add(n);
                }
            }
        }
        return al;
    }

    private static ArrayList<Node> getElements(String eval, Node root) {
        String[] path = eval.split("/");
        ArrayList<Node> nodes = new ArrayList<Node>();
        nodes.add(root);
        for(String part : path) {
            ArrayList<Node> repl = new ArrayList<Node>();
            for(Node scan : nodes) {
                for(Node n : get(scan, Node.ELEMENT_NODE)) {
                    if(n.getNodeName().equals(part)) {
                        repl.add(n);
                    }
                }
            }
            nodes = repl;
        }
        return nodes;
    }

    private static String printTree(Node root, int depth) {
        StringBuilder sb = new StringBuilder();
        String str = root.getNodeName();
        String spacing = "";
        if(depth > 0) {
            spacing = String.format("%-" + depth * 4 + "s", "");
        }
        if(root.hasAttributes()) {
            NamedNodeMap attribs = root.getAttributes();
            for(int i = attribs.getLength() - 1; i >= 0; i--) {
                str += " " + attribs.item(i).getNodeName() + "=\"" + attribs.item(i).getNodeValue() + "\"";
            }
        }
        ArrayList<Node> elements = get(root, Node.ELEMENT_NODE);
        sb.append(depth).append(": ").append(spacing).append("<").append(str).append(
                elements.isEmpty() ? "/" : "").append(">\n");
        for(Node n : elements) {
            sb.append(printTree(n, depth + 1));
        }
        if(!elements.isEmpty()) {
            sb.append(depth).append(": ").append(spacing).append("</").append(root.getNodeName()).append(
                    ">\n");
        }
        return sb.toString();
    }

    private static HashMap<String, Project> libs = new HashMap<String, Project>();

    private static DefaultListModel parseXML(InputStream is) {
        DefaultListModel listM = new DefaultListModel();
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Node root = docBuilder.parse(new BufferedInputStream(is));

            LOG.log(Level.FINER, "\n{0}", printTree(root, 0));

            String[] nodes = {"self", "libs", "programs"};
            for(String n : nodes) {
                ArrayList<Node> programs = getElements("root/" + n + "/entry", root);
                for(Node entry : programs) {
                    //<editor-fold defaultstate="collapsed" desc="Parse">
                    Project p = new Project();

                    p.name = getAttribute(entry, "name");

                    String depends = getAttribute(entry, "depends");
                    if(depends != null) {
                        String[] dependencies = depends.split(",");
                        for(String s : dependencies) {
                            p.depends.add(libs.get(s));
                        }
                    }

                    p.file = getAttribute(entry, "file");

                    Node java = last(getElements("java", entry));
                    if(java != null) {
                        p.main = getAttribute(java, "main");
                        p.args = argParse(getAttribute(java, "args"));
                    }

                    Node news = last(getElements("newsfeed", entry));
                    if(news != null) {
                        p.newsfeedURL = getAttribute(news, "url");
                    }

                    Node download = last(getElements("download", entry));
                    if(download != null) {
                        p.downloadURL = getAttribute(download, "url");
                    }

                    Node checksum = last(getElements("checksum", entry));
                    if(checksum != null) {
                        p.checksumURL = getAttribute(checksum, "url");
                    }
                    //</editor-fold>
                    if(n.equals(nodes[0])) {
                        p.self = true;
                        listM.addElement(p);
                    } else if(n.equals(nodes[1])) {
                        libs.put(p.name, p);
                    } else if(n.equals(nodes[2])) {
                        listM.addElement(p);
                    }
                }
            }
        } catch(Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return listM;
    }

    private static List<String> argParse(String cmd) {
        if(cmd == null) {
            return null;
        }
        return Arrays.asList(cmd.split(" "));


    }

    private static class Project {

        private String name;

        private String newsfeedURL;

        private String file;

        public File getFile() {
            if(self) {
                return new File(updateFile);
            }
            if(file == null) {
                return null;
            }
            return new File(progDir, file);
        }

        private String downloadURL;

        private String checksumURL;

        private boolean self;

        private List<String> args;

        private String main;

        private String changelogData;

        private JEditorPane jEditorPane;

        private HashSet<Project> depends = new HashSet<Project>();

        @Override
        public String toString() {
            return name;// + (!depends.isEmpty() ? (" " + depends.toString()) : "");
        }

        private HashSet<URL> classPath() {
            HashSet<URL> h = new HashSet<URL>();
            for(Project p : depends) {
                h.addAll(p.classPath());
            }
            File f = this.getFile();
            if(f != null) {
                try {
                    URL u = f.toURI().toURL();
                    h.add(u);
                } catch(MalformedURLException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            return h;
        }

        /**
         *
         * @return false if not up to date, true if up to date or offline
         */
        private boolean isLatest() {
            File f = getFile();
            if(f.exists()) {
                LOG.log(Level.INFO, "Checking {0} for updates...", this);
                try {
                    String checksum = checksum(f, "MD5");
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            new URL(checksumURL).openStream()));
                    String expected = br.readLine();
                    if(!checksum.equals(expected)) {
                        return false;
                    }
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            return true;
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton launchButton;
    private javax.swing.JList list;
    private javax.swing.JScrollPane listScrollPane;
    private javax.swing.JPanel sidePanel;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JScrollPane textScrollPane;
    // End of variables declaration//GEN-END:variables

}
