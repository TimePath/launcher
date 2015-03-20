package com.timepath.launcher.data

import com.timepath.launcher.Launcher
import com.timepath.util.concurrent.DaemonThreadFactory
import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.logging.Level
import java.util.logging.Logger
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences

/**
 * @author TimePath
 */
public object RepositoryManager {

    public val PREFS_REPOS: Preferences = Launcher.PREFS.node("repositories")
    public val KEY_URL: String = "url"
    public val KEY_ENABLED: String = "enabled"
    private val LOG = Logger.getLogger(javaClass<RepositoryManager>().getName())

    public fun addRepository(r: Repository) {
        getNode(r).put(KEY_URL, r.location!!)
    }

    private fun getNode(r: Repository): Preferences {
        return PREFS_REPOS.node(getNodeName(r))
    }

    private fun getNodeName(r: Repository): String {
        return r.hashCode().toString()
    }

    public fun setRepositoryEnabled(r: Repository, flag: Boolean) {
        getNode(r).putBoolean(KEY_ENABLED, flag)
    }

    public fun removeRepository(r: Repository) {
        getNode(r).remove(KEY_URL)
    }

    public fun loadCustom(): List<Repository> {
        val repositories = LinkedList<Repository>()
        val futures = LinkedList<Future<Repository>>()
        try {
            val pool = Executors.newCachedThreadPool(DaemonThreadFactory())
            for (s in PREFS_REPOS.childrenNames()) {
                val repo = PREFS_REPOS.node(s)
                val url = repo[KEY_URL, null]
                if (url == null) {
                    // Dead
                    repo.removeNode()
                    repo.flush()
                    continue
                }
                val enabled = repo.getBoolean(KEY_ENABLED, true)
                futures.add(pool.submit<Repository>(object : Callable<Repository> {
                    throws(javaClass<Exception>())
                    override fun call(): Repository? {
                        val r = Repository.fromIndex(url)
                        if (r == null) return null
                        if (s != getNodeName(r)) return null // Node name needs update
                        r.setEnabled(enabled)
                        return r
                    }
                }))
            }
        } catch (e: BackingStoreException) {
            LOG.log(Level.SEVERE, null, e)
        }

        for (future in futures) {
            try {
                val r = future.get()
                if (r != null) repositories.add(r)
            } catch (e: InterruptedException) {
                LOG.log(Level.SEVERE, null, e)
            } catch (e: ExecutionException) {
                LOG.log(Level.SEVERE, null, e)
            }

        }
        return repositories
    }
}
