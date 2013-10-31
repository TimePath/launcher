package com.timepath.launcher;

import com.timepath.launcher.DownloadManager.Download;
import java.awt.Component;
import java.awt.event.*;
import java.util.concurrent.Future;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 * @author TimePath
 */
public abstract class Launcher extends JFrame {

    public abstract void news(final Program p);

    public abstract void start(final Program p);

    public Future<?> download(Download d) {
        return downloadManager.submit(d);
    }

    public void display(Component c) {
        newsScroll.setViewportView(c);
    }

    public void setListModel(ListModel<Program> m) {
        programList.setModel(m);
    }

    public Launcher() {
        initComponents();

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                downloadManager.shutdown();
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

        programLaunch.addActionListener(new ActionListener() {
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
        programLaunch = new javax.swing.JButton();
        downloadManager = new com.timepath.launcher.DownloadManager();
        aboutPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("TimePath's program hub");

        programSplit.setContinuousLayout(true);

        programList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        programScroll.setViewportView(programList);

        programSplit.setLeftComponent(programScroll);

        programPanel.setLayout(new java.awt.BorderLayout());
        programPanel.add(newsScroll, java.awt.BorderLayout.CENTER);

        programLaunch.setText("Launch");
        programPanel.add(programLaunch, java.awt.BorderLayout.SOUTH);

        programSplit.setRightComponent(programPanel);

        tabbedPane.addTab("Programs", programSplit);
        tabbedPane.addTab("Downloads", downloadManager);

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
    private com.timepath.launcher.DownloadManager downloadManager;
    private javax.swing.JScrollPane newsScroll;
    private javax.swing.JButton programLaunch;
    private javax.swing.JList programList;
    private javax.swing.JPanel programPanel;
    private javax.swing.JScrollPane programScroll;
    private javax.swing.JSplitPane programSplit;
    private javax.swing.JTabbedPane tabbedPane;
    // End of variables declaration//GEN-END:variables
    //</editor-fold>

}
