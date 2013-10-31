package com.timepath.launcher;

import com.timepath.launcher.DownloadManager.Download;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.*;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.security.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class LauncherImpl extends Launcher {

    public static boolean debug = Utils.currentVersion == 0;

    private static final Logger LOG = Logger.getLogger(LauncherImpl.class.getName());

    private static Level consoleLevel = Level.INFO;

    private static HashMap<String, Program> libs = new HashMap<String, Program>();

    private static final File logFile;

    private static Level logfileLevel = Level.INFO;

    private static Program self;

    private static String updateName = "update.tmp";

    static long start = ManagementFactory.getRuntimeMXBean().getStartTime();

    static {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception", e);
            }
        });

        try {
            consoleLevel = Level.parse(Utils.settings.get("consoleLevel", "CONFIG"));
        } catch(IllegalArgumentException ex) {
        }
        Utils.settings.remove("consoleLevel"); // TEMP
        try {
            logfileLevel = Level.parse(Utils.settings.get("logfileLevel", "CONFIG"));
        } catch(IllegalArgumentException ex) {
        }
        Utils.settings.remove("logfileLevel"); // TEMP
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

        if(logfileLevel != Level.OFF && !debug) {
            logFile = new File(Utils.currentFile.getParentFile(),
                               "logs/log_" + System.currentTimeMillis() / 1000 + ".txt");
            try {
                logFile.getParentFile().mkdirs();
                final FileHandler fh = new FileHandler(logFile.getPath(), 0, 1, false); // I have to set this up to be able to recall it
                fh.setLevel(logfileLevel);
                fh.setFormatter(fileFormatter);
                Logger.getLogger("").addHandler(fh);
                LOG.log(Level.INFO, "Logging to {0}", logFile.getPath());
                try {
                    final URL u = logFile.toURI().toURL();
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            fh.flush();
                            fh.close();
                            Utils.logThread("exit", "launcher/" + Utils.currentVersion + "/logs",
                                            Utils.loadPage(u)).run();
                        }
                    };
                    Runtime.getRuntime().addShutdownHook(t);
                } catch(MalformedURLException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
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

        Policy.setPolicy(new Policy() {
            @Override
            public PermissionCollection getPermissions(CodeSource codesource) {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return (perms);
            }

            @Override
            public void refresh() {
            }
        });

        System.setSecurityManager(null);
    }

    public static File getFile(Program p) {
        if(p.self) {
            return new File(updateName);
        }
        if(p.file == null) {
            return null;
        }
        return new File(Utils.progDir, p.file);
    }

    public static void main(String... args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - start);
        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        Utils.checkForUpdate(updateName, args);
        started();
        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - start);

        Utils.lookAndFeel();
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                initUI();
            }

        });
    }

    private static HashSet<Program> depends(Program parent) {
        HashSet<Program> h = new HashSet<Program>();
        for(Program p : parent.depends) {
            h.addAll(depends(p));
        }
        if(parent.downloadURLs != null) {
            h.add(parent);
        }
        return h;
    }

    private static void initUI() {
        final LauncherImpl launcher = new LauncherImpl();

        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - start);

        new SwingWorker<DefaultListModel<Program>, Void>() {

            @Override
            protected DefaultListModel<Program> doInBackground() throws Exception {
                InputStream is = null;
                File f = new File(System.getProperty("user.home") + "/Dropbox/Public/projects.xml");
                if(debug && f.exists()) {
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
                DefaultListModel<Program> l = parseXML(is);
                LOG.log(Level.INFO, "Parsed at {0}ms",
                        System.currentTimeMillis() - start);
                return l;
            }

            @Override
            protected void done() {
                try {
                    launcher.listM = get();
                    launcher.setListModel(launcher.listM);
                    LOG.log(Level.INFO, "Listing at {0}ms",
                            System.currentTimeMillis() - start);
                    if(!isLatest(self)) {
                        JOptionPane.showMessageDialog(launcher,
                                                      "Please update", "A new version is available",
                                                      JOptionPane.INFORMATION_MESSAGE, null);
                    }
                } catch(InterruptedException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                } catch(ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }

        }.execute();

        Point mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        Dimension d = new Dimension(mid.x, mid.y);
        launcher.setSize(d);
        launcher.setLocationRelativeTo(null);
        launcher.setVisible(true);
        LOG.log(Level.INFO, "Visible at {0}ms", System.currentTimeMillis() - start);
    }

    private static DefaultListModel<Program> parseXML(InputStream is) {
        DefaultListModel<Program> listM = new DefaultListModel<Program>();
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

                    ArrayList<Node> downloads = XMLUtils.getElements("download", entry);

                    Node mainDL = Utils.last(downloads);
                    if(mainDL != null) {
                        p.downloadURLs.add(XMLUtils.getAttribute(mainDL, "url"));
                    }

                    Node mainCheck = Utils.last(XMLUtils.getElements("checksum", entry));
                    if(mainCheck != null) {
                        p.checksumURLs = XMLUtils.getAttribute(mainCheck, "url");
                    }

                    for(Node download : downloads) {
                        Node checksum = Utils.last(XMLUtils.getElements("checksum", entry));
                        String dlu = XMLUtils.getAttribute(download, "url");
                        if(dlu == null) {
                            continue;
                        }
                        String csu = null;
                        if(checksum != null) {
                            csu = XMLUtils.getAttribute(checksum, "url");
                        }
                        p.downloads.put(dlu, csu);
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

    private static void started() {
        String dbg = ManagementFactory.getRuntimeMXBean().getName();
        try {
            dbg += "\n" + InetAddress.getByName(null).getHostName();
        } catch(UnknownHostException ex) {
        }
        dbg += "\nEnvir = " + System.getenv().toString();
        dbg += "\nProps = " + System.getProperties().toString();
        LOG.info(dbg);
        if(!debug) {
            Utils.log("connected", "launcher/" + Utils.currentVersion + "/connects", dbg);
        }
    }

    /**
     *
     * @return false if not up to date, true if up to date or offline
     */
    static boolean isLatest(Program p) {
        File f = getFile(p);
        if(p.self) {
            f = Utils.currentFile;
        }
        if(f == null) {
            return false;
        }
        if(f.isDirectory()) {
            return true; // Development
        }
        if(!f.exists()) {
            return false;
        } else {
            LOG.log(Level.INFO, "Checking {0} for updates...", p);
            try {
                String checksum = Utils.checksum(f, "MD5");
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    new URL(p.checksumURLs).openStream()));
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

    public DefaultListModel<Program> listM = null;

    private HyperlinkListener linkListener = new HyperlinkListener() {
        public void hyperlinkUpdate(HyperlinkEvent he) {
            if(!Desktop.isDesktopSupported()) {
                return;
            }
            Desktop d = Desktop.getDesktop();
            if(!he.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                return;
            }
            if(d.isSupported(Desktop.Action.BROWSE)) {
                try {
                    URI u = null;
                    URL l = he.getURL();
                    if(l == null) {
                        u = new URI(he.getDescription());
                    } else if(u == null) {
                        u = l.toURI();
                    }
                    d.browse(u);
                } catch(Exception ex) {
                    LOG.log(Level.WARNING, null, ex);
                }
            }
        }
    };

    private HashMap<Program, Long> running = new HashMap<Program, Long>();

    CompositeClassLoader cl = new CompositeClassLoader();

    public LauncherImpl() {
        super();
        initAboutPanel();
    }

    @Override
    public void news(final Program p) {
        if(p.panel != null) {
            display(p.panel);
            return;
        }

        p.panel = new JPanel(new BorderLayout());
        display(p.panel);

        String str;
        if(p.newsfeedURL == null) {
            str = "No newsfeed available";
        } else {
            str = "Loading...";
        }
        final JEditorPane initial = new JEditorPane("text", str);
        initial.setEditable(false);
        p.panel.add(initial);

        if(p.newsfeedURL != null) {
            new SwingWorker<JEditorPane, Void>() {

                @Override
                protected JEditorPane doInBackground() throws Exception {
                    String s = Utils.loadPage(new URL(p.newsfeedURL));
                    final JEditorPane j = new JEditorPane(p.newsfeedType, s);
                    j.setEditable(false);
                    j.addHyperlinkListener(linkListener);
                    return j;
                }

                @Override
                protected void done() {
                    try {
                        p.panel.remove(initial);
                        p.panel.add(get());
                    } catch(InterruptedException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    } catch(ExecutionException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }

            }.execute();
        }
    }

    @Override
    public void start(final Program program) {
        if(program == null) {
            return;
        }
        if(!canStart(program)) {
            return;
        }
        Runnable done = new Runnable() {

            public void run() {
                if(program.main == null) {
                    JOptionPane.showMessageDialog(null, "Restart to apply",
                                                  "Update downloaded",
                                                  JOptionPane.INFORMATION_MESSAGE,
                                                  null);
                } else {
                    Thread t = createThread(program);
                    t.setDaemon(true);
                    t.start();
                }
            }
        };
        updateWorker(program, done).execute();
    }

    private boolean canStart(Program p) {
        if(running.containsKey(p)) {
            long c = running.get(p);
            if(System.currentTimeMillis() - c < 10000) {
                return false;
            }
            running.put(p, System.currentTimeMillis());
        }
        return true;
    }

    private Thread createThread(final Program run) {
        return new Thread(new Runnable() {
            public void run() {
                if(run.main == null) {
                    return; // Not executable
                }
                LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] {run, run.main});
                try {
                    cl.start(
                        run.main, run.args.toArray(new String[0]),
                        classPath(run).toArray(new URL[0]));
//                            for(Window w : Window.getWindows()) { // TODO: This will probably come back to haunt me later
//                                LOG.log(Level.INFO, "{0}  {1}", new Object[] {w,
//                                                                              w.isDisplayable()});
//                                if(!w.isVisible()) {
//                                    w.dispose();
//                                }
//                            }
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    private ArrayList<Future<?>> download(Program p, boolean checksum) {
        ArrayList<Future<?>> arr = null;
        try {
            arr = new ArrayList<Future<?>>();
            for(String url : p.downloadURLs) {
                arr.add(download(new Download(p.name, new URL(url), getFile(p))));
            }
            if(checksum) {
                arr.add(download(new Download(p.name, new URL(p.checksumURLs), new File(
                    getFile(p) + ".MD5"))));
            }
        } catch(MalformedURLException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return arr;
    }

    private void initAboutPanel() {
        final String latestThread = "http://steamcommunity.com/gid/103582791434775526/discussions";
        final JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBackground(new Color(0, 0, 0, 0));
        pane.addHyperlinkListener(linkListener);
        String aboutText = "<html><h2>This my launcher for launching things</h2>";
        aboutText += "<p>It's much easier to distribute them this way</p>";
        aboutText
        += "<p>Author: TimePath (<a href=\"http://steamcommunity.com/id/TimePath/\">steam</a>|<a href=\"http://www.reddit.com/user/TimePath/\">reddit</a>|<a href=\"https://github.com/TimePath/\">GitHub</a>)<br>";
        String local = "</p>";
        String aboutText2 = "<p>Please leave feedback or suggestions on "
                            + "<a href=\"" + latestThread + "\">the steam group</a>";

        // TODO: http://steamredirect.heroku.com or Runtime.exec() on older versions of java
        aboutText2 += "<br>You might be able to catch me live "
                      + "<a href=\"steam://friends/joinchat/103582791434775526\">in chat</a></p>";
        long time = Utils.currentVersion;
        if(time != 0) {
            DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
            aboutText2 += "<p>Build date: " + df.format(new Date(time * 1000)) + "</p>";
        }
        aboutText2 += "</html>";
        final String p1 = aboutText;
        final String p2 = aboutText2;
        pane.setText(p1 + local + p2);

        final DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
        df.setTimeZone(TimeZone.getTimeZone("Australia/Sydney"));
        final javax.swing.Timer t = new javax.swing.Timer(1000, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                String local = "My (presumed) local time: " + df.format(System.currentTimeMillis())
                               + "</p>";
                int i = pane.getSelectionStart();
                int j = pane.getSelectionEnd();
                pane.setText(p1 + local + p2);
                pane.select(i, j);
            }
        });
        t.setInitialDelay(0);
        this.addHierarchyListener(new HierarchyListener() {

            public void hierarchyChanged(HierarchyEvent e) {
                if((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) > 0) {
                    if(LauncherImpl.this.isDisplayable()) {
                        t.start();
                    } else {
                        t.stop();
                    }
                }
            }
        });
        t.start();
        aboutPanel.setLayout(new BorderLayout());
        aboutPanel.add(pane, BorderLayout.CENTER);
    }

    private SwingWorker<Void, Void> updateWorker(final Program run, final Runnable r) {
        return new SwingWorker<Void, Void>() {

            final HashMap<Program, List<Future<?>>> downloads
                                                    = new HashMap<Program, List<Future<?>>>();

            @Override
            protected Void doInBackground() throws Exception {
                HashSet<Program> ps = depends(run);
                LOG.log(Level.INFO, "Download list: {0}", ps.toString());
                for(Program p : ps) {
                    LOG.log(Level.INFO, "Checking for {0} updates...", p);
                    if(!isLatest(p)) {
                        LOG.log(Level.INFO, "Fetching {0}...", p);
                        downloads.put(p, download(p, (p.self && !Utils.runningTemp)));
                    } else {
                        LOG.log(Level.INFO, "{0} is up to date", p);
                        if(p.self) {
                            JOptionPane.showMessageDialog(null, "Launcher is up to date",
                                                          "Launcher is up to date",
                                                          JOptionPane.INFORMATION_MESSAGE,
                                                          null);
                        }
                    }
                }
                for(Entry<Program, List<Future<?>>> e : downloads.entrySet()) {
                    Program p = e.getKey();
                    List<Future<?>> futures = e.getValue();
                    try {
                        for(Future<?> future : futures) {
                            future.get(); // wait for download
                        }
                        LOG.log(Level.INFO, "Updated {0}", p);
                    } catch(Exception ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                r.run();
            }

        };
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
