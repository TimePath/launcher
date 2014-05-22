package com.timepath.classloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Breaks the {@code ClassLoader} contract to first delegate to other {@code ClassLoader}s before its parent
 *
 * @author TimePath
 * @see <a href="http://www.jdotsoft.com/JarClassLoader.php">JarClassLoader</a>
 */
public class CompositeClassLoader extends ClassLoader {

    private static final Logger                        LOG          = Logger.getLogger(CompositeClassLoader.class.getName());
    private final        Map<String, Class<?>>         classes      = new HashMap<>();
    private final        Map<String, Enumeration<URL>> enumerations = new HashMap<>();
    private final        Map<URL, ClassLoader>         jars         = new HashMap<>();
    private final        Map<String, String>           libraries    = new HashMap<>();
    private final        List<ClassLoader>             loaders      = Collections.synchronizedList(new LinkedList<ClassLoader>());
    private final        Map<String, URL>              resources    = new HashMap<>();

    public CompositeClassLoader() {
        add(getClass().getClassLoader()); // our parent classloader
    }

    /**
     * Registers a {@code ClassLoader} on the top of the stack
     *
     * @param loader
     *         the {@code ClassLoader}
     */
    public void add(ClassLoader loader) {
        if(loader == null) {
            throw new IllegalArgumentException("ClassLoader must not be null");
        }
        synchronized(loaders) {
            loaders.add(0, loader);
        }
    }

    /**
     * Start the specified main class with {@code args} using {@code urls}
     *
     * @param main
     *         the main class name
     * @param args
     *         the command line arguments. Converted to an empty array if null
     * @param urls
     *         additional resources
     */
    public void start(String main, String[] args, Iterable<URL> urls) {
        if(args == null) {
            args = new String[0];
        }
        LOG.log(Level.INFO, "{0} {1} {2}", new Object[] {
                main, Arrays.toString(args), urls
        });
        add(urls);
        try {
            invokeMain(main, args);
        } catch(Throwable t) {
            LOG.log(Level.SEVERE, null, t);
        }
    }

    /**
     * Registers {@code URLClassLoader}s for the {@code urls}
     *
     * @param urls
     *         the URLs
     */
    public void add(Iterable<URL> urls) {
        for(URL u : urls) {
            add(u);
        }
    }

    /**
     * Registers a new URLClassLoader for the specified {@code url}
     *
     * @param url
     *         the URL
     */
    public void add(final URL url) {
        if(jars.containsKey(url)) {
            return;
        }
        ClassLoader cl = AccessController.doPrivileged(new PrivilegedAction<URLClassLoader>() {
            @Override
            public URLClassLoader run() {
                return URLClassLoader.newInstance(new URL[] { url }, CompositeClassLoader.this);
            }
        });
        jars.put(url, cl);
        add(cl);
    }

    private void invokeMain(String name, String[] args)
    throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        Method method = loadClass(name).getMethod("main", String[].class);
        int modifiers = method.getModifiers();
        if(( method.getReturnType() != void.class ) || !Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
            throw new NoSuchMethodException("main");
        }
        method.invoke(null, new Object[] { args }); // varargs call
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> res = reflect(classes, "findClass", name);
        if(res != null) {
            return res;
        }
        return super.findClass(name);
    }

    /**
     * @param cache
     *         the cache variable
     * @param methodStr
     *         the parent method name
     * @param key
     *         the key
     * @param <A>
     *         type of key
     * @param <B>
     *         type of value
     *
     * @return the first found value
     */
    @SuppressWarnings("unchecked")
    private <A, B> B reflect(Map<A, B> cache, String methodStr, A key) {
        LOG.log(Level.FINE, "{0}: {1}", new Object[] { methodStr, key });
        B ret = cache.get(key);
        if(ret != null) {
            return ret;
        }
        synchronized(loaders) {
            for(ClassLoader cl : loaders) {
                try {
                    Method method = ClassLoader.class.getDeclaredMethod(methodStr, key.getClass());
                    method.setAccessible(true);
                    B u = (B) method.invoke(cl, key);
                    if(u != null) { // return with first result
                        cache.put(key, u);
                        return u;
                    }
                } catch(InvocationTargetException ite) { // caused by underlying method
                    try {
                        throw ite.getCause();
                    } catch(ClassNotFoundException ignored) { // ignore this one, keep trying other classloaders
                    } catch(Throwable t) {
                        LOG.log(Level.SEVERE, null, t);
                    }
                } catch(Throwable t) {
                    LOG.log(Level.SEVERE, null, t);
                }
            }
        }
        return null;
    }

    @Override
    protected URL findResource(String name) {
        URL res = reflect(resources, "findResource", name);
        if(res != null) {
            return res;
        }
        return super.findResource(name);
    }

    /**
     * TODO: calling class's jar only
     */
    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> res = reflect(enumerations, "findResources", name);
        if(res != null) {
            return res;
        }
        return super.findResources(name);
    }

    @Override
    protected String findLibrary(String libname) {
        String res = reflect(libraries, "findLibrary", libname);
        if(res != null) {
            return res;
        }
        // limitation: libraries must be files. Copy to temp file
        String s = System.mapLibraryName(libname);
        URL u = findResource(s);
        try {
            File file = File.createTempFile(libname, null);
            file.deleteOnExit();
            ReadableByteChannel rbc = Channels.newChannel(u.openStream());
            FileOutputStream outputStream = new FileOutputStream(file);
            FileChannel outputChannel = outputStream.getChannel();
            long total = 0, read;
            while(( read = outputChannel.transferFrom(rbc, total, 8192) ) > 0) {
                total += read;
            }
            return file.getAbsolutePath();
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return super.findLibrary(libname);
    }
}
