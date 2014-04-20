package com.timepath.launcher.ui.swing;

import com.timepath.launcher.DownloadMonitor;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.PackageFile;
import com.timepath.launcher.Program;
import com.timepath.launcher.util.Utils;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import static com.timepath.launcher.util.Utils.debug;
import static com.timepath.launcher.util.Utils.start;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class LauncherFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(LauncherFrame.class.getName());

    private Launcher launcher;

    public void display(Component c) {
        newsScroll.setViewportView(c);
    }

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

    @SuppressWarnings("unchecked")
    public void setListModel(ListModel<Program> m) {
        programList.setModel(m);
    }

    public void start(final Program program) {
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

    public LauncherFrame(final Launcher launcher) {
        Utils.lookAndFeel();
        initComponents();
        this.newsScroll.getVerticalScrollBar().setUnitIncrement(16);

        initAboutPanel();
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

        updateList();

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                launcher.shutdown();
            }
        });

        programList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if(e.getValueIsAdjusting()) {
                    return;
                }
                Program p = (Program) programList.getSelectedValue();
                if(p == null) {
                    return;
                }
                news(p);
            }
        });

        launchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Program p = (Program) programList.getSelectedValue();
                if(p == null) {
                    return;
                }
                start(p);
            }
        });

        programList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Program p = (Program) programList.getSelectedValue();
                if(p == null) {
                    return;
                }
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) {
                    start(p);
                }
            }
        });

        programList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                Program p = (Program) programList.getSelectedValue();
                if(p == null) {
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    start(p);
                }
            }
        });

        Point mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        Dimension d = new Dimension(mid.x, mid.y);
        setSize(d);
        setLocationRelativeTo(null);
    }

    private void updateList() {
        new SwingWorker<List<List<Program>>, Void>() {

            @Override
            protected List<List<Program>> doInBackground() throws Exception {
                return launcher.getListings();
            }

            @Override
            protected void done() {
                try {
                    DefaultListModel<Program> listM = new DefaultListModel<>();
                    for(List<Program> repo : get()) {
                        for(Program p : repo) {
                            listM.addElement(p);
                        }
                    }
                    setListModel(listM);
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

        tabbedPane = new javax.swing.JTabbedPane();
        programSplit = new javax.swing.JSplitPane();
        programScroll = new javax.swing.JScrollPane();
        programList = new javax.swing.JList();
        programPanel = new javax.swing.JPanel();
        newsScroll = new javax.swing.JScrollPane();
        launchButton = new javax.swing.JButton();
        downloadPanel = new com.timepath.launcher.ui.swing.DownloadPanel();
        aboutPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("TimePath's program hub");

        programSplit.setContinuousLayout(true);

        programList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        programScroll.setViewportView(programList);

        programSplit.setLeftComponent(programScroll);

        programPanel.setLayout(new java.awt.BorderLayout());
        programPanel.add(newsScroll, java.awt.BorderLayout.CENTER);

        launchButton.setText("Launch");
        programPanel.add(launchButton, java.awt.BorderLayout.SOUTH);

        programSplit.setRightComponent(programPanel);

        tabbedPane.addTab("Programs", programSplit);
        tabbedPane.addTab("Downloads", downloadPanel);

        org.jdesktop.layout.GroupLayout aboutPanelLayout = new org.jdesktop.layout.GroupLayout(aboutPanel);
        aboutPanel.setLayout(aboutPanelLayout);
        aboutPanelLayout.setHorizontalGroup(
            aboutPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 635, Short.MAX_VALUE)
        );
        aboutPanelLayout.setVerticalGroup(
            aboutPanelLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 454, Short.MAX_VALUE)
        );

        tabbedPane.addTab("About", aboutPanel);

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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JPanel aboutPanel;
    private com.timepath.launcher.ui.swing.DownloadPanel downloadPanel;
    private javax.swing.JButton launchButton;
    private javax.swing.JScrollPane newsScroll;
    private javax.swing.JList programList;
    private javax.swing.JPanel programPanel;
    private javax.swing.JScrollPane programScroll;
    private javax.swing.JSplitPane programSplit;
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
    //</editor-fold>

}
