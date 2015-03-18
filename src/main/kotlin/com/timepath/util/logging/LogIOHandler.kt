package com.timepath.util.logging


import java.io.IOException
import java.io.PrintWriter
import java.lang.management.ManagementFactory
import java.net.Socket
import java.text.DateFormat
import java.text.MessageFormat
import java.util.Date
import java.util.LinkedList
import java.util.logging.*
import kotlin.concurrent.thread

public class LogIOHandler : StreamHandler() {
    /**
     * Unique name
     */
    protected val node: String = ManagementFactory.getRuntimeMXBean().getName()
    SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // pollLast() actually does get
    private val recordDeque = LinkedList<String>()
    volatile private var pw: PrintWriter? = null

    {
        setFormatter(LogIOFormatter())
    }

    public fun connect(host: String, port: Int): LogIOHandler {
        thread {
            try {
                Socket(host, port).use { sock ->
                    pw = PrintWriter(sock.getOutputStream(), true)
                    send("+node|$node")
                }
            } catch (e: IOException) {
                LOG.log(Level.SEVERE, null, e)
            }

        }
        return this
    }

    synchronized public fun send(line: String) {
        recordDeque.addLast(line)
        if (pw != null) {
            run {
                val it = recordDeque.iterator()
                while (it.hasNext()) {
                    pw!!.print("${it.next()}\r\n")
                    it.remove()
                }
            }
            pw!!.flush()
        }
    }

    synchronized override fun publish(record: LogRecord) {
        send(getFormatter().format(record))
    }

    synchronized override fun flush() {
        if (pw != null) {
            pw!!.flush()
        }
    }

    synchronized override fun close() {
        send("-node|$node")
        if (pw != null) {
            pw!!.close()
        }
        pw = null
    }

    private inner class LogIOFormatter : Formatter() {

        private val dateFormat = DateFormat.getDateTimeInstance()

        override fun format(record: LogRecord): String {
            val level = record.getLevel().getName().toLowerCase()
            val message = MessageFormat.format("{0}: <{2}::{3}> {4}: {5}", dateFormat.format(Date(record.getMillis())), record.getLoggerName(), record.getSourceClassName(), record.getSourceMethodName(), record.getLevel(), formatMessage(record))
            return "+log||$node|$level|$message"
        }
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<LogIOHandler>().getName())
    }
}
