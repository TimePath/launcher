package com.timepath.launcher;

import com.timepath.launcher.logging.LogAggregator;
import com.timepath.launcher.ui.swing.LauncherFrame;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.security.*;
import java.util.*;
import java.util.logging.*;
import javax.swing.JApplet;
import javax.swing.SwingUtilities;

import static com.timepath.launcher.Utils.debug;
import static com.timepath.launcher.Utils.start;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Main extends JApplet {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    static {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception", e);
            }
        });

        Level consoleLevel = Level.CONFIG;
        try {
            consoleLevel = Level.parse(Utils.settings.get("consoleLevel", consoleLevel.getName()));
        } catch(IllegalArgumentException | NullPointerException ignore) {
        }

        Level logfileLevel = Level.CONFIG;
        try {
            logfileLevel = Level.parse(Utils.settings.get("logfileLevel", logfileLevel.getName()));
        } catch(IllegalArgumentException | NullPointerException ignore) {
        }

        // Choose finest level
        Level packageLevel = Level.parse(Integer.toString(Math.min(logfileLevel.intValue(),
                                                                   consoleLevel.intValue())));
        Logger.getLogger("com.timepath").setLevel(packageLevel);

        Logger globalLogger = Logger.getLogger("");

        SimpleFormatter consoleFormatter = new SimpleFormatter();
        if(consoleLevel != Level.OFF) {
            for(Handler h : globalLogger.getHandlers()) {
                if(h instanceof ConsoleHandler) {
                    h.setLevel(consoleLevel);
                    h.setFormatter(consoleFormatter);
                }
            }
        }

        if(logfileLevel != Level.OFF) {
            LogAggregator lh = new LogAggregator();
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
                return (perms);
            }

            @Override
            public void refresh() {
            }
        });

        System.setSecurityManager(null);
    }

    public static void main(String[] args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - start);
        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        Utils.checkForUpdate(args);
        String dbg = ManagementFactory.getRuntimeMXBean().getName();
        dbg += "\n{Envir=" + System.getenv().toString();
        dbg += ",\nProps=" + System.getProperties().toString() + "}";
        LOG.info(dbg);

        if(!debug) {
            Utils.log("connected", "launcher/" + Utils.currentVersion + "/connects", dbg);
        }

        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - start);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Utils.lookAndFeel();
                Launcher l = new Launcher();
                new LauncherFrame(l).setVisible(true);
                LOG.log(Level.INFO, "Visible at {0}ms", System.currentTimeMillis() - start);
            }
        });
    }

    @Override
    public void init() {
        main(new String[0]);
    }

}
