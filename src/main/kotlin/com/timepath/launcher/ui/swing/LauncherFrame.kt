package com.timepath.launcher.ui.swing

import com.timepath.IOUtils
import com.timepath.SwingUtils
import com.timepath.launcher.Launcher
import com.timepath.launcher.LauncherUtils
import com.timepath.launcher.data.DownloadManager.DownloadMonitor
import com.timepath.launcher.data.Program
import com.timepath.launcher.data.Repository
import com.timepath.launcher.data.RepositoryManager
import com.timepath.maven.Package
import com.timepath.maven.PersistentCache
import com.timepath.swing.ThemeSelector

import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger
import java.util.prefs.BackingStoreException
import java.util.regex.Pattern
import kotlin.properties.Delegates
import java.awt.GraphicsEnvironment
import java.awt.Dimension
import java.awt.Cursor
import java.awt.BorderLayout
import java.awt.Color
import java.util.TimeZone
import java.util.Date

SuppressWarnings("serial")
public class LauncherFrame(protected var launcher: Launcher) : JFrame() {
    protected var repositoryManager: RepositoryManagerPanel = object : RepositoryManagerPanel() {

        override fun addActionPerformed(evt: ActionEvent) {
            // FIXME: showInternalInputDialog returns immediately
            val `in` = JOptionPane.showInputDialog(this@LauncherFrame.getContentPane(), "Enter URL")
            if (`in` == null) return
            val r = Repository.fromIndex(`in`)
            if (r == null) {
                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), "Invalid repository", "Invalid repository", JOptionPane.WARNING_MESSAGE)
            } else {
                RepositoryManager.addRepository(r)
                updateList()
            }
        }

        override fun removeActionPerformed(evt: ActionEvent) {
            var i = 0
            val selection = jTable1!!.getSelectedRows()
            selection.sort()
            val rows = model!!.getRows()
            for (r in rows.copyToArray()) {
                val selected = selection.binarySearch(i++) >= 0
                if (selected) RepositoryManager.removeRepository(r)
            }
            updateList()
        }
    }
    protected var aboutPanel: JPanel by Delegates.notNull()
    protected var downloadPanel: DownloadPanel by Delegates.notNull()
    protected var launchButton: JButton by Delegates.notNull()
    protected var newsScroll: JScrollPane by Delegates.notNull()
    protected var programList: JTree? = null
    protected var programSplit: JSplitPane by Delegates.notNull()
    protected var tabs: JTabbedPane by Delegates.notNull()

    ;{
        // Has to be here to catch exceptions occurring on the EDT
        Thread.setDefaultUncaughtExceptionHandler(object : Thread.UncaughtExceptionHandler {
            override fun uncaughtException(t: Thread, e: Throwable) {
                val msg = "Uncaught Exception in $t:"
                Logger.getLogger(t.getName()).log(Level.SEVERE, msg, e)
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), object : JScrollPane(object : JTextArea("$msg\n${sw.toString()}") {
                    {
                        setEditable(false)
                        setTabSize(4)
                    }
                }) {
                    {
                        val size = this@LauncherFrame.getSize()
                        size.width /= 2
                        size.height /= 2
                        setPreferredSize(size)
                    }
                }, "Uncaught Exception", JOptionPane.ERROR_MESSAGE)
            }
        })
    }

    {
        launcher.downloadManager.addListener(object : DownloadMonitor {
            var c = AtomicInteger()

            override fun onSubmit(pkgFile: Package) {
                downloadPanel.tableModel!!.add(pkgFile)
                updateTitle(c.incrementAndGet())
            }

            fun updateTitle(i: Int) {
                setTitle("Downloads${if (i > 0) " ($i)" else ""}")
            }

            fun setTitle(title: String) {
                val index = tabs.indexOfComponent(downloadPanel)
                tabs.setTitleAt(index, title)
            }

            override fun onUpdate(pkgFile: Package) {
                downloadPanel.tableModel!!.update(pkgFile)
            }

            override fun onFinish(pkgFile: Package) {
                updateTitle(c.decrementAndGet())
            }
        })
        this.initComponents()
        this.updateList()
        this.setTitle("TimePath's program hub")
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
        val mid = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint()
        this.setSize(Dimension(mid.x, mid.y))
        this.setLocationRelativeTo(null)
        LOG.log(Level.INFO, "Created UI at {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME)
    }

    /**
     * Schedule a listing update
     */
    protected fun updateList() {
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
        object : SwingWorker<MutableList<Repository>, Void>() {
            throws(javaClass<Exception>())
            override fun doInBackground(): MutableList<Repository> {
                return launcher.getRepositories(true)
            }

            override fun done() {
                try {
                    LOG.log(Level.INFO, "Listing at {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME)
                    val repos = get()
                    repositoryManager.setRepositories(repos)
                    // Update the program list
                    val rootNode = DefaultMutableTreeNode()
                    for (repo in repos) {
                        var repoNode = rootNode
                        // Create a new pseudo root node if there are multiple repositories
                        if (repos.size() > 1) {
                            repoNode = DefaultMutableTreeNode(repo.getName())
                            rootNode.add(repoNode)
                        }
                        for (p in repo.getExecutions()) {
                            repoNode.add(DefaultMutableTreeNode(p))
                        }
                    }
                    val newModel = DefaultTreeModel(rootNode)
                    programList!!.setModel(newModel)
                    val firstLeaf = rootNode.getFirstLeaf()
                    val path = TreePath(firstLeaf.getPath())
                    programList!!.expandPath(path)
                    programList!!.setSelectionPath(path)
                    pack(programSplit)
                    if (!LauncherUtils.DEBUG && launcher.updateRequired()) {
                        // Show update notification
                        JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), "Please update", "A new version is available", JOptionPane.INFORMATION_MESSAGE, null)
                    }
                } catch (e: InterruptedException) {
                    LOG.log(Level.SEVERE, null, e)
                } catch (e: ExecutionException) {
                    LOG.log(Level.SEVERE, null, e)
                } finally {
                    this@LauncherFrame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
                }
            }
        }.execute()
    }

    /**
     * Hack to pack the SplitPane
     */
    protected fun pack(programSplit: JSplitPane) {
        val pcl = object : PropertyChangeListener {
            /** Flag to ignore the first event */
            var ignore = true

            override fun propertyChange(evt: PropertyChangeEvent) {
                if (ignore) {
                    ignore = false
                    return
                }
                programSplit.removePropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, this)
                programSplit.setDividerLocation(Math.max(evt.getNewValue() as Int, programList!!.getPreferredScrollableViewportSize().width))
            }
        }
        programSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, pcl)
        programSplit.setDividerLocation(-1)
    }

    protected fun initComponents() {
        aboutPanel = object : JPanel(BorderLayout()) {
            {
                add(initAboutPanel(), BorderLayout.CENTER)
            }
        }
        setContentPane(object : JTabbedPane() {
            {
                addTab("Programs", object : JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true) {
                    {
                        setLeftComponent(JScrollPane(object : JTree(null as? TreeModel) {
                            {
                                setRootVisible(false)
                                setShowsRootHandles(true)
                                getSelectionModel().addTreeSelectionListener(object : TreeSelectionListener {
                                    override fun valueChanged(e: TreeSelectionEvent) {
                                        news(getSelected(getLastSelectedPathComponent()))
                                    }
                                })
                                addKeyListener(object : KeyAdapter() {
                                    override fun keyPressed(e: KeyEvent) {
                                        if (e.getKeyCode() == KeyEvent.VK_ENTER) start(getSelected(getLastSelectedPathComponent()))
                                    }
                                })
                                val adapter = object : MouseAdapter() {
                                    override fun mouseClicked(e: MouseEvent) {
                                        if (select(e) == -1) return
                                        if (SwingUtilities.isLeftMouseButton(e) && (e.getClickCount() >= 2)) {
                                            val p = getSelected(getLastSelectedPathComponent())
                                            start(p)
                                        }
                                    }

                                    override fun mousePressed(e: MouseEvent) {
                                        select(e)
                                    }

                                    override fun mouseDragged(e: MouseEvent) {
                                        select(e)
                                    }

                                    private fun select(e: MouseEvent): Int {
                                        val selRow = getClosestRowForLocation(e.getX(), e.getY())
                                        setSelectionRow(selRow)
                                        return selRow
                                    }
                                }
                                addMouseMotionListener(adapter)
                                addMouseListener(adapter)
                            }
                        }.let {
                            programList = it
                            it
                        }))
                        setRightComponent(object : JPanel(BorderLayout()) {
                            {
                                add(object : JScrollPane() {
                                    {
                                        getVerticalScrollBar().setUnitIncrement(16)
                                    }
                                }.let {
                                    newsScroll = it
                                    it
                                }, BorderLayout.CENTER)
                                add(JButton(object : AbstractAction("Launch") {
                                    override fun actionPerformed(e: ActionEvent) {
                                        start(getSelected(programList!!.getLastSelectedPathComponent()))
                                    }
                                }).let {
                                    launchButton = it
                                    it
                                }, BorderLayout.SOUTH)
                                launchButton.setEnabled(false)
                            }
                        })
                    }
                }.let {
                    programSplit = it
                    it
                })
                addTab("Downloads", DownloadPanel().let {
                    downloadPanel = it
                    it
                })
            }
        }.let {
            tabs = it
            it
        })
        setJMenuBar(object : JMenuBar() {
            {
                add(object : JMenu("Tools") {
                    {
                        add(JMenuItem(object : AbstractAction("Repository management") {
                            override fun actionPerformed(e: ActionEvent) {
                                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), repositoryManager, "Repository manager", JOptionPane.PLAIN_MESSAGE)
                            }
                        }))
                        add(JMenuItem(object : AbstractAction("Clear cache") {
                            override fun actionPerformed(event: ActionEvent) {
                                try {
                                    PersistentCache.drop()
                                    JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), "Restart to check for updates", "Cleared cache", JOptionPane.INFORMATION_MESSAGE)
                                } catch (e: BackingStoreException) {
                                    LOG.log(Level.WARNING, "Unable to drop caches", e)
                                    JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), "Unable to clear cache", "Error", JOptionPane.ERROR_MESSAGE)
                                }

                            }
                        }))
                        add(JMenuItem(object : AbstractAction("Preferences") {
                            override fun actionPerformed(e: ActionEvent) {
                                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), ThemeSelector(), "Select theme", JOptionPane.PLAIN_MESSAGE)
                            }
                        }))
                    }
                })
                add(object : JMenu("Help") {
                    {
                        add(JMenuItem(object : AbstractAction("About") {
                            override fun actionPerformed(e: ActionEvent) {
                                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), aboutPanel)
                            }
                        }))
                    }
                })
            }
        })
    }

    /**
     * Display the news for a program
     */
    protected fun news(p: Program?) {
        if (p == null) {
            // Handle things other than programs
            newsScroll.setViewportView(null)
            launchButton.setEnabled(false)
        } else {
            newsScroll.setViewportView(p.getPanel())
            launchButton.setEnabled(!Launcher.isLocked(p.`package`))
        }
    }

    protected fun start(program: Program?) {
        if (program == null) return  // Handle things other than programs
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
        launchButton.setEnabled(false)
        object : SwingWorker<Set<Package>, Void>() {
            override fun doInBackground(): Set<Package>? {
                return launcher.update(program)
            }

            override fun done() {
                try {
                    val updates = get()
                    if (updates != null) {
                        // Ready to start
                        val parent = program.`package`
                        var run = true
                        if (!LauncherUtils.DEBUG && parent.isSelf()) {
                            // Alert on self update
                            if (updates.contains(parent)) {
                                run = false
                                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), "Restart to apply", "Update downloaded", JOptionPane.INFORMATION_MESSAGE, null)
                            } else {
                                run = false
                                JOptionPane.showInternalMessageDialog(this@LauncherFrame.getContentPane(), "Launcher is up to date", "Launcher is up to date", JOptionPane.INFORMATION_MESSAGE, null)
                            }
                        }
                        if (run) {
                            launcher.start(program)
                        }
                    }
                } catch (e: InterruptedException) {
                    LOG.log(Level.SEVERE, null, e)
                } catch (e: ExecutionException) {
                    LOG.log(Level.SEVERE, null, e)
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
                    launchButton.setEnabled(true)
                }
            }
        }.execute()
    }

    protected fun initAboutPanel(): JEditorPane {
        val pane = object : JEditorPane("text/html", "") {
            {
                setEditable(false)
                setOpaque(false)
                setBackground(Color(255, 255, 255, 0))
                addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER)
            }
        }
        var buildDate = "unknown"
        val time = LauncherUtils.CURRENT_VERSION
        val df = SimpleDateFormat("EEE dd MMM yyyy, hh:mm:ss a z")
        if (time != 0L) buildDate = df.format(Date(time))
        val aboutText = IOUtils.requestPage(javaClass.getResource("/com/timepath/launcher/ui/swing/about.html").toString())!!
                .replace("\${buildDate}", buildDate)
                .replace("\${steamGroup}", "http://steamcommunity.com/gid/103582791434775526")
                .replace("\${steamChat}", "steam://friends/joinchat/103582791434775526")
        val split = aboutText.split(Pattern.quote("\${localtime}"))
        pane.setText("${split[0]}calculating...${split[1]}")
        df.setTimeZone(TimeZone.getTimeZone("Australia/Sydney"))
        val timer = Timer(1000, object : ActionListener {
            override fun actionPerformed(e: ActionEvent) {
                val time = df.format(System.currentTimeMillis())
                SwingUtilities.invokeLater(object : Runnable {
                    override fun run() {
                        val i = pane.getSelectionStart()
                        val j = pane.getSelectionEnd()
                        pane.setText("${split[0]}$time${split[1]}")
                        pane.select(i, j)
                    }
                })
            }
        })
        timer.setInitialDelay(0)
        addHierarchyListener(object : HierarchyListener {
            override fun hierarchyChanged(e: HierarchyEvent) {
                if ((e.getChangeFlags() and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) == 0L) return
                if (isDisplayable()) {
                    timer.start()
                } else {
                    timer.stop()
                }
            }
        })
        return pane
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<LauncherFrame>().getName())

        /**
         * Get a program from a TreeNode
         */
        protected fun getSelected(selected: Any): Program? {
            if (selected !is DefaultMutableTreeNode) return null
            val obj = (selected).getUserObject()
            return if (obj is Program) obj else null
        }
    }
}
