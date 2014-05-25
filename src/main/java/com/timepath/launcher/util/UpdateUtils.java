package com.timepath.launcher.util;

import java.io.*;
import java.net.URL;
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
public class UpdateUtils {

    private static final Logger LOG = Logger.getLogger(UpdateUtils.class.getName());

    private UpdateUtils() {}

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
        File updateFile = new File(cwd, "update.tmp");
        if(updateFile.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", updateFile);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if(!JARUtils.CURRENT_FILE.equals(updateFile)) {
                try {
                    File updateChecksum = new File(updateFile.getPath() + ".sha1");
                    if(updateChecksum.exists()) {
                        String cksumExpected;
                        try(InputStreamReader isr = new InputStreamReader(new FileInputStream(updateChecksum),
                                                                          StandardCharsets.UTF_8)) {
                            cksumExpected = new BufferedReader(isr).readLine();
                        }
                        LOG.log(Level.INFO, "Expecting checksum = {0}", cksumExpected);
                        String cksum = checksum(updateFile, "SHA1");
                        LOG.log(Level.INFO, "Actual checksum = {0}", cksum);
                        if(cksum.equals(cksumExpected)) {
                            Collection<String> cmds = new LinkedList<>();
                            cmds.add("-jar");
                            cmds.add(updateFile.getPath());
                            cmds.add("-u");
                            cmds.add(updateFile.getPath());
                            cmds.add(JARUtils.CURRENT_FILE.getPath());
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
            if("-u".equalsIgnoreCase(args[i])) {
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
                    new File(updateFile.getPath() + ".sha1").delete();
                    sourceFile.deleteOnExit();
                    return destFile.getName();// Can continue running from temp file
                } catch(IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
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

    public static boolean download(URL u, File file) {
        boolean ret;
        InputStream is = null;
        try {
            LOG.log(Level.INFO, "Downloading {0} to {1}", new Object[] { u, file });
            is = new BufferedInputStream(u.openStream());
            Utils.createFile(file);
            try(FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[10240]; // 10K
                int read;
                while(( read = is.read(buffer) ) != -1) {
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

    public static void extract(URL u, File file) throws IOException {
        LOG.log(Level.INFO, "Extracting {0} > {1}", new Object[] { u, file });
        Utils.createFile(file);
        byte[] buffer = new byte[8192];
        try(InputStream is = new BufferedInputStream(u.openStream(), buffer.length);
            OutputStream fos = new BufferedOutputStream(new FileOutputStream(file), buffer.length)) {
            for(int read; ( read = is.read(buffer) ) > -1; ) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        }
    }
}
