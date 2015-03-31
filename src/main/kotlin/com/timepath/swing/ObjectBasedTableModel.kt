package com.timepath.swing


import java.util.ArrayList
import java.util.Arrays
import javax.swing.table.AbstractTableModel

/**
 * @param <E>
 * @author TimePath
 */
SuppressWarnings("serial")
public abstract class ObjectBasedTableModel<E> protected() : AbstractTableModel() {

    private val columns = Arrays.asList<String>(*columns())
    private var rows: MutableList<E> = ArrayList()

    public fun getRows(): List<E> {
        return rows
    }

    public fun setRows(rows: MutableList<E>) {
        fireTableRowsDeleted(0, Math.max(this.rows.size() - 1, 0))
        this.rows = rows
        fireTableRowsInserted(0, Math.max(this.rows.size() - 1, 0))
    }

    /**
     * Add Object o to the model
     *
     * @param o the Object
     * @return true if added
     */
    public fun add(o: E): Boolean {
        val idx = rows.indexOf(o)
        if (idx >= 0) {
            return false
        }
        rows.add(o)
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1)
        return true
    }

    public abstract fun columns(): Array<String>

    override fun getColumnName(column: Int): String {
        if (column < columns.size()) {
            val name = columns[column]
            if (name != null) return name
        }
        return super.getColumnName(column)
    }

    override fun getRowCount(): Int {
        return rows.size()
    }

    override fun getColumnCount(): Int {
        return columns.size()
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        return get(rows[rowIndex], columnIndex)
    }

    /**
     * Gets a property from an Object based on an index
     *
     * @param o           the Object
     * @param columnIndex index to Object property
     * @return the property
     */
    public abstract fun get(o: E, columnIndex: Int): Any?

    /**
     * Remove Object o from the model
     *
     * @param o the Object
     * @return true if removed
     */
    public fun remove(o: E): Boolean {
        val idx = rows.indexOf(o)
        if (idx < 0) {
            return false
        }
        rows.remove(idx)
        fireTableRowsDeleted(idx, idx)
        return true
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return isCellEditable(rows[rowIndex], columnIndex)
    }

    protected open fun isCellEditable(e: E, columnIndex: Int): Boolean {
        return false
    }

    /**
     * Fire update for Object o in the model
     *
     * @param o the Object
     * @return true if not updated (because the Object isn't in the model)
     */
    public fun update(o: E): Boolean {
        val idx = rows.indexOf(o)
        if (idx < 0) {
            return false
        }
        fireTableRowsUpdated(idx, idx)
        return true
    }
}
