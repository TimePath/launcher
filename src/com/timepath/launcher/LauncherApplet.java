package com.timepath.launcher;

import javax.swing.JApplet;

/**
 *
 * @author TimePath
 */
public class LauncherApplet extends JApplet {

    @Override
    public void init() {
        LauncherImpl.main("");
    }

}
