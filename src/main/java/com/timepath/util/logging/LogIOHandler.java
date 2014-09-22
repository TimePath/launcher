package com.timepath.util.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.*;

public class LogIOHandler extends StreamHandler {

    private static final Logger LOG = Logger.getLogger(LogIOHandler.class.getName());
    /**
     * unique
     */
    protected final String node = ManagementFactory.getRuntimeMXBean().getName();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // pollLast() actually does get
    private final Deque<LogRecord> recordDeque = new LinkedList<>();
    private PrintWriter pw;

    public LogIOHandler() {
        setFormatter(new LogIOFormatter());
    }

    public LogIOHandler connect(final String host, final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket sock = new Socket(host, port);
                    pw = new PrintWriter(sock.getOutputStream(), true);
                    send("+node|" + node);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }).start();
        return this;
    }

    private void send(String req) {
        pw.print(req + "\r\n");
        pw.flush();
    }

    @Override
    public synchronized void publish(LogRecord record) {
        recordDeque.addLast(record);
        if (pw != null) {
            send(getFormatter().format(record));
            recordDeque.pollLast(); // remove it after sending
        }
    }

    @Override
    public synchronized void flush() {
        pw.flush();
    }

    @Override
    public synchronized void close() {
        send("-node|" + node);
        pw.close();
        pw = null;
    }

    private class LogIOFormatter extends Formatter {

        private DateFormat dateFormat;

        private LogIOFormatter() {
        }

        @Override
        public synchronized String format(LogRecord record) {
            if (dateFormat == null) {
                dateFormat = DateFormat.getDateTimeInstance();
            }
            String level = record.getLevel().getName().toLowerCase();
            String message = MessageFormat.format("{0}: <{2}::{3}> {4}: {5}",
                    dateFormat.format(new Date(record.getMillis())),
                    record.getLoggerName(),
                    record.getSourceClassName(),
                    record.getSourceMethodName(),
                    record.getLevel(),
                    formatMessage(record));
            return "+log||" + node + '|' + level + '|' + message;
        }
    }
}
