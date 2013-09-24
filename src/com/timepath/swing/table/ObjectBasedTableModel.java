package com.timepath.swing.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author TimePath
 */
public abstract class ObjectBasedTableModel<O> extends AbstractTableModel {

    public ObjectBasedTableModel() {
        
    }
    
    public abstract String[] columns();

    private ArrayList<O> rows = new ArrayList<O>();
    
    private List<String> columns = Arrays.asList(columns());

    /**
     * Add Object o to the model
     * @param o the Object
     * @return true if added
     */
    public boolean add(O o) {
        int idx = rows.indexOf(o);
        if(idx >= 0) {
            return false;
        }
        rows.add(o);
        this.fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        return true;
    }
    
    /**
     * Fire update for Object o in the model
     * @param o the Object
     * @return true if not updated (because the Object isn't in the model)
     */
    public boolean update(O o) {
        int idx = rows.indexOf(o);
        if(idx < 0) {
            return false;
        }
        this.fireTableRowsUpdated(idx, idx);
        return true;
    }

    /**
     * Remove Object o from the model
     * @param o the Object
     * @return true if removed
     */
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

    /**
     * Gets a property from an Object based on an index
     * @param o the Object
     * @param columnIndex index to Object property
     * @return the property
     */
    public abstract Object get(O o, int columnIndex);

}
