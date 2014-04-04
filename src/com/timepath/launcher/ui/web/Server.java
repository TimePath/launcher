package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.*;
import com.timepath.launcher.Utils;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.HyperlinkEvent;

/**
 * http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/com/sun/net/httpserver/HttpServer.java
 * <p>
 * @author TimePath
 */
public class Server {

    public static final int BACKLOG = 20;

    public static final String ENDPOINT_LAUNCH = "/run";

    public static final String ENDPOINT_PROXY = "/proxy";

    public static final String ENDPOINT_SSE = "/events";

    public static final int PORT = 8000;

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) throws Exception {
        LOG.log(Level.INFO, "Starting server on port {0}", PORT);
        final HttpServer server = HttpServer.create(new InetSocketAddress(PORT), BACKLOG);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext(ENDPOINT_LAUNCH, new LaunchHandler());
        server.createContext(ENDPOINT_SSE, new SSEHandler());
        server.createContext("/", new WebHandler(new Runnable() {

            public void run() {
                server.start();
                LOG.log(Level.INFO, "Server up on port {0}", PORT);
                String s = "http://127.0.0.1";
                if(PORT != 80) {
                    s += ":" + PORT;
                }
                HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED,
                                                      null, s);
                Utils.linkListener.hyperlinkUpdate(e);
            }

        }));
    }

}
