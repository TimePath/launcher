package com.timepath.launcher;

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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * Inspired by http://www.jdotsoft.com/JarClassLoader.php
 * <p>
 * @author TimePath
 */
public class CompositeClassLoader extends ClassLoader {

    public static final Logger LOG = Logger.getLogger(CompositeClassLoader.class.getName());

    //<editor-fold defaultstate="collapsed" desc="Overriding methods and caches">
    private final HashMap<String, Class<?>> classes = new HashMap<String, Class<?>>();

    private final HashMap<String, Enumeration<URL>> enumerations
                                                    = new HashMap<String, Enumeration<URL>>();

    private HashMap<URL, ClassLoader> jars = new HashMap<URL, ClassLoader>();

    private final HashMap<String, String> libraries = new HashMap<String, String>();

    private final List<ClassLoader> loaders = Collections.synchronizedList(
        new ArrayList<ClassLoader>());

    private final HashMap<String, URL> resources = new HashMap<String, URL>();

    {
        add(Object.class.getClassLoader()); // bootstrap
        add(getClass().getClassLoader());   // whichever classloader loaded this jar
    }

    public void add(ClassLoader loader) {
        loaders.add(0, loader); // newest on top
    }

    public void add(URL u) {
        if(jars.containsKey(u)) {
            return;
        }
        URLClassLoader ucl = URLClassLoader.newInstance(new URL[] {u}, this);
        ClassLoader cl = ucl;
        jars.put(u, cl);
        add(cl);
    }

    public void add(URL[] urls) {
        for(URL u : urls) {
            add(u);
        }
    }

    public void invokeMain(String name, String[] args) throws ClassNotFoundException,
                                                              NoSuchMethodException,
                                                              InvocationTargetException {
        Class c = loadClass(name);
        Method m = c.getMethod("main", new Class[] {args.getClass()});
        m.setAccessible(true);
        int mods = m.getModifiers();
        if(m.getReturnType() != void.class || !Modifier.isStatic(mods) || !Modifier.isPublic(mods)) {
            throw new NoSuchMethodException("main");
        }
        try {
            m.invoke(null, new Object[] {args});
        } catch(IllegalAccessException e) {
        }
    }

    public void start(String name, String[] args, URL[] urls) {
        LOG.log(Level.INFO, "{0} {1} {2}", new Object[] {name, Arrays.toString(args),
                                                         Arrays.toString(urls)});
        add(urls);
        try {
            invokeMain(name, args);
        } catch(Throwable t) {
            LOG.log(Level.SEVERE, null, t);
        }
    }

    private <A, B> B reflect(Map<A, B> cache, String method, A key) {
        LOG.log(Level.INFO, "{0}: {1}", new Object[] {method, key});
        B ret = cache.get(key);
        if(cache.containsKey(key)) {
            return ret;
        } else {
            cache.put(key, ret);
            for(ClassLoader cl : loaders) {
                if(cl == null) {
                    continue;
                }
                try {
                    Method m = ClassLoader.class.getDeclaredMethod(method, key.getClass());
                    m.setAccessible(true);
                    B u = (B) m.invoke(cl, key);
                    if(u != null) {
                        cache.put(key, u);
                        return u;
                    }
                } catch(InvocationTargetException ite) { // caused by underlying method
                    try {
                        throw ite.getCause();
                    } catch(ClassNotFoundException ex) {
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
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> res = reflect(classes, "findClass", name);
        if(res != null) {
            return res;
        }
        return super.findClass(name);
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
            File f = File.createTempFile(libname, null);
            f.deleteOnExit();
            ReadableByteChannel rbc = Channels.newChannel(u.openStream());
            FileOutputStream outputStream = new FileOutputStream(f);
            FileChannel outputChannel = outputStream.getChannel();
            long total = 0, read;
            while((read = outputChannel.transferFrom(rbc, total, 8192)) > 0) {
                total += read;
            }
            return f.getAbsolutePath();
        } catch(IOException ex) {
            Logger.getLogger(CompositeClassLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return super.findLibrary(libname);
    }
    //</editor-fold>
    
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
     * <p/>
     * @param name
     *             <p/>
     * @return
     *         <p/>
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

}
