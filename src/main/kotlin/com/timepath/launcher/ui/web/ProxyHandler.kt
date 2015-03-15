package com.timepath.launcher.ui.web

import com.sun.net.httpserver.HttpExchange

import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author TimePath
 */
class ProxyHandler {
    class object {

        private val LOG = Logger.getLogger(javaClass<ProxyHandler>().getName())

        /**
         * Handles page requests of the form: 'http://www.something.com'
         *
         * @param t
         * @param loc
         */
        public fun handleProxy(t: HttpExchange, loc: String) {
            LOG.log(Level.INFO, "Proxy: {0}", loc)
            val url: URL
            try {
                url = URL(loc)
            } catch (ex: MalformedURLException) {
                LOG.log(Level.SEVERE, null, ex)
                return
            }

            val headIn = t.getRequestHeaders()
            val conn: HttpURLConnection
            try {
                conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", headIn["User-Agent"][0])
                conn.setRequestMethod(t.getRequestMethod())
                conn.connect()
            } catch (ex: IOException) {
                LOG.log(Level.SEVERE, null, ex)
                return
            }

            try {
                t.getResponseBody().use { os ->
                    val code = conn.getResponseCode()
                    val broad = code / 100
                    when (broad) {
                        2 -> {
                            val size = conn.getContentLengthLong()
                            val baos = ByteArrayOutputStream(size.toInt())
                            conn.getInputStream().buffered().copyTo(baos)
                            val doc = String(baos.toByteArray(), StandardCharsets.UTF_8)
                            val cType = conn.getContentType()
                            val raw = doc.toByteArray()
                            val responseHeaders = t.getResponseHeaders()
                            responseHeaders.set("Content-Type", cType)
                            t.sendResponseHeaders(code, raw.size().toLong()) // TODO: proper return code handling
                            os.write(raw)
                        }
                        3 -> {
                            LOG.log(Level.INFO, "{0} -> {1}", array<Any>(code, conn.getHeaderField("Location")))
                            t.sendResponseHeaders(code, 0) // TODO: redirect
                        }
                        else -> {
                            LOG.log(Level.INFO, "{0}: {1}", array<Any>(code, loc))
                            t.sendResponseHeaders(code, 0)
                        }
                    }
                }
            } catch (ex: IOException) {
                LOG.log(Level.SEVERE, null, ex)
            }

        }

        throws(javaClass<MalformedURLException>())
        fun checkProxy(exchange: HttpExchange, request: String): Boolean {
            var proxyRequest: String? = null
            if (request.startsWith(Server.ENDPOINT_PROXY)) {
                proxyRequest = request
            } else {
                val ref = exchange.getRequestHeaders().getFirst("Referer")
                LOG.log(Level.FINE, "Has referer {0}", ref)
                if (ref != null) {
                    val path = URL(ref + '/' + request).getPath()
                    if (path.startsWith(Server.ENDPOINT_PROXY)) {
                        proxyRequest = path
                    }
                }
            }
            if (proxyRequest != null) {
                handleProxy(exchange, proxyRequest!!.substring(Server.ENDPOINT_PROXY.length() + 1)) // Remove leading '/proxy/'
                return true
            }
            return false
        }
    }
}
