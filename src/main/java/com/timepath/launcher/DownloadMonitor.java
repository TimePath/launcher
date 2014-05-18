package com.timepath.launcher;

/**
 * @author TimePath
 */
public interface DownloadMonitor {

    void submit(PackageFile pkgFile);

    void update(PackageFile pkgFile);
}
