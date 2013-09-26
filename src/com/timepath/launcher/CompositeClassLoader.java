package com.timepath.launcher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author TimePath
 */
public class CompositeClassLoader extends ClassLoader {

    public static final Logger LOG = Logger.getLogger(CompositeClassLoader.class.getName());

    private final List<ClassLoader> loaders = Collections.synchronizedList(
            new ArrayList<ClassLoader>());

    {
        add(Object.class.getClassLoader()); // bootstrap
        add(getClass().getClassLoader());   // whichever classloader loaded this jar
    }

    public void add(ClassLoader loader) {
        loaders.add(0, loader); // newest on top
    }
    
    private HashMap<URL, URLClassLoader> jars = new HashMap<URL, URLClassLoader>();

    public void add(URL u) {
        if(jars.containsKey(u)) {
            return;
        }
        URLClassLoader cl = URLClassLoader.newInstance(new URL[] {u}, this);
        jars.put(u, cl);
        add(cl);
    }

    public void add(URL[] urls) {
        for(URL u : urls) {
            add(u);
        }
    }

    public void invokeClass(String name, String[] args) throws ClassNotFoundException,
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
            invokeClass(name, args);
        } catch(Throwable t) {
            LOG.log(Level.SEVERE, null, t);
        }
    }

    private <A, B> B reflect(Map<A, B> cache, String method, A key) {
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

    //<editor-fold defaultstate="collapsed" desc="Overriding methods and caches">
    private final HashMap<String, Class<?>> classes = new HashMap<String, Class<?>>();
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> res = reflect(classes, "findClass", name);
        if(res != null) {
            return res;
        }
        return super.findClass(name);
    }
    
    private final HashMap<String, URL> resources = new HashMap<String, URL>();
    
    @Override
    protected URL findResource(String name) {
        URL res = reflect(resources, "findResource", name);
        if(res != null) {
            return res;
        }
        return super.findResource(name);
    }
    
    private final HashMap<String, Enumeration<URL>> enumerations = new HashMap<String, Enumeration<URL>>();
    
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
    
    private final HashMap<String, String> libraries = new HashMap<String, String>();
    
    @Override
    protected String findLibrary(String libname) {
        String res = reflect(libraries, "findLibrary", libname);
        if(res != null) {
            return res;
        }
        return super.findLibrary(libname);
    }
    
    //</editor-fold>
}
