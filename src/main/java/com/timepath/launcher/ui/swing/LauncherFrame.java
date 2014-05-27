package com.timepath.launcher.ui.swing;

import com.timepath.launcher.DownloadManager.DownloadMonitor;
import com.timepath.launcher.Launcher;
import com.timepath.maven.Package;
import com.timepath.launcher.Program;
import com.timepath.launcher.Repository;
import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.SwingUtils;
import com.timepath.launcher.util.Utils;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@SuppressWarnings("serial")
public class LauncherFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(LauncherFrame.class.getName());
    protected JPanel                aboutPanel;
    private   Launcher              launcher;
    private   RepositoryManagerImpl repositoryManager;
    private   DownloadPanel         downloadPanel;
    private   JButton               launchButton;
    private   JScrollPane           newsScroll;
    private   JTree                 programList;
    private   JSplitPane            programSplit;

    public LauncherFrame(Launcher l) {
        launcher = l;
        launcher.getDownloadManager().addListener(new DownloadMonitor() {
            @Override
            public void submit(Package pkg) {
                downloadPanel.getTableModel().add(pkg);
            }

            @Override
            public void update(Package pkg) {
                downloadPanel.getTableModel().update(pkg);
            }
        });
        repositoryManager = new RepositoryManagerImpl();
        initComponents();
        updateList();
        // set frame properties
        setJMenuBar(new JMenuBar() {{
            add(new JMenu() {{
                setText("Tools");
                add(new JMenuItem(new AbstractAction("Repository management") {
                    @Override
                    public void actionPerformed(final ActionEvent e) {
                        JOptionPane.showMessageDialog(LauncherFrame.this,
                                                      repositoryManager,
                                                      "Repository manager",
                                                      JOptionPane.PLAIN_MESSAGE);
                    }
                }));
                add(new JMenuItem(new AbstractAction("Preferences") {
                    @Override
                    public void actionPerformed(final ActionEvent e) {
                        JOptionPane.showMessageDialog(LauncherFrame.this,
                                                      new ThemeSelector(),
                                                      "Select theme",
                                                      JOptionPane.PLAIN_MESSAGE);
                    }
                }));
            }});
            add(new JMenu() {{
                setText("Help");
                add(new JMenuItem(new AbstractAction("About") {
                    @Override
                    public void actionPerformed(final ActionEvent e) {
                        JOptionPane.showMessageDialog(LauncherFrame.this, aboutPanel);
                    }
                }));
            }});
        }});
        setTitle("TimePath's program hub");
        setBounds(0, 0, 700, 500);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Point mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        setSize(new Dimension(mid.x, mid.y));
        setLocationRelativeTo(null);
        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
    }

    private void updateList() {
        new SwingWorker<List<Repository>, Void>() {
            @Override
            protected List<Repository> doInBackground() throws Exception {
                return launcher.getRepositories();
            }

            @Override
            protected void done() {
                try {
                    LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
                    List<Repository> repos = get();
                    // update the repository manager
                    int i = repositoryManager.model.getRowCount();
                    while(i > 0) { repositoryManager.model.removeRow(--i); }
                    for(Repository repo : repos) {
                        repositoryManager.model.addRow(new Object[] { repo });
                    }
                    // update the program list
                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
                    for(Repository repo : repos) {
                        DefaultMutableTreeNode repoNode = rootNode;
                        // create a new pseudo root node if there are multiple repositories
                        if(repos.size() > 1) {
                            repoNode = new DefaultMutableTreeNode(repo.getName());
                            rootNode.add(repoNode);
                        }
                        for(Program p : repo.getExecutions()) {
                            repoNode.add(new DefaultMutableTreeNode(p));
                        }
                    }
                    programList.setModel(new DefaultTreeModel(rootNode));
                    // hack to pack the SplitPane
                    PropertyChangeListener pcl = new PropertyChangeListener() {
                        /**
                         * Flag to ignore the first event
                         */
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
                    // show update notification
                    if(!Utils.DEBUG && launcher.updateRequired()) {
                        JOptionPane.showMessageDialog(LauncherFrame.this,
                                                      "Please update",
                                                      "A new version is available",
                                                      JOptionPane.INFORMATION_MESSAGE,
                                                      null);
                    }
                } catch(InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }.execute();
    }

    private void initComponents() {
        aboutPanel = new JPanel(new BorderLayout()) {{
            add(initAboutPanel(), BorderLayout.CENTER);
        }};
        setContentPane(new JTabbedPane() {{
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
                            if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                                start(getSelected(getLastSelectedPathComponent()));
                            }
                        }
                    });
                    MouseAdapter adapter = new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if(select(e) == -1) {
                                return;
                            }
                            if(SwingUtilities.isLeftMouseButton(e) && ( e.getClickCount() >= 2 )) {
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
                        public void actionPerformed(final ActionEvent e) {
                            start(getSelected(programList.getLastSelectedPathComponent()));
                        }
                    }), BorderLayout.SOUTH);
                }});
            }});
            addTab("Downloads", downloadPanel = new DownloadPanel());
        }});
    }

    public void news(Program p) {
        // handle things other than programs
        if(p == null) {
            newsScroll.setViewportView(null);
            launchButton.setEnabled(false);
        } else {
            newsScroll.setViewportView(p.getPanel());
            launchButton.setEnabled(!p.getPackage().isLocked());
        }
    }

    public void start(final Program program) {
        // handle things other than programs
        if(program == null) return;
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
                    if(updates != null) { // ready to start
                        Package parent = program.getPackage();
                        boolean run = true;
                        if(!Utils.DEBUG && parent.isSelf()) { // alert on self update
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
                }
            }
        }.execute();
    }

    private static Program getSelected(Object selected) {
        if(!( selected instanceof DefaultMutableTreeNode )) {
            return null;
        }
        Object obj = ( (DefaultMutableTreeNode) selected ).getUserObject();
        if(!( obj instanceof Program )) {
            return null;
        }
        return (Program) obj;
    }

    private JEditorPane initAboutPanel() {
        final JEditorPane pane = new JEditorPane("text/html", "") {{
            setEditable(false);
            setOpaque(false);
            setBackground(new Color(255, 255, 255, 0));
            addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER);
        }};
        String buildDate = "unknown";
        long time = JARUtils.CURRENT_VERSION;
        final DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
        if(time != 0) {
            buildDate = df.format(new Date(time));
        }
        String aboutText = IOUtils.loadPage(getClass().getResource("/com/timepath/launcher/swing/about.html"))
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
        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if(( e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED ) != 0) {
                    if(isDisplayable()) {
                        timer.setInitialDelay(0);
                        timer.start();
                    } else {
                        timer.stop();
                    }
                }
            }
        });
        return pane;
    }

    private class RepositoryManagerImpl extends RepositoryManager {

        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();

        RepositoryManagerImpl() {
            model.setColumnCount(1);
        }

        @Override
        protected void addActionPerformed(ActionEvent evt) {
            String in = JOptionPane.showInputDialog(LauncherFrame.this, "Enter URL");
            if(in == null) {
                return;
            }
            Repository r = Repository.fromIndex(in);
            Launcher.addRepository(r);
            updateList();
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
