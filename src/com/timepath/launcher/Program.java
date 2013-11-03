package com.timepath.launcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Override
    public String toString() {
        return title + (LauncherImpl.debug ? ("(" + downloads + ")" + (!depends.isEmpty() ? (" " + depends.toString()) : "")) : "");
    }

    void setSelf(boolean b) {
        self = b;
        programDirectory = null;
        for(Downloadable d : downloads) {
            d.programDirectory = null;
        }
    }

}
