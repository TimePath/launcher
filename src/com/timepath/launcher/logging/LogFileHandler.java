package com.timepath.launcher.logging;

import com.timepath.launcher.util.Utils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.logging.*;

public class LogFileHandler extends Handler {

    private static final Logger LOG = Logger.getLogger(LogFileHandler.class.getName());

    private final FileHandler fh;

    private final File logFile;

    public LogFileHandler() throws IOException, SecurityException {
        // I have to set this up to be able to recall it
        logFile = new File(Utils.currentFile.getParentFile(), MessageFormat.format(
                           "logs/log_{0}.txt", System.currentTimeMillis() / 1000));
        logFile.getParentFile().mkdirs();
        
        Formatter f = new XMLFormatter();
        fh = new FileHandler(logFile.getPath(), 0, 1, false);
        fh.setFormatter(f);
        final URL u = logFile.toURI().toURL();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                fh.flush();
                fh.close();
                Utils.logThread(Utils.UNAME + ".xml.gz", "launcher/" + Utils.currentVersion + "/logs",
                                Utils.loadPage(u)).run();
            }
        });
    }

    @Override
    public void close() throws SecurityException {
        fh.close();
    }

    @Override
    public void flush() {
        fh.flush();
    }

    public File getLogFile() {
        return logFile;
    }

    @Override
    public void publish(LogRecord lr) {
        fh.publish(lr);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " " + logFile;
    }

}
