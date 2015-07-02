package com.timepath.launcher.data

import com.timepath.SwingUtils
import com.timepath.classloader.CompositeClassLoader
import com.timepath.launcher.Launcher
import com.timepath.maven.Package
import com.timepath.maven.UpdateChecker
import java.awt.BorderLayout
import java.io.IOException
import java.io.InputStream
import java.net.*
import java.util.HashSet
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingWorker

public class Program(public val `package`: Package, public val title: String, private val newsfeedURL: String?, public val main: String, args: List<String>?) {
    private val args: List<String>
    public val id: Int = autoId.getAndIncrement()
    private var starred: Boolean = false
    private var daemon: Boolean = false
    private var panel: JPanel? = null

    init {
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

    public fun run(cl: CompositeClassLoader) {
        LOG.log(Level.INFO, "Starting {0} ({1})", arrayOf(this, main))
        val argv = args.toTypedArray()
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

    fun URL.openFollowRedirects(proxy: Proxy = Proxy.NO_PROXY): InputStream {
        val conn = openConnection(proxy) as HttpURLConnection
        return when (conn.getResponseCode()) {
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER -> {
                URI(conn.getHeaderField("Location")).toURL().openFollowRedirects(proxy)
            }
            else -> conn.getInputStream()
        }
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
                    val s = try {
                        URI(newsfeedURL).toURL().openFollowRedirects().bufferedReader().readText()
                    } catch (ignored: IOException) {
                        null
                    }
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

    companion object {

        private val LOG = Logger.getLogger(javaClass<Program>().getName())
        private val autoId = AtomicInteger()
    }
}
