package com.timepath.launcher.logging;

import com.timepath.launcher.Utils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 *
 * Delegates to a FileHandler and allows us to keep track of the specified file
 * 
 * @author TimePath
 */
public class LogFileHandler extends Handler {

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
    
    private final File logFile;
    
    private final FileHandler fh;

    public LogFileHandler() throws IOException, SecurityException {
        // I have to set this up to be able to recall it
        logFile = new File(Utils.currentFile.getParentFile(), "logs/log_" + System
            .currentTimeMillis() / 1000 + ".txt");
        logFile.getParentFile().mkdirs();

        SimpleFormatter fileFormatter = new SimpleFormatter();
        fh = new FileHandler(logFile.getPath(), 0, 1, false);
        fh.setFormatter(fileFormatter);
        final URL u = logFile.toURI().toURL();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                fh.flush();
                fh.close();
                Utils.logThread("exit", "launcher/" + Utils.currentVersion + "/logs",
                                        Utils.loadPage(u)).run();
            }
        });
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
