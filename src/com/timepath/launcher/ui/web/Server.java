package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.*;
import com.timepath.launcher.util.Utils;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.HyperlinkEvent;

/**
 * http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/com/sun/net/httpserver/HttpServer.java
 * <p/>
 * @author TimePath
 */
public class Server extends Thread {

    public static final int BACKLOG = 20;

    public static final String ENDPOINT_LAUNCH = "/run";

    public static final String ENDPOINT_PROXY = "/proxy";

    public static final String ENDPOINT_SHUTDOWN = "/shutdown";

    public static final String ENDPOINT_SSE = "/events";

    public static final int PORT = 8000;

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) {
        new Server().start();
    }

    Server() {
        this.setDaemon(false);
    }

    @Override
    public void run() {
        LOG.log(Level.INFO, "Starting server on port {0}", PORT);
        final HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), BACKLOG);
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return;
        }

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
        LOG.log(Level.INFO, "Server up on port {0}", PORT);

        // Open browser
        String s = "http://127.0.0.1";
        if(PORT != 80) {
            s += ":" + PORT;
        }
        HyperlinkEvent e = new HyperlinkEvent(server, HyperlinkEvent.EventType.ACTIVATED, null, s);
        Utils.linkListener.hyperlinkUpdate(e);

        // Block until shutdown
        try {
            latch.await();
        } catch(InterruptedException ignore) {
        }
        LOG.log(Level.INFO, "Exiting");
    }

    private static class DaemonThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setDaemon(true);
            return thread;
        }

    }

}
