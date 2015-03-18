package com.timepath.launcher.ui.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.timepath.XMLUtils
import com.timepath.launcher.Launcher
import com.timepath.launcher.LauncherUtils
import com.timepath.util.Cache

import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.stream.StreamSource
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.Arrays
import java.util.Timer
import java.util.TimerTask
import java.util.logging.Level
import java.util.logging.Logger

class WebHandler(private val launcher: Launcher) : HttpHandler {
    /**
     * Current working directory
     */
    private val cwd: URL?
    /**
     * Current working package
     */
    private val cwp: String
    private val cache = object : Cache<String, Page>() {

        override fun fill(key: String): Page? {
            if ("/" == key || "/raw" == key) {
                try {
                    val future = System.currentTimeMillis() + (EXPIRES_INDEX * 1000).toLong()
                    val source = Converter.serialize(launcher.getRepositories())
                    val index = Converter.transform(StreamSource(getStream("/projects.xsl")), source)
                    val indexPage = Page(index, future)
                    this.put("/", indexPage)
                    val raw = XMLUtils.pprint(source, 4)
                    val rawPage = Page(raw, future)
                    this.put("/raw", rawPage)
                    return if ("/" == key) indexPage else rawPage
                } catch (ex: TransformerException) {
                    LOG.log(Level.SEVERE, null, ex)
                } catch (ex: ParserConfigurationException) {
                    LOG.log(Level.SEVERE, null, ex)
                } catch (ex: IOException) {
                    LOG.log(Level.SEVERE, null, ex)
                }

            }
            try {
                val future = System.currentTimeMillis() + (EXPIRES_ALL * 1000).toLong()
                val s = key.substring(1)
                var `is`: InputStream? = javaClass.getResourceAsStream(cwp + s)
                if (cwd != null) `is` = URL("${cwd}${s}").openStream()
                if (`is` == null) throw FileNotFoundException("File not found: $key")
                val data = read(`is`!!)
                return Page(data, future)
            } catch (ignored: FileNotFoundException) {
            } catch (e: IOException) {
                LOG.log(Level.WARNING, null, e)
            }

            return null
        }

        override fun expire(key: String, value: Page?): Page? {
            return if ((LauncherUtils.DEBUG || (value != null && value.expired()))) null else value
        }
    }

    {
        cwd = javaClass<Server>().getResource("")
        cwp = ("/${javaClass.getPackage().getName().replace('.', '/')}/")
        LOG.log(Level.INFO, "cwd: {0}", cwd)
        LOG.log(Level.INFO, "cwp: {0}", cwp)
        val task = object : TimerTask() {
            override fun run() {
                cache["/"]
            }
        }
        task.run() // Ensure synchronous first call
        val period = (EXPIRES_INDEX * 1000).toLong()
        Timer("page-rebuild-timer", true).scheduleAtFixedRate(task, period, period)
    }

    throws(javaClass<IOException>())
    private fun getStream(request: String): InputStream? {
        val page = cache[request]
        if (page == null) return null
        return BufferedInputStream(ByteArrayInputStream(page.data))
    }

    throws(javaClass<IOException>())
    override fun handle(exchange: HttpExchange) {
        LOG.log(Level.INFO, "{0} {1}: {2}", array<Any>(exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI()))
        LOG.log(Level.FINE, "{0}", Arrays.toString(exchange.getRequestHeaders().entrySet().copyToArray()))
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(Arrays.toString(exchange.getRequestHeaders().entrySet().copyToArray()))
        }
        val request = exchange.getRequestURI().toString()
        if (ProxyHandler.checkProxy(exchange, request)) return
        val page = cache[exchange.getRequestURI().toString()]
        if (page == null) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0)
            return
        }
        val headers = exchange.getResponseHeaders()
        if ("/" == request) {
            headers.set("Cache-Control", "max-age=$EXPIRES_INDEX")
        } else {
            headers.set("Cache-Control", "max-age=$EXPIRES_ALL")
        }
        if (request.endsWith(".css")) {
            headers.set("Content-type", "text/css")
        } else if (request.endsWith(".js")) {
            headers.set("Content-type", "text/javascript")
        } else {
            headers.set("Content-type", "text/html")
        }
        exchange.getResponseBody().use { os ->
            val bytes = page.data
            if (bytes != null) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.size().toLong())
                os.write(bytes)
            }
        }
    }

    class object {

        private val EXPIRES_ALL = 60 * 60 // Hour
        private val EXPIRES_INDEX = if (LauncherUtils.DEBUG) 1 else 10
        private val LOG = Logger.getLogger(javaClass<WebHandler>().getName())

        /**
         * Reads an InputStream to a byte array
         *
         * @param is The stream to read from
         * @return The bytes read
         * @throws IOException
         */
        throws(javaClass<IOException>())
        private fun read(`is`: InputStream): ByteArray {
            var `is` = `is`.buffered()
            val baos = ByteArrayOutputStream(`is`.available())
            `is`.copyTo(baos)
            return baos.toByteArray()
        }


        private fun Page(data: String, expires: Long): Page {
            return Page(data.toByteArray(), expires)
        }

        private class Page(var data: ByteArray?, var expires: Long) {

            fun expired(): Boolean {
                return System.currentTimeMillis() >= expires
            }
        }
    }

}
