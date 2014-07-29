package com.timepath.maven;

import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import com.timepath.util.concurrent.DaemonThreadFactory;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles maven packages
 *
 * @author TimePath
 */
public class Package {

    private static final Logger       LOG       = Logger.getLogger(Package.class.getName());
    private final        Set<Package> downloads = Collections.synchronizedSet(new HashSet<Package>());
    /**
     * Download status
     */
    public               long         progress  = -1, size = -1;
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
    private Node    pom;

    /**
     * Instantiate a Package instance from an XML node
     *
     * @param root
     *         the root node
     * @param context
     *         the parent package
     */
    public static Package parse(Node root, Package context) {
        if(root == null) {
            throw new IllegalArgumentException("The root node cannot be null");
        }
        Package p = new Package();
        LOG.log(Level.INFO, "Constructing Package from node");
        String pprint = Utils.pprint(new DOMSource(root), 2);
        LOG.log(Level.FINE, "{0}", pprint);
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
        if(context != null) {
            p.expand(context);
        }
        try {
            p.baseURL = MavenResolver.resolve(Coordinate.from(p.gid, p.aid, p.ver, null));
            LOG.log(Level.INFO, "Resolved to {0}", p.baseURL);
        } catch(FileNotFoundException e) {
            LOG.log(Level.SEVERE, null, e);
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

    /**
     * Expands properties
     * TODO: recursion
     */
    public void expand(Package context) {
        gid = expand(context, gid.replace("${project.groupId}", context.gid));
        ver = expand(context, ver.replace("${project.version}", context.ver));
    }

    private String expand(Package context, String string) {
        Matcher matcher = Pattern.compile("\\$\\{(.*?)}").matcher(string);
        while(matcher.find()) {
            String property = matcher.group(1);
            List<Node> properties = XMLUtils.getElements(context.pom, "properties");
            Node propertyNodes = properties.get(0);
            for(Node n : XMLUtils.get(propertyNodes, Node.ELEMENT_NODE)) {
                String value = n.getFirstChild().getNodeValue();
                string = string.replace("${" + property + "}", value);
            }
        }
        return string;
    }

    @Override
    public int hashCode() {
        if(baseURL != null) {
            return baseURL.hashCode();
        }
        LOG.log(Level.SEVERE, "baseURL not set: {0}", this);
        return 0;
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
            String expected = getChecksum(new URL(getChecksumURL()));
            String actual = IOUtils.checksum(existing, "SHA1");
            if(!actual.equals(expected)) {
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

    private String getChecksum(URL url) {
        String line = IOUtils.loadPage(url);
        if(line == null) return null;
        return line;
    }

    public File getFile() {
        if(this.isSelf()) return JARUtils.CURRENT_FILE;
        return new File(getProgramDirectory(), getFileName());
    }

    public String getFileName() {
        return IOUtils.name(getDownloadURL());
    }

    /**
     * TODO: other package types
     *
     * @return
     */
    public String getDownloadURL() {
        return baseURL + ".jar";
    }

    public String getProgramDirectory() {
        return MessageFormat.format("{0}/{1}/{2}/{3}", MavenResolver.getLocal(), gid.replace('.', '/'), aid, ver);
    }

    public boolean isSelf() {
        return self || ( "launcher".equals(aid) && "com.timepath".equals(gid) );
    }

    public void setSelf(final boolean self) {
        this.self = self;
    }

    /**
     * TODO: other checksum types
     *
     * @return
     */
    public String getChecksumURL() {
        return baseURL + ".jar.sha1";
    }

    /**
     * Check the integrity of a single package
     *
     * @return true if matches SHA1 checksum
     */
    public boolean verify() {
        LOG.log(Level.INFO, "Checking integrity of {0}", this);
        try {
            File existing = getFile();
            File checksum = new File(existing.getParent(), existing.getName() + ".sha1");
            if(!checksum.exists() || !existing.exists()) {
                LOG.log(Level.INFO, "Don''t have {0}, reacquire", existing);
                return false;
            }
            String expected = getChecksum(checksum.toURI().toURL());
            String actual = IOUtils.checksum(existing, "SHA1");
            if(!actual.equals(expected)) {
                LOG.log(Level.INFO,
                        "Checksum mismatch for {0}, reacquire. {1} vs {2}",
                        new Object[] { existing, expected, actual });
                return false;
            }
        } catch(IOException | NoSuchAlgorithmException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return false;
        }
        LOG.log(Level.INFO, "Verified {0}", this);
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
            if(!p.verify()) {
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
            pom = XMLUtils.rootNode(MavenResolver.resolvePomStream(Coordinate.from(gid, aid, ver, null)), "project");
            ExecutorService pool = Executors.newCachedThreadPool(new DaemonThreadFactory());
            Map<Node, Future<Set<Package>>> futures = new HashMap<>();
            for(final Node d : XMLUtils.getElements(pom, "dependencies/dependency")) {
                // Check scope
                String type = XMLUtils.get(d, "scope");
                if(type == null) type = "compile";
                // TODO: 'import' scope
                switch(type.toLowerCase()) {
                    case "provided":
                    case "test":
                    case "system":
                        continue;
                }
                futures.put(d, pool.submit(new Callable<Set<Package>>() {
                    @Override
                    public Set<Package> call() throws Exception {
                        try {
                            Package pkg = Package.parse(d, Package.this);
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
                    if(result != null) {
                        downloads.addAll(result);
                    } else {
                        LOG.log(Level.SEVERE, "Download enumeration failed: {0}", e.getKey());
                    }
                } catch(InterruptedException | ExecutionException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
        } catch(IOException | ParserConfigurationException | SAXException | IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "initDownloads", e);
        }
        return downloads;
    }

    public File getChecksumFile() {
        return new File(getProgramDirectory(), IOUtils.name(getChecksumURL()));
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
