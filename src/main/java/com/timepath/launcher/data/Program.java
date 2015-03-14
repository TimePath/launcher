package com.timepath.launcher.data;

import com.timepath.IOUtils;
import com.timepath.SwingUtils;
import com.timepath.classloader.CompositeClassLoader;
import com.timepath.launcher.Launcher;
import com.timepath.maven.Package;
import com.timepath.maven.UpdateChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class Program {

    private static final Logger LOG = Logger.getLogger(Program.class.getName());
    private static final AtomicInteger autoId = new AtomicInteger();
    private final String main;
    @NotNull
    private final List<String> args;
    private final int id = autoId.getAndIncrement();
    private final String newsfeedURL;
    private final String title;
    private final Package parent;
    private boolean starred;
    private boolean daemon;
    @Nullable
    private JPanel panel;

    public Program(Package parent,
                   String title,
                   String newsfeedURL,
                   String main,
                   @Nullable List<String> args) {
        this.parent = parent;
        this.title = title;
        this.newsfeedURL = newsfeedURL;
        this.main = main;
        this.args = args != null ? args : Collections.<String>emptyList();
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        this.starred = starred;
    }

    public int getId() {
        return id;
    }

    public Package getPackage() {
        return parent;
    }

    @Override
    public String toString() {
        return title;
    }

    public void start(@NotNull Launcher context) {
        context.update(this);
        context.start(this);
    }

    public void run(@NotNull CompositeClassLoader cl) throws Throwable {
        LOG.log(Level.INFO, "Starting {0} ({1})", new Object[]{this, main});
        @NotNull String[] argv = args.toArray(new String[args.size()]);
        @NotNull Set<URL> cp = getClassPath();
        cl.start(main, argv, cp);
    }

    /**
     * @return all dependencies, flattened
     */
    @NotNull
    private Set<URL> getClassPath() {
        Set<Package> all = parent.getDownloads();
        @NotNull Set<URL> h = new HashSet<>(all.size());
        for (@NotNull Package download : all) {
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

    @Nullable
    public JPanel getPanel() {
        if (panel != null) return panel;
        panel = new JPanel(new BorderLayout());
        // Create placeholder
        @NotNull final JEditorPane initial = new JEditorPane("text", (newsfeedURL == null)
                ? "No newsfeed available"
                : "Loading...");
        initial.setEditable(false);
        panel.add(initial);
        // Load real feed in background
        if (newsfeedURL != null) {
            new SwingWorker<JEditorPane, Void>() {
                @Nullable
                @Override
                protected JEditorPane doInBackground() {
                    @Nullable String s = IOUtils.requestPage(newsfeedURL);
                    @NotNull JEditorPane editorPane = new JEditorPane("text/html", s);
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
                    } catch (@NotNull InterruptedException | ExecutionException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }.execute();
        }
        return panel;
    }
}
