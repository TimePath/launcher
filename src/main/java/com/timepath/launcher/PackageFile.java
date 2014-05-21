package com.timepath.launcher;

import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.Utils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author TimePath
 */
public class PackageFile {

    public static final  String PROGRAM_DIRECTORY = Utils.SETTINGS.get("progStoreDir",
                                                                       new File(JARUtils.CURRENT_FILE.getParentFile(),
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
        this(downloadURL, checksumURL, JARUtils.name(downloadURL));
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
        return JARUtils.name(downloadURL);
    }

    public File getFile() {
        if(fileName() == null) {
            return null;
        }
        return new File(programDirectory, fileName());
    }

    public File versionFile() {
        return new File(programDirectory, versionName());
    }

    public String versionName() {
        String s = JARUtils.name(checksumURL);
        if(s.contains(JARUtils.name(downloadURL))) {
            String[] split = s.split(Pattern.quote(JARUtils.name(downloadURL)));
            s = split[0] + fileName + split[1];
        }
        return s;
    }
}
