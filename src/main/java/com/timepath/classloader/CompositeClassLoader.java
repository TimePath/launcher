package com.timepath.classloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.security.PrivilegedAction;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.security.AccessController.doPrivileged;

/**
 * Breaks the {@code ClassLoader} contract to first delegate to other {@code ClassLoader}s before its parent
 *
 * @author TimePath
 * @see <a href="http://www.jdotsoft.com/JarClassLoader.php">JarClassLoader</a>
 */
public class CompositeClassLoader extends ClassLoader {

    private static final Logger LOG = Logger.getLogger(CompositeClassLoader.class.getName());
    private final Map<String, Class<?>> classes = new HashMap<>();
    private final Map<String, Enumeration<URL>> enumerations = new HashMap<>();
    private final Map<URL, ClassLoader> jars = new HashMap<>();
    private final Map<String, String> libraries = new HashMap<>();
    private final List<ClassLoader> loaders = Collections.synchronizedList(new LinkedList<ClassLoader>());
    private final Map<String, URL> resources = new HashMap<>();

    public CompositeClassLoader() {
        add(getClass().getClassLoader()); // our parent classloader
    }

    public static CompositeClassLoader createPrivileged() {
        return doPrivileged(new PrivilegedAction<CompositeClassLoader>() {
            @NotNull
            @Override
            public CompositeClassLoader run() {
                return new CompositeClassLoader();
            }
        });
    }

    /**
     * Registers a {@code ClassLoader} on the top of the stack
     *
     * @param loader the {@code ClassLoader}
     */
    public void add(@Nullable ClassLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("ClassLoader must not be null");
        }
        synchronized (loaders) {
            loaders.add(0, loader);
        }
    }

    /**
     * Start the specified main class with {@code args} using {@code urls}
     *
     * @param main the main class name
     * @param args the command line arguments. Converted to an empty array if null
     * @param urls additional resources
     */
    public void start(String main, @Nullable String[] args, @NotNull Iterable<URL> urls) throws Throwable {
        if (args == null) {
            args = new String[0];
        }
        LOG.log(Level.INFO, "{0} {1} {2}", new Object[]{
                main, Arrays.toString(args), urls
        });
        add(urls);
        invokeMain(main, args);
    }

    /**
     * Registers {@code URLClassLoader}s for the {@code urls}
     *
     * @param urls the URLs
     */
    public void add(@NotNull Iterable<URL> urls) {
        for (URL u : urls) {
            add(u);
        }
    }

    /**
     * Registers a new URLClassLoader for the specified {@code url}
     *
     * @param url the URL
     */
    public void add(final URL url) {
        if (jars.containsKey(url)) {
            return;
        }
        ClassLoader cl = doPrivileged(new PrivilegedAction<URLClassLoader>() {
            @Override
            public URLClassLoader run() {
                return URLClassLoader.newInstance(new URL[]{url}, CompositeClassLoader.this);
            }
        });
        jars.put(url, cl);
        add(cl);
    }

    private void invokeMain(String name, String[] args)
            throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Method method = loadClass(name).getMethod("main", String[].class);
        int modifiers = method.getModifiers();
        if ((method.getReturnType() != void.class) || !Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
            throw new NoSuchMethodException("main");
        }
        method.setAccessible(true);
        method.invoke(null, new Object[]{args}); // varargs call
    }

    @Nullable
    @Override
    protected Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
        @Nullable Class<?> res = reflect(classes, "findClass", name);
        if (res != null) {
            return res;
        }
        return super.findClass(name);
    }

    /**
     * @param cache     the cache variable
     * @param methodStr the parent method name
     * @param key       the key
     * @param <A>       type of key
     * @param <B>       type of value
     * @return the first found value
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private <A, B> B reflect(@NotNull Map<A, B> cache, String methodStr, @NotNull A key) {
        LOG.log(Level.FINE, "{0}: {1}", new Object[]{methodStr, key});
        B ret = cache.get(key);
        if (ret != null) {
            return ret;
        }
        synchronized (loaders) {
            for (ClassLoader cl : loaders) {
                try {
                    Method method = ClassLoader.class.getDeclaredMethod(methodStr, key.getClass());
                    method.setAccessible(true);
                    @NotNull B u = (B) method.invoke(cl, key);
                    if (u != null) { // return with first result
                        cache.put(key, u);
                        return u;
                    }
                } catch (InvocationTargetException ite) { // caused by underlying method
                    try {
                        throw ite.getCause();
                    } catch (ClassNotFoundException ignored) { // ignore this one, keep trying other classloaders
                    } catch (Throwable t) {
                        LOG.log(Level.SEVERE, null, t);
                    }
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, null, t);
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    protected URL findResource(@NotNull String name) {
        @Nullable URL res = reflect(resources, "findResource", name);
        if (res != null) {
            return res;
        }
        return super.findResource(name);
    }

    /**
     * TODO: calling class's jar only
     */
    @Nullable
    @Override
    protected Enumeration<URL> findResources(@NotNull String name) throws IOException {
        @Nullable Enumeration<URL> res = reflect(enumerations, "findResources", name);
        if (res != null) {
            return res;
        }
        return super.findResources(name);
    }

    @Nullable
    @Override
    protected String findLibrary(@NotNull String libname) {
        @Nullable String res = reflect(libraries, "findLibrary", libname);
        if (res != null) {
            return res;
        }
        // limitation: libraries must be files. Copy to temp file
        String s = System.mapLibraryName(libname);
        @Nullable URL u = findResource(s);
        try {
            @NotNull File file = File.createTempFile(libname, null);
            file.deleteOnExit();
            ReadableByteChannel rbc = Channels.newChannel(u.openStream());
            @NotNull FileOutputStream outputStream = new FileOutputStream(file);
            @NotNull FileChannel outputChannel = outputStream.getChannel();
            long total = 0, read;
            while ((read = outputChannel.transferFrom(rbc, total, 8192)) > 0) {
                total += read;
            }
            return file.getAbsolutePath();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return super.findLibrary(libname);
    }
}
