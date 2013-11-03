package com.timepath.swing.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author TimePath
 * @param <O>
 */
@SuppressWarnings("serial")
public abstract class ObjectBasedTableModel<O> extends AbstractTableModel {

    private final List<String> columns = Arrays.asList(columns());

    private final ArrayList<O> rows = new ArrayList<O>();

    public ObjectBasedTableModel() {

    }

    /**
     * Add Object o to the model
     * <p>
     * @param o the Object
     * <p>
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

    public abstract String[] columns();

    /**
     * Gets a property from an Object based on an index
     * <p>
     * @param o           the Object
     * @param columnIndex index to Object property
     * <p>
     * @return the property
     */
    public abstract Object get(O o, int columnIndex);

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return super.getColumnClass(columnIndex);
    }

    public int getColumnCount() {
        return columns.size();
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

    public int getRowCount() {
        return rows.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return get(rows.get(rowIndex), columnIndex);
    }

    /**
     * Remove Object o from the model
     * <p>
     * @param o the Object
     * <p>
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

    /**
     * Fire update for Object o in the model
     * <p>
     * @param o the Object
     * <p>
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

}
