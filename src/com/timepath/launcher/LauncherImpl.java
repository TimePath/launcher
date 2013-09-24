package com.timepath.launcher;

import com.timepath.launcher.DownloadManager.Download;
import java.awt.EventQueue;
import java.awt.Window;
import java.io.*;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/**
 *
 * @author TimePath
 */
public class LauncherImpl extends Launcher {

    private static long start = System.currentTimeMillis();

    private static final Logger LOG = Logger.getLogger(LauncherImpl.class.getName());

    private static final Preferences settings = Preferences.userRoot().node("timepath");

    private static HashMap<String, Program> libs = new HashMap<String, Program>();

    private static final String progDir = settings.get("progStoreDir", "bin");

    private static String updateName = "update.tmp";

    private static Program self;

    public DefaultListModel listM = null;

    public LauncherImpl() {
        super();
    }

    @Override
    public void news(final Program p) {
        if(p.loading) {
            return;
        }
        p.loading = true;

        String str = "Loading...";
        if(p.newsfeedURL == null) {
            str = "No newsfeed available";
        }
        JEditorPane none = new JEditorPane("text/html", str);
        none.setEditable(false);
        display(none);

        if(p.newsfeedURL != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String s = Utils.loadPage(new URL(p.newsfeedURL));
                        final JEditorPane j = new JEditorPane("text/html", s);
                        j.setEditable(false);
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                display(j);
                            }
                        });
                    } catch(IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }).start();
        }
    }

    @Override
    public void start(final Program run) {
        new Thread(new Runnable() {
            public void run() {
                HashSet<Program> ps = depends(run);
                LOG.log(Level.INFO, "Download list: {0}", ps.toString());

                final ArrayList<Program> programs = new ArrayList<Program>();
                programs.addAll(ps);
                final ArrayList<Future> futures = new ArrayList<Future>();
                for(Program p : programs) {
                    File f = getFile(p);
                    boolean latest = false;
                    if(f.exists()) {
                        latest = isLatest(p);
                    }
                    if(!latest) {
                        LOG.log(Level.INFO, "Fetching {0}", p);
                        futures.add(download(p));
                    }
                }
                new Thread(new Runnable() {
                    public void run() {
                        for(int i = 0; i < futures.size(); i++) {
                            Program p = programs.get(i);
                            Future future = futures.get(i);
                            File f = getFile(p);
                            try {
                                future.get();
                                if(p.self && !Utils.runningTemp) {
                                    Utils.download(new URL(p.checksumURL), new File(
                                            f.getPath() + ".MD5"));
                                }
                                LOG.log(Level.INFO, "{0} is up to date", p);
                            } catch(MalformedURLException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            } catch(InterruptedException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            } catch(ExecutionException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                        if(run.self) {
                            JOptionPane.showMessageDialog(null, "Restart to apply",
                                                          "Update downloaded",
                                                          JOptionPane.INFORMATION_MESSAGE, null);
                        } else {
                            LOG.log(Level.INFO, "Starting {0} ({1})",
                                    new Object[] {run, run.main});
                            if(run.main == null) { // TODO
                                LOG.log(Level.SEVERE, "Manifest detection not implemented ({0})",
                                        run);
                                return;
                            }
                            try {
                                Utils.start(run.main, run.args.toArray(new String[0]),
                                            classPath(run).toArray(
                                        new URL[0]));
                                for(Window w : Window.getWindows()) { // TODO: This will probably come back to haunt me later
                                    LOG.log(Level.INFO, "{0}  {1}", new Object[] {w,
                                                                                  w.isDisplayable()});
                                    if(!w.isVisible()) {
                                        w.dispose();
                                    }
                                }
                            } catch(Exception ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }).start();
            }
        }).start();
    }

    //<editor-fold defaultstate="collapsed" desc="Debugging">
    private static final File logFile;

    private static Level consoleLevel = Level.INFO;

    private static Level logfileLevel = Level.INFO;

    static {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception", e);
            }
        });

        try {
            consoleLevel = Level.parse(settings.get("consoleLevel", "FINE"));
        } catch(IllegalArgumentException ex) {
        }
        try {
            logfileLevel = Level.parse(settings.get("logfileLevel", "FINE"));
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
            logFile = new File(Utils.currentFile.getParentFile(),
                               "logs/log_" + System.currentTimeMillis() / 1000 + ".txt");
            try {
                logFile.getParentFile().mkdirs();
                FileHandler fh = new FileHandler(logFile.getPath(), 0, 1, false); // I have to set this up to be able to recall it
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

    public static void main(String[] args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - start);
        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        Utils.checkForUpdate(updateName, args);
        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - start);

//        switch(OS.get()) {
//            case OSX:
//                UIManager.installLookAndFeel("Quaqua", "ch.randelshofer.quaqua.QuaquaLookAndFeel");
//                break;
//            case Linux:
//                UIManager.installLookAndFeel("GTK extended", "org.gtk.laf.extended.GTKLookAndFeelExtended");
//                break;
//        }

        //<editor-fold defaultstate="collapsed" desc="Look and feel setting code">
        String envTheme = System.getProperty("swing.defaultlaf");
        String usrTheme = settings.get("laf", null);
        //<editor-fold defaultstate="collapsed" desc="Validate user theme">
        if(usrTheme != null) {
            try {
                Class.forName(usrTheme);
            } catch(ClassNotFoundException ex) {
                LOG.log(Level.WARNING, "Invalid user theme: {0}", usrTheme);
                usrTheme = null;
                settings.remove("laf");
            }
        }
        //</editor-fold>
        if(usrTheme == null) {
            //<editor-fold defaultstate="collapsed" desc="Detect a default">
            HashMap<String, String> laf = new HashMap<String, String>();
            for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                laf.put(info.getName(), info.getClassName());
            }
            // In order of preference
            String[] test = {
                "Nimbus",
                UIManager.getCrossPlatformLookAndFeelClassName(),
                UIManager.getSystemLookAndFeelClassName(),};
            for(String s : test) {
                if(laf.containsKey(s)) {
                    usrTheme = laf.get(s);
                    settings.put("laf", usrTheme);
                    LOG.log(Level.CONFIG, "Set default user theme: {0}", usrTheme);
                    break;
                }
            }
            //</editor-fold>
        }

        String theme1 = envTheme != null ? envTheme : usrTheme; // envTheme authorative
        String theme2 = usrTheme == null ? envTheme : usrTheme; // usrTheme authorative
        String theme = theme1; // TODO: add preference

        try {
            UIManager.setLookAndFeel(theme);
            LOG.log(Level.INFO, "Set theme at {0}ms", System.currentTimeMillis() - start);
        } catch(Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Improve native LaF">
//        if(UIManager.getLookAndFeel().isNativeLookAndFeel()) {
//            try {
//                LOG.log(Level.INFO, "Adding swing enhancements for {0}", new Object[] {OS.get()});
//                if(OS.isMac()) {
//                    UIManager.setLookAndFeel("ch.randelshofer.quaqua.QuaquaLookAndFeel"); // Apply quaqua if available
//                } else if(OS.isLinux()) {
//                    if(UIManager.getLookAndFeel().getClass().getName().equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
//                        GtkFixer.installGtkPopupBugWorkaround(); // Apply clearlooks java menu fix if applicable
//                        UIManager.setLookAndFeel("org.gtk.laf.extended.GTKLookAndFeelExtended"); // Apply extended gtk theme is available. http://danjared.wordpress.com/2012/05/21/mejorando-la-integracion-de-javaswing-con-gtk/
//                    }
//                }
//                LOG.info("All swing enhancements installed");
//            } catch(InstantiationException ex) {
//                Logger.getLogger(HUDEditor.class.getName()).log(Level.SEVERE, null, ex);
//            } catch(IllegalAccessException ex) {
//                Logger.getLogger(HUDEditor.class.getName()).log(Level.SEVERE, null, ex);
//            } catch(UnsupportedLookAndFeelException ex) {
//                Logger.getLogger(HUDEditor.class.getName()).log(Level.SEVERE, null, ex);
//            } catch(ClassNotFoundException ex) {
////                Logger.getLogger(EditorFrame.class.getName()).log(Level.INFO, null, ex);
//                LOG.warning("Unable to load enhanced L&F");
//            }
//        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="UI creation">
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                final LauncherImpl launcher = new LauncherImpl();

                LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - start);

                //<editor-fold defaultstate="collapsed" desc="Load list">
                new Thread(new Runnable() {
                    public void run() {
                        InputStream is = null;
                        File f = new File(
                                System.getProperty("user.home") + "/Dropbox/Public/projects.xml");
                        if(f.exists()) {
                            try {
                                is = new FileInputStream(f);
                            } catch(FileNotFoundException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
//                is = null;
                        if(is == null) {
                            try {
                                long start = System.currentTimeMillis();
                                String s = "http://dl.dropboxusercontent.com/u/42745598/projects.xml";
                                LOG.log(Level.INFO, "Resolving...");
                                URL u = new URL(s);
                                LOG.log(Level.INFO, "Resolved at {0}ms",
                                        System.currentTimeMillis() - start);
                                LOG.log(Level.INFO, "Connecting...");
                                URLConnection c = u.openConnection();
                                LOG.log(Level.INFO, "Connected at {0}ms",
                                        System.currentTimeMillis() - start);
                                LOG.log(Level.INFO, "Streaming...");
                                is = c.getInputStream();
                            } catch(IOException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                        LOG.log(Level.INFO, "Stream opened at {0}ms",
                                System.currentTimeMillis() - start);
                        LOG.log(Level.INFO, "Parsing...");
                        launcher.listM = parseXML(is);
                        LOG.log(Level.INFO, "Parsed at {0}ms", System.currentTimeMillis() - start);
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                launcher.setListModel(launcher.listM);
                                LOG.log(Level.INFO, "Listing at {0}ms",
                                        System.currentTimeMillis() - start);
                            }
                        });
                    }
                }).start();
                //</editor-fold>

                launcher.pack();
                launcher.setLocationRelativeTo(null);
                launcher.setVisible(true);
                LOG.log(Level.INFO, "Visible at {0}ms", System.currentTimeMillis() - start);
            }
        });
        //</editor-fold>
    }

    private static DefaultListModel parseXML(InputStream is) {
        DefaultListModel listM = new DefaultListModel();
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Node root = docBuilder.parse(new BufferedInputStream(is));

            LOG.log(Level.FINEST, "\n{0}", XMLUtils.printTree(root, 0));

            String[] nodes = {"self", "libs", "programs"};
            for(String n : nodes) {
                ArrayList<Node> programs = XMLUtils.getElements("root/" + n + "/entry", root);
                for(Node entry : programs) {
                    //<editor-fold defaultstate="collapsed" desc="Parse">
                    Program p = new Program();

                    p.name = XMLUtils.getAttribute(entry, "name");

                    String depends = XMLUtils.getAttribute(entry, "depends");
                    if(depends != null) {
                        String[] dependencies = depends.split(",");
                        for(String s : dependencies) {
                            p.depends.add(libs.get(s));
                        }
                    }

                    p.file = XMLUtils.getAttribute(entry, "file");

                    Node java = Utils.last(XMLUtils.getElements("java", entry));
                    if(java != null) {
                        p.main = XMLUtils.getAttribute(java, "main");
                        p.args = Utils.argParse(XMLUtils.getAttribute(java, "args"));
                    }

                    Node news = Utils.last(XMLUtils.getElements("newsfeed", entry));
                    if(news != null) {
                        p.newsfeedURL = XMLUtils.getAttribute(news, "url");
                    }

                    Node download = Utils.last(XMLUtils.getElements("download", entry));
                    if(download != null) {
                        p.downloadURL = XMLUtils.getAttribute(download, "url");
                    }

                    Node checksum = Utils.last(XMLUtils.getElements("checksum", entry));
                    if(checksum != null) {
                        p.checksumURL = XMLUtils.getAttribute(checksum, "url");
                    }
                    //</editor-fold>
                    if(n.equals(nodes[0])) {
                        p.self = true;
                        self = p;
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

    private static HashSet<Program> depends(Program parent) {
        HashSet<Program> h = new HashSet<Program>();
        for(Program p : parent.depends) {
            h.addAll(depends(p));
        }
        if(parent.downloadURL != null) {
            h.add(parent);
        }
        return h;
    }

    public File getFile(Program p) {
        if(p.self) {
            return new File(updateName);
        }
        if(p.file == null) {
            return null;
        }
        return new File(progDir, p.file);
    }

    private Future<?> download(Program p) {
        try {
            return download(new Download(p.name, new URL(p.downloadURL), getFile(p)));
        } catch(MalformedURLException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     *
     * @return false if not up to date, true if up to date or offline
     */
    boolean isLatest(Program p) {
        File f = getFile(p);
        if(f.exists()) {
            LOG.log(Level.INFO, "Checking {0} for updates...", p);
            try {
                String checksum = Utils.checksum(f, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        new URL(p.checksumURL).openStream()));
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

    HashSet<URL> classPath(Program run) {
        HashSet<URL> h = new HashSet<URL>();
        for(Program p : run.depends) {
            h.addAll(classPath(p));
        }
        File f = getFile(run);
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

}
