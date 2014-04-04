package com.timepath.launcher;

import java.util.logging.Logger;
import javax.swing.JApplet;

/**
 *
 * @author TimePath
 */
public class LauncherApplet extends JApplet {

    private static final Logger LOG = Logger.getLogger(LauncherApplet.class.getName());

    @Override
    public void init() {
        LauncherMain.main("");
    }

}
