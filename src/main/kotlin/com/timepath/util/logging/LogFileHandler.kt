package com.timepath.util.logging

import com.timepath.launcher.LauncherUtils
import java.io.File
import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.XMLFormatter

public class LogFileHandler [throws(javaClass<IOException>())]() : Handler() {
    private val fh: FileHandler
    public val logFile: File

    init {
        // I have to set this up to be able to recall it
        logFile = File(LauncherUtils.CURRENT_FILE.getParentFile(), "logs/log_${System.currentTimeMillis() / 1000}.txt")
        logFile.getParentFile().mkdirs()
        val formatter = XMLFormatter()
        fh = FileHandler(logFile.getPath(), 0, 1, false)
        fh.setFormatter(formatter)
        Runtime.getRuntime().addShutdownHook(Thread {
            fh.flush()
            fh.close()
            LauncherUtils.logThread("${LauncherUtils.USER}.xml.gz", "launcher/${LauncherUtils.CURRENT_VERSION}/logs",
                    logFile.toURI().toURL().readText()
            ).run()
        })
    }

    override fun publish(record: LogRecord) = fh.publish(record)

    override fun flush() = fh.flush()

    override fun close() = fh.close()

    override fun toString() = "${javaClass.getName()} -> $logFile"

}
