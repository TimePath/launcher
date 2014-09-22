package com.timepath.launcher.util;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class JARUtils {

    public static final String UPDATE_NAME = "update.tmp";
    public static final File CURRENT_FILE = locate();
    public static final long CURRENT_VERSION = version();
    private static final Logger LOG = Logger.getLogger(JARUtils.class.getName());

    public static File locate() {
        return locate(Utils.class);
    }

    private static File locate(Class<?> clazz) {
        String encoded = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            return new File(URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException ex) {
            LOG.log(Level.WARNING, null, ex);
        }
        String ans = System.getProperty("user.dir") + File.separator;
        String cmd = System.getProperty("sun.java.command");
        int idx = cmd.lastIndexOf(File.separator);
        return new File(ans + ((idx < 0) ? "" : cmd.substring(0, idx + 1)));
    }

    public static long version() {
        return version(Utils.class);
    }

    private static long version(Class<?> clazz) {
        String impl = clazz.getPackage().getImplementationVersion();
        if (impl != null) {
            try {
                return Long.parseLong(impl);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public static String getMainClassName(URL url) throws IOException {
        URL u = new URL("jar", "", url + "!/");
        JarURLConnection uc = (JarURLConnection) u.openConnection();
        Attributes attr = uc.getMainAttributes();
        return (attr != null) ? attr.getValue(Attributes.Name.MAIN_CLASS) : null;
    }
}
