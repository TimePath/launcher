package com.timepath.launcher.ui.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
class LaunchHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(LaunchHandler.class.getName());

    public void handle(HttpExchange t) throws IOException {
        Runtime.getRuntime().exec("konsole");
    }

}
