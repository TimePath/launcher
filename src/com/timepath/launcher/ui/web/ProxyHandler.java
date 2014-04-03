package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 *
 * @author TimePath
 */
class ProxyHandler implements HttpHandler {

    public void handle(HttpExchange t) throws IOException {
        String loc = t.getRequestURI().toString().substring(Server.ENDPOINT_PROXY.length() + 1);
        System.out.println("Proxy: " + loc);
        URL url = new URL(loc);
        Headers headIn = t.getRequestHeaders();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", headIn.get("User-Agent").get(0));
        conn.connect();
        OutputStream os = t.getResponseBody();
        int code = conn.getResponseCode();
        int broad = code / 100;
        switch(broad) {
            case 2:
                String len = conn.getHeaderField("Content-Length");
                long size = -1;
                try {
                    size = Long.parseLong(len);
                } catch(NumberFormatException e) {
                }
                t.sendResponseHeaders(code, size); // TODO: return code
                byte[] buffer = new byte[8192];
                InputStream is = new BufferedInputStream(conn.getInputStream(), buffer.length);
                int read;
                while((read = is.read(buffer)) > -1) {
                    os.write(buffer, 0, read);
                    os.flush();
                }
                break;
            case 3:
                System.out.println("redirect: " + conn.getHeaderField("Location"));
                break;
            default:
                System.out.println("err: " + code);
                break;
        }
        os.close();
    }
    
}
