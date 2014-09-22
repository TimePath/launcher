package com.timepath.launcher.ui.swing;

import com.timepath.launcher.DownloadManager.DownloadMonitor;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.data.Program;
import com.timepath.launcher.data.Repository;
import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.SwingUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.maven.Package;
import com.timepath.swing.ThemeSelector;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@SuppressWarnings("serial")
public class LauncherFrame extends JFrame {

    private static final Logger                LOG               = Logger.getLogger(LauncherFrame.class.getName());
    protected            RepositoryManagerImpl repositoryManager = new RepositoryManagerImpl();
    protected JPanel        aboutPanel;
    protected Launcher      launcher;
    protected DownloadPanel downloadPanel;
    protected JButton       launchButton;
    protected JScrollPane   newsScroll;
    protected JTree         programList;
    protected JSplitPane    programSplit;
    protected JTabbedPane   tabs;

    public LauncherFrame(Launcher l) {
        launcher = l;
        launcher.getDownloadManager().addListener(new DownloadMonitor() {
            AtomicInteger c = new AtomicInteger();

            @Override
            public void onSubmit(Package pkg) {
                downloadPanel.getTableModel().add(pkg);
                updateTitle(c.incrementAndGet());
            }

            void updateTitle(int i) { setTitle("Downloads" + ( i > 0 ? " (" + i + ")" : "" )); }

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
        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
    }

    /** Schedule a listing update */
    protected void updateList() {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<Repository>, Void>() {
            @Override
            protected List<Repository> doInBackground() throws Exception { return launcher.getRepositories(); }

            @Override
            protected void done() {
                try {
                    LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
                    List<Repository> repos = get();
                    // Update the repository manager
                    int i = repositoryManager.model.getRowCount();
                    while(i > 0) { repositoryManager.model.removeRow(--i); }
                    for(Repository repo : repos) repositoryManager.model.addRow(new Object[] { repo });
                    // Update the program list
                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
                    for(Repository repo : repos) {
                        DefaultMutableTreeNode repoNode = rootNode;
                        // Create a new pseudo root node if there are multiple repositories
                        if(repos.size() > 1) {
                            repoNode = new DefaultMutableTreeNode(repo.getName());
                            rootNode.add(repoNode);
                        }
                        for(Program p : repo.getExecutions()) {
                            repoNode.add(new DefaultMutableTreeNode(p));
                        }
                    }
                    programList.setModel(new DefaultTreeModel(rootNode));
                    pack(programSplit);
                    if(!Utils.DEBUG && launcher.updateRequired()) { // Show update notification
                        JOptionPane.showMessageDialog(LauncherFrame.this,
                                                      "Please update",
                                                      "A new version is available",
                                                      JOptionPane.INFORMATION_MESSAGE,
                                                      null);
                    }
                } catch(InterruptedException | ExecutionException e) {
                    LOG.log(Level.SEVERE, null, e);
                } finally {
                    LauncherFrame.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }.execute();
    }

    /** Hack to pack the SplitPane */
    protected void pack(final JSplitPane programSplit) {
        PropertyChangeListener pcl = new PropertyChangeListener() {
            /** Flag to ignore the first event */
            boolean ignore = true;

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(ignore) {
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
                        public void valueChanged(TreeSelectionEvent e) { news(getSelected(getLastSelectedPathComponent())); }
                    });
                    addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyPressed(KeyEvent e) {
                            if(e.getKeyCode() == KeyEvent.VK_ENTER) start(getSelected(getLastSelectedPathComponent()));
                        }
                    });
                    MouseAdapter adapter = new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if(select(e) == -1) return;
                            if(SwingUtilities.isLeftMouseButton(e) && ( e.getClickCount() >= 2 )) {
                                Program p = getSelected(getLastSelectedPathComponent());
                                start(p);
                            }
                        }

                        @Override
                        public void mousePressed(MouseEvent e) { select(e); }

                        @Override
                        public void mouseDragged(MouseEvent e) { select(e); }

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
                        public void actionPerformed(ActionEvent e) { start(getSelected(programList.getLastSelectedPathComponent())); }
                    }), BorderLayout.SOUTH);
                }});
            }});
            addTab("Downloads", downloadPanel = new DownloadPanel());
        }});
        setJMenuBar(new JMenuBar() {{
            add(new JMenu("Tools") {{
                add(new JMenuItem(new AbstractAction("Repository management") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showMessageDialog(LauncherFrame.this,
                                                      repositoryManager,
                                                      "Repository manager",
                                                      JOptionPane.PLAIN_MESSAGE);
                    }
                }));
                add(new JMenuItem(new AbstractAction("Preferences") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JOptionPane.showMessageDialog(LauncherFrame.this,
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
                        JOptionPane.showMessageDialog(LauncherFrame.this, aboutPanel);
                    }
                }));
            }});
        }});
    }

    /** Display the news for a program */
    protected void news(Program p) {
        if(p == null) { // Handle things other than programs
            newsScroll.setViewportView(null);
            launchButton.setEnabled(false);
        } else {
            newsScroll.setViewportView(p.getPanel());
            launchButton.setEnabled(!p.getPackage().isLocked());
        }
    }

    protected void start(final Program program) {
        if(program == null) return; // Handle things other than programs
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
                    if(updates != null) { // Ready to start
                        Package parent = program.getPackage();
                        boolean run = true;
                        if(!Utils.DEBUG && parent.isSelf()) { // Alert on self update
                            if(updates.contains(parent)) {
                                run = false;
                                JOptionPane.showMessageDialog(null,
                                                              "Restart to apply",
                                                              "Update downloaded",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              null);
                            } else {
                                run = false;
                                JOptionPane.showMessageDialog(null,
                                                              "Launcher is up to date",
                                                              "Launcher is up to date",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              null);
                            }
                        }
                        if(run) {
                            launcher.start(program);
                        }
                        launchButton.setEnabled(true);
                    }
                } catch(InterruptedException | ExecutionException e) {
                    LOG.log(Level.SEVERE, null, e);
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    launchButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /** Get a program from a TreeNode */
    protected static Program getSelected(Object selected) {
        if(!( selected instanceof DefaultMutableTreeNode )) return null;
        Object obj = ( (DefaultMutableTreeNode) selected ).getUserObject();
        return obj instanceof Program ? (Program) obj : null;
    }

    protected JEditorPane initAboutPanel() {
        final JEditorPane pane = new JEditorPane("text/html", "") {{
            setEditable(false);
            setOpaque(false);
            setBackground(new Color(255, 255, 255, 0));
            addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER);
        }};
        String buildDate = "unknown";
        long time = JARUtils.CURRENT_VERSION;
        final DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
        if(time != 0) buildDate = df.format(new Date(time));
        String aboutText = IOUtils.loadPage(getClass().getResource("/com/timepath/launcher/ui/swing/about.html"))
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
                if(( e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED ) == 0) return;
                if(isDisplayable()) {
                    timer.start();
                } else {
                    timer.stop();
                }
            }
        });
        return pane;
    }

    protected class RepositoryManagerImpl extends RepositoryManager {

        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();

        RepositoryManagerImpl() { model.setColumnCount(1); }

        @Override
        protected void addActionPerformed(ActionEvent evt) {
            String in = JOptionPane.showInputDialog(LauncherFrame.this, "Enter URL");
            if(in == null) return;
            Repository r = Repository.fromIndex(in);
            if(r == null) {
                JOptionPane.showMessageDialog(LauncherFrame.this,
                                              "Invalid repository",
                                              "Invalid repository",
                                              JOptionPane.WARNING_MESSAGE);
            } else {
                Launcher.addRepository(r);
                updateList();
            }
        }

        @Override
        protected void removeActionPerformed(ActionEvent evt) {
            for(int row : jTable1.getSelectedRows()) {
                Launcher.removeRepository((Repository) model.getValueAt(row, 0));
            }
            updateList();
        }
    }
}