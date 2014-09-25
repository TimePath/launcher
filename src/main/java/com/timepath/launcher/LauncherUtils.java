package com.timepath.launcher;

import com.timepath.JARUtils;
import com.timepath.Utils;
import com.timepath.util.logging.DBInbox;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public class LauncherUtils {

    public static final String USER = MessageFormat.format("{0}@{1}",
            System.getProperty("user.name"),
            ManagementFactory.getRuntimeMXBean()
                    .getName()
                    .split("@")[1]
    );
    public static final long CURRENT_VERSION = JARUtils.version(LauncherUtils.class);
    public static final boolean DEBUG = CURRENT_VERSION == 0;
    public static final Preferences SETTINGS = Preferences.userRoot().node("timepath");
    public static final long START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();
    public static final File UPDATE = new File("update.tmp");
    public static final File CURRENT_FILE = Utils.currentFile(LauncherUtils.class);

    private LauncherUtils() {
    }

    public static void log(String name, String dir, Object o) {
        logThread(name, dir, o.toString()).start();
    }

    public static Thread logThread(final String fileName, final String directory, final String str) {
        Runnable submit = new Runnable() {
            @Override
            public void run() {
                try {
                    debug("Response: " + DBInbox.send("dbinbox.timepath.ddns.info", "timepath", fileName, directory, str));
                } catch (IOException ioe) {
                    debug(ioe);
                }
            }

            public void debug(Object o) {
                System.out.println(o);
            }
        };
        return new Thread(submit);
    }
}
