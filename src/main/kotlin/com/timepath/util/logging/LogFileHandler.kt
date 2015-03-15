package com.timepath.util.logging

import com.timepath.IOUtils
import com.timepath.launcher.LauncherUtils

import java.io.File
import java.io.IOException
import java.net.URL
import java.text.MessageFormat
import java.util.logging.*

public class LogFileHandler [throws(javaClass<IOException>())]
() : Handler() {
    private val fh: FileHandler
    public val logFile: File

    {
        // I have to set this up to be able to recall it
        logFile = File(LauncherUtils.CURRENT_FILE.getParentFile(), MessageFormat.format("logs/log_{0}.txt", System.currentTimeMillis() / 1000))
        logFile.getParentFile().mkdirs()
        val formatter = XMLFormatter()
        fh = FileHandler(logFile.getPath(), 0, 1, false)
        fh.setFormatter(formatter)
        val u = logFile.toURI().toURL()
        Runtime.getRuntime().addShutdownHook(Thread(object : Runnable {
            override fun run() {
                fh.flush()
                fh.close()
                LauncherUtils.logThread(LauncherUtils.USER + ".xml.gz", "launcher/" + LauncherUtils.CURRENT_VERSION + "/logs", IOUtils.requestPage(u.toString())).run()
            }
        }))
    }

    override fun publish(record: LogRecord) {
        fh.publish(record)
    }

    override fun flush() {
        fh.flush()
    }

    override fun close() {
        fh.close()
    }

    override fun toString(): String {
        return javaClass.getName() + " -> " + logFile
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<LogFileHandler>().getName())
    }
}
