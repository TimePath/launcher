package com.timepath.launcher;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *
 * @author TimePath
 */
public class Downloadable {

    public static final String PROGRAM_DIRECTORY = Utils.settings.get("progStoreDir", "bin");

    private static final Logger LOG = Logger.getLogger(Downloadable.class.getName());

    static String name(URL u) {
        return u.getFile().substring(u.getFile().lastIndexOf('/') + 1);
    }

    static String name(String s) {
        return s.substring(s.lastIndexOf('/') + 1);
    }

    public String downloadURL;

    public String filename;

    public long progress, size = -1;

    public String versionURL;

    String programDirectory = PROGRAM_DIRECTORY;

    public Downloadable(String dlu, String csu, String name) {
        this.downloadURL = dlu;
        this.versionURL = csu;
        this.filename = name;
    }

    public Downloadable(String dlu, String csu) {
        this(dlu, csu, name(dlu));
    }

    public Downloadable() {

    }

    public String fileName() {
        if(filename != null) {
            return filename;
        }
        if(downloadURL == null) {
            return null;
        }
        return name(downloadURL);
    }

    @Override
    public String toString() {
        return downloadURL + " > " + programDirectory + fileName();
    }

        public String versionName() {
            String n = name(versionURL);
            if(n.contains(name(downloadURL))) {
                String[] str = n.split(Pattern.quote(name(downloadURL)));
                n = str[0] + filename + str[1];
            }
            return n;
        }

    File file() {
        if(fileName() == null) {
            return null;
        }
        return new File(programDirectory, fileName());
    }

    File versionFile() {
        return new File(programDirectory, versionName());
    }

}
