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

    WebHandler(final Runnable done) {
        LOG.log(Level.INFO, "cwd: {0}", cwd);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            private boolean init = false;

            @Override
            public void run() {
                try {
                    Page p = new Page();
                    String s = transform(getStream("projects.xsl"), new URL("http://dl.dropboxusercontent.com/u/42745598/projects.xml").openStream());
                    p.data = s.getBytes();
                    p.expires = System.currentTimeMillis() + EXPIRES_INDEX * 1000;
                    cache.put("", p);
                } catch(TransformerException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
                if(!init) {
                    done.run();
                    init = true;
                }
            }
        }, 0, EXPIRES_INDEX * 1000);

    }

    public void handle(HttpExchange t) throws IOException {
        LOG.log(Level.INFO, "{0} {1}: {2}", new Object[]{t.getProtocol(), t.getRequestMethod(), t.getRequestURI()});
        String request = t.getRequestURI().toString();
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
        
        OutputStream os = t.getResponseBody();
        if(bytes != null) {
            t.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
            os.write(bytes);
            os.flush();
        }
        os.close();
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

    private class Page {

        byte[] data;

        long expires;

        boolean expired() {
            return System.currentTimeMillis() >= expires;
        }

    }

}
