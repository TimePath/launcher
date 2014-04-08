package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

class WebHandler implements HttpHandler {

    private static final int EXPIRES_ALL = 60 * 60; // hour

    private static final int EXPIRES_INDEX = 10;

    private static final Logger LOG = Logger.getLogger(WebHandler.class.getName());

    private static final Map<String, Page> cache = Collections.synchronizedMap(
        new HashMap<String, Page>());

    private static final URL cwd = Server.class.getResource("");

    WebHandler() {
        LOG.log(Level.INFO, "cwd: {0}", cwd);

        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                try {
                    Page p = new Page();
                    String s = transform(getStream("projects.xsl"), new URL(
                                         "http://dl.dropboxusercontent.com/u/42745598/projects.xml")
                                         .openStream());
                    p.data = s.getBytes();
                    p.expires = System.currentTimeMillis() + EXPIRES_INDEX * 1000;
                    cache.put("", p);
                } catch(TransformerException | IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        };
        long period = EXPIRES_INDEX * 1000;
        task.run(); // Ensure synchronous first call
        new Timer("page-rebuild-timer", true).scheduleAtFixedRate(task, period, period);
    }

    @Override
    public void handle(HttpExchange t) throws IOException {

        LOG.log(Level.INFO, "{0} {1}: {2}", new Object[] {t.getProtocol(), t.getRequestMethod(),
                                                          t.getRequestURI()});
        LOG.log(Level.FINE, "{0}", Arrays.toString(t.getRequestHeaders().entrySet().toArray()));
        String request = t.getRequestURI().toString();

        String proxyRequest = null;
        LOG.fine(Arrays.toString(t.getRequestHeaders().entrySet().toArray()));
        String ref = t.getRequestHeaders().getFirst("Referer");
        LOG.log(Level.FINE, "Has referer {0}", ref);
        if(request.startsWith(Server.ENDPOINT_PROXY)) {
            proxyRequest = request;
        } else if(ref != null) {
            String path = new URL(ref + '/' + request).getPath();
            if(path.startsWith(Server.ENDPOINT_PROXY)) {
                proxyRequest = path;
            }
        }
        if(proxyRequest != null) {
            handleProxy(t, proxyRequest.substring(Server.ENDPOINT_PROXY.length() + 1)); // remove leading '/proxy/'
            return;
        }

        byte[] bytes = get(request.substring(1));
        Headers headers = t.getResponseHeaders();
        if(request.equals("/")) {
            headers.set("Cache-Control", "max-age=" + (EXPIRES_INDEX));
        } else {
            headers.set("Cache-Control", "max-age=" + (EXPIRES_ALL));
        }
        if(request.endsWith(".css")) {
            headers.set("Content-type", "text/css");
        } else if(request.endsWith(".js")) {
            headers.set("Content-type", "text/javascript");
        } else {
            headers.set("Content-type", "text/html");
        }

        try(OutputStream os = t.getResponseBody()) {
            if(bytes != null) {
                t.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
                os.write(bytes);
                os.flush();
            }
        }
    }

    /**
     * Handles page requests of the form: 'http://www.something.com'
     * <p/>
     * @param t
     * @param loc
     */
    public void handleProxy(HttpExchange t, String loc) {
        LOG.log(Level.INFO, "Proxy: {0}", loc);

        URL url;
        try {
            url = new URL(loc);
        } catch(MalformedURLException ex) {
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
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return;
        }

        try(OutputStream os = t.getResponseBody()) {
            int code = conn.getResponseCode();
            int broad = code / 100;
            switch(broad) {
                case 2:
                    long size = conn.getContentLengthLong();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream((int) size);
                    byte[] buffer = new byte[8192];
                    InputStream is = new BufferedInputStream(conn.getInputStream(), buffer.length);
                    int read;
                    while((read = is.read(buffer)) > -1) {
                        baos.write(buffer, 0, read);
                    }
                    String doc = new String(baos.toByteArray());
                    String cType = conn.getContentType();

                    byte[] raw = doc.getBytes();
                    Headers responseHeaders = t.getResponseHeaders();
                    responseHeaders.set("Content-Type", cType);
                    t.sendResponseHeaders(code, raw.length); // TODO: proper return code handling
                    os.write(raw);
                    break;
                case 3:
                    LOG.log(Level.INFO, "{0} -> {1}", new Object[] {code,
                                                                    conn.getHeaderField("Location")});
                    t.sendResponseHeaders(code, 0); // TODO: redirect
                    break;
                default:
                    LOG.log(Level.INFO, "{0}: {1}", new Object[] {code, loc});
                    t.sendResponseHeaders(code, 0);
                    break;
            }
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    private byte[] get(String request) throws MalformedURLException, IOException {
        URL u = new URL(cwd + request);
        Page cached = cache.get(request);
        if(cached == null || cached.expired()) { // Not in cache or is expired
            Page p = new Page();
            p.data = read(u.openStream());
            p.expires = System.currentTimeMillis() + EXPIRES_ALL * 1000;
            cached = p;
            cache.put(request, p);
        }
        return cached.data;
    }

    private InputStream getStream(String request) throws MalformedURLException, IOException {
        return new BufferedInputStream(new ByteArrayInputStream(get(request)));
    }

    /**
     * Reads an InputStream to a byte array
     * <p>
     * @param is The stream to read from
     * <p>
     * @return The bytes read
     * <p>
     * @throws IOException
     */
    private byte[] read(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(is.available());
        is = new BufferedInputStream(is);
        byte[] buf = new byte[1024];
        int read;
        while((read = is.read(buf, 0, buf.length)) != -1) {
            baos.write(buf, 0, read);
        }
        baos.flush();
        return baos.toByteArray();
    }

    private String transform(InputStream xsl, InputStream xml) throws TransformerException {
        TransformerFactory tFactory = TransformerFactory.newInstance();

        StreamSource xslDoc = new StreamSource(xsl);
        StreamSource xmlDoc = new StreamSource(xml);

        Transformer trasform = tFactory.newTransformer(xslDoc);
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream(10240);
        StreamResult result = new StreamResult(byteArray);
        trasform.transform(xmlDoc, result);
        return byteArray.toString();
    }

    private static class Page {

        byte[] data;

        long expires;

        boolean expired() {
            return System.currentTimeMillis() >= expires;
        }

    }

}
