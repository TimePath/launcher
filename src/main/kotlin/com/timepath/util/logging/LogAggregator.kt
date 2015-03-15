package com.timepath.util.logging

import java.util.LinkedList
import java.util.logging.Handler
import java.util.logging.LogRecord

public class LogAggregator : Handler() {

    private val handlers = LinkedList<Handler>()

    public fun addHandler(h: Handler) {
        handlers.add(h)
    }

    override fun publish(record: LogRecord) {
        for (h in handlers) {
            h.publish(record)
        }
    }

    override fun flush() {
        for (h in handlers) {
            h.flush()
        }
    }

    override fun close() {
        for (h in handlers) {
            h.close()
        }
    }
}
