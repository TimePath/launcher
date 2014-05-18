package com.timepath.launcher.logging;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.timepath.launcher.util.Utils.debug;

public class LogAggregator extends Handler {

    private static final Logger              LOG      = Logger.getLogger(LogAggregator.class.getName());
    private final        Collection<Handler> handlers = new LinkedList<>();

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
    public void publish(LogRecord record) {
        for(Handler h : handlers) {
            h.publish(record);
        }
    }

    @Override
    public void flush() {
        for(Handler h : handlers) {
            h.flush();
        }
    }

    @Override
    public void close() {
        for(Handler h : handlers) {
            h.close();
        }
    }
}
