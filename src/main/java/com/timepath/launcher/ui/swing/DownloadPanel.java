package com.timepath.launcher.ui.swing;

import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import com.timepath.swing.ObjectBasedTableModel;

import javax.swing.*;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class DownloadPanel extends JPanel {

    protected ObjectBasedTableModel<Package> tableModel;

    public DownloadPanel() {
        final JTable jTable1 = new JTable();
        jTable1.setModel(tableModel = new ObjectBasedTableModel<Package>() {
            @Override
            public String[] columns() {
                return new String[]{"Name", "Progress"};
            }

            @Override
            public Object get(Package o, int columnIndex) {
                switch (columnIndex) {
                    case 0:
                        return UpdateChecker.getFileName(o);
                    case 1:
                        if (o.size <= 0) return "";
                        return String.format("%s / %s (%.1f%%)",
                                human(o.progress), human(o.size), (o.progress * 100.0d) / o.size);
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
                .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE));
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE));
    }

    public static String human(double count) {
        int factor = 1000;
        String[] multiples = {"KB", "MB", "GB"};
        int i = 0;
        while ((count /= factor) >= factor) i++;
        return String.format("%.1f %s", count, multiples[i]);
    }

    public ObjectBasedTableModel<Package> getTableModel() {
        return tableModel;
    }
}
