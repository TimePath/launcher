package com.timepath.util.logging;

import com.timepath.IOUtils;
import com.timepath.launcher.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.logging.*;

public class LogFileHandler extends Handler {

    private static final Logger LOG = Logger.getLogger(LogFileHandler.class.getName());
    private final FileHandler fh;
    private final File logFile;

    public LogFileHandler() throws IOException {
        // I have to set this up to be able to recall it
        logFile = new File(Utils.CURRENT_FILE.getParentFile(),
                MessageFormat.format("logs/log_{0}.txt", System.currentTimeMillis() / 1000));
        logFile.getParentFile().mkdirs();
        Formatter formatter = new XMLFormatter();
        fh = new FileHandler(logFile.getPath(), 0, 1, false);
        fh.setFormatter(formatter);
        final URL u = logFile.toURI().toURL();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                fh.flush();
                fh.close();
                Utils.logThread(Utils.USER + ".xml.gz", "launcher/" + Utils.CURRENT_VERSION + "/logs", IOUtils.requestPage(u.toString()))
                        .run();
            }
        }));
    }

    public File getLogFile() {
        return logFile;
    }

    @Override
    public void publish(LogRecord record) {
        fh.publish(record);
    }

    @Override
    public void flush() {
        fh.flush();
    }

    @Override
    public void close() {
        fh.close();
    }

    @Override
    public String toString() {
        return getClass().getName() + " -> " + logFile;
    }
}
