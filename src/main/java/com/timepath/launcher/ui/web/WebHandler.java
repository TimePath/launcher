package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.timepath.XMLUtils;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.LauncherUtils;
import com.timepath.util.Cache;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

class WebHandler implements HttpHandler {

    private static final int EXPIRES_ALL = 60 * 60; // Hour
    private static final int EXPIRES_INDEX = LauncherUtils.DEBUG ? 1 : 10;
    private static final Logger LOG = Logger.getLogger(WebHandler.class.getName());
    /**
     * Current working directory
     */
    private final URL cwd;
    /**
     * Current working package
     */
    private final String cwp;
    private final Map<String, Page> cache = new Cache<String, Page>() {

        @Override
        protected Page fill(String key) {
            if ("/".equals(key) || "/raw".equals(key)) {
                try {
                    long future = System.currentTimeMillis() + EXPIRES_INDEX * 1000;
                    Source source = Converter.serialize(launcher.getRepositories());
                    String index = Converter.transform(new StreamSource(getStream("/projects.xsl")), source);
                    Page indexPage = new Page(index, future);
                    cache.put("/", indexPage);
                    String raw = XMLUtils.pprint(source, 4);
                    Page rawPage = new Page(raw, future);
                    cache.put("/raw", rawPage);
                    return "/".equals(key) ? indexPage : rawPage;
                } catch (TransformerException | ParserConfigurationException | IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            try {
                long future = System.currentTimeMillis() + EXPIRES_ALL * 1000;
                String s = key.substring(1);
                InputStream is = getClass().getResourceAsStream(cwp + s);
                if (cwd != null) is = new URL(cwd + s).openStream();
                if (is == null) throw new FileNotFoundException("File not found: " + key);
                byte[] data = read(is);
                return new Page(data, future);
            } catch (FileNotFoundException ignored) {
            } catch (IOException e) {
                LOG.log(Level.WARNING, null, e);
            }
            return null;
        }

        @Override
        protected Page expire(String key, Page value) {
            return (LauncherUtils.DEBUG || (value != null && value.expired())) ? null : value;
        }
    };
    private final Launcher launcher;

    WebHandler(final Launcher launcher) {
        this.launcher = launcher;
        cwd = Server.class.getResource("");
        cwp = ('/' + getClass().getPackage().getName().replace('.', '/') + '/');
        LOG.log(Level.INFO, "cwd: {0}", cwd);
        LOG.log(Level.INFO, "cwp: {0}", cwp);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                cache.get("/");
            }
        };
        task.run(); // Ensure synchronous first call
        long period = EXPIRES_INDEX * 1000;
        new Timer("page-rebuild-timer", true).scheduleAtFixedRate(task, period, period);
    }

    /**
     * Reads an InputStream to a byte array
     *
     * @param is The stream to read from
     * @return The bytes read
     * @throws IOException
     */
    private static byte[] read(InputStream is) throws IOException {
        is = new BufferedInputStream(is);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(is.available());
        byte[] buf = new byte[1024];
        int read;
        while ((read = is.read(buf, 0, buf.length)) != -1) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    private InputStream getStream(String request) throws IOException {
        Page page = cache.get(request);
        if (page == null) return null;
        return new BufferedInputStream(new ByteArrayInputStream(page.data));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOG.log(Level.INFO, "{0} {1}: {2}", new Object[]{
                exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI()
        });
        LOG.log(Level.FINE, "{0}", Arrays.toString(exchange.getRequestHeaders().entrySet().toArray()));
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(Arrays.toString(exchange.getRequestHeaders().entrySet().toArray()));
        }
        String request = exchange.getRequestURI().toString();
        if (ProxyHandler.checkProxy(exchange, request)) return;
        Page page = cache.get(exchange.getRequestURI().toString());
        if (page == null) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0);
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        if ("/".equals(request)) {
            headers.set("Cache-Control", "max-age=" + EXPIRES_INDEX);
        } else {
            headers.set("Cache-Control", "max-age=" + EXPIRES_ALL);
        }
        if (request.endsWith(".css")) {
            headers.set("Content-type", "text/css");
        } else if (request.endsWith(".js")) {
            headers.set("Content-type", "text/javascript");
        } else {
            headers.set("Content-type", "text/html");
        }
        try (OutputStream os = exchange.getResponseBody()) {
            byte[] bytes = page.data;
            if (bytes != null) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
                os.write(bytes);
            }
        }
    }

    private static class Page {

        byte[] data;
        long expires;

        Page(String data, long expires) {
            this(data.getBytes(StandardCharsets.UTF_8), expires);
        }

        Page(byte[] data, long expires) {
            this.data = data;
            this.expires = expires;
        }

        boolean expired() {
            return System.currentTimeMillis() >= expires;
        }
    }

}
