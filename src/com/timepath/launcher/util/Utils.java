package com.timepath.launcher.util;

import java.awt.Desktop;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.zip.GZIPOutputStream;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Utils {

    public static final String UNAME = MessageFormat.format(
        "{0}@{1}", System.getProperty("user.name"),
        ManagementFactory.getRuntimeMXBean().getName().split("@")[1]);

    public static final String UPDATE_NAME = "update.tmp";

    public static final File currentFile = locate();

    public static final long currentVersion = Utils.version();

    public static final boolean debug = currentVersion == 0;

    public static final HyperlinkListener linkListener = new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent he) {
            if(!Desktop.isDesktopSupported()) {
                return;
            }
            Desktop d = Desktop.getDesktop();
            if(!he.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                return;
            }
            if(d.isSupported(Desktop.Action.BROWSE)) {
                try {
                    URI u;
                    URL l = he.getURL();
                    if(l != null) {
                        u = l.toURI();
                    } else {
                        u = new URI(he.getDescription());
                    }
                    d.browse(u);
                } catch(IOException | URISyntaxException ex) {
                    LOG.log(Level.WARNING, null, ex);
                }
            }
        }
    };

    public static final Preferences settings = Preferences.userRoot().node("timepath");

    public static final long start = ManagementFactory.getRuntimeMXBean().getStartTime();

    private static final Logger LOG = Logger.getLogger(Utils.class.getName());

    public static List<String> argParse(String cmd) {
        if(cmd == null) {
            return null;
        }
        return Arrays.asList(cmd.split(" "));
    }

    /**
     * Checks for an update file and starts it if necessary
     * <p/>
     * @param args
     *             <p/>
     * @return null if not started, name of executable this method was called from (download updates
     *         here)
     */
    public static String checkForUpdate(String[] args) {
        LOG.log(Level.INFO, "Current version = {0}", currentVersion);
        LOG.log(Level.INFO, "Current file = {0}", currentFile);
        File cwd = currentFile.getParentFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd.getAbsoluteFile());
        File updateFile = new File(cwd, "update.tmp");
        if(updateFile.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", updateFile);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if(!currentFile.equals(updateFile)) {
                try {
                    File updateChecksum = new File(updateFile.getPath() + ".MD5");
                    if(updateChecksum.exists()) {
                        String expectedMd5;
                        try(InputStreamReader isr = new InputStreamReader(
                            new FileInputStream(updateChecksum))) {
                            expectedMd5 = new BufferedReader(isr).readLine();
                        }
                        LOG.log(Level.INFO, "Expecting checksum = {0}", expectedMd5);

                        String md5 = checksum(updateFile, "MD5");
                        LOG.log(Level.INFO, "Actual checksum = {0}", md5);
                        if(md5.equals(expectedMd5)) {
                            List<String> cmds = new LinkedList<>();
                            cmds.add("-jar");
                            cmds.add(updateFile.getPath());
                            cmds.add("-u");
                            cmds.add(updateFile.getPath());
                            cmds.add(currentFile.getPath());
                            fork(updateFile, cmds, null);
                            System.exit(0);
                            return null;
                        } else {
                            updateChecksum.delete();
                        }
                    }
                    updateFile.delete();
                    throw new Exception("Corrupt update file");
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            //</editor-fold>
        }

        //<editor-fold defaultstate="collapsed" desc="on update detected restart">
        for(int i = 0; i < args.length; i++) {
            if(args[i].equalsIgnoreCase("-u")) {
                try {
                    File sourceFile = new File(args[i + 1]);
                    File destFile = new File(args[i + 2]);
                    LOG.log(Level.INFO, "Updating {0}", destFile);
                    destFile.delete();
                    destFile.createNewFile();

                    FileChannel source = null;
                    FileChannel destination = null;
                    try {
                        source = new RandomAccessFile(sourceFile, "rw").getChannel();
                        destination = new RandomAccessFile(destFile, "rw").getChannel();
                        long position = 0;
                        long count = source.size();

                        source.transferTo(position, count, destination);
                    } finally {
                        if(source != null) {
                            source.close();
                        }
                        if(destination != null) {
                            destination.force(true);
                            destination.close();
                        }
                    }
                    new File(updateFile.getPath() + ".MD5").delete();
                    sourceFile.deleteOnExit();
                    return destFile.getName();// Can continue running from temp file
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        return null;
    }

    public static String checksum(ByteBuffer buf, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(buf);
        byte[] cksum = md.digest();
        StringBuilder sb = new StringBuilder(cksum.length * 2);
        for(int i = 0; i < cksum.length; i++) {
            sb.append(Integer.toString((cksum[i] & 0xFF) + 256, 16).substring(1));
        }
        return sb.toString();
    }

    public static String checksum(File f, String algorithm) throws IOException,
                                                                   NoSuchAlgorithmException {
        FileChannel c = new RandomAccessFile(f, "r").getChannel();
        MappedByteBuffer buf = c.map(FileChannel.MapMode.READ_ONLY, 0, c.size());
        return checksum(buf, algorithm);
    }

    public static boolean download(URL u, File f) {
        boolean ret;
        InputStream is = null;
        try {
            LOG.log(Level.INFO, "Downloading {0} to {1}", new Object[] {u, f});
            is = new BufferedInputStream(u.openStream());
            f.mkdirs();
            f.delete();
            f.createNewFile();
            try(FileOutputStream fos = new FileOutputStream(f)) {
                byte[] buffer = new byte[10240]; // 10K
                int read;
                while((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
            }
            ret = true;
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            ret = false;
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        return ret;
    }

    public static void extract(URL u, File f) throws IOException {
        LOG.log(Level.INFO, "Extracting {0} > {1}", new Object[] {u, f});
        f.mkdirs();
        f.delete();
        f.createNewFile();
        byte[] buffer = new byte[8192];

        try(InputStream is = new BufferedInputStream(u.openStream(), buffer.length);
            OutputStream fos = new BufferedOutputStream(new FileOutputStream(f),
                                                        buffer.length)) {
            for(int read; (read = is.read(buffer)) > -1;) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        }
    }

    public static void fork(File mainJar, List<String> args, String main) {
        try {
            List<String> cmd = new LinkedList<>();

            String jreBin = MessageFormat.format("{1}{0}bin{0}java",
                                                 File.separator, System.getProperty("java.home"));
            cmd.add(jreBin);

            if(args != null) {
                cmd.addAll(args);
            } else {
                if(main == null) {
                    cmd.add("-jar");
                    cmd.add(mainJar.getPath());
                } else {
                    cmd.add(main);
                }
            }
            LOG.log(Level.INFO, "Invoking other: {0}", cmd.toString());
            ProcessBuilder process = new ProcessBuilder(cmd.toArray(new String[cmd.size()]));
            process.start();
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    public static <E> E last(List<E> arr) {
        if(arr == null || arr.isEmpty()) {
            return null;
        } else {
            return arr.get(arr.size() - 1);
        }
    }

    public static String loadPage(URL u) {
        try {
            URLConnection c = u.openConnection();
            try(InputStreamReader isr = new InputStreamReader(c.getInputStream())) {
                BufferedReader br = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder(Math.min(c.getContentLength(), 0));
                String line;
                while((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static File locate() {
        return locate(Utils.class);
    }

    public static void log(String name, String dir, Object o) {
        logThread(name, dir, o.toString()).start();
    }

    public static Thread logThread(final String fileName, final String directory, final String str) {
        Runnable submit = new Runnable() {
            public void debug(Object o) {
                System.out.println(o);
            }

            @Override
            public void run() {
                try {
                    byte[] in = (str).getBytes("UTF-8");
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(in.length);
                    try(GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                        gzip.write(in);
                    }
                    byte[] bytes = baos.toByteArray();

                    debug(MessageFormat.format("Uploading {0} bytes", bytes.length));
                    String boundary = "**********";

                    final URL url = new URL("http://dbinbox.com/send/timepath/" + directory);

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "keep-alive");
                    conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary="
                                                                + boundary);

                    try(DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                        out.writeBytes("--" + boundary + "\r\n");
                        out.writeBytes(
                            "Content-Disposition: form-data; name=\"files[]\"; filename=\""
                                + fileName + "\"\r\n");
                        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");

                        out.write(bytes);

                        out.writeBytes("\r\n--" + boundary + "--\r\n");
                        out.flush();
                    }
                    try(BufferedReader br = new BufferedReader(new InputStreamReader(conn
                        .getInputStream()))) {

                        StringBuilder sb = new StringBuilder(0);
                        for(String line; (line = br.readLine()) != null;) {
                            sb.append("\n").append(line);
                        }
                        debug("Response: " + sb.toString());
                    }
                } catch(IOException ioe) {
                    debug(ioe);
                }
            }
        };
        return new Thread(submit);
    }

    public static void lookAndFeel() {
        //<editor-fold defaultstate="collapsed" desc="Load native extended themes">
//        switch(OS.get()) {
//            case OSX:
//                UIManager.installLookAndFeel("Quaqua", "ch.randelshofer.quaqua.QuaquaLookAndFeel");
//                break;
//            case Linux:
//                UIManager.installLookAndFeel("GTK extended", "org.gtk.laf.extended.GTKLookAndFeelExtended");
//                break;
//        }
        //</editor-fold>
        UIManager.installLookAndFeel("Substance", "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel");
        
        String usrTheme = settings.get("laf", null);
        if(usrTheme != null) { // Validate user theme
            try {
                Class.forName(usrTheme);
            } catch(ClassNotFoundException ex) {
                LOG.log(Level.WARNING, "Invalid user theme: {0}", usrTheme);
                usrTheme = null;
                settings.remove("laf");
            }
        }
        fallback:
            if(usrTheme == null) { // Still null, pick a default
                // In order of preference
                String[] test = {
                    "Nimbus",
                    UIManager.getSystemLookAndFeelClassName(),
                    UIManager.getCrossPlatformLookAndFeelClassName()
                };

                // Build a map for faster querying
                Map<String, String> laf = new HashMap<>(0);
                for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    laf.put(info.getName(), info.getClassName());
                }

                for(String s : test) {
                    if((usrTheme = laf.get(s)) != null) {
                        settings.put("laf", usrTheme);
                        LOG.log(Level.CONFIG, "Set default user theme: {0}", usrTheme);
                        break fallback;
                    }
                }
                usrTheme = null;
            }

        String envTheme = System.getProperty("swing.defaultlaf");
        boolean lafOverride = settings.getBoolean("lafOverride", false);
        String theme;
        if(lafOverride) {
            theme = usrTheme == null ? envTheme : usrTheme; // usrTheme authorative
        } else {
            theme = envTheme != null ? envTheme : usrTheme; // envTheme authorative
        }

        try {
            UIManager.setLookAndFeel(theme);
            LOG.log(Level.INFO, "Set theme at {0}ms", System.currentTimeMillis() - start);
        } catch(ClassNotFoundException | IllegalAccessException | InstantiationException |
                UnsupportedLookAndFeelException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }

        //<editor-fold defaultstate="collapsed" desc="Improve native LaF">
//        if(UIManager.getLookAndFeel().isNativeLookAndFeel()) {
//            try {
//                LOG.log(Level.INFO, "Adding swing enhancements for {0}", new Object[] {OS.get()});
//                if(OS.isMac()) {
//                    UIManager.setLookAndFeel("ch.randelshofer.quaqua.QuaquaLookAndFeel"); // Apply quaqua if available
//                } else if(OS.isLinux()) {
//                    if(UIManager.getLookAndFeel().getClass().getName().equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
//                        GtkFixer.installGtkPopupBugWorkaround(); // Apply clearlooks java menu fix if applicable
//                        UIManager.setLookAndFeel("org.gtk.laf.extended.GTKLookAndFeelExtended"); // Apply extended gtk theme is available. http://danjared.wordpress.com/2012/05/21/mejorando-la-integracion-de-javaswing-con-gtk/
//                    }
//                }
//                LOG.info("All swing enhancements installed");
//            } catch(InstantiationException ex) {
//                LOG.log(Level.SEVERE, null, ex);
//            } catch(IllegalAccessException ex) {
//                LOG.log(Level.SEVERE, null, ex);
//            } catch(UnsupportedLookAndFeelException ex) {
//                LOG.log(Level.SEVERE, null, ex);
//            } catch(ClassNotFoundException ex) {
////                LOG.log(Level.INFO, null, ex);
//                LOG.warning("Unable to load enhanced L&F");
//            }
//        }
        //</editor-fold>
    }

    public static String name(URL u) {
        return name(u.getFile());
    }

    public static String name(String s) {
        return s.substring(s.lastIndexOf('/') + 1);
    }

    public static String pprint(Source xmlInput, int indent) {
        try {
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch(IllegalArgumentException | TransformerException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static String pprint(Map<String, ?> map) {
        try {
            Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = d.createElement("root");
            d.appendChild(root);
            for(Element e : pprint(map, d)) {
                root.appendChild(e);
            }
            return pprint(new DOMSource(d), 2);
        } catch(ParserConfigurationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static List<Element> pprint(Map<?, ?> map, Document d) {
        List<Element> elems = new LinkedList<>();
        for(Map.Entry<?, ?> entry : map.entrySet()) {
            Element e = d.createElement("entry");
            e.setAttribute("key", String.valueOf(entry.getKey()));
            if(entry.getValue() instanceof Map) {
                for(Element child : pprint((Map<?, ?>) entry.getValue(), d)) {
                    e.appendChild(child);
                }
            } else {
                e.setAttribute("value", String.valueOf(entry.getValue()));
            }
            elems.add(e);
        }
        return elems;
    }

    public static void start(String name, String[] args, URL[] urls)
        throws InstantiationException, NoSuchMethodException, IllegalAccessException,
               ClassNotFoundException, IllegalArgumentException, InvocationTargetException {
        LOG.log(Level.INFO, "Classpath = {0}", Arrays.toString(urls));
        URLClassLoader loader = new URLClassLoader(urls, Utils.class.getClassLoader());
        Class<?> clazz = loader.loadClass(name);
        Method m = clazz.getMethod("main", String[].class);
        m.invoke(clazz.newInstance(), (Object) args);
    }

    public static long version() {
        return version(Utils.class);
    }

    private static File locate(Class<?> c) {
        String encoded = c.getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            return new File(URLDecoder.decode(encoded, "UTF-8"));
        } catch(UnsupportedEncodingException ex) {
            LOG.log(Level.WARNING, null, ex);
        }
        String ans = System.getProperty("user.dir") + File.separator;
        String cmd = System.getProperty("sun.java.command");
        int idx = cmd.lastIndexOf(File.separator);
        if(idx != -1) {
            cmd = cmd.substring(0, idx + 1);
        } else {
            cmd = "";
        }
        ans += cmd;
        return new File(ans);
    }

    private static long version(Class<?> c) {
        String impl = c.getPackage().getImplementationVersion();
        if(impl != null) {
            try {
                return Long.parseLong(impl);
            } catch(NumberFormatException nfe) {
            }
        }
        return 0;
    }

    public String getMainClassName(URL url) throws IOException {
        URL u = new URL("jar", "", url + "!/");
        JarURLConnection uc = (JarURLConnection) u.openConnection();
        Attributes attr = uc.getMainAttributes();
        return attr != null ? attr.getValue(Attributes.Name.MAIN_CLASS) : null;
    }

    public static class DaemonThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }

    }

}
