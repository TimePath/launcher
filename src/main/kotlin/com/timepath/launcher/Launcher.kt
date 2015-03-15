package com.timepath.launcher

import com.timepath.classloader.CompositeClassLoader
import com.timepath.launcher.data.DownloadManager
import com.timepath.launcher.data.Program
import com.timepath.launcher.data.Repository
import com.timepath.launcher.data.RepositoryManager
import com.timepath.maven.Package
import com.timepath.maven.UpdateChecker
import com.timepath.util.Cache

import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.logging.Level
import java.util.logging.Logger
import java.util.prefs.Preferences

/**
 * @author TimePath
 */
public class Launcher {
    private val cl = CompositeClassLoader.createPrivileged()
    public val downloadManager: DownloadManager = DownloadManager()
    private var self: Package? = null
    private var repositories: MutableList<Repository>? = null

    /**
     * @return fetch and return a list of all repositories
     */
    public fun getRepositories(override: Boolean): MutableList<Repository> {
        if (!override && repositories != null) return repositories!!
        repositories = LinkedList<Repository>()
        val main = Repository.fromIndex(REPO_MAIN)
        main!!.setEnabled(true)
        repositories!!.add(main)
        self = main.self
        repositories!!.addAll(RepositoryManager.loadCustom())
        return repositories!!
    }

    public fun getRepositories(): List<Repository> {
        return getRepositories(false)
    }

    /**
     * @return true if self is up to date
     */
    public fun updateRequired(): Boolean {
        return !UpdateChecker.verify(self!!, LauncherUtils.CURRENT_FILE)
    }

    /**
     * Starts a program. Returns after started; quickly if the target defers to the EDT
     *
     * @param program
     */
    public fun start(program: Program) {
        val t = Thread(object : Runnable {
            override fun run() {
                try {
                    program.run(cl)
                } catch (throwable: Throwable) {
                    throw RuntimeException(throwable)
                }

            }
        })
        t.setName(program.toString())
        t.setDaemon(program.isDaemon())
        t.setContextClassLoader(cl)
        t.start()
    }

    /**
     * @param program
     * @return a Set of updated {@code Package}s, or null if currently updating
     */
    public fun update(program: Program): Set<Package>? {
        val parent = program.`package`
        if (isLocked(parent)) {
            LOG.log(Level.INFO, "Package {0} locked, aborting: {1}", array(parent, program))
            return null
        }
        try {
            setLocked(parent, true)
            LOG.log(Level.INFO, "Checking for updates")
            val downloads = HashMap<Package, Future<*>>()
            val updates = UpdateChecker.getUpdates(parent)
            LOG.log(Level.INFO, "Updates: {0}", updates)
            for (pkg in updates) {
                LOG.log(Level.INFO, "Submitting {0}", pkg)
                downloads.put(pkg, downloadManager.submit(pkg))
            }
            LOG.log(Level.INFO, "Waiting for completion")
            for (e in downloads.entrySet()) {
                val pkg = e.getKey()
                val future = e.getValue()
                try {
                    future.get()
                    LOG.log(Level.INFO, "Updated {0}", pkg)
                } catch (ex: InterruptedException) {
                    LOG.log(Level.SEVERE, null, ex)
                } catch (ex: ExecutionException) {
                    LOG.log(Level.SEVERE, null, ex)
                }

            }
            return updates
        } finally {
            setLocked(parent, false)
        }
    }

    class object {

        public val REPO_MAIN: String = "http://oss.jfrog.org/artifactory/oss-snapshot-local/com/timepath/launcher/config/" + "public.xml"
        public val PREFS: Preferences = Preferences.userNodeForPackage(javaClass<Launcher>())
        private val LOG = Logger.getLogger(javaClass<Launcher>().getName())
        private val locked = object : Cache<Package, Boolean>() {
            override fun fill(key: Package): Boolean {
                return false
            }
        }

        public fun isLocked(aPackage: Package): Boolean {
            return locked[aPackage]
        }

        public fun setLocked(aPackage: Package, lock: Boolean) {
            LOG.log(Level.INFO, (if (lock) "L" else "Unl") + "ocking {0}", aPackage)
            locked.put(aPackage, lock)
        }
    }
}
