package com.timepath.launcher.ui.swing;

import com.timepath.IOUtils;
import com.timepath.SwingUtils;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.LauncherUtils;
import com.timepath.launcher.data.DownloadManager.DownloadMonitor;
import com.timepath.launcher.data.Program;
import com.timepath.launcher.data.Repository;
import com.timepath.launcher.data.RepositoryManager;
import com.timepath.maven.MavenResolver;
import com.timepath.maven.Package;
import com.timepath.swing.ThemeSelector;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.regex.Pattern;

@SuppressWarnings("serial")
public class LauncherFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(LauncherFrame.class.getName());
    protected RepositoryManagerPanel repositoryManager = new RepositoryManagerPanel() {

        @Override
        protected void addActionPerformed(ActionEvent evt) {
            // FIXME: showInternalInputDialog returns immediately
            String in = JOptionPane.showInputDialog(LauncherFrame.this.getContentPane(), "Enter URL");
            if (in == null) return;
            Repository r = Repository.fromIndex(in);
            if (r == null) {
                JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                        "Invalid repository",
                        "Invalid repository",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                RepositoryManager.addRepository(r);
                updateList();
            }
        }

        @Override
        protected void removeActionPerformed(ActionEvent evt) {
            int i = 0;
            int[] selection = jTable1.getSelectedRows();
            Arrays.sort(selection);
            List<Repository> rows = model.getRows();
            for(Repository r : rows.toArray(new Repository[rows.size()])) {
                boolean selected = Arrays.binarySearch(selection, i++) >= 0;
                if(selected) RepositoryManager.removeRepository(r);
            }
            updateList();
        }
    };
    protected JPanel aboutPanel;
    protected Launcher launcher;
    protected DownloadPanel downloadPanel;
    protected JButton launchButton;
    protected JScrollPane newsScroll;
    protected JTree programList;
    protected JSplitPane programSplit;
    protected JTabbedPane tabs;

    {
        // Has to be here to catch exceptions occurring on the EDT
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                String msg = "Uncaught Exception in " + t + ":";
                Logger.getLogger(t.getName()).log(Level.SEVERE, msg, e);
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                        new JScrollPane(new JTextArea(msg + '\n' + sw.toString()) {{
                            setEditable(false);
                            setTabSize(4);
                        }}) {{
                            Dimension size = LauncherFrame.this.getSize();
                            size.width /= 2;
                            size.height /= 2;
                            setPreferredSize(size);
                        }},
                        "Uncaught Exception",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public LauncherFrame(Launcher l) {
        launcher = l;
        launcher.getDownloadManager().addListener(new DownloadMonitor() {
            AtomicInteger c = new AtomicInteger();

            @Override
            public void onSubmit(Package pkg) {
                downloadPanel.getTableModel().add(pkg);
                updateTitle(c.incrementAndGet());
            }

            void updateTitle(int i) {
                setTitle("Downloads" + (i > 0 ? " (" + i + ")" : ""));
            }

            void setTitle(String title) {
                int index = tabs.indexOfComponent(downloadPanel);
                tabs.setTitleAt(index, title);
            }

            @Override
            public void onUpdate(Package pkg) {
                downloadPanel.getTableModel().update(pkg);
            }

            @Override
            public void onFinish(Package pkg) {
                updateTitle(c.decrementAndGet());
            }
        });
        this.initComponents();
        this.updateList();
        this.setTitle("TimePath's program hub");
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Point mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        this.setSize(new Dimension(mid.x, mid.y));
        this.setLocationRelativeTo(null);
        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME);
    }

    /**
     * Get a program from a TreeNode
     */
    protected static Program getSelected(Object selected) {
        if (!(selected instanceof DefaultMutableTreeNode)) return null;
        Object obj = ((DefaultMutableTreeNode) selected).getUserObject();
        return obj instanceof Program ? (Program) obj : null;
    }

    /**
     * Schedule a listing update
     */
    protected void updateList() {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<Repository>, Void>() {
            @Override
            protected List<Repository> doInBackground() throws Exception {
                return launcher.getRepositories(true);
            }

            @Override
            protected void done() {
                try {
                    LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME);
                    List<Repository> repos = get();
                    repositoryManager.setRepositories(repos);
                    // Update the program list
                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
                    for (Repository repo : repos) {
                        DefaultMutableTreeNode repoNode = rootNode;
                        // Create a new pseudo root node if there are multiple repositories
                        if (repos.size() > 1) {
                            repoNode = new DefaultMutableTreeNode(repo.getName());
                            rootNode.add(repoNode);
                        }
                        for (Program p : repo.getExecutions()) {
                            repoNode.add(new DefaultMutableTreeNode(p));
                        }
                    }
                    final DefaultTreeModel newModel = new DefaultTreeModel(rootNode);
                    programList.setModel(newModel);
                    final DefaultMutableTreeNode firstLeaf = rootNode.getFirstLeaf();
                    final TreePath path = new TreePath(firstLeaf.getPath());
                    programList.expandPath(path);
                    programList.setSelectionPath(path);
                    pack(programSplit);
                    if (!LauncherUtils.DEBUG && launcher.updateRequired()) { // Show update notification
                        JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                "Please update",
                                "A new version is available",
                                JOptionPane.INFORMATION_MESSAGE,
                                null);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    LOG.log(Level.SEVERE, null, e);
                } finally {
                    LauncherFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }.execute();
    }

    /**
     * Hack to pack the SplitPane
     */
    protected void pack(final JSplitPane programSplit) {
        PropertyChangeListener pcl = new PropertyChangeListener() {
            /** Flag to ignore the first event */
            boolean ignore = true;

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (ignore) {
                    ignore = false;
                    return;
                }
                programSplit.removePropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
                programSplit.setDividerLocation(Math.max((int) evt.getNewValue(),
                        programList.getPreferredScrollableViewportSize().width));
            }
        };
        programSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, pcl);
        programSplit.setDividerLocation(-1);
    }

    protected void initComponents() {
        aboutPanel = new JPanel(new BorderLayout()) {{
            add(initAboutPanel(), BorderLayout.CENTER);
        }};
        setContentPane(tabs = new JTabbedPane() {{
            addTab("Programs", programSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true) {{
                setLeftComponent(new JScrollPane(programList = new JTree((TreeModel) null) {{
                    setRootVisible(false);
                    setShowsRootHandles(true);
                    getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
                        @Override
                        public void valueChanged(TreeSelectionEvent e) {
                            news(getSelected(getLastSelectedPathComponent()));
                        }
                    });
                    addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyPressed(KeyEvent e) {
                            if (e.getKeyCode() == KeyEvent.VK_ENTER) start(getSelected(getLastSelectedPathComponent()));
                        }
                    });
                    MouseAdapter adapter = new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (select(e) == -1) return;
                            if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() >= 2)) {
                                Program p = getSelected(getLastSelectedPathComponent());
                                start(p);
                            }
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            select(e);
                        }

                        @Override
                        public void mouseDragged(MouseEvent e) {
                            select(e);
                        }

                        private int select(MouseEvent e) {
                            int selRow = getClosestRowForLocation(e.getX(), e.getY());
                            setSelectionRow(selRow);
                            return selRow;
                        }
                    };
                    addMouseMotionListener(adapter);
                    addMouseListener(adapter);
                }}));
                setRightComponent(new JPanel(new BorderLayout()) {{
                    add(newsScroll = new JScrollPane() {{
                        getVerticalScrollBar().setUnitIncrement(16);
                    }}, BorderLayout.CENTER);
                    add(launchButton = new JButton(new AbstractAction("Launch") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            start(getSelected(programList.getLastSelectedPathComponent()));
                        }
                    }), BorderLayout.SOUTH);
                    launchButton.setEnabled(false);
                }});
            }});
            addTab("Downloads", downloadPanel = new DownloadPanel());
        }});
        setJMenuBar(new JMenuBar() {{
            add(new JMenu("Tools") {{
                add(new JMenuItem(new AbstractAction("Repository management") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                repositoryManager,
                                "Repository manager",
                                JOptionPane.PLAIN_MESSAGE);
                    }
                }));
                add(new JMenuItem(new AbstractAction("Clear cache") {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        try {
                            MavenResolver.invalidateCaches();
                            JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                    "Restart to check for updates",
                                    "Cleared cache",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } catch (BackingStoreException e) {
                            LOG.log(Level.WARNING, "Unable to drop caches", e);
                            JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                    "Unable to clear cache",
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }));
                add(new JMenuItem(new AbstractAction("Preferences") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                new ThemeSelector(),
                                "Select theme",
                                JOptionPane.PLAIN_MESSAGE);
                    }
                }));
            }});
            add(new JMenu("Help") {{
                add(new JMenuItem(new AbstractAction("About") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(), aboutPanel);
                    }
                }));
            }});
        }});
    }

    /**
     * Display the news for a program
     */
    protected void news(Program p) {
        if (p == null) { // Handle things other than programs
            newsScroll.setViewportView(null);
            launchButton.setEnabled(false);
        } else {
            newsScroll.setViewportView(p.getPanel());
            launchButton.setEnabled(!Launcher.isLocked(p.getPackage()));
        }
    }

    protected void start(final Program program) {
        if (program == null) return; // Handle things other than programs
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        launchButton.setEnabled(false);
        new SwingWorker<Set<Package>, Void>() {
            @Override
            protected Set<Package> doInBackground() {
                return launcher.update(program);
            }

            @Override
            protected void done() {
                try {
                    Set<Package> updates = get();
                    if (updates != null) { // Ready to start
                        Package parent = program.getPackage();
                        boolean run = true;
                        if (!LauncherUtils.DEBUG && Package.isSelf(parent)) { // Alert on self update
                            if (updates.contains(parent)) {
                                run = false;
                                JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                        "Restart to apply",
                                        "Update downloaded",
                                        JOptionPane.INFORMATION_MESSAGE,
                                        null);
                            } else {
                                run = false;
                                JOptionPane.showInternalMessageDialog(LauncherFrame.this.getContentPane(),
                                        "Launcher is up to date",
                                        "Launcher is up to date",
                                        JOptionPane.INFORMATION_MESSAGE,
                                        null);
                            }
                        }
                        if (run) {
                            launcher.start(program);
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    LOG.log(Level.SEVERE, null, e);
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    launchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    protected JEditorPane initAboutPanel() {
        final JEditorPane pane = new JEditorPane("text/html", "") {{
            setEditable(false);
            setOpaque(false);
            setBackground(new Color(255, 255, 255, 0));
            addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER);
        }};
        String buildDate = "unknown";
        long time = LauncherUtils.CURRENT_VERSION;
        final DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
        if (time != 0) buildDate = df.format(new Date(time));
        String aboutText = IOUtils.requestPage(getClass().getResource("/com/timepath/launcher/ui/swing/about.html").toString())
                .replace("${buildDate}", buildDate)
                .replace("${steamGroup}", "http://steamcommunity.com/gid/103582791434775526")
                .replace("${steamChat}", "steam://friends/joinchat/103582791434775526");
        final String[] split = aboutText.split(Pattern.quote("${localtime}"));
        pane.setText(split[0] + "calculating..." + split[1]);
        df.setTimeZone(TimeZone.getTimeZone("Australia/Sydney"));
        final Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String time = df.format(System.currentTimeMillis());
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        int i = pane.getSelectionStart();
                        int j = pane.getSelectionEnd();
                        pane.setText(split[0] + time + split[1]);
                        pane.select(i, j);
                    }
                });
            }
        });
        timer.setInitialDelay(0);
        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
                if (isDisplayable()) {
                    timer.start();
                } else {
                    timer.stop();
                }
            }
        });
        return pane;
    }
}
