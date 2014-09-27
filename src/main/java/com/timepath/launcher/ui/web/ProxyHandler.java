package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
class ProxyHandler {

    private static final Logger LOG = Logger.getLogger(ProxyHandler.class.getName());

    /**
     * Handles page requests of the form: 'http://www.something.com'
     *
     * @param t
     * @param loc
     */
    public static void handleProxy(@NotNull HttpExchange t, String loc) {
        LOG.log(Level.INFO, "Proxy: {0}", loc);
        URL url;
        try {
            url = new URL(loc);
        } catch (MalformedURLException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return;
        }
        Headers headIn = t.getRequestHeaders();
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", headIn.get("User-Agent").get(0));
            conn.setRequestMethod(t.getRequestMethod());
            conn.connect();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return;
        }
        try (OutputStream os = t.getResponseBody()) {
            int code = conn.getResponseCode();
            int broad = code / 100;
            switch (broad) {
                case 2:
                    long size = conn.getContentLengthLong();
                    @NotNull ByteArrayOutputStream baos = new ByteArrayOutputStream((int) size);
                    @NotNull byte[] buffer = new byte[8192];
                    @NotNull InputStream is = new BufferedInputStream(conn.getInputStream(), buffer.length);
                    int read;
                    while ((read = is.read(buffer)) > -1) {
                        baos.write(buffer, 0, read);
                    }
                    @NotNull String doc = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                    String cType = conn.getContentType();
                    @NotNull byte[] raw = doc.getBytes(StandardCharsets.UTF_8);
                    Headers responseHeaders = t.getResponseHeaders();
                    responseHeaders.set("Content-Type", cType);
                    t.sendResponseHeaders(code, raw.length); // TODO: proper return code handling
                    os.write(raw);
                    break;
                case 3:
                    LOG.log(Level.INFO, "{0} -> {1}", new Object[]{
                            code, conn.getHeaderField("Location")
                    });
                    t.sendResponseHeaders(code, 0); // TODO: redirect
                    break;
                default:
                    LOG.log(Level.INFO, "{0}: {1}", new Object[]{code, loc});
                    t.sendResponseHeaders(code, 0);
                    break;
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    static boolean checkProxy(@NotNull HttpExchange exchange, @NotNull String request) throws MalformedURLException {
        @Nullable String proxyRequest = null;
        if (request.startsWith(Server.ENDPOINT_PROXY)) {
            proxyRequest = request;
        } else {
            String ref = exchange.getRequestHeaders().getFirst("Referer");
            LOG.log(Level.FINE, "Has referer {0}", ref);
            if (ref != null) {
                String path = new URL(ref + '/' + request).getPath();
                if (path.startsWith(Server.ENDPOINT_PROXY)) {
                    proxyRequest = path;
                }
            }
        }
        if (proxyRequest != null) {
            handleProxy(exchange,
                    proxyRequest.substring(Server.ENDPOINT_PROXY.length() + 1)); // Remove leading '/proxy/'
            return true;
        }
        return false;
    }
}
