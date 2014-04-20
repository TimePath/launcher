package com.timepath.launcher;

import com.timepath.launcher.util.Utils;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.timepath.launcher.util.Utils.name;

/**
 *
 * @author TimePath
 */
public class PackageFile {

    public static final String PROGRAM_DIRECTORY = Utils.settings.get("progStoreDir", new File(
                                                                      Utils.currentFile
                                                                      .getParentFile(), "bin")
                                                                      .getPath());

    private static final Logger LOG = Logger.getLogger(PackageFile.class.getName());

    public String downloadURL;

    public String filename;

    public long progress, size = -1;

    public String versionURL;

    public String programDirectory = PROGRAM_DIRECTORY;

    public List<PackageFile> nested = new LinkedList<>();

    public PackageFile(String dlu, String csu, String name) {
        this.downloadURL = dlu;
        this.versionURL = csu;
        this.filename = name;
    }

    public PackageFile(String dlu, String csu) {
        this(dlu, csu, name(dlu));
    }

    public PackageFile() {

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
        return downloadURL + " > " + programDirectory + File.separator + fileName();
    }

    public String versionName() {
        String n = name(versionURL);
        if(n.contains(name(downloadURL))) {
            String[] str = n.split(Pattern.quote(name(downloadURL)));
            n = str[0] + filename + str[1];
        }
        return n;
    }

    File getFile() {
        if(fileName() == null) {
            return null;
        }
        return new File(programDirectory, fileName());
    }

    File versionFile() {
        return new File(programDirectory, versionName());
    }

}
