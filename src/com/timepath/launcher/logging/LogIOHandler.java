package com.timepath.launcher.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.logging.*;

public class LogIOHandler extends StreamHandler {

    private static final Logger LOG = Logger.getLogger(LogIOHandler.class.getName());

    private final LinkedList<LogRecord> ll = new LinkedList<LogRecord>();

    private final String node = ManagementFactory.getRuntimeMXBean().getName(); // unique

    private PrintWriter pw;

    public LogIOHandler() {
        this.setFormatter(new LogIOFormatter());
    }

    @Override
    public synchronized void close() throws SecurityException {
        send("-node|" + node);
        pw.close();
        pw = null;
    }

    public LogIOHandler connect(String host, int port) {
        try {
            Socket sock = new Socket(host, port);
            pw = new PrintWriter(sock.getOutputStream(), true);
            send("+node|" + node);
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return this;
    }

    @Override
    public synchronized void flush() {
        pw.flush();
    }

    @Override
    public synchronized void publish(LogRecord lr) {
        ll.addLast(lr);
        if(pw != null) {
            send(getFormatter().format(lr));
            ll.pollLast(); // Remove it after sending
        }
    }

    private void send(String req) {
        pw.print(req + "\r\n");
        pw.flush();
    }

    private class LogIOFormatter extends Formatter {

        private DateFormat dateFormat;

        @Override
        public synchronized String format(LogRecord lr) {
            if(dateFormat == null) {
                dateFormat = DateFormat.getDateTimeInstance();
            }

            String level = lr.getLevel().getName().toLowerCase();
            String message = dateFormat.format(new Date(lr.getMillis())) + ": "
                                 //                             + lr.getLoggerName() + " => "
                                 + "<" + lr.getSourceClassName() + "::" + lr.getSourceMethodName()
                             + "> "
                                 + lr.getLevel() + ": " + formatMessage(lr);
            return "+log||" + node + "|" + level + "|" + message;
        }

    }

}
