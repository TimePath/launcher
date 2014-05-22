package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.Program;
import com.timepath.launcher.Repository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class WebHandler implements HttpHandler {

    private static final int               EXPIRES_ALL   = 60 * 60; // hour
    private static final int               EXPIRES_INDEX = 10;
    private static final Logger            LOG           = Logger.getLogger(WebHandler.class.getName());
    private static final Map<String, Page> cache         = Collections.synchronizedMap(new HashMap<String, Page>(0));
    private static final URL               cwd           = Server.class.getResource("");

    WebHandler(final Launcher launcher) {
        LOG.log(Level.INFO, "cwd: {0}", cwd);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    Page p = new Page();
                    String s = transform(new StreamSource(getStream("projects.xsl")), serialize(launcher.getRepositories()));
                    p.data = s.getBytes(StandardCharsets.UTF_8);
                    p.expires = System.currentTimeMillis() + EXPIRES_INDEX * 1000;
                    cache.put("", p);
                } catch(TransformerException | ParserConfigurationException | IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        };
        task.run(); // Ensure synchronous first call
        long period = EXPIRES_INDEX * 1000;
        new Timer("page-rebuild-timer", true).scheduleAtFixedRate(task, period, period);
    }

    private static InputStream getStream(String request) throws IOException {
        return new BufferedInputStream(new ByteArrayInputStream(get(request)));
    }

    private static byte[] get(String request) throws IOException {
        URL u = new URL(cwd + request);
        Page cached = cache.get(request);
        if(( cached == null ) || cached.expired()) { // Not in cache or is expired
            Page p = new Page();
            p.data = read(u.openStream());
            p.expires = System.currentTimeMillis() + EXPIRES_ALL * 1000;
            cached = p;
            cache.put(request, p);
        }
        return cached.data;
    }

    /**
     * Reads an InputStream to a byte array
     *
     * @param is
     *         The stream to read from
     *
     * @return The bytes read
     *
     * @throws IOException
     */
    private static byte[] read(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(is.available());
        is = new BufferedInputStream(is);
        byte[] buf = new byte[1024];
        int read;
        while(( read = is.read(buf, 0, buf.length) ) != -1) {
            baos.write(buf, 0, read);
        }
        baos.flush();
        return baos.toByteArray();
    }

    private static Source serialize(Iterable<Repository> repos) throws ParserConfigurationException {
        Collection<Program> programs = new LinkedList<>();
        for(Repository repo : repos) {
            programs.addAll(repo.getExecutions());
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();
        Element root = document.createElement("root");
        Element rootPrograms = document.createElement("programs");
        root.appendChild(rootPrograms);
        Element rootLibs = document.createElement("libs");
        root.appendChild(rootLibs);
        for(Program p : programs) {
            Element e = document.createElement("entry");
            e.setAttribute("name", p.getTitle());
            StringBuilder sb = new StringBuilder(0);
            for(com.timepath.launcher.Package dep : p.getPackage().getDownloads()) {
                sb.append(',').append(dep.name);
            }
            String deps = sb.substring(Math.min(1, sb.length()));
            e.setAttribute("depends", deps);
            rootPrograms.appendChild(e);
            rootLibs.appendChild(e.cloneNode(true));
        }
        return new DOMSource(root);
    }

    /**
     * Uses JAXP to transform xml with xsl
     *
     * @param xslDoc
     * @param xmlDoc
     *
     * @return
     *
     * @throws TransformerException
     */
    private static String transform(Source xslDoc, Source xmlDoc) throws TransformerException {
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer trasformer = tFactory.newTransformer(xslDoc);
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream(10240);
        Result result = new StreamResult(byteArray);
        trasformer.transform(xmlDoc, result);
        return byteArray.toString();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOG.log(Level.INFO, "{0} {1}: {2}", new Object[] {
                exchange.getProtocol(), exchange.getRequestMethod(), exchange.getRequestURI()
        });
        LOG.log(Level.FINE, "{0}", Arrays.toString(exchange.getRequestHeaders().entrySet().toArray()));
        String request = exchange.getRequestURI().toString();
        if(LOG.isLoggable(Level.FINE)) {LOG.fine(Arrays.toString(exchange.getRequestHeaders().entrySet().toArray()));}
        String ref = exchange.getRequestHeaders().getFirst("Referer");
        LOG.log(Level.FINE, "Has referer {0}", ref);
        String proxyRequest = null;
        if(request.startsWith(Server.ENDPOINT_PROXY)) {
            proxyRequest = request;
        } else if(ref != null) {
            String path = new URL(ref + '/' + request).getPath();
            if(path.startsWith(Server.ENDPOINT_PROXY)) {
                proxyRequest = path;
            }
        }
        if(proxyRequest != null) {
            handleProxy(exchange, proxyRequest.substring(Server.ENDPOINT_PROXY.length() + 1)); // remove leading '/proxy/'
            return;
        }
        byte[] bytes = get(request.substring(1));
        Headers headers = exchange.getResponseHeaders();
        if("/".equals(request)) {
            headers.set("Cache-Control", "max-age=" + EXPIRES_INDEX);
        } else {
            headers.set("Cache-Control", "max-age=" + EXPIRES_ALL);
        }
        if(request.endsWith(".css")) {
            headers.set("Content-type", "text/css");
        } else if(request.endsWith(".js")) {
            headers.set("Content-type", "text/javascript");
        } else {
            headers.set("Content-type", "text/html");
        }
        try(OutputStream os = exchange.getResponseBody()) {
            if(bytes != null) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
                os.write(bytes);
                os.flush();
            }
        }
    }

    /**
     * Handles page requests of the form: 'http://www.something.com'
     *
     * @param t
     * @param loc
     */
    public static void handleProxy(HttpExchange t, String loc) {
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
                    while(( read = is.read(buffer) ) > -1) {
                        baos.write(buffer, 0, read);
                    }
                    String doc = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                    String cType = conn.getContentType();
                    byte[] raw = doc.getBytes(StandardCharsets.UTF_8);
                    Headers responseHeaders = t.getResponseHeaders();
                    responseHeaders.set("Content-Type", cType);
                    t.sendResponseHeaders(code, raw.length); // TODO: proper return code handling
                    os.write(raw);
                    break;
                case 3:
                    LOG.log(Level.INFO, "{0} -> {1}", new Object[] {
                            code, conn.getHeaderField("Location")
                    });
                    t.sendResponseHeaders(code, 0); // TODO: redirect
                    break;
                default:
                    LOG.log(Level.INFO, "{0}: {1}", new Object[] { code, loc });
                    t.sendResponseHeaders(code, 0);
                    break;
            }
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    private static class Page {

        byte[] data;
        long   expires;

        private Page() {}

        boolean expired() {
            return System.currentTimeMillis() >= expires;
        }
    }
}
