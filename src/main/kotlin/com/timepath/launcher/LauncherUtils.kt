package com.timepath.launcher

import com.timepath.Utils
import com.timepath.util.logging.DBInbox
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.text.MessageFormat
import java.util.prefs.Preferences

public object LauncherUtils {

    public val USER: String = MessageFormat.format("{0}@{1}",
            System.getProperty("user.name"), ManagementFactory.getRuntimeMXBean().getName().splitBy("@")[1])

    private fun initVersion(): Long {
        val impl = javaClass<LauncherUtils>().getPackage().getSpecificationVersion()
        if (impl != null) {
            try {
                return java.lang.Long.parseLong(impl)
            } catch (ignored: NumberFormatException) {
            }

        }
        return 0
    }

    public val CURRENT_VERSION: Long = initVersion()
    public val DEBUG: Boolean = CURRENT_VERSION == 0L
    public val SETTINGS: Preferences = Preferences.userRoot().node("timepath")
    public val START_TIME: Long = ManagementFactory.getRuntimeMXBean().getStartTime()
    public val UPDATE: File = File("update.tmp")
    public val CURRENT_FILE: File = Utils.currentFile(javaClass<LauncherUtils>())
    public val WORK_DIR: File = CURRENT_FILE.getParentFile()

    public fun log(name: String, dir: String, o: Any) {
        logThread(name, dir, o.toString()).start()
    }

    public fun logThread(fileName: String, directory: String, str: String): Thread = object : Thread() {
        override fun run() {
            fun debug(o: Any) = System.out.println(o)
            try {
                debug("Response: ${DBInbox.send("dbinbox.timepath.ddns.info", "timepath", fileName, directory, str)}")
            } catch (ioe: IOException) {
                debug(ioe)
            }
        }
    }
}
