package com.timepath.launcher.ui.swing;

import com.timepath.launcher.*;
import com.timepath.launcher.util.Utils;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import static com.timepath.launcher.util.Utils.debug;
import static com.timepath.launcher.util.Utils.start;

@SuppressWarnings("serial")
public class LauncherFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(LauncherFrame.class.getName());

    private Launcher launcher;

    private RepositoryManagerImpl repositoryManager;

    public void display(Component c) {
        newsScroll.setViewportView(c);
    }

    public void news(final Program p) {
        if(p == null) {
            display(null);
            this.launchButton.setEnabled(false);
            return;
        }
        this.launchButton.setEnabled(!p.lock);
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
                    j.addHyperlinkListener(Utils.linkListener);
                    return j;
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

    public void setListModel(DefaultTreeModel m) {
        programList.setModel(m);
    }

    public void start(final Program program) {
        if(program == null) {
            return;
        }
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                LauncherFrame.this.launchButton.setEnabled(false);
                launcher.start(program);
                return null;
            }

            @Override
            protected void done() {
                LauncherFrame.this.launchButton.setEnabled(true);
            }

        }.execute();

    }

    private Program getSelected(Object selected) {
        if(!(selected instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Object obj = ((DefaultMutableTreeNode) selected).getUserObject();
        if(!(obj instanceof Program)) {
            return null;
        }
        return (Program) obj;
    }

    public LauncherFrame(final Launcher launcher) {
        initComponents();
        initAboutPanel();
        this.newsScroll.getVerticalScrollBar().setUnitIncrement(16);

        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - start);

        this.launcher = launcher;
        launcher.getDownloadManager().addListener(new DownloadMonitor() {

            @Override
            public void submit(PackageFile d) {
                LauncherFrame.this.downloadPanel.tableModel.add(d);
            }

            @Override
            public void update(PackageFile d) {
                LauncherFrame.this.downloadPanel.tableModel.update(d);
            }
        });

        repositoryManager = new RepositoryManagerImpl();

        updateList();

        this.addWindowListener(new WindowAdapter() {
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
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    Program p = getSelected(programList.getLastSelectedPathComponent());
                    start(p);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                select(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
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
        Dimension d = new Dimension(mid.x, mid.y);
        setSize(d);
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
                    DefaultTreeModel listM = new DefaultTreeModel(rootNode);
                    for(int i = repositoryManager.model.getRowCount(); i > 0; ) {
                        repositoryManager.model.removeRow(--i);
                    }
                    for(Repository repo : repos) {
                        repositoryManager.model.addRow(new Object[] {repo, repo.isEnabled()});

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
                            programSplit.removePropertyChangeListener(
                                JSplitPane.DIVIDER_LOCATION_PROPERTY, this);
                            programSplit.setDividerLocation(Math.max(
                                (int) evt.getNewValue(),
                                programList.getPreferredScrollableViewportSize().width));
                        }
                    };
                    programSplit
                        .addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, pcl);
                    programSplit.setDividerLocation(-1);

                    LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - start);
                    if(!debug && !launcher.selfCheck()) {
                        JOptionPane.showMessageDialog(LauncherFrame.this,
                                                      "Please update", "A new version is available",
                                                      JOptionPane.INFORMATION_MESSAGE, null);
                    }
                } catch(InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }.execute();
    }

    private void initAboutPanel() {
        final String latestThread = "http://steamcommunity.com/gid/103582791434775526/discussions";
        final JEditorPane pane = new JEditorPane("text/html", "");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBackground(new Color(0, 0, 0, 0));
        pane.addHyperlinkListener(Utils.linkListener);
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

            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) > 0) {
                    if(LauncherFrame.this.isDisplayable()) {
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

    //<editor-fold defaultstate="collapsed" desc="Generated Code">
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        aboutPanel = new javax.swing.JPanel();
        tabbedPane = new javax.swing.JTabbedPane();
        programSplit = new javax.swing.JSplitPane();
        programScroll = new javax.swing.JScrollPane();
        programList = new javax.swing.JTree();
        programPanel = new javax.swing.JPanel();
        newsScroll = new javax.swing.JScrollPane();
        launchButton = new javax.swing.JButton();
        downloadPanel = new com.timepath.launcher.ui.swing.DownloadPanel();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        jMenuItem2 = new javax.swing.JMenuItem();

        org.jdesktop.layout.GroupLayout aboutPanelLayout = new org.jdesktop.layout.GroupLayout(aboutPanel);
        aboutPanel.setLayout(aboutPanelLayout);
        aboutPanelLayout.setHorizontalGroup(
            aboutPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 635, Short.MAX_VALUE)
        );
        aboutPanelLayout.setVerticalGroup(
            aboutPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 410, Short.MAX_VALUE)
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("TimePath's program hub");

        programSplit.setContinuousLayout(true);

        programList.setModel(null);
        programList.setRootVisible(false);
        programList.setShowsRootHandles(true);
        programScroll.setViewportView(programList);

        programSplit.setLeftComponent(programScroll);

        programPanel.setLayout(new java.awt.BorderLayout());
        programPanel.add(newsScroll, java.awt.BorderLayout.CENTER);

        launchButton.setText("Launch");
        programPanel.add(launchButton, java.awt.BorderLayout.SOUTH);

        programSplit.setRightComponent(programPanel);

        tabbedPane.addTab("Programs", programSplit);
        tabbedPane.addTab("Downloads", downloadPanel);

        jMenu1.setText("Tools");

        jMenuItem1.setText("Add repository");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem1);

        jMenuItem3.setText("Preferences");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Help");

        jMenuItem2.setText("About");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        jMenu2.add(jMenuItem2);

        jMenuBar1.add(jMenu2);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, tabbedPane)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, tabbedPane)
        );

        setBounds(0, 0, 650, 510);
    }// </editor-fold>//GEN-END:initComponents

    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
        JOptionPane.showMessageDialog(this, aboutPanel);
    }//GEN-LAST:event_jMenuItem2ActionPerformed

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        JOptionPane.showMessageDialog(this, repositoryManager);
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
        JOptionPane.showMessageDialog(this, new ThemeSelector(), "Select theme", JOptionPane.PLAIN_MESSAGE);
    }//GEN-LAST:event_jMenuItem3ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JPanel aboutPanel;
    private com.timepath.launcher.ui.swing.DownloadPanel downloadPanel;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenu jMenu2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JButton launchButton;
    private javax.swing.JScrollPane newsScroll;
    private javax.swing.JTree programList;
    private javax.swing.JPanel programPanel;
    private javax.swing.JScrollPane programScroll;
    private javax.swing.JSplitPane programSplit;
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
    //</editor-fold>

    private class RepositoryManagerImpl extends RepositoryManager {

        DefaultTableModel model = (DefaultTableModel) this.jTable1.getModel();

        RepositoryManagerImpl() {
            super();
            model.setColumnCount(1);
        }
        

        @Override
        protected void addActionPerformed(ActionEvent evt) {
            String in = JOptionPane.showInputDialog(LauncherFrame.this, "Enter URL");
            if(in == null) {
                return;
            }
            Repository r = new Repository(in);
            launcher.addRepository(r);
            updateList();
        }

        @Override
        protected void removeActionPerformed(ActionEvent evt) {
            for(int row : this.jTable1.getSelectedRows()) {
                launcher.removeRepository((Repository) model.getValueAt(row, 0));
            }
            updateList();
        }

    }

}
