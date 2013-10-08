package com.timepath.launcher;

import java.util.HashSet;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

/**
 *
 * @author TimePath
 */
public class Program {

    public String name;

    public String newsfeedURL;

    public String file;

    public String downloadURL;

    public String checksumURL;

    public boolean self;

    public List<String> args;

    public String main;

    public String changelogData;

    public JEditorPane jEditorPane;

    public HashSet<Program> depends = new HashSet<Program>();

    public JPanel panel;

    String newsfeedType = "text/html";

    boolean active;

    @Override
    public String toString() {
        return name; // + (!depends.isEmpty() ? (" " + depends.toString()) : "");
    }

}
