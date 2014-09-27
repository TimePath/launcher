package com.timepath.launcher.ui.swing;

import com.timepath.launcher.data.Repository;
import com.timepath.swing.ObjectBasedTableModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
abstract class RepositoryManagerPanel extends JPanel {

    public static final String[] COLUMNS = new String[]{"Repository", "Location", "Enabled"};
    protected JButton addButton;
    protected JButton removeButton;
    protected JPanel jPanel1;
    protected JScrollPane jScrollPane1;
    protected JTable jTable1;
    protected ObjectBasedTableModel<Repository> model;

    protected RepositoryManagerPanel() {
        jTable1 = new JTable(model = new ObjectBasedTableModel<Repository>(){
            @Override
            public String[] columns() {
                return COLUMNS;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 0: return String.class;
                    case 1: return String.class;
                    case 2: return Boolean.class;
                }
                return super.getColumnClass(columnIndex);
            }

            @Override
            public Object get(Repository o, int columnIndex) {
                switch (columnIndex) {
                    case 0: return o.getName();
                    case 1: return o.getLocation();
                    case 2: return o.isEnabled();
                }
                return null;
            }

            @Override
            protected boolean isCellEditable(Repository repository, int columnIndex) {
                switch (columnIndex) {
                    case 0: return false;
                    case 1: return true;
                    case 2: return true;
                }
                return super.isCellEditable(repository, columnIndex);
            }
        });
        jScrollPane1 = new JScrollPane(jTable1);
        addButton = new JButton(new AbstractAction("Add from URL") {
            @Override
            public void actionPerformed(ActionEvent e) {
                addActionPerformed(e);
            }
        });
        removeButton = new JButton(new AbstractAction("Remove selected") {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeActionPerformed(e);
            }
        });
        jPanel1 = new JPanel();
        GroupLayout jPanel1Layout = new GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(addButton)
                                .addPreferredGap(LayoutStyle.ComponentPlacement
                                        .RELATED)
                                .addComponent(removeButton)
                                .addContainerGap(150, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel1Layout.createParallelGroup
                                        (GroupLayout.Alignment.BASELINE)
                                        .addComponent(addButton)
                                        .addComponent(removeButton))
                                .addContainerGap(GroupLayout.DEFAULT_SIZE,
                                        Short.MAX_VALUE))
        );
        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING,
                                layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                                .addComponent(jPanel1,
                                                        GroupLayout.DEFAULT_SIZE,
                                                        GroupLayout.DEFAULT_SIZE,
                                                        Short.MAX_VALUE)
                                                .addComponent(jScrollPane1,
                                                        GroupLayout.PREFERRED_SIZE,
                                                        0,
                                                        Short.MAX_VALUE))
                                        .addContainerGap()
                        )
        );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING,
                                layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 275, Short.MAX_VALUE)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jPanel1,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE)
                                        .addContainerGap()
                        )
        );
    }

    protected abstract void addActionPerformed(ActionEvent evt);

    protected abstract void removeActionPerformed(ActionEvent evt);

    public void setRepositories(List<Repository> repositories) {
        model.setRows(repositories);
    }

}
