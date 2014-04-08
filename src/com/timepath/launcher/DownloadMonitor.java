package com.timepath.launcher;

/**
 *
 * @author TimePath
 */
public interface DownloadMonitor {

    void submit(Downloadable d);

    void update(Downloadable d);
    
}
