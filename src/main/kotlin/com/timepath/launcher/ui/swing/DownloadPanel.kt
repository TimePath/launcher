package com.timepath.launcher.ui.swing

import com.timepath.maven.Package
import com.timepath.maven.UpdateChecker
import com.timepath.swing.ObjectBasedTableModel

import javax.swing.*

/**
 * @author TimePath
 */
SuppressWarnings("serial")
class DownloadPanel : JPanel() {

    public var tableModel: ObjectBasedTableModel<Package>? = null
        protected set

    init {
        val jTable1 = JTable()
        jTable1.setModel(object : ObjectBasedTableModel<Package>() {
            override fun columns(): Array<String> {
                return array("Name", "Progress")
            }

            override fun get(o: Package, columnIndex: Int): Any? {
                when (columnIndex) {
                    0 -> return UpdateChecker.getFileName(o)
                    1 -> {
                        if (o.size <= 0) return ""
                        return "%s / %s (%.1f%%)".format(human(o.progress.toDouble()), human(o.size.toDouble()), (o.progress.toDouble() * 100.0) / o.size.toDouble())
                    }
                    2 -> return o.size
                    else -> return null
                }
            }
        }.let {
            tableModel = it
            it
        })
        val jScrollPane1 = JScrollPane(jTable1)
        val layout = GroupLayout(this)
        setLayout(layout)
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 400, java.lang.Short.MAX_VALUE.toInt()))
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 300, java.lang.Short.MAX_VALUE.toInt()))
    }

    companion object {

        public fun human(count: Double): String {
            val factor = 1000
            val multiples = array("bytes", "KB", "MB", "GB")
            val i = (Math.log(count) / Math.log(factor.toDouble()))
            return try {
                "%.1f %s".format(count / factor * i, multiples[i.toInt()])
            } catch(e: ArrayIndexOutOfBoundsException) {
                "?"
            }
        }
    }
}
