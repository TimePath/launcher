package com.timepath.swing;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @param <E>
 * @author TimePath
 */
@SuppressWarnings("serial")
public abstract class ObjectBasedTableModel<E> extends AbstractTableModel {

    private final List<String> columns = Arrays.asList(columns());
    private List<E> rows = new ArrayList<>();

    protected ObjectBasedTableModel() {
    }

    public List<E> getRows() {
        return rows;
    }

    public void setRows(List<E> rows) {
        fireTableRowsDeleted(0, Math.max(this.rows.size() - 1, 0));
        this.rows = rows;
        fireTableRowsInserted(0, Math.max(this.rows.size() - 1, 0));
    }

    /**
     * Add Object o to the model
     *
     * @param o the Object
     * @return true if added
     */
    public boolean add(E o) {
        int idx = rows.indexOf(o);
        if (idx >= 0) {
            return false;
        }
        rows.add(o);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        return true;
    }

    public abstract String[] columns();

    @Override
    public String getColumnName(int column) {
        if (column < columns.size()) {
            String name = columns.get(column);
            if (name != null) return name;
        }
        return super.getColumnName(column);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return get(rows.get(rowIndex), columnIndex);
    }

    /**
     * Gets a property from an Object based on an index
     *
     * @param o           the Object
     * @param columnIndex index to Object property
     * @return the property
     */
    public abstract Object get(E o, int columnIndex);

    /**
     * Remove Object o from the model
     *
     * @param o the Object
     * @return true if removed
     */
    public boolean remove(E o) {
        int idx = rows.indexOf(o);
        if (idx < 0) {
            return false;
        }
        rows.remove(idx);
        fireTableRowsDeleted(idx, idx);
        return true;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return isCellEditable(rows.get(rowIndex), columnIndex);
    }

    protected boolean isCellEditable(E e, int columnIndex) {
        return false;
    }

    /**
     * Fire update for Object o in the model
     *
     * @param o the Object
     * @return true if not updated (because the Object isn't in the model)
     */
    public boolean update(E o) {
        int idx = rows.indexOf(o);
        if (idx < 0) {
            return false;
        }
        fireTableRowsUpdated(idx, idx);
        return true;
    }
}
