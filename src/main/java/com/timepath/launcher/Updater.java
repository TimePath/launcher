package com.timepath.launcher;

import com.timepath.FileUtils;
import com.timepath.IOUtils;
import com.timepath.maven.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    @Nullable
    public static String checkForUpdate(@NotNull String[] args) {
        LOG.log(Level.INFO, "Current version = {0}", LauncherUtils.CURRENT_VERSION);
        LOG.log(Level.INFO, "Current file = {0}", LauncherUtils.CURRENT_FILE);
        File cwd = LauncherUtils.CURRENT_FILE.getParentFile();
        LOG.log(Level.INFO, "Working directory = {0}", cwd.getAbsoluteFile());
        @NotNull final File updateFile = new File(cwd, LauncherUtils.UPDATE.getName());
        if (updateFile.exists()) {
            LOG.log(Level.INFO, "Update file = {0}", updateFile);
            //<editor-fold defaultstate="collapsed" desc="on user restart">
            if (!LauncherUtils.CURRENT_FILE.equals(updateFile)) {
                try {
                    @NotNull File updateChecksum = new File(updateFile.getPath() + '.' + Constants.ALGORITHM);
                    if (updateChecksum.exists()) {
                        @NotNull String cksumExpected = IOUtils.requestPage(updateChecksum.toURI().toURL().toString()).trim();
                        LOG.log(Level.INFO, "Expecting checksum = {0}", cksumExpected);
                        @NotNull String cksum = FileUtils.checksum(updateFile, Constants.ALGORITHM);
                        LOG.log(Level.INFO, "Actual checksum = {0}", cksum);
                        if (cksum.equals(cksumExpected)) {
                            @NotNull final Collection<String> cmds = new LinkedList<>();
                            cmds.add("-jar");
                            cmds.add(updateFile.getPath());
                            cmds.add("-u");
                            cmds.add(updateFile.getPath());
                            cmds.add(LauncherUtils.CURRENT_FILE.getPath());
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
                    @NotNull File sourceFile = new File(args[i + 1]);
                    @NotNull File destFile = new File(args[i + 2]);
                    LOG.log(Level.INFO, "Updating {0}", destFile);
                    destFile.delete();
                    destFile.createNewFile();
                    // TODO: assert checksums again
                    try (FileChannel source = new RandomAccessFile(sourceFile, "rw").getChannel();
                         FileChannel destination = new RandomAccessFile(destFile, "rw").getChannel()) {
                        source.transferTo(0, source.size(), destination);
                    }
                    new File(updateFile.getPath() + '.' + Constants.ALGORITHM).delete();
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

    private static void fork(@NotNull File mainJar, @Nullable Collection<String> args, @Nullable String main) {
        try {
            @NotNull List<String> cmd = new LinkedList<>();
            @NotNull String jreBin = MessageFormat.format("{1}{0}bin{0}java", File.separator, System.getProperty("java.home"));
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
            @NotNull ProcessBuilder process = new ProcessBuilder(cmd.toArray(new String[cmd.size()]));
            process.start();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
    }

}
