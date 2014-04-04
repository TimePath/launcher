package com.timepath.launcher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

/**
 *
 * @author TimePath
 */
public class Program extends Downloadable {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());

    public List<String> args;

    public String changelogData;

    public HashSet<Program> depends = new HashSet<Program>();

    public JEditorPane jEditorPane;

    public String main;

    public String newsfeedURL;

    public JPanel panel;

    public boolean self;

    public String title;

    /**
     * A map of downloads to checksums. TODO: allow for versions
     */
    ArrayList<Downloadable> downloads = new ArrayList<Downloadable>();

    String newsfeedType = "text/html";

    public HashSet<URL> classPath() {
        HashSet<URL> h = new HashSet<URL>();
        for(Downloadable d : downloads) {
            try {
                h.add(d.file().toURI().toURL());
            } catch(MalformedURLException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        for(Program p : depends) {
            h.addAll(p.classPath());
        }
        File f = file();
        if(f != null) {
            try {
                URL u = f.toURI().toURL();
                h.add(u);
            } catch(MalformedURLException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        return h;
    }

    public Thread createThread(final CompositeClassLoader cl) {
        return new Thread(new Runnable() {
            public void run() {
                if(main == null) {
                    return; // Not executable
                }
                LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] {this, main});
                try {
                    cl.start(main, args.toArray(new String[args.size()]),
                             classPath().toArray(new URL[0]));
//                    for(Window w : Window.getWindows()) { // TODO: This will probably come back to haunt me later
//                        LOG.log(Level.INFO, "{0}  {1}", new Object[] {w, w.isDisplayable()});
//                        if(!w.isVisible()) {
//                            w.dispose();
//                        }
//                    }
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public Set<Program> rdepends() {
        HashSet<Program> h = new HashSet<Program>();
        // Add all parent dependencies first
        for(Program p : depends) {
            h.addAll(p.rdepends());
        }
        // Not just a run configuration
        if(!downloads.isEmpty()) {
            h.add(this);
        }
        return h;
    }

    @Override
    public String toString() {
        return title + (LauncherMain.debug ? ("(" + downloads + ")" + (!depends.isEmpty() ? (" "
                                                                                             + depends
                                                                                             .toString())
                                                                       : "")) : "");
    }

    void setSelf(boolean b) {
        self = b;
        programDirectory = null;
        for(Downloadable d : downloads) {
            d.programDirectory = null;
        }
    }

}
