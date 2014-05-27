package com.timepath.maven;

import com.timepath.launcher.util.*;
import com.timepath.launcher.util.XMLUtils;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles maven packages
 *
 * @author TimePath
 */
public class Package {

    public static final  String       PROGRAM_DIRECTORY = Utils.SETTINGS.get("progStoreDir",
                                                                             new File(JARUtils.CURRENT_FILE.getParentFile(),
                                                                                      "bin").getPath()
                                                                            );
    private static final Logger       LOG               = Logger.getLogger(Package.class.getName());
    private final        Set<Package> downloads         = Collections.synchronizedSet(new HashSet<Package>());
    /**
     * Download status
     */
    public               long         progress          = -1, size = -1;
    private String name;
    /**
     * Maven coordinates
     */
    private String gid, aid, ver;
    /**
     * Base URL in maven repo
     */
    private String  baseURL;
    private boolean locked;
    private boolean self;

    /**
     * Instantiate a Package instance from an XML node
     *
     * @param root
     */
    public static Package parse(Node root) {
        if(root == null) {
            throw new IllegalArgumentException("The root node cannot be null");
        }
        Package p = new Package();
        LOG.log(Level.INFO, "Constructing Package from node");
        LOG.log(Level.FINE, "{0}", Utils.pprint(new DOMSource(root), 2));
        p.name = XMLUtils.get(root, "name");
        p.gid = inherit(root, "groupId");
        if(p.gid == null) { // invalid pom
            LOG.log(Level.WARNING, "Invalid POM, no groupId");
            return null;
        }
        p.aid = XMLUtils.get(root, "artifactId");
        p.ver = inherit(root, "version");
        if(p.ver == null) {
            throw new UnsupportedOperationException("TODO: dependencyManagement/dependencies/dependency/version");
        }
        p.baseURL = MavenResolver.resolve(p.gid, p.aid, p.ver, null);
        if(p.baseURL != null) {
            LOG.log(Level.INFO, "Resolved to {0}", p.baseURL);
        } else {
            LOG.log(Level.WARNING, "Could not resolve {0}", MavenResolver.coordinate(p.gid, p.aid, p.ver, null));
        }
        return p;
    }

    private static String inherit(Node root, String name) {
        String ret = XMLUtils.get(root, name);
        if(ret == null) {
            return XMLUtils.get(XMLUtils.last(XMLUtils.getElements(root, "parent")), name);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return baseURL.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        return baseURL.equals(( (Package) o ).baseURL);
    }

    @Override
    public String toString() {
        return name == null ? IOUtils.name(baseURL) : name;
    }

    /**
     * Check a <b>single package</b> for updates
     *
     * @return false if completely up to date, true if up to date or working offline
     */
    public boolean isLatest() {
        LOG.log(Level.INFO, "Checking {0} for updates...", this);
        try {
            File existing = getFile();
            LOG.log(Level.INFO, "Version file: {0}", existing);
            LOG.log(Level.INFO, "Version url: {0}", getChecksumURL());
            if(!existing.exists()) {
                LOG.log(Level.INFO, "Don''t have {0}, not latest", existing);
                return false;
            }
            String expected = IOUtils.loadPage(new URL(getChecksumURL())).trim();
            String actual = IOUtils.checksum(existing, "SHA1");
            if(!expected.equals(actual)) {
                LOG.log(Level.INFO,
                        "Checksum mismatch for {0}, not latest. {1} vs {2}",
                        new Object[] { existing, expected, actual });
                return false;
            }
        } catch(IOException | NoSuchAlgorithmException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return false;
        }
        LOG.log(Level.INFO, "{0} is up to date", this);
        return true;
    }

    /**
     * @return all updates, flattened
     */
    public Set<Package> getUpdates() {
        Set<Package> downloads = getDownloads();
        Set<Package> outdated = new HashSet<>();
        LOG.log(Level.INFO, "Download list: {0}", downloads.toString());
        for(Package p : downloads) {
            if(!p.isLatest()) {
                LOG.log(Level.INFO, "{0} is outdated", p);
                outdated.add(p);
            }
        }
        return outdated;
    }

    /**
     * @return all package files, flattened
     */
    public Set<Package> getDownloads() {
        return downloads.isEmpty() ? initDownloads() : Collections.unmodifiableSet(downloads);
    }

    /**
     * Fetches all dependency information recursively
     * TODO: eager loading
     */
    private Set<Package> initDownloads() {
        LOG.log(Level.INFO, "initDownloads: {0}", this);
        downloads.add(this);
        try {
            // pull the pom
            Node pom = XMLUtils.rootNode(MavenResolver.resolvePomStream(gid, aid, ver, null), "project");
            ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            Map<Node, Future<Set<Package>>> futures = new HashMap<>();
            for(final Node d : XMLUtils.getElements(pom, "dependencies/dependency")) {
                futures.put(d, pool.submit(new Callable<Set<Package>>() {
                    @Override
                    public Set<Package> call() throws Exception {
                        try {
                            Package pkg = Package.parse(d);
                            pkg.gid = pkg.gid.replace("${project.groupId}", gid);
                            pkg.ver = pkg.ver.replace("${project.version}", ver);
                            return pkg.getDownloads();
                        } catch(IllegalArgumentException e) {
                            LOG.log(Level.SEVERE, null, e);
                        }
                        return null;
                    }
                }));
            }
            for(Entry<Node, Future<Set<Package>>> e : futures.entrySet()) {
                try {
                    Set<Package> result = e.getValue().get();
                    if(result != null) { downloads.addAll(result); } else {
                        LOG.log(Level.SEVERE, "Download enumeration failed: {0}", e.getKey());
                    }
                } catch(InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        } catch(IOException | ParserConfigurationException | SAXException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        return downloads;
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
        return IOUtils.name(getDownloadURL());
    }

    public File getFile() {
        if(this.isSelf()) return JARUtils.CURRENT_FILE;
        return new File(getProgramDirectory(), getFileName());
    }

    public File getChecksumFile() {
        return new File(getProgramDirectory(), IOUtils.name(getChecksumURL()));
    }

    public String getProgramDirectory() {
        return MessageFormat.format("{0}/{1}/{2}/{3}", PROGRAM_DIRECTORY, gid.replace('.', '/'), aid, ver);
    }

    /**
     * TODO: other checksum types
     *
     * @return
     */
    public String getChecksumURL() {
        return baseURL + ".jar.sha1";
    }

    public boolean isSelf() {
        return self || ( "launcher".equals(aid) && "com.timepath".equals(gid) );
    }

    public void setSelf(final boolean self) {
        this.self = self;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(final boolean locked) {
        if(locked) { LOG.log(Level.INFO, "Locking {0}", this); } else { LOG.log(Level.INFO, "unlocking {0}", this); }
        this.locked = locked;
    }

    public String getName() {
        return name;
    }
}
