package com.timepath.launcher.ui.swing;

import com.timepath.launcher.PackageFile;
import com.timepath.swing.table.ObjectBasedTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class DownloadPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(DownloadPanel.class.getName());
    ObjectBasedTableModel<PackageFile> tableModel;
    private JTable jTable1;

    public DownloadPanel() {
        initComponents();
        DefaultTableModel model;
        tableModel = new ObjectBasedTableModel<PackageFile>() {
            @Override
            public String[] columns() {
                return new String[] { "Name", "Progress" };
            }

            @Override
            public Object get(PackageFile o, int columnIndex) {
                switch(columnIndex) {
                    case 0:
                        return o.fileName();
                    case 1:
                        double percent = ( o.progress * 100.0 ) / o.size;
                        return ( percent >= 0 ) ? String.format("%.1f%%", percent) : '?';
                    case 2:
                        return o.size;
                    default:
                        return null;
                }
            }
        };
        jTable1.setModel(tableModel);
    }

    private void initComponents() {
        JScrollPane jScrollPane1 = new JScrollPane();
        jTable1 = new JTable();
        jTable1.setModel(new DefaultTableModel(new Object[][] {
        }, new String[] {
        }
        ));
        jScrollPane1.setViewportView(jTable1);
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
                                 );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                      .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                               );
    }
}
