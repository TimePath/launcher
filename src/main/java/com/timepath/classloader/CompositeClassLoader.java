package com.timepath.classloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
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
 * @author TimePath
 * @see <a href="http://www.jdotsoft.com/JarClassLoader.php">JarClassLoader</a>
 */
public class CompositeClassLoader extends ClassLoader {

    private static final Logger                        LOG          = Logger.getLogger(CompositeClassLoader.class.getName());
    private final        Map<String, Class<?>>         classes      = new HashMap<>(0);
    private final        Map<String, Enumeration<URL>> enumerations = new HashMap<>(0);
    private final        Map<URI, ClassLoader>         jars         = new HashMap<>(0);
    private final        Map<String, String>           libraries    = new HashMap<>(0);
    private final        List<ClassLoader>             loaders      = Collections.synchronizedList(new LinkedList<ClassLoader>());
    private final        Map<String, URL>              resources    = new HashMap<>(0);

    public CompositeClassLoader() {
        add(Object.class.getClassLoader()); // bootstrap
        add(getClass().getClassLoader());   // whichever classloader loaded this jar
    }

    public void add(ClassLoader loader) {
        loaders.add(0, loader); // newest on top
    }

    public void start(String name, String[] args, URI[] urls) {
        LOG.log(Level.INFO, "{0} {1} {2}", new Object[] {
                name, Arrays.toString(args), Arrays.toString(urls)
        });
        add(urls);
        try {
            invokeMain(name, args);
        } catch(Throwable t) {
            LOG.log(Level.SEVERE, null, t);
        }
    }

    public void add(URI[] urls) {
        for(URI u : urls) {
            add(u);
        }
    }

    public void add(URI uri) {
        if(jars.containsKey(uri)) {
            return;
        }
        final URL url;
        try {
            url = uri.toURL();
        } catch(MalformedURLException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return;
        }
        ClassLoader cl = AccessController.doPrivileged(new PrivilegedAction<URLClassLoader>() {
            @Override
            public URLClassLoader run() {
                return URLClassLoader.newInstance(new URL[] { url }, CompositeClassLoader.this);
            }
        });
        jars.put(uri, cl);
        add(cl);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void invokeMain(String name, String[] args)
    throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException
    {
        Class<?> clazz = loadClass(name);
        Method method = clazz.getMethod("main", String[].class);
        int mods = method.getModifiers();
        if(( method.getReturnType() != void.class ) || !Modifier.isStatic(mods) || !Modifier.isPublic(mods)) {
            throw new NoSuchMethodException("main");
        }
        try {
            String[] argv = args;
            if(args == null) {
                argv = new String[0];
            }
            method.invoke(null, new Object[] { argv });
        } catch(IllegalAccessException ignored) {
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> res = reflect(classes, "findClass", name);
        if(res != null) {
            return res;
        }
        return super.findClass(name);
    }

    @SuppressWarnings("unchecked")
    private <A, B> B reflect(Map<A, B> cache, String methodStr, A key) {
        LOG.log(Level.FINE, "{0}: {1}", new Object[] { methodStr, key });
        B ret = cache.get(key);
        if(cache.containsKey(key)) {
            return ret;
        }
        cache.put(key, ret);
        for(ClassLoader cl : loaders) {
            if(cl == null) {
                continue;
            }
            try {
                Method method = ClassLoader.class.getDeclaredMethod(methodStr, key.getClass());
                method.setAccessible(true);
                B u = (B) method.invoke(cl, key);
                if(u != null) {
                    cache.put(key, u);
                    return u;
                }
            } catch(InvocationTargetException ite) { // Caused by underlying method
                try {
                    throw ite.getCause();
                } catch(ClassNotFoundException ignored) {
                } catch(Throwable t) {
                    LOG.log(Level.SEVERE, null, t);
                }
            } catch(Throwable t) {
                LOG.log(Level.SEVERE, null, t);
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
     *
     * @param name
     *         *
     *
     * @return *
     *
     * @throws IOException
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
