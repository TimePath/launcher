package com.timepath.launcher;

/**
 *
 * @author TimePath
 */
public interface DownloadMonitor {

    void submit(PackageFile d);

    void update(PackageFile d);
    
}
