package com.timepath.launcher.data

import com.timepath.IOUtils
import com.timepath.SwingUtils
import com.timepath.classloader.CompositeClassLoader
import com.timepath.launcher.Launcher
import com.timepath.maven.Package
import com.timepath.maven.UpdateChecker

import javax.swing.*
import java.net.MalformedURLException
import java.net.URL
import java.util.HashSet
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger
import java.awt.BorderLayout

/**
 * @author TimePath
 */
public class Program(public val `package`: Package, public val title: String, private val newsfeedURL: String?, public val main: String, args: List<String>?) {
    private val args: List<String>
    public val id: Int = autoId.getAndIncrement()
    private var starred: Boolean = false
    private var daemon: Boolean = false
    private var panel: JPanel? = null

    {
        this.args = args ?: listOf<String>()
    }

    public fun isStarred(): Boolean {
        return starred
    }

    public fun setStarred(starred: Boolean) {
        this.starred = starred
    }

    override fun toString(): String {
        return title
    }

    public fun start(context: Launcher) {
        context.update(this)
        context.start(this)
    }

    throws(javaClass<Throwable>())
    public fun run(cl: CompositeClassLoader) {
        LOG.log(Level.INFO, "Starting {0} ({1})", array(this, main))
        val argv = args.copyToArray()
        val cp = getClassPath()
        cl.start(main, argv, cp)
    }

    /**
     * @return all dependencies, flattened
     */
    private fun getClassPath(): Set<URL> {
        val all = `package`.getDownloads()
        val h = HashSet<URL>(all.size())
        for (download in all) {
            try {
                h.add(UpdateChecker.getFile(download).toURI().toURL())
            } catch (e: MalformedURLException) {
                LOG.log(Level.SEVERE, null, e)
            }

        }
        return h
    }

    public fun isDaemon(): Boolean {
        return daemon
    }

    public fun setDaemon(daemon: Boolean) {
        this.daemon = daemon
    }

    public fun getPanel(): JPanel? {
        if (panel != null) return panel
        panel = JPanel(BorderLayout())
        // Create placeholder
        val initial = JEditorPane("text", if ((newsfeedURL == null))
            "No newsfeed available"
        else
            "Loading...")
        initial.setEditable(false)
        panel!!.add(initial)
        // Load real feed in background
        if (newsfeedURL != null) {
            object : SwingWorker<JEditorPane, Void>() {
                override fun doInBackground(): JEditorPane? {
                    val s = IOUtils.requestPage(newsfeedURL)
                    val editorPane = JEditorPane("text/html", s)
                    editorPane.setEditable(false)
                    editorPane.addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER)
                    return editorPane
                }

                override fun done() {
                    try {
                        panel!!.remove(initial)
                        panel!!.add(get())
                        panel!!.updateUI()
                        LOG.log(Level.INFO, "Loaded {0}", newsfeedURL)
                    } catch (ex: InterruptedException) {
                        LOG.log(Level.SEVERE, null, ex)
                    } catch (ex: ExecutionException) {
                        LOG.log(Level.SEVERE, null, ex)
                    }

                }
            }.execute()
        }
        return panel
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<Program>().getName())
        private val autoId = AtomicInteger()
    }
}
