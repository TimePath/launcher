package com.timepath.classloader


import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.security.PrivilegedAction
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

import java.security.AccessController.doPrivileged

/**
 * Breaks the {@code ClassLoader} contract to first delegate to other {@code ClassLoader}s before its parent
 *
 * @author TimePath
 * @see <a href="http://www.jdotsoft.com/JarClassLoader.php">JarClassLoader</a>
 */
public class CompositeClassLoader : ClassLoader() {
    private val classes = HashMap<String, Class<*>>()
    private val enumerations = HashMap<String, Enumeration<URL>>()
    private val jars = HashMap<URL, ClassLoader>()
    private val libraries = HashMap<String, String>()
    private val loaders = Collections.synchronizedList<ClassLoader>(LinkedList<ClassLoader>())
    private val resources = HashMap<String, URL>()

    /**
     * Registers a {@code ClassLoader} on the top of the stack
     *
     * @param loader the {@code ClassLoader}
     */
    public fun add(loader: ClassLoader) {
        synchronized (loaders) {
            loaders.add(0, loader)
        }
    }

    {
        add(javaClass.getClassLoader()) // our parent classloader
    }

    /**
     * Start the specified main class with {@code args} using {@code urls}
     *
     * @param main the main class name
     * @param args the command line arguments
     * @param urls additional resources
     */
    throws(javaClass<Throwable>())
    public fun start(main: String, args: Array<String>, urls: Iterable<URL>) {
        LOG.log(Level.INFO, "{0} {1} {2}", array(main, Arrays.toString(args), urls))
        add(urls)
        invokeMain(main, args)
    }

    /**
     * Registers {@code URLClassLoader}s for the {@code urls}
     *
     * @param urls the URLs
     */
    public fun add(urls: Iterable<URL>) {
        for (u in urls) {
            add(u)
        }
    }

    /**
     * Registers a new URLClassLoader for the specified {@code url}
     *
     * @param url the URL
     */
    public fun add(url: URL) {
        if (jars.containsKey(url)) {
            return
        }
        val cl = doPrivileged(object : PrivilegedAction<URLClassLoader> {
            override fun run(): URLClassLoader {
                return URLClassLoader.newInstance(array(url), this@CompositeClassLoader)
            }
        })
        jars.put(url, cl)
        add(cl)
    }

    throws(javaClass<ClassNotFoundException>(), javaClass<IllegalAccessException>(), javaClass<InvocationTargetException>(), javaClass<NoSuchMethodException>())
    private fun invokeMain(name: String, args: Array<String>) {
        val method = loadClass(name).getMethod("main", javaClass<Array<String>>())
        val modifiers = method.getModifiers()
        if ((method.getReturnType() != Void.TYPE) || !Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
            throw NoSuchMethodException("main")
        }
        method.setAccessible(true)
        method.invoke(null, *array<Any>(args)) // varargs call
    }

    throws(javaClass<ClassNotFoundException>())
    override fun findClass(name: String): Class<*> {
        val res = reflect(classes, "findClass", name)
        if (res != null) {
            return res
        }
        return super.findClass(name)
    }

    /**
     * @param cache     the cache variable
     * @param methodStr the parent method name
     * @param key       the key
     * @param <A>       type of key
     * @param <B>       type of value
     * @return the first found value
     */
    SuppressWarnings("unchecked")
    private fun <A, B> reflect(cache: MutableMap<A, B>, methodStr: String, key: A): B {
        LOG.log(Level.FINE, "{0}: {1}", array<Any>(methodStr, key))
        val ret = cache[key]
        if (ret != null) {
            return ret
        }
        synchronized (loaders) {
            for (cl in loaders) {
                try {
                    val method = javaClass<ClassLoader>().getDeclaredMethod(methodStr, key.javaClass)
                    method.setAccessible(true)
                    val u = method.invoke(cl, key) as B
                    if (u != null) {
                        // return with first result
                        cache.put(key, u)
                        return u
                    }
                } catch (ite: InvocationTargetException) {
                    // caused by underlying method
                    try {
                        throw ite.getCause()
                    } catch (ignored: ClassNotFoundException) {
                        // ignore this one, keep trying other classloaders
                    } catch (t: Throwable) {
                        LOG.log(Level.SEVERE, null, t)
                    }

                } catch (t: Throwable) {
                    LOG.log(Level.SEVERE, null, t)
                }

            }
        }
        return null
    }

    override fun findResource(name: String): URL? {
        val res = reflect(resources, "findResource", name)
        if (res != null) {
            return res
        }
        return super.findResource(name)
    }

    /**
     * TODO: calling class's jar only
     */
    throws(javaClass<IOException>())
    override fun findResources(name: String): Enumeration<URL> {
        val res = reflect(enumerations, "findResources", name)
        if (res != null) {
            return res
        }
        return super.findResources(name)
    }

    override fun findLibrary(libname: String): String? {
        val res = reflect(libraries, "findLibrary", libname)
        if (res != null) {
            return res
        }
        // limitation: libraries must be files. Copy to temp file
        val s = System.mapLibraryName(libname)
        val u = findResource(s)
        try {
            val file = File.createTempFile(libname, null)
            file.deleteOnExit()
            val rbc = Channels.newChannel(u!!.openStream())
            val outputStream = FileOutputStream(file)
            val outputChannel = outputStream.getChannel()
            var total: Long = 0
            while (true) {
                val read = outputChannel.transferFrom(rbc, total, 8192)
                if(read <= 0) break
                total += read
            }
            return file.getAbsolutePath()
        } catch (ex: IOException) {
            LOG.log(Level.SEVERE, null, ex)
        }

        return super.findLibrary(libname)
    }

    class object {

        private val LOG = Logger.getLogger(javaClass<CompositeClassLoader>().getName())

        public fun createPrivileged(): CompositeClassLoader {
            return doPrivileged(object : PrivilegedAction<CompositeClassLoader> {
                override fun run(): CompositeClassLoader {
                    return CompositeClassLoader()
                }
            })
        }
    }
}
