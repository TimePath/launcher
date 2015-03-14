package com.timepath.launcher;

import com.timepath.SwingUtils;
import com.timepath.launcher.ui.swing.LauncherFrame;
import com.timepath.util.logging.LogAggregator;
import com.timepath.util.logging.LogFileHandler;
import com.timepath.util.logging.LogIOHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

/**
 * @author TimePath
 */
public class Main implements Protocol {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    static {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NotNull Thread t, Throwable e) {
                Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception in " + t + ":", e);
            }
        });
        Policy.setPolicy(new Policy() {
            @NotNull
            @Override
            public PermissionCollection getPermissions(CodeSource codesource) {
                @NotNull Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return perms;
            }
        });
        System.setSecurityManager(null);
    }

    @NotNull
    private static String RMI_ENDPOINT = "com/timepath/launcher";
    private Launcher launcher;

    public static void main(@NotNull String[] args) {
        @Nullable Protocol main = getInstance();
        boolean local = main instanceof Main;
        if (local) {
            LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME);
            LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args));
            Updater.checkForUpdate(args);
            initLogging();
            @NotNull Map<String, Object> dbg = new HashMap<>(3);
            dbg.put("name", ManagementFactory.getRuntimeMXBean().getName());
            dbg.put("env", System.getenv());
            dbg.put("properties", System.getProperties());
            @Nullable String pprint = com.timepath.Utils.pprint(dbg);
            if (!LauncherUtils.DEBUG) {
                LauncherUtils.log(LauncherUtils.USER + ".xml.gz", "launcher/" + LauncherUtils.CURRENT_VERSION + "/connects", pprint);
            }
            LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME);
        }
        try {
            main.newFrame();
        } catch (RemoteException e) {
            LOG.log(Level.SEVERE, null, e);
        }
    }

    @Nullable
    public static Protocol getInstance() {
        int port = 1099; // FIXME: Hardcoded
        @Nullable Protocol stub = null;
        if (Launcher.PREFS.getBoolean("rmi", false)) {
            if ((stub = createServer(port)) == null) stub = createClient(port);
        }
        if (stub == null) stub = new Main(); // Legacy fallback
        return stub;
    }

    @Nullable
    private static Protocol createClient(int port) {
        LOG.log(Level.INFO, "RMI server already started, connecting...");
        try {
            @NotNull Registry registry = LocateRegistry.getRegistry("localhost", port);
            return (Protocol) registry.lookup(RMI_ENDPOINT);
        } catch (@NotNull RemoteException | NotBoundException e) {
            LOG.log(Level.SEVERE, "Unable to connect to RMI server", e);
        }
        return null;
    }

    @Nullable
    private static Protocol createServer(int port) {
        try {
            class LocalRMIServerSocketFactory implements RMIServerSocketFactory {

                ServerSocket socket;

                @NotNull
                @Override
                public ServerSocket createServerSocket(int port) throws IOException {
                    return (socket = new ServerSocket(port, 0, InetAddress.getByName(null)));
                }
            }
            @NotNull LocalRMIServerSocketFactory serverFactory = new LocalRMIServerSocketFactory();
            @NotNull Registry registry = LocateRegistry.createRegistry(port, new RMIClientSocketFactory() {
                @NotNull
                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    return new Socket(host, port);
                }
            }, serverFactory);
            port = serverFactory.socket.getLocalPort();
            LOG.log(Level.INFO, "RMI server listening on port {0}", port);
            @NotNull Main main = new Main();
            @NotNull Protocol stub = (Protocol) UnicastRemoteObject.exportObject(main, 0);
            registry.rebind(RMI_ENDPOINT, stub);
            return main;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unable to start RMI server: {0}", e.getMessage());
            return null;
        }
    }

    private static void initLogging() {
        Level consoleLevel = Level.CONFIG;
        try {
            consoleLevel = Level.parse(LauncherUtils.SETTINGS.get("consoleLevel", consoleLevel.getName()));
        } catch (@NotNull IllegalArgumentException | NullPointerException ignored) {
        }
        Level logfileLevel = Level.CONFIG;
        try {
            logfileLevel = Level.parse(LauncherUtils.SETTINGS.get("logfileLevel", logfileLevel.getName()));
        } catch (@NotNull IllegalArgumentException | NullPointerException ignored) {
        }
        // Choose finest level
        @NotNull Level packageLevel = Level.parse(Integer.toString(Math.min(logfileLevel.intValue(), consoleLevel.intValue())));
        Logger.getLogger("com.timepath").setLevel(packageLevel);
        @NotNull Logger globalLogger = Logger.getLogger("");
        @NotNull SimpleFormatter consoleFormatter = new SimpleFormatter();
        if (!consoleLevel.equals(Level.OFF)) {
            for (Handler h : globalLogger.getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    h.setLevel(consoleLevel);
                    h.setFormatter(consoleFormatter);
                }
            }
        }
        if (!logfileLevel.equals(Level.OFF)) {
            @NotNull LogAggregator lh = new LogAggregator();
            if (!LauncherUtils.DEBUG) {
                try {
                    lh.addHandler(new LogFileHandler());
                } catch (@NotNull IOException | SecurityException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            lh.addHandler(new LogIOHandler().connect("logging.timepath.ddns.info", 28777));
            lh.setLevel(logfileLevel);
            globalLogger.addHandler(lh);
            LOG.log(Level.INFO, "Logger: {0}", lh);
        }
        LOG.log(Level.INFO, "Console level: {0}", consoleLevel);
        LOG.log(Level.INFO, "Logfile level: {0}", logfileLevel);
        LOG.log(Level.INFO, "Package level: {0}", packageLevel);
    }

    @Override
    public void newFrame() throws RemoteException {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (launcher == null) {
                    SwingUtils.lookAndFeel(LauncherUtils.SETTINGS);
                    launcher = new Launcher();
                }
                new LauncherFrame(launcher).setVisible(true);
            }
        });
    }
}
