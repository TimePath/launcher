package com.timepath.util.logging;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class LogAggregator extends Handler {

    private final Collection<Handler> handlers = new LinkedList<>();

    public void addHandler(Handler h) {
        handlers.add(h);
    }

    @Override
    public void publish(LogRecord record) {
        for (@NotNull Handler h : handlers) {
            h.publish(record);
        }
    }

    @Override
    public void flush() {
        for (@NotNull Handler h : handlers) {
            h.flush();
        }
    }

    @Override
    public void close() {
        for (@NotNull Handler h : handlers) {
            h.close();
        }
    }
}
