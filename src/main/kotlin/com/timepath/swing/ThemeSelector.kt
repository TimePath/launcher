package com.timepath.swing

import com.timepath.launcher.LauncherUtils
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.*

/**
 * @author TimePath
 */
SuppressWarnings("serial")
public class ThemeSelector : JComboBox<String>() {

    init {
        val model = DefaultComboBoxModel<String>()
        setModel(model)
        val currentLafClass = UIManager.getLookAndFeel().javaClass.getName()
        for (lafInfo in UIManager.getInstalledLookAndFeels()) {
            try {
                Class.forName(lafInfo.getClassName())
            } catch (ignored: ClassNotFoundException) {
                continue // Registered but not found on classpath
            }

            val name = lafInfo.getName()
            model.addElement(name)
            if (lafInfo.getClassName() == currentLafClass) {
                model.setSelectedItem(name)
            }
        }
        addActionListener(object : ActionListener {
            override fun actionPerformed(evt: ActionEvent) {
                val target = model.getSelectedItem() as String
                for (info in UIManager.getInstalledLookAndFeels()) {
                    if (target == info.getName()) {
                        LOG.log(Level.INFO, "Setting L&F: {0}", info.getClassName())
                        try {
                            val usrTheme = info.getClassName()
                            UIManager.setLookAndFeel(usrTheme)
                            for (w in Window.getWindows()) {
                                // TODO: Instrumentation to access detached components
                                SwingUtilities.updateComponentTreeUI(w)
                            }
                            LauncherUtils.SETTINGS.put("laf", usrTheme)
                            return
                        } catch (e: InstantiationException) {
                            LOG.log(Level.SEVERE, null, e)
                        } catch (e: IllegalAccessException) {
                            LOG.log(Level.SEVERE, null, e)
                        } catch (e: UnsupportedLookAndFeelException) {
                            LOG.log(Level.SEVERE, null, e)
                        } catch (e: ClassNotFoundException) {
                            LOG.log(Level.WARNING, "Unable to load user L&F", e)
                        }

                    }
                }
            }
        })
    }

    companion object {

        private val LOG = Logger.getLogger(javaClass<ThemeSelector>().getName())
    }
}
