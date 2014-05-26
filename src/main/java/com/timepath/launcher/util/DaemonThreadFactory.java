package com.timepath.launcher.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author TimePath
 */
public class DaemonThreadFactory implements ThreadFactory {

    public DaemonThreadFactory() {}

    @Override
    public Thread newThread(Runnable r) {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    }
}
