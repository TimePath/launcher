package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.logging.Logger;

/**
 * Handles Server-Sent Events
 * <p>
 * @author TimePath
 */
class SSEHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(SSEHandler.class.getName());

    public void handle(HttpExchange t) throws IOException {
        final String response = "Ping";
        Headers head = t.getResponseHeaders();
        head.set("Connection", "keep-alive");
        head.set("Cache-Control", "no-cache");
        head.set("Content-Type", "text/event-stream");
        t.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
        final OutputStream os = t.getResponseBody();
        new Thread() {
            @Override
            public void run() {
                try {
                    while(true) {
                        os.write(event(response).getBytes());
                        os.flush();
                        Thread.sleep(10000);
                    }
                } catch(Exception e) {
                    try {
                        os.close();
                    } catch(Exception e2) {
                    }
                }
            }
        }.start();
    }

    private String event(String message) {
        return event(message, null);
    }

    private String event(String message, String type) {
        /*
         * If a line doesn't contain a colon,
         * the entire line is treated as the field name,
         * with an empty value string.
         */
        if(message == null) {
            return type;
        }
        
        String[] lines = message.split("\n");
        
        int size = (1 + message.length() + (lines.length * 6)) + (type != null ? 7 + type.length() : 0) + 1;
        StringBuilder sb = new StringBuilder(size);
        
        // Named event
        if(type != null) {
            sb.append("event: ").append(type).append('\n');
        }
        
        // \n handling
        for(String line : lines) {
            sb.append("data: ").append(line).append('\n');
        }
        
        sb.append('\n');
        
        return sb.toString();
    }

}
