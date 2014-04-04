package com.timepath.launcher.logging;

import com.timepath.launcher.LauncherImpl;
import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.*;

public class LogAggregator extends Handler {

    private static final Logger LOG = Logger.getLogger(LogAggregator.class.getName());

    private final LinkedList<Handler> handlers = new LinkedList<Handler>();

    public LogAggregator() {
        if(!LauncherImpl.debug) {
            try {
                handlers.add(new LogFileHandler());
            } catch(IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            } catch(SecurityException ex) {
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
