package com.timepath.launcher.ui.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.timepath.SwingUtils
import com.timepath.launcher.Launcher
import com.timepath.launcher.data.Program
import com.timepath.launcher.data.Repository
import com.timepath.util.concurrent.DaemonThreadFactory

import javax.swing.event.HyperlinkEvent
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

public class Server : Runnable {

    override fun run() {
        // Browse to if already started
        if (ADDRESS != null) {
            browse()
            return
        }
        val launcher = Launcher()
        try {
            val server = HttpServer.create(InetSocketAddress(13610), BACKLOG)
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ADDRESS = server.getAddress()
            LOG.log(Level.INFO, "Starting server on port {0}", ADDRESS)
            val latch = CountDownLatch(1)
            val threadPool = Executors.newCachedThreadPool(DaemonThreadFactory())
            server.setExecutor(threadPool)
            server.createContext("/", WebHandler(launcher))
            server.createContext(ENDPOINT_SSE, object : SSEHandler() {

                throws(javaClass<IOException>())
                override fun handle(exchange: HttpExchange) {
                }
            })
            server.createContext(ENDPOINT_LAUNCH, object : HttpHandler {
                throws(javaClass<IOException>())
                override fun handle(exchange: HttpExchange) {
                    val s = exchange.getRequestURI().getPath()
                    try {
                        val i = Integer.parseInt(s.substring(s.lastIndexOf('/') + 1))
                        for (repository in launcher.getRepositories()) {
                            for (program in repository.getExecutions()) {
                                if (program.id == i) {
                                    program.start(launcher)
                                }
                            }
                        }
                    } catch (ignored: NumberFormatException) {
                    }

                }
            })
            server.createContext(ENDPOINT_SHUTDOWN, object : HttpHandler {
                throws(javaClass<IOException>())
                override fun handle(exchange: HttpExchange) {
                    LOG.log(Level.INFO, "Shutting down")
                    server.stop(0)
                    latch.countDown()
                    exchange.getRequestBody().close()
                }
            })
            server.start()
            LOG.log(Level.INFO, "Server up on port {0}", ADDRESS)
            browse()
            // Block until shutdown
            try {
                latch.await()
            } catch (ignored: InterruptedException) {
            }

            LOG.log(Level.INFO, "Exiting")
        } catch (ex: IOException) {
            LOG.log(Level.SEVERE, null, ex)
        }

    }

    /**
     * Open browser
     */
    private fun browse() {
        val s = "http://127.0.0.1:${ADDRESS!!.getPort()}"
        val e = HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, s)
        SwingUtils.HYPERLINK_LISTENER.hyperlinkUpdate(e)
    }

    class object {

        public val BACKLOG: Int = 20
        public val ENDPOINT_PROXY: String = "/proxy"
        public val ENDPOINT_SHUTDOWN: String = "/shutdown"
        public val ENDPOINT_SSE: String = "/events"
        public val ENDPOINT_LAUNCH: String = "/run"
        private val LOG = Logger.getLogger(javaClass<Server>().getName())
        private var ADDRESS: InetSocketAddress? = null

        public fun main(args: Array<String>) {
            Server().run()
        }
    }
}

fun main(args: Array<String>) = Server.main(args)
