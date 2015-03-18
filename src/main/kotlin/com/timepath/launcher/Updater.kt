package com.timepath.launcher

import com.timepath.FileUtils
import com.timepath.IOUtils
import com.timepath.maven.Constants

import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author TimePath
 */
public class Updater private() {
    class object {

        private val LOG = Logger.getLogger(javaClass<Updater>().getName())

        /**
         * Checks for an update file and starts it if necessary
         *
         * @param args
         * @return null if not started, name of executable this method was called from (download updates here)
         */
        public fun checkForUpdate(args: Array<String>): String? {
            LOG.log(Level.INFO, "Current version = {0}", LauncherUtils.CURRENT_VERSION)
            LOG.log(Level.INFO, "Current file = {0}", LauncherUtils.CURRENT_FILE)
            val cwd = LauncherUtils.CURRENT_FILE.getParentFile()
            LOG.log(Level.INFO, "Working directory = {0}", cwd.getAbsoluteFile())
            val updateFile = File(cwd, LauncherUtils.UPDATE.getName())
            if (updateFile.exists()) {
                LOG.log(Level.INFO, "Update file = {0}", updateFile)
                //<editor-fold defaultstate="collapsed" desc="on user restart">
                if (LauncherUtils.CURRENT_FILE != updateFile) {
                    try {
                        val updateChecksum = File("${updateFile.getPath()}.${Constants.ALGORITHM}")
                        if (updateChecksum.exists()) {
                            val cksumExpected = updateChecksum.toURI().toURL().readText().trim()
                            LOG.log(Level.INFO, "Expecting checksum = {0}", cksumExpected)
                            val cksum = FileUtils.checksum(updateFile, Constants.ALGORITHM)
                            LOG.log(Level.INFO, "Actual checksum = {0}", cksum)
                            if (cksum == cksumExpected) {
                                val cmds = LinkedList<String>()
                                cmds.add("-jar")
                                cmds.add(updateFile.getPath())
                                cmds.add("-u")
                                cmds.add(updateFile.getPath())
                                cmds.add(LauncherUtils.CURRENT_FILE.getPath())
                                Runtime.getRuntime().addShutdownHook(Thread {
                                    fork(updateFile, cmds, null)
                                })
                                System.exit(0)
                                return null
                            } else {
                                LOG.log(Level.WARNING, "Checksum mismatch")
                                updateChecksum.delete()
                                updateFile.delete()
                                throw Exception("Corrupt update file")
                            }
                        }
                    } catch (ex: Exception) {
                        LOG.log(Level.SEVERE, null, ex)
                    }
                }
                //</editor-fold>
            }
            //<editor-fold defaultstate="collapsed" desc="on update detected restart">
            for (i in args.size().indices) {
                if ("-u".equalsIgnoreCase(args[i])) {
                    try {
                        val sourceFile = File(args[i + 1])
                        val destFile = File(args[i + 2])
                        LOG.log(Level.INFO, "Updating {0}", destFile)
                        destFile.delete()
                        destFile.createNewFile()
                        // TODO: assert checksums again
                        sourceFile.copyTo(destFile)
                        File("${updateFile.getPath()}.${Constants.ALGORITHM}").delete()
                        sourceFile.deleteOnExit()
                        return destFile.getName() // can continue running from temp file
                    } catch (ex: IOException) {
                        LOG.log(Level.SEVERE, "Error during update process:\n{0}", ex)
                    }
                }
            }
            //</editor-fold>
            return null
        }

        private fun fork(mainJar: File, args: Collection<String>?, main: String?) = try {
            val cmd = LinkedList<String>()
            cmd.add(File(File(System.getProperty("java.home"), "bin"), "java").toString())
            if (args != null) {
                cmd.addAll(args)
            } else {
                if (main == null) {
                    cmd.add("-jar")
                    cmd.add(mainJar.getPath())
                } else {
                    cmd.add(main)
                }
            }
            LOG.log(Level.INFO, "Invoking other: {0}", cmd.toString())
            ProcessBuilder(cmd).start()
        } catch (ex: IOException) {
            LOG.log(Level.SEVERE, null, ex)
        }
    }

}
