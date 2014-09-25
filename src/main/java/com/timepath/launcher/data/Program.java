package com.timepath.launcher.data;

import com.timepath.IOUtils;
import com.timepath.classloader.CompositeClassLoader;
import com.timepath.SwingUtils;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class Program {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());
    private final String main;
    private final List<String> args;
    private String newsfeedURL;
    private boolean daemon;
    private JPanel panel;
    private String title;
    private com.timepath.maven.Package parent;

    public Program(final Package parent,
                   final String title,
                   final String newsfeedURL,
                   final String main,
                   final List<String> args) {
        this.parent = parent;
        this.title = title;
        this.newsfeedURL = newsfeedURL;
        this.main = main;
        this.args = args;
    }

    public Package getPackage() {
        return parent;
    }

    @Override
    public String toString() {
        return title;
    }

    public void run(final CompositeClassLoader cl) throws Throwable {
        LOG.log(Level.INFO, "Starting {0} ({1})", new Object[]{this, main});
        String[] argv = null;
        if (args != null) {
            argv = args.toArray(new String[args.size()]);
        }
        Set<URL> cp = getClassPath();
        cl.start(main, argv, cp);
    }

    /**
     * @return all dependencies, flattened
     */
    private Set<URL> getClassPath() {
        Set<Package> all = parent.getDownloads();
        Set<URL> h = new HashSet<>(all.size());
        for (Package download : all) {
            try {
                h.add(UpdateChecker.getFile(download).toURI().toURL());
            } catch (MalformedURLException e) {
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

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public JPanel getPanel() {
        if (panel != null) return panel;
        panel = new JPanel(new BorderLayout());
        // create placeholder
        String str = (newsfeedURL == null) ? "No newsfeed available" : "Loading...";
        final JEditorPane initial = new JEditorPane("text", str);
        initial.setEditable(false);
        panel.add(initial);
        // load real feed asynchronously
        if (newsfeedURL != null) {
            new SwingWorker<JEditorPane, Void>() {
                @Override
                protected JEditorPane doInBackground() throws Exception {
                    String s = IOUtils.requestPage(newsfeedURL);
                    JEditorPane editorPane = new JEditorPane("text/html", s);
                    editorPane.setEditable(false);
                    editorPane.addHyperlinkListener(SwingUtils.HYPERLINK_LISTENER);
                    return editorPane;
                }

                @Override
                protected void done() {
                    try {
                        panel.remove(initial);
                        panel.add(get());
                        panel.updateUI();
                        LOG.log(Level.INFO, "Loaded {0}", newsfeedURL);
                    } catch (InterruptedException | ExecutionException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }.execute();
        }
        return panel;
    }
}
