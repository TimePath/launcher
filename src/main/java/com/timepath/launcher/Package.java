package com.timepath.launcher;

import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.UpdateUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.Utils.DaemonThreadFactory;
import com.timepath.launcher.util.XMLUtils;
import com.timepath.maven.MavenResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
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
    private final String name;
    private final Set<Package> downloads = Collections.synchronizedSet(new HashSet<Package>());
    /**
     * Download status
     */
    public        long         progress  = -1, size = -1;
    /**
     * Maven coordinates
     */
    private String gid, aid, ver;
    /**
     * Base URL in maven repo
     */
    private String baseURL;
    private List<Program> executions = new LinkedList<>();
    private boolean locked;
    private boolean self;

    /**
     * Instantiate a Program instance from XML
     *
     * @param root
     */
    public Package(Node root) {
        if(root == null) {
            throw new IllegalArgumentException("The root node must not be null");
        }
        LOG.log(Level.INFO, "Constructing Package from node");
        LOG.log(Level.FINE, "{0}", Utils.pprint(new DOMSource(root), 2));
        name = XMLUtils.get(root, "name");
        for(Node execution : XMLUtils.getElements(root, "executions/execution")) {
            Node cfg = last(XMLUtils.getElements(execution, "configuration"));
            Program e = new Program(this,
                                    XMLUtils.get(execution, "name"),
                                    XMLUtils.get(execution, "url"),
                                    XMLUtils.get(cfg, "main"),
                                    Utils.argParse(XMLUtils.get(cfg, "args")));
            executions.add(e);
            String daemonStr = XMLUtils.get(cfg, "daemon");
            if(daemonStr != null) {
                e.setDaemon(Boolean.parseBoolean(daemonStr));
            }
        }
        gid = inherit(root, "groupId");
        if(gid == null) return; // invalid pom
        aid = XMLUtils.get(root, "artifactId");
        ver = inherit(root, "version");
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

    public static Package fromDependency(Model m, Dependency d) throws IOException, SAXException, ParserConfigurationException {
        Node root = MavenResolver.resolvePom(depGroup(m, d), d.getArtifactId(), depVersion(m, d), null);
        LOG.log(Level.INFO, "Got rootnode: {0}", root);
        return new Package(root);
    }

    /**
     * TODO: dependencyManagement/dependencies/dependency/version
     */
    private static String depVersion(Model parent, Dependency d) {
        String ret = d.getVersion() == null ? "3.2.1" : d.getVersion();
        if(parent.getVersion() != null) ret.replace("${project.version}", parent.getVersion());
        return ret;
    }

    private static String depGroup(Model parent, Dependency d) {
        String ret = d.getGroupId();
        if(parent.getGroupId() != null) ret.replace("${project.groupId}", parent.getGroupId());
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
        return name == null ? JARUtils.name(baseURL) : name;
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
            if(UPDATE_NAME.equals(existing.getName())) { // edge case for current file
                existing = JARUtils.CURRENT_FILE;
            }
            LOG.log(Level.INFO, "Version file: {0}", existing);
            LOG.log(Level.INFO, "Version url: {0}", getChecksumURL());
            if(!existing.exists()) {
                LOG.log(Level.INFO, "Don't have {0}, not latest", existing);
                return false;
            }
            String expected = Utils.loadPage(new URL(getChecksumURL())).trim();
            String actual = UpdateUtils.checksum(existing, "SHA1");
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
            final Model m = new MavenXpp3Reader().read(MavenResolver.resolvePomStream(gid, aid, ver, null));
            ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            Map<Dependency, Future<Set<Package>>> futures = new HashMap<>();
            for(final Dependency d : m.getDependencies()) {
                futures.put(d, pool.submit(new Callable<Set<Package>>() {
                    @Override
                    public Set<Package> call() throws Exception {
                        try {
                            Package pkg = Package.fromDependency(m, d);
                            return pkg.getDownloads();
                        } catch(SAXException | ParserConfigurationException | MalformedURLException | IllegalArgumentException
                                e) {
                            LOG.log(Level.SEVERE, null, e);
                        }
                        return null;
                    }
                }));
            }
            for(Entry<Dependency, Future<Set<Package>>> e : futures.entrySet()) {
                try {
                    Set<Package> result = e.getValue().get();
                    if(result != null) downloads.addAll(result);
                    else LOG.log(Level.SEVERE, "Download enumeration failed: {0}", e.getKey());
                } catch(InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        } catch(IOException | XmlPullParserException e) {
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

    public List<Program> getExecutions() {
        return executions;
    }

    public boolean isSelf() {
        return self;
    }

    public void setSelf(final boolean self) {
        this.self = self;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(final boolean locked) {
        this.locked = locked;
    }

    public String getName() {
        return name;
    }
}
