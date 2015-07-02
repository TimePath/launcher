package com.timepath.launcher.ui.swing

import com.timepath.launcher.data.Repository
import com.timepath.swing.ObjectBasedTableModel
import java.awt.event.ActionEvent
import javax.swing.*

abstract class RepositoryManagerPanel protected constructor() : JPanel() {
    protected var addButton: JButton
    protected var removeButton: JButton
    protected var jPanel1: JPanel
    protected var jScrollPane1: JScrollPane? = null
    protected var jTable1: JTable? = null
    protected var model: ObjectBasedTableModel<Repository>? = null

    init {
        jTable1 = JTable(object : ObjectBasedTableModel<Repository>() {
            override fun columns(): Array<String> {
                return COLUMNS
            }

            override fun getColumnClass(columnIndex: Int): Class<*> {
                when (columnIndex) {
                    0 -> return javaClass<String>()
                    1 -> return javaClass<String>()
                    2 -> return javaClass<Boolean>()
                }
                return super.getColumnClass(columnIndex)
            }

            override fun get(o: Repository, columnIndex: Int): Any? {
                when (columnIndex) {
                    0 -> return o.getName()
                    1 -> return o.location
                    2 -> return o.isEnabled()
                }
                return null
            }

            override fun isCellEditable(e: Repository, columnIndex: Int) = when (columnIndex) {
                0 -> false
                1 -> true
                2 -> true
                else -> super.isCellEditable(e, columnIndex)
            }
        }.let {
            model = it
            it
        })
        jScrollPane1 = JScrollPane(jTable1)
        addButton = JButton(object : AbstractAction("Add from URL") {
            override fun actionPerformed(e: ActionEvent) {
                addActionPerformed(e)
            }
        })
        removeButton = JButton(object : AbstractAction("Remove selected") {
            override fun actionPerformed(e: ActionEvent) {
                removeActionPerformed(e)
            }
        })
        jPanel1 = JPanel()
        val jPanel1Layout = GroupLayout(jPanel1)
        jPanel1.setLayout(jPanel1Layout)
        jPanel1Layout.setHorizontalGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(jPanel1Layout.createSequentialGroup().addContainerGap().addComponent(addButton).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(removeButton).addContainerGap(150, java.lang.Short.MAX_VALUE.toInt())))
        jPanel1Layout.setVerticalGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(jPanel1Layout.createSequentialGroup().addContainerGap().addGroup(jPanel1Layout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(addButton).addComponent(removeButton)).addContainerGap(GroupLayout.DEFAULT_SIZE, java.lang.Short.MAX_VALUE.toInt())))
        val layout = GroupLayout(this)
        setLayout(layout)
        layout.setHorizontalGroup(
                layout.createParallelGroup(
                        GroupLayout.Alignment.LEADING)
                        .addGroup(
                                GroupLayout.Alignment.TRAILING,
                                layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(
                                                layout.createParallelGroup(
                                                        GroupLayout.Alignment.TRAILING)
                                                        .addComponent(
                                                                jPanel1,
                                                                GroupLayout.DEFAULT_SIZE,
                                                                GroupLayout.DEFAULT_SIZE,
                                                                java.lang.Short.MAX_VALUE.toInt())
                                                        .addComponent(
                                                                jScrollPane1!!,
                                                                GroupLayout.PREFERRED_SIZE,
                                                                0,
                                                                java.lang.Short.MAX_VALUE.toInt()))
                                        .addContainerGap()))
        layout.setVerticalGroup(
                layout.createParallelGroup(
                        GroupLayout.Alignment.LEADING)
                        .addGroup(
                                GroupLayout.Alignment.TRAILING,
                                layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(
                                                jScrollPane1!!,
                                                GroupLayout.DEFAULT_SIZE,
                                                275,
                                                java.lang.Short.MAX_VALUE.toInt())
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jPanel1,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE).
                                        addContainerGap()))
    }

    protected abstract fun addActionPerformed(evt: ActionEvent)

    protected abstract fun removeActionPerformed(evt: ActionEvent)

    public fun setRepositories(repositories: MutableList<Repository>) {
        model!!.setRows(repositories)
    }

    companion object {

        public val COLUMNS: Array<String> = arrayOf("Repository", "Location", "Enabled")
    }

}
