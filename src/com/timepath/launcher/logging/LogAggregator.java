package com.timepath.launcher.logging;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.*;

import static com.timepath.launcher.util.Utils.debug;

public class LogAggregator extends Handler {

    private static final Logger LOG = Logger.getLogger(LogAggregator.class.getName());

    private final List<Handler> handlers = new LinkedList<>();

    public LogAggregator() {
        if(!debug) {
            try {
                handlers.add(new LogFileHandler());
            } catch(IOException | SecurityException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        handlers.add(new LogIOHandler().connect("5.175.143.139", 28777));
    }

    @Override
    public void close() throws SecurityException {
        for(Handler h : handlers) {
            h.close();
        }
    }

    @Override
    public void flush() {
        for(Handler h : handlers) {
            h.flush();
        }
    }

    @Override
    public void publish(LogRecord lr) {
        for(Handler h : handlers) {
            h.publish(lr);
        }
    }

}
