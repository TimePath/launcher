package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Handles Server-Sent Events
 *
 * @author TimePath
 */
class SSEHandler implements HttpHandler {

    SSEHandler() {
    }

    @Nullable
    protected static String event(String message) {
        return event(message, null);
    }

    @Nullable
    protected static String event(@Nullable String message, @Nullable String type) {
        /*
         * If a line doesn't contain a colon,
         * the entire line is treated as the field name,
         * with an empty value string.
         */
        if (message == null) {
            return type;
        }
        @NotNull String[] lines = message.split("\n");
        int size = 1 + message.length() + (lines.length * 6) + ((type != null) ? (7 + type.length()) : 0) + 1;
        @NotNull StringBuilder sb = new StringBuilder(size);
        // Named event
        if (type != null) {
            sb.append("event: ").append(type).append('\n');
        }
        // \n handling
        for (String line : lines) {
            sb.append("data: ").append(line).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    @Override
    public void handle(@NotNull HttpExchange exchange) throws IOException {
        Headers head = exchange.getResponseHeaders();
        head.set("Connection", "keep-alive");
        head.set("Cache-Control", "no-cache");
        head.set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
    }
}
