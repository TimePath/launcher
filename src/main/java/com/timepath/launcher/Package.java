package com.timepath.launcher;

import com.timepath.classloader.CompositeClassLoader;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.UpdateUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import com.timepath.maven.MavenResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.timepath.launcher.util.JARUtils.UPDATE_NAME;
import static com.timepath.launcher.util.XMLUtils.last;

/**
 * @author TimePath
 */
public class Package {

    public static final  String PROGRAM_DIRECTORY = Utils.SETTINGS.get("progStoreDir",
                                                                       new File(JARUtils.CURRENT_FILE.getParentFile(),
                                                                                "bin").getPath()
                                                                      );
    private static final Logger LOG               = Logger.getLogger(Package.class.getName());
    public final String  name;
    public       boolean self;
    public       boolean lock;
    public long progress, size = -1;
    /**
     * Base URL in maven repo
     */
    protected String baseURL;
    List<Executable> executions = new LinkedList<>();
    private List<Package> downloads;

    public Package(final Dependency d) throws IOException, SAXException, ParserConfigurationException {
        this(new URL(MavenResolver.resolve(d.getGroupId(), d.getArtifactId(),
                                           // TODO: dependencyManagement/dependencies/dependency/version
                                           d.getVersion() == null ? "3.2.1" : d.getVersion(), null, "pom")));
    }

    public Package(final URL u) throws ParserConfigurationException, IOException, SAXException {
        this(getNode(u));
    }

    /**
     * Instantiate a Program instance from XML
     *
     * @param root
     */
    public Package(Node root) {
        LOG.log(Level.INFO, "{0}", Utils.pprint(new DOMSource(root), 2));
        name = XMLUtils.get(root, "name");
        for(Node execution : XMLUtils.getElements(root, "executions/execution")) {
            Node cfg = last(XMLUtils.getElements(execution, "configuration"));
            Executable e = new Executable(XMLUtils.get(execution, "name"),
                                          XMLUtils.get(execution, "url"),
                                          XMLUtils.get(cfg, "main"),
                                          Utils.argParse(XMLUtils.get(cfg, "args")));
            executions.add(e);
            String daemonStr = XMLUtils.get(cfg, "daemon");
            if(daemonStr != null) {
                e.daemon = Boolean.parseBoolean(daemonStr);
            }
        }
        String gid = inherit(root, "groupId");
        if(gid == null) return; // invalid pom
        String aid = XMLUtils.get(root, "artifactId");
        String ver = inherit(root, "version");
        if(ver == null) { // TODO: dependencyManagement/dependencies/dependency/version
            ver = "3.2.1";
        }
        baseURL = MavenResolver.resolve(gid, aid, ver, null);
        LOG.log(Level.INFO, "Resolved to {0}", baseURL);
    }

    private static String inherit(Node root, String name) {
        String ret = XMLUtils.get(root, name);
        if(ret == null) {
            return XMLUtils.get(last(XMLUtils.getElements(root, "parent")), name);
        }
        return ret;
    }

    private static Node getNode(final URL u) throws ParserConfigurationException, IOException, SAXException {
        LOG.log(Level.INFO, "Getting program: {0}", u);
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(new BufferedInputStream(new BufferedInputStream(u.openStream())));
        return XMLUtils.getElements(doc, "project").get(0);
    }

    @Override
    public String toString() {
        return name;
    }

    public void markSelf(boolean self) {
        this.self = self;
    }

    /**
     * Flattens all dependencies
     *
     * @return
     */
    public Set<URL> calculateClassPath() {
        List<Package> all = getDownloads();
        Set<URL> h = new HashSet<>(all.size());
        for(Package download : all) {
            try {
                h.add(download.getFile().toURI().toURL());
            } catch(MalformedURLException e) {
                LOG.log(Level.SEVERE, null, e);
            }
        }
        return h;
    }

    /**
     * Check a program for updates
     *
     * @return false if not up to date, true if up to date or working offline
     */
    public boolean isLatest() {
        LOG.log(Level.INFO, "Checking {0} for updates...", this);
        for(Package pkgFile : getDownloads()) {
            try {
                File file = pkgFile.getFile();
                if(UPDATE_NAME.equals(file.getName())) { // edge case for current file
                    file = JARUtils.CURRENT_FILE;
                }
                LOG.log(Level.INFO, "Version file: {0}", file);
                LOG.log(Level.INFO, "Version url: {0}", pkgFile.getChecksumURL());
                if(!file.exists()) {
                    LOG.log(Level.INFO, "Don't have {0}, not latest", file);
                    return false;
                }
                if(pkgFile.getChecksumURL() == null) {
                    LOG.log(Level.INFO, "{0} not versioned, skipping", file);
                    continue; // have unversioned file, skip check
                }
                String checksum = UpdateUtils.checksum(file, "SHA1");
                BufferedReader br = new BufferedReader(new InputStreamReader(new URL(pkgFile.getChecksumURL()).openStream(),
                                                                             StandardCharsets.UTF_8));
                String expected = br.readLine();
                if(!checksum.equals(expected)) {
                    LOG.log(Level.INFO, "Checksum mismatch for {0}, not latest", file);
                    return false;
                }
            } catch(IOException | NoSuchAlgorithmException ex) {
                LOG.log(Level.SEVERE, null, ex);
                return false;
            }
        }
        LOG.log(Level.INFO, "{0} doesn't need updating", this);
        return true;
    }

    /**
     * @return A list of updates for this program
     */
    public List<Package> getUpdates() {
        List<Package> outdated = new LinkedList<>();
        Collection<Package> ps = getDownloads();
        LOG.log(Level.INFO, "Download list: {0}", ps.toString());
        for(Package p : ps) {
            if(p == null) {
                continue;
            }
            if(p.isLatest()) {
                LOG.log(Level.INFO, "{0} is up to date", p);
            } else {
                LOG.log(Level.INFO, "{0} is outdated", p);
                outdated.add(p);
            }
        }
        return outdated;
    }

    /**
     * A map of downloads to checksums. TODO: allow for versions
     */
    public List<Package> getDownloads() {
        if(downloads == null) {
            initDownloads();
        }
        return downloads;
    }

    private Package initDownloads() {
        downloads = new LinkedList<>();
        downloads.add(this);
        try {
            for(Dependency d : new MavenXpp3Reader().read(new URL(baseURL + ".pom").openStream()).getDependencies()) {
                try {
                    downloads.add(new Package(d).initDownloads());
                } catch(SAXException | ParserConfigurationException | MalformedURLException e) {
                    LOG.log(Level.SEVERE, null, e);
                }
            }
        } catch(IOException | XmlPullParserException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        return this;
    }

    /**
     * TODO: other package types
     *
     * @return
     */
    public String getDownloadURL() {
        return baseURL + ".jar";
    }

    public String getFileName() {
        return JARUtils.name(getDownloadURL());
    }

    public File getFile() {
        return new File(PROGRAM_DIRECTORY, getFileName());
    }

    public File getChecksumFile() {
        return new File(PROGRAM_DIRECTORY, JARUtils.name(getChecksumURL()));
    }

    /**
     * TODO: other checksum types
     *
     * @return
     */
    public String getChecksumURL() {
        return baseURL + ".jar.sha1";
    }

    public List<Executable> getExecutions() {
        return executions;
    }

    public class Executable {

        public final  String       main;
        private final List<String> args;
        public        String       title;
        public        String       newsfeedURL;
        public        boolean      daemon;
        public        JPanel       panel;

        public Executable(final String title, final String newsfeedURL, final String main, final List<String> args) {
            this.title = title;
            this.newsfeedURL = newsfeedURL;
            this.main = main;
            this.args = args;
        }

        public Package getPackage() { return Package.this; }

        @Override
        public String toString() {
            return title;
        }

        public Thread createThread(final CompositeClassLoader cl) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    if(main == null) { // Not executable
                        return;
                    }
                    LOG.log(Level.INFO, "Starting {0} ({1})", new Object[] { this, main });
                    try {
                        String[] argv = null;
                        if(args != null) {
                            argv = args.toArray(new String[args.size()]);
                        }
                        Set<URL> cp = calculateClassPath();
                        cl.start(main, argv, cp);
                    } catch(Exception ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            });
            t.setContextClassLoader(cl);
            t.setDaemon(daemon);
            return t;
        }
    }
}
