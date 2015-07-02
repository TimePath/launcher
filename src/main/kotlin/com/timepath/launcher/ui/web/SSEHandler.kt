package com.timepath.launcher.ui.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.net.HttpURLConnection

/**
 * Handles Server-Sent Events
 */
open class SSEHandler : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        val head = exchange.getResponseHeaders()
        head.set("Connection", "keep-alive")
        head.set("Cache-Control", "no-cache")
        head.set("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
    }

    companion object {

        protected fun event(message: String): String? {
            return event(message, null)
        }

        protected fun event(message: String?, type: String?): String? {
            /*
         * If a line doesn't contain a colon,
         * the entire line is treated as the field name,
         * with an empty value string.
         */
            if (message == null) {
                return type
            }
            val lines = message.splitBy("\n")
            val size = 1 + message.length() + (lines.size() * 6) + (if ((type != null)) (7 + type.length()) else 0) + 1
            val sb = StringBuilder(size)
            // Named event
            if (type != null) {
                sb.append("event: ").append(type).append('\n')
            }
            // \n handling
            for (line in lines) {
                sb.append("data: ").append(line).append('\n')
            }
            sb.append('\n')
            return sb.toString()
        }
    }
}
