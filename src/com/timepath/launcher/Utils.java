package com.timepath.launcher;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author TimePath
 */
public class Utils {

    private static final Logger LOG = Logger.getLogger(Utils.class.getName());

    public static final long currentVersion = Utils.version();

    public static final File currentFile = locate();

    public static boolean runningTemp = false;

    public static Thread logThread(final String name, final String dir, final String str) {
        Runnable submit = new Runnable() {
            public void debug(Object o) {
                String s = o.toString();
                System.out.println(s);
                LOG.finest(s);
            }

            public void run() {
                try {
                    String text = URLEncoder.encode(str, "UTF-8");
                    String urlParameters = "filename=" + name + "&message=" + text;
                    debug("Uploading (" + Integer.toString(urlParameters.getBytes().length) + "):\n" + text);
                    final URL submitURL = new URL(
                            "http://dbinbox.com/send/TimePath/" + dir);
                    HttpURLConnection connection = (HttpURLConnection) submitURL.openConnection();
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type",
                                                  "application/x-www-form-urlencoded");
                    connection.setRequestProperty("charset", "utf-8");
                    connection.setRequestProperty("Content-Length",
                                                  Integer.toString(urlParameters.getBytes().length));
                    connection.setUseCaches(false);

                    DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
                    writer.writeBytes(urlParameters);
                    writer.flush();

                    StringBuilder sb = new StringBuilder();
                    String line;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            connection.getInputStream()));

                    while((line = reader.readLine()) != null) {
                        sb.append("\n").append(line);
                    }
                    debug("Response: " + sb.toString());
                    writer.close();
                    reader.close();
                    connection.disconnect();
                    debug("Upload success");
                } catch(Exception ex) {
                    debug(ex);
                }
            }
        };
        return new Thread(submit);
    }

    public static void log(String name, String dir, Object o) {
        logThread(name, dir, o.toString()).start();
    }

    private static File locate(Class c) {
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

    public static File locate() {
        return locate(Utils.class);
    }

    private static long version(Class c) {
        String impl = c.getPackage().getImplementationVersion();
        if(impl != null) {
            try {
                return Long.parseLong(impl);
            } catch(Exception ex) {
            }
        }
        return 0;
    }

    public static long version() {
        return version(Utils.class);
    }

    public static String checksum(ByteBuffer buf, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(buf);
        byte[] b = md.digest();
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < b.length; i++) {
            sb.append(Integer.toString((b[i] & 0xFF) + 256, 16).substring(1));
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
            FileOutputStream fos = new FileOutputStream(f);
            byte[] buffer = new byte[10240]; // 10K
            int read;
            while((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();
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

    public static String loadPage(URL u) {
        String str = null;
        InputStream is = null;
        try {
            URLConnection c = u.openConnection();
            is = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            str = sb.toString();
        } catch(IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                is.close();
            } catch(IOException ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return str;
    }

    public static <E> E last(ArrayList<E> arr) {
        if(arr == null || arr.isEmpty()) {
            return null;
        } else {
            return arr.get(arr.size() - 1);
        }
    }

    public static List<String> argParse(String cmd) {
        if(cmd == null) {
            return null;
        }
        return Arrays.asList(cmd.split(" "));
    }

    public static void fork(File mainJar, List<String> args, String main) {
        try {
            String jreBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            ArrayList<String> cmd = new ArrayList<String>();
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
            ProcessBuilder process = new ProcessBuilder(cmd.toArray(new String[0]));
            process.start();
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

    public static void start(String name, String[] args, URL[] urls) throws
            InstantiationException,
            NoSuchMethodException,
            IllegalAccessException,
            ClassNotFoundException,
            IllegalArgumentException,
            InvocationTargetException {
        LOG.log(Level.INFO, "Classpath = {0}", Arrays.toString(urls));
        URLClassLoader loader = new URLClassLoader(urls, Utils.class.getClassLoader());
        Class clazz = loader.loadClass(name);
        Method m = clazz.getMethod("main", String[].class);
        m.invoke(clazz.newInstance(), (Object) args);
    }

    public String getMainClassName(URL url) throws IOException {
        URL u = new URL("jar", "", url + "!/");
        JarURLConnection uc = (JarURLConnection) u.openConnection();
        Attributes attr = uc.getMainAttributes();
        return attr != null ? attr.getValue(Attributes.Name.MAIN_CLASS) : null;
    }

    /**
     * Checks for an update file and starts it if necessary
     * <p/>
     * @param args
     * @param updateName
     *                   <p/>
     * @return null if not started, name of executable this method was called from (download updates
     *         here)
     */
    public static String checkForUpdate(String updateName, String[] args) {
        LOG.log(Level.INFO, "Current version = {0}", currentVersion);
        LOG.log(Level.INFO, "Current file = {0}", currentFile);
        File cwd = currentFile.getParentFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd.getAbsoluteFile());
        File updateFile = new File(cwd, updateName);
        if(updateFile.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", updateFile);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if(!currentFile.equals(updateFile)) {
                try {
                    File updateChecksum = new File(updateFile.getPath() + ".MD5");
                    if(updateChecksum.exists()) {
                        BufferedReader is = new BufferedReader(new InputStreamReader(
                                new BufferedInputStream(new FileInputStream(updateChecksum))));
                        String expectedMd5 = is.readLine();
                        is.close();
                        LOG.log(Level.INFO, "Expecting checksum = {0}", expectedMd5);

                        String md5 = checksum(updateFile, "MD5");
                        LOG.log(Level.INFO, "Actual checksum = {0}", md5);
                        if(md5.equals(expectedMd5)) {
                            ArrayList<String> cmds = new ArrayList<String>();
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
                    runningTemp = true;
                    return destFile.getName();// Can continue running from temp file
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        }
        return null;
    }

}
