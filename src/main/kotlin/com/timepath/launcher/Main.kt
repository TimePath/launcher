package com.timepath.launcher

import com.timepath.SwingUtils
import com.timepath.launcher.ui.swing.LauncherFrame
import com.timepath.util.logging.LogAggregator
import com.timepath.util.logging.LogFileHandler
import com.timepath.util.logging.LogIOHandler

import javax.swing.*
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.rmi.NotBoundException
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.rmi.server.RMIClientSocketFactory
import java.rmi.server.RMIServerSocketFactory
import java.rmi.server.UnicastRemoteObject
import java.security.*
import java.util.Arrays
import java.util.HashMap
import java.util.logging.*
import kotlin.properties.Delegates
import kotlin.platform.platformStatic

/**
 * @author TimePath
 */
public class Main : Protocol {
    private var launcher: Launcher? = null

    throws(javaClass<RemoteException>())
    override fun newFrame() {
        SwingUtilities.invokeLater(object : Runnable {
            override fun run() {
                if (launcher == null) {
                    SwingUtils.lookAndFeel(LauncherUtils.SETTINGS)
                    launcher = Launcher()
                }
                LauncherFrame(launcher!!).setVisible(true)
            }
        })
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<Main>().getName())

        ;{
            Thread.setDefaultUncaughtExceptionHandler(object : Thread.UncaughtExceptionHandler {
                override fun uncaughtException(t: Thread, e: Throwable) {
                    Logger.getLogger(t.getName()).log(Level.SEVERE, "Uncaught Exception in " + t + ":", e)
                }
            })
            Policy.setPolicy(object : Policy() {
                override fun getPermissions(codesource: CodeSource): PermissionCollection {
                    val perms = Permissions()
                    perms.add(AllPermission())
                    return perms
                }
            })
            System.setSecurityManager(null)
        }

        private val RMI_ENDPOINT = "com/timepath/launcher"

        public platformStatic fun main(args: Array<String>) {
            val main = getInstance()
            val local = main is Main
            if (local) {
                LOG.log(Level.INFO, "Initial: {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME)
                LOG.log(Level.INFO, "Args = {0}", Arrays.toString(args))
                Updater.checkForUpdate(args)
                initLogging()
                val dbg = HashMap<String, Any>(3)
                dbg.put("name", ManagementFactory.getRuntimeMXBean().getName())
                dbg.put("env", System.getenv())
                dbg.put("properties", System.getProperties())
                val pprint = com.timepath.Utils.pprint(dbg)
                if (!LauncherUtils.DEBUG) {
                    LauncherUtils.log(LauncherUtils.USER + ".xml.gz", "launcher/" + LauncherUtils.CURRENT_VERSION + "/connects", pprint)
                }
                LOG.log(Level.INFO, "Startup: {0}ms", System.currentTimeMillis() - LauncherUtils.START_TIME)
            }
            try {
                main!!.newFrame()
            } catch (e: RemoteException) {
                LOG.log(Level.SEVERE, null, e)
            }

        }

        public fun getInstance(): Protocol? {
            val port = 1099 // FIXME: Hardcoded
            var stub: Protocol? = null
            if (Launcher.PREFS.getBoolean("rmi", false)) {
                stub = createServer(port) ?: createClient(port)
            }
            if (stub == null) stub = Main() // Legacy fallback
            return stub
        }

        private fun createClient(port: Int): Protocol? {
            LOG.log(Level.INFO, "RMI server already started, connecting...")
            try {
                val registry = LocateRegistry.getRegistry("localhost", port)
                return registry.lookup(RMI_ENDPOINT) as Protocol
            } catch (e: RemoteException) {
                LOG.log(Level.SEVERE, "Unable to connect to RMI server", e)
            } catch (e: NotBoundException) {
                LOG.log(Level.SEVERE, "Unable to connect to RMI server", e)
            }

            return null
        }

        private fun createServer(port: Int): Protocol? {
            var port = port
            try {
                class LocalRMIServerSocketFactory : RMIServerSocketFactory {

                    var socket: ServerSocket by Delegates.notNull()

                    throws(javaClass<IOException>())
                    override fun createServerSocket(port: Int): ServerSocket {
                        socket = ServerSocket(port, 0, InetAddress.getByName(null))
                        return socket
                    }
                }

                val serverFactory = LocalRMIServerSocketFactory()
                val registry = LocateRegistry.createRegistry(port, object : RMIClientSocketFactory {
                    throws(javaClass<IOException>())
                    override fun createSocket(host: String, port: Int): Socket {
                        return Socket(host, port)
                    }
                }, serverFactory)
                port = serverFactory.socket.getLocalPort()
                LOG.log(Level.INFO, "RMI server listening on port {0}", port)
                val main = Main()
                val stub = UnicastRemoteObject.exportObject(main, 0) as Protocol
                registry.rebind(RMI_ENDPOINT, stub)
                return main
            } catch (e: IOException) {
                LOG.log(Level.WARNING, "Unable to start RMI server: {0}", e.getMessage())
                return null
            }

        }

        private fun initLogging() {
            var consoleLevel = Level.CONFIG
            try {
                consoleLevel = Level.parse(LauncherUtils.SETTINGS["consoleLevel", consoleLevel.getName()])
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: NullPointerException) {
            }

            var logfileLevel = Level.CONFIG
            try {
                logfileLevel = Level.parse(LauncherUtils.SETTINGS["logfileLevel", logfileLevel.getName()])
            } catch (ignored: IllegalArgumentException) {
            } catch (ignored: NullPointerException) {
            }

            // Choose finest level
            val packageLevel = Level.parse(Integer.toString(Math.min(logfileLevel.intValue(), consoleLevel.intValue())))
            Logger.getLogger("com.timepath").setLevel(packageLevel)
            val globalLogger = Logger.getLogger("")
            val consoleFormatter = SimpleFormatter()
            if (consoleLevel != Level.OFF) {
                for (h in globalLogger.getHandlers()) {
                    if (h is ConsoleHandler) {
                        h.setLevel(consoleLevel)
                        h.setFormatter(consoleFormatter)
                    }
                }
            }
            if (logfileLevel != Level.OFF) {
                val lh = LogAggregator()
                if (!LauncherUtils.DEBUG) {
                    try {
                        lh.addHandler(LogFileHandler())
                    } catch (ex: IOException) {
                        LOG.log(Level.SEVERE, null, ex)
                    } catch (ex: SecurityException) {
                        LOG.log(Level.SEVERE, null, ex)
                    }

                }
                lh.addHandler(LogIOHandler().connect("logging.timepath.ddns.info", 28777))
                lh.setLevel(logfileLevel)
                globalLogger.addHandler(lh)
                LOG.log(Level.INFO, "Logger: {0}", lh)
            }
            LOG.log(Level.INFO, "Console level: {0}", consoleLevel)
            LOG.log(Level.INFO, "Logfile level: {0}", logfileLevel)
            LOG.log(Level.INFO, "Package level: {0}", packageLevel)
        }
    }
}
