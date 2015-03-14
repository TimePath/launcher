package com.timepath.util.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.*;

public class LogIOHandler extends StreamHandler {

    private static final Logger LOG = Logger.getLogger(LogIOHandler.class.getName());
    /**
     * Unique name
     */
    protected final String node = ManagementFactory.getRuntimeMXBean().getName();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // pollLast() actually does get
    private final Deque<String> recordDeque = new LinkedList<>();
    @Nullable
    private volatile PrintWriter pw;

    public LogIOHandler() {
        setFormatter(new LogIOFormatter());
    }

    @NotNull
    public LogIOHandler connect(final String host, final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (@NotNull Socket sock = new Socket(host, port)) {
                    pw = new PrintWriter(sock.getOutputStream(), true);
                    send("+node|" + node);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, null, e);
                }
            }
        }).start();
        return this;
    }

    public synchronized void send(@NotNull String line) {
        recordDeque.addLast(line);
        if (pw != null) {
            for (@NotNull Iterator<String> it = recordDeque.iterator(); it.hasNext(); ) {
                pw.print(it.next() + "\r\n");
                it.remove();
            }
            pw.flush();
        }
    }

    @Override
    public synchronized void publish(@NotNull LogRecord record) {
        send(getFormatter().format(record));
    }

    @Override
    public synchronized void flush() {
        if (pw != null) {
            pw.flush();
        }
    }

    @Override
    public synchronized void close() {
        send("-node|" + node);
        if (pw != null) {
            pw.close();
        }
        pw = null;
    }

    private class LogIOFormatter extends Formatter {

        @NotNull
        private DateFormat dateFormat = DateFormat.getDateTimeInstance();

        @NotNull
        @Override
        public String format(@NotNull LogRecord record) {
            @NotNull String level = record.getLevel().getName().toLowerCase();
            @NotNull String message = MessageFormat.format("{0}: <{2}::{3}> {4}: {5}",
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
