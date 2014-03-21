package com.timepath.launcher.logging;

import com.timepath.launcher.Utils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

/**
 *
 * @author TimePath
 */
public class LogFileHandler extends FileHandler {

    private final File logFile;

    public LogFileHandler() throws IOException, SecurityException {
        // I have to set this up to be able to recall it
        logFile = new File(Utils.currentFile.getParentFile(), "logs/log_" + System
            .currentTimeMillis() / 1000 + ".txt");
        logFile.getParentFile().mkdirs();

        final FileHandler fh = new FileHandler(logFile.getPath(), 0, 1, false);
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

        SimpleFormatter fileFormatter = new SimpleFormatter();
        this.setFormatter(fileFormatter);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " " + logFile;
    }

}
