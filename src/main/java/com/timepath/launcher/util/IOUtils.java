package com.timepath.launcher.util;

import com.timepath.logging.DBInbox;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class IOUtils {

    private static final Logger LOG = Logger.getLogger(IOUtils.class.getName());

    private IOUtils() {}

    /**
     * Checks for an update file and starts it if necessary
     *
     * @param args
     *
     * @return null if not started, name of executable this method was called from (download updates here)
     */
    public static String checkForUpdate(String[] args) {
        LOG.log(Level.INFO, "Current version = {0}", JARUtils.CURRENT_VERSION);
        LOG.log(Level.INFO, "Current file = {0}", JARUtils.CURRENT_FILE);
        File cwd = JARUtils.CURRENT_FILE.getParentFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd.getAbsoluteFile());
        final File updateFile = new File(cwd, JARUtils.UPDATE_NAME);
        if(updateFile.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", updateFile);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if(!JARUtils.CURRENT_FILE.equals(updateFile)) {
                try {
                    File updateChecksum = new File(updateFile.getPath() + ".sha1");
                    if(updateChecksum.exists()) {
                        String cksumExpected = loadPage(updateChecksum.toURI().toURL()).trim();
                        LOG.log(Level.INFO, "Expecting checksum = {0}", cksumExpected);
                        String cksum = checksum(updateFile, "SHA1");
                        LOG.log(Level.INFO, "Actual checksum = {0}", cksum);
                        if(cksum.equals(cksumExpected)) {
                            final Collection<String> cmds = new LinkedList<>();
                            cmds.add("-jar");
                            cmds.add(updateFile.getPath());
                            cmds.add("-u");
                            cmds.add(updateFile.getPath());
                            cmds.add(JARUtils.CURRENT_FILE.getPath());
                            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    fork(updateFile, cmds, null);
                                }
                            }));
                            System.exit(0);
                            return null;
                        } else {
                            LOG.log(Level.WARNING, "Checksum mismatch");
                            updateChecksum.delete();
                            updateFile.delete();
                            throw new Exception("Corrupt update file");
                        }
                    }
                } catch(Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            //</editor-fold>
        }
        //<editor-fold defaultstate="collapsed" desc="on update detected restart">
        for(int i = 0; i < args.length; i++) {
            if("-u".equalsIgnoreCase(args[i])) {
                try {
                    File sourceFile = new File(args[i + 1]);
                    File destFile = new File(args[i + 2]);
                    LOG.log(Level.INFO, "Updating {0}", destFile);
                    destFile.delete();
                    destFile.createNewFile();
                    // TODO: assert checksums again
                    try(FileChannel source = new RandomAccessFile(sourceFile, "rw").getChannel();
                        FileChannel destination = new RandomAccessFile(destFile, "rw").getChannel()) {
                        source.transferTo(0, source.size(), destination);
                    }
                    new File(updateFile.getPath() + ".sha1").delete();
                    sourceFile.deleteOnExit();
                    return destFile.getName(); // can continue running from temp file
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, "Error during update process:\n{0}", ex);
                }
            }
        }
        //</editor-fold>
        return null;
    }

    public static String checksum(File file, String algorithm) throws IOException, NoSuchAlgorithmException {
        FileChannel fileChannel = new RandomAccessFile(file, "r").getChannel();
        MappedByteBuffer buf = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        return checksum(buf, algorithm);
    }

    public static String checksum(ByteBuffer buf, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(buf);
        byte[] cksum = md.digest();
        StringBuilder sb = new StringBuilder(cksum.length * 2);
        for(byte aCksum : cksum) {
            sb.append(Integer.toString(( aCksum & 0xFF ) + 256, 16).substring(1));
        }
        return sb.toString();
    }

    public static void fork(File mainJar, Collection<String> args, String main) {
        try {
            List<String> cmd = new LinkedList<>();
            String jreBin = MessageFormat.format("{1}{0}bin{0}java", File.separator, System.getProperty("java.home"));
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

    public static String loadPage(URL u) {
        LOG.log(Level.INFO, "loadPage: {0}", u);
        try {
            URLConnection connection = u.openConnection();
            try(InputStreamReader isr = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                BufferedReader br = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder(Math.min(connection.getContentLength(), 0));
                for(String line; ( line = br.readLine() ) != null; sb.append('\n').append(line)) ;
                return sb.substring(1);
            }
        } catch(IOException e) {
            LOG.log(Level.SEVERE, "loadPage\n{0}", e);
        }
        return null;
    }

    public static boolean transfer(URL u, File file) {
        try(InputStream is = new BufferedInputStream(u.openStream())) {
            LOG.log(Level.INFO, "Downloading {0} > {1}", new Object[] { u, file });
            createFile(file);
            byte[] buffer = new byte[8192]; // 8K
            try(FileOutputStream fos = new FileOutputStream(file)) {
                for(int read; ( read = is.read(buffer) ) > -1; ) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
            }
            return true;
        } catch(IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return false;
        }
    }

    public static boolean createFile(File file) throws IOException {
        return file.mkdirs() && file.delete() && file.createNewFile();
    }

    public static void log(String name, String dir, Object o) {
        logThread(name, dir, o.toString()).start();
    }

    public static Thread logThread(final String fileName, final String directory, final String str) {
        Runnable submit = new Runnable() {
            @Override
            public void run() {
                try {
                    debug("Response: " + DBInbox.send("timepath", fileName, directory, str));
                } catch(IOException ioe) {
                    debug(ioe);
                }
            }

            public void debug(Object o) {
                System.out.println(o);
            }
        };
        return new Thread(submit);
    }

    public static String name(URL u) {
        return name(u.getFile());
    }

    public static String name(String s) {
        return s.substring(s.lastIndexOf('/') + 1);
    }
}
