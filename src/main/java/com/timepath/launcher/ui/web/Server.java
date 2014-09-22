package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.timepath.launcher.Launcher;
import com.timepath.launcher.util.SwingUtils;
import com.timepath.util.concurrent.DaemonThreadFactory;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server implements Runnable {

    public static final int BACKLOG = 20;
    public static final String ENDPOINT_PROXY = "/proxy";
    public static final String ENDPOINT_SHUTDOWN = "/shutdown";
    public static final String ENDPOINT_SSE = "/events";
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private static InetSocketAddress ADDRESS;

    public Server() {
    }

    public static void main(String[] args) {
        new Server().run();
    }

    @Override
    public void run() {
        // Browse to if already started
        if (ADDRESS != null) {
            browse();
            return;
        }
        Launcher launcher = new Launcher();
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress(0), BACKLOG);
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ADDRESS = server.getAddress();
            LOG.log(Level.INFO, "Starting server on port {0}", ADDRESS);
            final CountDownLatch latch = new CountDownLatch(1);
            ExecutorService threadPool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            server.setExecutor(threadPool);
            server.createContext("/", new WebHandler(launcher));
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
            LOG.log(Level.INFO, "Server up on port {0}", ADDRESS);
            browse();
            // Block until shutdown
            try {
                latch.await();
            } catch (InterruptedException ignored) {
            }
            LOG.log(Level.INFO, "Exiting");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Open browser
     */
    private void browse() {
        String s = "http://127.0.0.1:" + ADDRESS.getPort();
        HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, s);
        SwingUtils.HYPERLINK_LISTENER.hyperlinkUpdate(e);
    }
}
