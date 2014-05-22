package com.timepath.launcher.ui.swing;

import com.timepath.launcher.DownloadManager.DownloadMonitor;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.PackageFile;
import com.timepath.launcher.Program;
import com.timepath.launcher.Repository;
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
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.timepath.launcher.util.Utils.DEBUG;

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

    public LauncherFrame(final Launcher launcher) {
        this.launcher = launcher;
        launcher.getDownloadManager().addListener(new DownloadMonitor() {
            @Override
            public void submit(PackageFile pkgFile) {
                downloadPanel.getTableModel().add(pkgFile);
            }

            @Override
            public void update(PackageFile pkgFile) {
                downloadPanel.getTableModel().update(pkgFile);
            }
        });
        repositoryManager = new RepositoryManagerImpl();
        initComponents();
        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
        updateList();
        setTitle("TimePath's program hub");
        setBounds(0, 0, 650, 510);
        setJMenuBar(new JMenuBar() {{
            add(new JMenu() {{
                setText("Tools");
                add(new JMenuItem(new AbstractAction("Add repository") {
                    @Override
                    public void actionPerformed(final ActionEvent e) {
                        JOptionPane.showMessageDialog(LauncherFrame.this, repositoryManager);
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
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                launcher.shutdown();
                LauncherFrame.this.dispose();
            }
        });
        Point mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        setSize(new Dimension(mid.x, mid.y));
        setLocationRelativeTo(null);
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
                    List<Repository> repos = get();
                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
                    TreeModel listM = new DefaultTreeModel(rootNode);
                    int i = repositoryManager.model.getRowCount();
                    while(i > 0) { repositoryManager.model.removeRow(--i); }
                    for(final Repository repo : repos) {
                        repositoryManager.model.addRow(new Object[] { repo, repo.isEnabled() });
                        DefaultMutableTreeNode repoNode = rootNode;
                        if(repos.size() > 1) {
                            repoNode = new DefaultMutableTreeNode(repo.getName());
                            rootNode.add(repoNode);
                        }
                        for(Program p : repo.getPackages()) {
                            repoNode.add(new DefaultMutableTreeNode(p));
                        }
                    }
                    setListModel(listM);
                    PropertyChangeListener pcl = new PropertyChangeListener() {
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
                    LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
                    if(!DEBUG && launcher.updateRequired()) {
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

    public void setListModel(TreeModel model) {
        programList.setModel(model);
    }

    private void initComponents() {
        aboutPanel = new JPanel(new BorderLayout()) {{
            add(initAboutPanel(), BorderLayout.CENTER);
        }};
        JTabbedPane tabbedPane = new JTabbedPane() {{
            addTab("Programs", programSplit = new JSplitPane() {{
                setLeftComponent(new JScrollPane(programList = new JTree((TreeModel) null) {{
                    setRootVisible(false);
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
                                Program p = getSelected(getLastSelectedPathComponent());
                                start(p);
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
        }};
        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(tabbedPane, GroupLayout.Alignment.TRAILING));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                      .addComponent(tabbedPane, GroupLayout.Alignment.TRAILING));
    }

    public void news(final Program p) {
        if(p == null) {
            display(null);
            launchButton.setEnabled(false);
            return;
        }
        launchButton.setEnabled(!p.lock);
        if(p.panel != null) {
            display(p.panel);
            return;
        }
        p.panel = new JPanel(new BorderLayout());
        display(p.panel);
        String str = ( p.newsfeedURL == null ) ? "No newsfeed available" : "Loading...";
        final JEditorPane initial = new JEditorPane("text", str);
        initial.setEditable(false);
        p.panel.add(initial);
        if(p.newsfeedURL != null) {
            new SwingWorker<JEditorPane, Void>() {
                @Override
                protected JEditorPane doInBackground() throws Exception {
                    String s = Utils.loadPage(new URL(p.newsfeedURL));
                    JEditorPane editorPane = new JEditorPane(p.newsfeedType, s);
                    editorPane.setEditable(false);
                    editorPane.addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER);
                    return editorPane;
                }

                @Override
                protected void done() {
                    try {
                        p.panel.remove(initial);
                        p.panel.add(get());
                    } catch(InterruptedException | ExecutionException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }.execute();
        }
    }

    public void display(Component component) {
        newsScroll.setViewportView(component);
    }

    public void start(final Program program) {
        if(program == null) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                launchButton.setEnabled(false);
                launcher.start(program);
                return null;
            }

            @Override
            protected void done() {
                launchButton.setEnabled(true);
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
        String aboutText = Utils.loadPage(getClass().getResource("/com/timepath/launcher/swing/about.html"))
                                .replace("${buildDate}", buildDate)
                                .replace("${steamGroup}", "steam://friends/joinchat/103582791434775526");
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
            Repository r = new Repository(in);
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
