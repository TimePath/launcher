package com.timepath.launcher.data

import com.timepath.IOUtils
import com.timepath.launcher.LauncherUtils
import com.timepath.maven.Constants
import com.timepath.maven.Package
import com.timepath.maven.UpdateChecker
import com.timepath.util.concurrent.DaemonThreadFactory

import java.io.*
import java.net.SocketTimeoutException
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.HashMap
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author TimePath
 */
public class DownloadManager {
    private val semaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS, true)
    protected val monitors: MutableList<DownloadMonitor> = LinkedList()
    protected val pool: ExecutorService = Executors.newCachedThreadPool(DaemonThreadFactory())
    protected val tasks: MutableMap<Package, Future<*>> = HashMap()

    public fun addListener(downloadMonitor: DownloadMonitor) {
        synchronized (monitors) {
            monitors.add(downloadMonitor)
        }
    }

    public fun removeListener(downloadMonitor: DownloadMonitor) {
        synchronized (monitors) {
            monitors.remove(downloadMonitor)
        }
    }

    public fun shutdown() {
        pool.shutdown()
    }

    synchronized public fun submit(pkgFile: Package): Future<*> {
        var future: Future<*>? = tasks[pkgFile]
        if (future == null) {
            // Not already submitted
            fireSubmitted(pkgFile)
            future = pool.submit(DownloadTask(pkgFile))
            tasks.put(pkgFile, future!!)
        }
        return future!!
    }

    protected fun fireSubmitted(pkgFile: Package) {
        synchronized (monitors) {
            for (monitor in monitors) {
                monitor.onSubmit(pkgFile)
            }
        }
    }

    protected fun fireUpdated(pkgFile: Package) {
        synchronized (monitors) {
            for (monitor in monitors) {
                monitor.onUpdate(pkgFile)
            }
        }
    }

    protected fun fireFinished(pkgFile: Package) {
        synchronized (monitors) {
            for (monitor in monitors) {
                monitor.onFinish(pkgFile)
            }
        }
    }

    public trait DownloadMonitor {

        public fun onSubmit(pkgFile: Package)

        public fun onUpdate(pkgFile: Package)

        public fun onFinish(pkgFile: Package)
    }

    private inner class DownloadTask (private val pkgFile: Package) : Runnable {
        private var temp: File? = null

        init {
            try {
                this.temp = File.createTempFile("pkg", "")
            } catch (e: IOException) {
                LOG.log(Level.SEVERE, "Unable to create temp file for download", e)
            }

        }

        override fun run() {
            try {
                semaphore.acquireUninterruptibly()
                val retryCount = 10
                for (i in (retryCount + 1).indices) {
                    try {
                        val downloadFile: File
                        val checksumFile: File
                        if (pkgFile.isSelf()) {
                            // Special case
                            downloadFile = LauncherUtils.UPDATE
                            checksumFile = File(LauncherUtils.WORK_DIR, "${LauncherUtils.UPDATE.getName()}.${Constants.ALGORITHM}")
                        } else {
                            downloadFile = UpdateChecker.getFile(pkgFile)
                            checksumFile = UpdateChecker.getChecksumFile(pkgFile, Constants.ALGORITHM)
                        }
                        download(pkgFile)
                        // Get the checksum before the package is moved into place
                        LOG.log(Level.INFO, "Saving checksum: {0} > {1}", array<Any>(checksumFile, checksumFile.getAbsoluteFile()))
                        Files.createDirectories(checksumFile.getAbsoluteFile().getParentFile().toPath())
                        FileOutputStream(checksumFile).use { checksumOutputStream -> checksumOutputStream.write(UpdateChecker.getChecksum(pkgFile, Constants.ALGORITHM)!!.toByteArray()) }
                        val move = Files.move(temp!!.toPath(), downloadFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        LOG.log(Level.INFO, "Complete: {0} > {1}", array<Any>(UpdateChecker.getDownloadURL(pkgFile), move))
                        return
                    } catch (ignored: SocketTimeoutException) {
                    } catch (e: IOException) {
                        LOG.log(Level.SEVERE, "DownloadTask", e)
                    }

                }
                LOG.log(Level.WARNING, "Failed all attempts: {0}", UpdateChecker.getDownloadURL(pkgFile))
            } finally {
                fireFinished(pkgFile)
                semaphore.release()
            }
        }

        throws(javaClass<IOException>())
        private fun download(p: Package) {
            val s = UpdateChecker.getDownloadURL(p)
            val connection = IOUtils.requestConnection(s, object : IOUtils.ConnectionSettings {
                override fun apply(u: URLConnection) {
                    u.setRequestProperty("Range", "bytes=${pkgFile.progress}-")
                }
            })
            val partial = "bytes" == connection.getHeaderField("Accept-Ranges")
            pkgFile.size = connection.getContentLengthLong()
            p.associate(connection)
            val temp = temp!!
            IOUtils.createFile(temp)
            LOG.log(Level.INFO, "Downloading {0} > {1}", array<Any>(s, temp))
            val buffer = ByteArray(8192)
            IOUtils.openStream(connection).use { `is` ->
                BufferedOutputStream(FileOutputStream(temp, partial)).use { fos ->
                    var total: Long = 0
                    while (true) {
                        val read = `is`.read(buffer)
                        if (read <= 0) break
                        fos.write(buffer, 0, read)
                        total += read.toLong()
                        pkgFile.progress = total
                        fireUpdated(pkgFile)
                    }
                    fos.flush()
                }
            }
        }
    }

    companion object {

        public val MAX_CONCURRENT_CONNECTIONS: Int = 10
        val LOG = Logger.getLogger(javaClass<DownloadManager>().getName())
    }
}
