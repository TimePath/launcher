package com.timepath.launcher;

import java.util.ArrayList;
import java.util.HashMap;
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

    public ArrayList<String> downloadURLs = new ArrayList<String>();

    public String checksumURLs;

    public boolean self;

    public List<String> args;

    public String main;

    public String changelogData;

    public JEditorPane jEditorPane;

    public HashSet<Program> depends = new HashSet<Program>();

    public JPanel panel;
    
    /**
     * A map of downloads to checksums
     */
    HashMap<String, String> downloads = new HashMap<String, String>();

    String newsfeedType = "text/html";

    @Override
    public String toString() {
        return name;// + "(" + downloads + ")" + (!depends.isEmpty() ? (" " + depends.toString()) : "");
    }

}
