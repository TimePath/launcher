package com.timepath.launcher;

import com.timepath.launcher.util.Utils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.timepath.launcher.util.Utils.name;

/**
 * @author TimePath
 */
public class PackageFile {

    public static final  String PROGRAM_DIRECTORY = Utils.settings.get("progStoreDir",
                                                                       new File(Utils.currentFile.getParentFile(),
                                                                                "bin").getPath()
                                                                      );
    public               String programDirectory  = PROGRAM_DIRECTORY;
    private static final Logger LOG               = Logger.getLogger(PackageFile.class.getName());
    public String downloadURL;
    public String fileName;
    public long progress, size = -1;
    public String checksumURL;
    public List<PackageFile> nested = new LinkedList<>();

    public PackageFile(String downloadURL, String checksumURL) {
        this(downloadURL, checksumURL, name(downloadURL));
    }

    public PackageFile(String downloadURL, String checksumURL, String fileName) {
        this.downloadURL = downloadURL;
        this.checksumURL = checksumURL;
        this.fileName = fileName;
    }

    public PackageFile() {
    }

    @Override
    public String toString() {
        return downloadURL + " > " + programDirectory + File.separator + fileName();
    }

    public String fileName() {
        if(fileName != null) {
            return fileName;
        }
        if(downloadURL == null) {
            return null;
        }
        return name(downloadURL);
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

    public String versionName() {
        String s = name(checksumURL);
        if(s.contains(name(downloadURL))) {
            String[] split = s.split(Pattern.quote(name(downloadURL)));
            s = split[0] + fileName + split[1];
        }
        return s;
    }
}
