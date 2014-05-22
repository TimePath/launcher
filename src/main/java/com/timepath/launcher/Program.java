package com.timepath.launcher;

import com.timepath.classloader.CompositeClassLoader;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class Program {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());
    private final String       main;
    private final List<String> args;
    private       String       newsfeedURL;
    private       boolean      daemon;
    private       JPanel       panel;
    private       String       title;
    private       Package      parent;

    public Program(final Package parent,
                   final String title,
                   final String newsfeedURL,
                   final String main,
                   final List<String> args)
    {
        this.parent = parent;
        this.title = title;
        this.newsfeedURL = newsfeedURL;
        this.main = main;
        this.args = args;
    }

    public Package getPackage() { return parent; }

    @Override
    public String toString() {
        return title;
    }

    public Thread createThread(final CompositeClassLoader cl) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] { this, main });
                try {
                    String[] argv = null;
                    if(args != null) {
                        argv = args.toArray(new String[args.size()]);
                    }
                    Set<URL> cp = getClassPath();
                    cl.start(main, argv, cp);
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        });
        t.setContextClassLoader(cl);
        t.setDaemon(daemon);
        return t;
    }

    /**
     * @return all dependencies, flattened
     */
    private Set<URL> getClassPath() {
        Set<Package> all = parent.getDownloads();
        Set<URL> h = new HashSet<>(all.size());
        for(Package download : all) {
            try {
                h.add(download.getFile().toURI().toURL());
            } catch(MalformedURLException e) {
                LOG.log(Level.SEVERE, null, e);
            }
        }
        return h;
    }

    public String getTitle() {
        return title;
    }

    public String getMain() {
        return main;
    }

    public String getNewsfeedURL() {
        return newsfeedURL;
    }

    public void setDaemon(final boolean daemon) {
        this.daemon = daemon;
    }

    public JPanel getPanel() {
        return panel;
    }

    public void setPanel(final JPanel panel) {
        this.panel = panel;
    }
}
