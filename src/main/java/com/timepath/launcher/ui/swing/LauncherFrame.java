package com.timepath.launcher.ui.swing;

import com.timepath.launcher.*;
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

import static com.timepath.launcher.util.Utils.debug;

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
        initComponents();
        initAboutPanel();
        newsScroll.getVerticalScrollBar().setUnitIncrement(16);
        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
        this.launcher = launcher;
        launcher.getDownloadManager().addListener(new DownloadMonitor() {
            @Override
            public void submit(PackageFile pkgFile) {
                downloadPanel.tableModel.add(pkgFile);
            }

            @Override
            public void update(PackageFile pkgFile) {
                downloadPanel.tableModel.update(pkgFile);
            }
        });
        repositoryManager = new RepositoryManagerImpl();
        updateList();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                launcher.shutdown();
            }
        });
        programList.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                news(getSelected(programList.getLastSelectedPathComponent()));
            }
        });
        launchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Program p = getSelected(programList.getLastSelectedPathComponent());
                start(p);
            }
        });
        programList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    Program p = getSelected(programList.getLastSelectedPathComponent());
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
                    Program p = getSelected(programList.getLastSelectedPathComponent());
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
                int selRow = programList.getClosestRowForLocation(e.getX(), e.getY());
                programList.setSelectionRow(selRow);
                return selRow;
            }
        };
        programList.addMouseMotionListener(adapter);
        programList.addMouseListener(adapter);
        Point mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        Dimension dimension = new Dimension(mid.x, mid.y);
        setSize(dimension);
        setLocationRelativeTo(null);
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
                    editorPane.addHyperlinkListener(Utils.linkListener);
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
                    for(int i = repositoryManager.model.getRowCount(); i > 0; ) {
                        repositoryManager.model.removeRow(--i);
                    }
                    for(Repository repo : repos) {
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
                    if(!debug && launcher.updateRequired()) {
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

    private void initAboutPanel() {
        final JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBackground(new Color(0, 0, 0, 0));
        pane.addHyperlinkListener(Utils.linkListener);
        String aboutText = "<html><h2>This my launcher for launching things</h2>";
        aboutText += "<p>It's much easier to distribute them this way</p>";
        aboutText += "<p>Author: TimePath (<a href=\"http://steamcommunity.com/id/TimePath/\">steam</a>|<a href=\"http://www" +
                     ".reddit.com/user/TimePath/\">reddit</a>|<a href=\"https://github.com/TimePath/\">GitHub</a>)<br>";
        String latestThread = "http://steamcommunity.com/gid/103582791434775526/discussions";
        String aboutText2 = "<p>Please leave feedback or suggestions on " + "<a href=\"" + latestThread +
                            "\">the steam group</a>";
        // TODO: http://steamredirect.heroku.com or Runtime.exec() on older versions of java
        aboutText2 += "<br>You might be able to catch me live " +
                      "<a href=\"steam://friends/joinchat/103582791434775526\">in chat</a></p>";
        long time = Utils.currentVersion;
        if(time != 0) {
            DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
            aboutText2 += "<p>Build date: " + df.format(new Date(time)) + "</p>";
        }
        aboutText2 += "</html>";
        final String p1 = aboutText;
        final String p2 = aboutText2;
        String local = "</p>";
        pane.setText(p1 + local + p2);
        final DateFormat df = new SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z");
        df.setTimeZone(TimeZone.getTimeZone("Australia/Sydney"));
        final Timer t = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String s = "My (presumed) local time: " + df.format(System.currentTimeMillis()) + "</p>";
                int i = pane.getSelectionStart();
                int j = pane.getSelectionEnd();
                pane.setText(p1 + s + p2);
                pane.select(i, j);
            }
        });
        t.setInitialDelay(0);
        addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if(( e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED ) > 0) {
                    if(isDisplayable()) {
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

    private void initComponents() {
        aboutPanel = new JPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        programSplit = new JSplitPane();
        JScrollPane programScroll = new JScrollPane();
        programList = new JTree();
        JPanel programPanel = new JPanel();
        newsScroll = new JScrollPane();
        launchButton = new JButton();
        downloadPanel = new DownloadPanel();
        JMenuBar jMenuBar1 = new JMenuBar();
        JMenu jMenu1 = new JMenu();
        JMenuItem jMenuItem1 = new JMenuItem();
        JMenuItem jMenuItem3 = new JMenuItem();
        JMenu jMenu2 = new JMenu();
        JMenuItem jMenuItem2 = new JMenuItem();
        GroupLayout aboutPanelLayout = new GroupLayout(aboutPanel);
        aboutPanel.setLayout(aboutPanelLayout);
        aboutPanelLayout.setHorizontalGroup(aboutPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                            .addGap(0, 635, Short.MAX_VALUE)
                                           );
        aboutPanelLayout.setVerticalGroup(aboutPanelLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                          .addGap(0, 410, Short.MAX_VALUE)
                                         );
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("TimePath's program hub");
        programList.setModel(null);
        programList.setRootVisible(false);
        programScroll.setViewportView(programList);
        programSplit.setLeftComponent(programScroll);
        programPanel.setLayout(new BorderLayout());
        programPanel.add(newsScroll, BorderLayout.CENTER);
        launchButton.setText("Launch");
        programPanel.add(launchButton, BorderLayout.SOUTH);
        programSplit.setRightComponent(programPanel);
        tabbedPane.addTab("Programs", programSplit);
        tabbedPane.addTab("Downloads", downloadPanel);
        jMenu1.setText("Tools");
        jMenuItem1.setText("Add repository");
        jMenuItem1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jMenuItem1ActionPerformed(e);
            }
        });
        jMenu1.add(jMenuItem1);
        jMenuItem3.setText("Preferences");
        jMenuItem3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jMenuItem3ActionPerformed(e);
            }
        });
        jMenu1.add(jMenuItem3);
        jMenuBar1.add(jMenu1);
        jMenu2.setText("Help");
        jMenuItem2.setText("About");
        jMenuItem2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                jMenuItem2ActionPerformed(e);
            }
        });
        jMenu2.add(jMenuItem2);
        jMenuBar1.add(jMenu2);
        setJMenuBar(jMenuBar1);
        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(tabbedPane, GroupLayout.Alignment.TRAILING)
                                 );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                      .addComponent(tabbedPane, GroupLayout.Alignment.TRAILING)
                               );
        setBounds(0, 0, 650, 510);
    }

    private void jMenuItem2ActionPerformed(ActionEvent evt) {
        JOptionPane.showMessageDialog(this, aboutPanel);
    }

    private void jMenuItem1ActionPerformed(ActionEvent evt) {
        JOptionPane.showMessageDialog(this, repositoryManager);
    }

    private void jMenuItem3ActionPerformed(ActionEvent evt) {
        JOptionPane.showMessageDialog(this, new ThemeSelector(), "Select theme", JOptionPane.PLAIN_MESSAGE);
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
