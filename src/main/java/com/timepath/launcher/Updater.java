package com.timepath.launcher;

import com.timepath.FileUtils;
import com.timepath.IOUtils;
import com.timepath.maven.UpdateChecker;

import java.io.*;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class Updater {

    private static final Logger LOG = Logger.getLogger(Updater.class.getName());

    private Updater() {
    }

    /**
     * Checks for an update file and starts it if necessary
     *
     * @param args
     * @return null if not started, name of executable this method was called from (download updates here)
     */
    public static String checkForUpdate(String[] args) {
        LOG.log(Level.INFO, "Current version = {0}", Utils.CURRENT_VERSION);
        LOG.log(Level.INFO, "Current file = {0}", Utils.CURRENT_FILE);
        File cwd = Utils.CURRENT_FILE.getParentFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd.getAbsoluteFile());
        final File updateFile = new File(cwd, Utils.UPDATE.getName());
        if (updateFile.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", updateFile);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if (!Utils.CURRENT_FILE.equals(updateFile)) {
                try {
                    File updateChecksum = new File(updateFile.getPath() + '.' + UpdateChecker.ALGORITHM);
                    if (updateChecksum.exists()) {
                        String cksumExpected = IOUtils.requestPage(updateChecksum.toURI().toURL().toString()).trim();
                        LOG.log(Level.INFO, "Expecting checksum = {0}", cksumExpected);
                        String cksum = FileUtils.checksum(updateFile, UpdateChecker.ALGORITHM);
                        LOG.log(Level.INFO, "Actual checksum = {0}", cksum);
                        if (cksum.equals(cksumExpected)) {
                            final Collection<String> cmds = new LinkedList<>();
                            cmds.add("-jar");
                            cmds.add(updateFile.getPath());
                            cmds.add("-u");
                            cmds.add(updateFile.getPath());
                            cmds.add(Utils.CURRENT_FILE.getPath());
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
                } catch (Exception ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            //</editor-fold>
        }
        //<editor-fold defaultstate="collapsed" desc="on update detected restart">
        for (int i = 0; i < args.length; i++) {
            if ("-u".equalsIgnoreCase(args[i])) {
                try {
                    File sourceFile = new File(args[i + 1]);
                    File destFile = new File(args[i + 2]);
                    LOG.log(Level.INFO, "Updating {0}", destFile);
                    destFile.delete();
                    destFile.createNewFile();
                    // TODO: assert checksums again
                    try (FileChannel source = new RandomAccessFile(sourceFile, "rw").getChannel();
                         FileChannel destination = new RandomAccessFile(destFile, "rw").getChannel()) {
                        source.transferTo(0, source.size(), destination);
                    }
                    new File(updateFile.getPath() + '.' + UpdateChecker.ALGORITHM).delete();
                    sourceFile.deleteOnExit();
                    return destFile.getName(); // can continue running from temp file
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error during update process:\n{0}", ex);
                }
            }
        }
        //</editor-fold>
        return null;
    }

    private static void fork(File mainJar, Collection<String> args, String main) {
        try {
            List<String> cmd = new LinkedList<>();
            String jreBin = MessageFormat.format("{1}{0}bin{0}java", File.separator, System.getProperty("java.home"));
            cmd.add(jreBin);
            if (args != null) {
                cmd.addAll(args);
            } else {
                if (main == null) {
                    cmd.add("-jar");
                    cmd.add(mainJar.getPath());
                } else {
                    cmd.add(main);
                }
            }
            LOG.log(Level.INFO, "Invoking other: {0}", cmd.toString());
            ProcessBuilder process = new ProcessBuilder(cmd.toArray(new String[cmd.size()]));
            process.start();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

}
