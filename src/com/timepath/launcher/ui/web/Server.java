package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.*;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.Utils.DaemonThreadFactory;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.HyperlinkEvent;

public class Server extends Thread {

    public static final int BACKLOG = 20;

    public static final String ENDPOINT_LAUNCH = "/run";

    public static final String ENDPOINT_PROXY = "/proxy";

    public static final String ENDPOINT_SHUTDOWN = "/shutdown";

    public static final String ENDPOINT_SSE = "/events";

    private static InetSocketAddress ADDR;

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) {
        new Server().start();
    }

    @Override
    public void run() {
        if(ADDR != null) {
            browse();
            return;
        }

        final HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(0), BACKLOG);
            ADDR = server.getAddress();
            LOG.log(Level.INFO, "Starting server on port {0}", ADDR);

            final CountDownLatch latch = new CountDownLatch(1);

            ExecutorService threadPool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            server.setExecutor(threadPool);

            server.createContext("/", new WebHandler());
            server.createContext(ENDPOINT_LAUNCH, new LaunchHandler());
            server.createContext(ENDPOINT_SSE, new SSEHandler());
            server.createContext(ENDPOINT_SHUTDOWN, new HttpHandler() {

                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    LOG.log(Level.INFO, "Shutting down");
                    server.stop(0);
                    latch.countDown();
                    exchange.getRequestBody().close();
                }

            });
            server.start();
            LOG.log(Level.INFO, "Server up on port {0}", ADDR);

            browse();

            // Block until shutdown
            try {
                latch.await();
            } catch(InterruptedException ignore) {
            }
            LOG.log(Level.INFO, "Exiting");
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Open browser
     */
    private void browse() {
        String s = "http://127.0.0.1:" + ADDR.getPort();
        HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, s);
        Utils.linkListener.hyperlinkUpdate(e);
    }

}
