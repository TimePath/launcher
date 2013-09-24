package com.timepath.swing.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author TimePath
 */
public abstract class ObjectTableModel<O> extends AbstractTableModel {

    public abstract String[] columns();

    private ArrayList<O> rows = new ArrayList<O>();

    public boolean add(O o) {
        int idx = rows.indexOf(o);
        if(idx >= 0) {
            return false;
        }
        rows.add(o);
        this.fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        return true;
    }
    
    public boolean update(O o) {
        int idx = rows.indexOf(o);
        if(idx < 0) {
            return false;
        }
        this.fireTableRowsUpdated(idx, idx);
        return true;
    }

    public boolean remove(O o) {
        int idx = rows.indexOf(o);
        if(idx < 0) {
            return false;
        }
        rows.remove(idx);
        this.fireTableRowsDeleted(idx, idx);
        return true;
    }

    public int getRowCount() {
        return rows.size();
    }

    List<String> columns = Arrays.asList(columns());

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return super.getColumnClass(columnIndex);
    }

    @Override
    public String getColumnName(int column) {
        if(column < columns.size()) {
            String name = columns.get(column);
            if(name != null) {
                return name;
            }
        }
        return super.getColumnName(column);
    }

    public int getColumnCount() {
        return columns.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return get(rows.get(rowIndex), columnIndex);
    }

    public abstract Object get(O o, int columnIndex);

}
