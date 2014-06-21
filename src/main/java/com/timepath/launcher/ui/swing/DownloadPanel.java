package com.timepath.launcher.ui.swing;

import com.timepath.maven.Package;
import com.timepath.launcher.ui.swing.table.ObjectBasedTableModel;

import javax.swing.*;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class DownloadPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(DownloadPanel.class.getName());
    protected ObjectBasedTableModel<Package> tableModel;

    public DownloadPanel() {
        initComponents();
    }

    private void initComponents() {
        final JTable jTable1 = new JTable();
        jTable1.setModel(tableModel = new ObjectBasedTableModel<Package>() {
            @Override
            public String[] columns() {
                return new String[] { "Name", "Progress" };
            }

            @Override
            public Object get(Package o, int columnIndex) {
                switch(columnIndex) {
                    case 0:
                        return o.getFileName();
                    case 1:
                        double percent = ( o.progress * 100.0 ) / o.size;
                        return ( percent < 0 ) ? '?' : String.format("%.1f%%", percent);
                    case 2:
                        return o.size;
                    default:
                        return null;
                }
            }
        });
        JScrollPane jScrollPane1 = new JScrollPane(jTable1);
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
                                 );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                      .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                               );
    }

    public ObjectBasedTableModel<Package> getTableModel() {
        return tableModel;
    }
}
