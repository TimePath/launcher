package com.timepath.launcher;

import com.timepath.launcher.logging.LogAggregator;
import com.timepath.launcher.ui.swing.LauncherFrame;
import java.awt.*;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;
import javax.swing.JApplet;

import static com.timepath.launcher.Utils.debug;
import static com.timepath.launcher.Utils.start;

/**
 *
 * @author TimePath
 */
@SuppressWarnings("serial")
public class Main extends JApplet {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static String updateName = "update.tmp";

    static {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception", e);
            }
        });

        Level consoleLevelTmp = Level.INFO;
        try {
            consoleLevelTmp = Level.parse(Utils.settings.get("consoleLevel", "CONFIG"));
        } catch(IllegalArgumentException ex) {
        }
        Utils.settings.remove("consoleLevel"); // TEMP

        Level logfileLevelTmp = Level.INFO;
        try {
            logfileLevelTmp = Level.parse(Utils.settings.get("logfileLevel", "CONFIG"));
        } catch(IllegalArgumentException ex) {
        }
        Utils.settings.remove("logfileLevel"); // TEMP

        Level packageLevel = consoleLevelTmp;
        if(consoleLevelTmp != Level.OFF && logfileLevelTmp != Level.OFF) {
            if(logfileLevelTmp.intValue() > consoleLevelTmp.intValue()) {
                packageLevel = logfileLevelTmp;
            }
        }
        Logger.getLogger("com.timepath").setLevel(packageLevel);

        SimpleFormatter consoleFormatter = new SimpleFormatter();

        if(consoleLevelTmp != Level.OFF) {
            Handler[] hs = Logger.getLogger("").getHandlers();
            for(Handler h : hs) {
                if(h instanceof ConsoleHandler) {
                    h.setLevel(consoleLevelTmp);
                    h.setFormatter(consoleFormatter);
                }
            }
        }

        if(logfileLevelTmp != Level.OFF) {
            LogAggregator lh = new LogAggregator();
            lh.setLevel(logfileLevelTmp);
            Logger.getLogger("").addHandler(lh);
            LOG.log(Level.INFO, "Logger: {0}", lh);
        }
        LOG.log(Level.INFO, "Console level: {0}", consoleLevelTmp);
        LOG.log(Level.INFO, "Logfile level: {0}", logfileLevelTmp);

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

    public static void main(String... args) {
        LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - start);
        LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
        Utils.checkForUpdate(updateName, args);
        String dbg = ManagementFactory.getRuntimeMXBean().getName();
        try {
            dbg += "\n" + InetAddress.getByName(null).getHostName();
        } catch(UnknownHostException ex) {
        }
        dbg += "\nEnvir = " + System.getenv().toString();
        dbg += "\nProps = " + System.getProperties().toString();
        LOG.info(dbg);
        if(!debug) {
            Utils.log("connected", "launcher/" + Utils.currentVersion + "/connects", dbg);
        }
        LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - start);

        EventQueue.invokeLater(new Runnable() {
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
        main("");
    }

}
