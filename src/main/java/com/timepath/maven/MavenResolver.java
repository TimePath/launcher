package com.timepath.maven;

import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import com.timepath.util.Cache;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 * @author TimePath
 */
public class MavenResolver {

    private static final Collection<String> repositories;
    private static final String  REPO_CENTRAL   = "http://repo.maven.apache.org/maven2";
    private static final String  REPO_JFROG     = "http://oss.jfrog.org/oss-snapshot-local";
    private static final String  REPO_CUSTOM    = "https://dl.dropboxusercontent.com/u/42745598/maven2";
    private static final String  REPO_JETBRAINS = "http://repository.jetbrains.com/all";
    private static final Pattern RE_VERSION     = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
    private static final Logger  LOG            = Logger.getLogger(MavenResolver.class.getName());
    private static final long    META_LIFETIME  = 10 * 60 * 1000; // 10 minutes

    static {
        repositories = new LinkedHashSet<>();
        addRepository(REPO_JFROG);
        addRepository(REPO_CUSTOM);
        addRepository(REPO_JETBRAINS);
        addRepository(REPO_CENTRAL);
    }

    /** Cache of coordinates to base urls */
    private static final Map<Coordinate, String> urlCache = new Cache<Coordinate, String>() {
        @Override
        protected String fill(Coordinate key) {
            LOG.log(Level.INFO, "Resolving baseURL (missed): {0}", key);
            String s = '/' + key.groupId.replace('.', '/') + '/' + key.artifactId + '/' + key.version + '/';
            String classifier = ( key.classifier == null || key.classifier.isEmpty() ) ? "" : '-' + key.classifier;
            for(String repository : getRepositories()) {
                String base = repository + s;
                // TODO: Check version ranges at `new URL(baseArtifact + "maven-metadata.xml")`
                if(key.version.endsWith("-SNAPSHOT")) {
                    try {
                        // TODO: Handle metadata when using REPO_LOCAL
                        Node metadata = XMLUtils.rootNode(new URL(base + "maven-metadata.xml").openStream(),
                                                          "metadata");
                        Node snapshot = XMLUtils.last(XMLUtils.getElements(metadata, "versioning/snapshot"));
                        String timestamp = XMLUtils.get(snapshot, "timestamp");
                        String buildNumber = XMLUtils.get(snapshot, "buildNumber");
                        String versionNumber = key.version.substring(0, key.version.lastIndexOf("-SNAPSHOT"));
                        String versionSuffix = ( buildNumber == null ) ? "-SNAPSHOT" : "";
                        //noinspection ConstantConditions
                        return MessageFormat.format("{0}{1}-{2}{3}{4}{5}",
                                                    base,
                                                    key.artifactId,
                                                    versionNumber + versionSuffix,
                                                    ( timestamp == null ) ? "" : ( "-" + timestamp ),
                                                    ( buildNumber == null ) ? "" : ( "-" + buildNumber ),
                                                    classifier);
                    } catch(IOException | ParserConfigurationException | SAXException e) {
                        if(e instanceof FileNotFoundException) {
                            LOG.log(Level.WARNING,
                                    "Metadata not found for {0} in {1}",
                                    new Object[] { key, repository });
                        } else {
                            LOG.log(Level.WARNING, "Unable to resolve " + key, e);
                        }
                    }
                } else { // Simple string manipulation with a test
                    String url = MessageFormat.format("{0}{1}-{2}{3}", base, key.artifactId, key.version, classifier);
                    try {
                        if(!pomCache.containsKey(key)) { // Test it with the pom
                            String pom = IOUtils.loadPage(new URL(url + ".pom"));
                            if(pom == null) continue;
                            pomCache.put(key, pom); // May as well cache the pom while we have it
                        }
                        return url;
                    } catch(MalformedURLException ignored) { }
                }
            }
            LOG.log(Level.WARNING, "Resolving baseURL (failed): {0}", key);
            return null;
        }

        private Preferences getCached(Coordinate c) {
            Preferences cachedNode = Preferences.userNodeForPackage(MavenResolver.class);
            for(String nodeName : c.toString().replaceAll("[.:-]", "/").split("/")) {
                cachedNode = cachedNode.node(nodeName);
            }
            return cachedNode;
        }

        @Override
        protected boolean expire(final Coordinate key) {
            Preferences cachedNode = getCached(key);
            return System.currentTimeMillis() >= cachedNode.getLong("expires", 0);
        }

        private void persist(Coordinate key, String url) {
            Preferences cachedNode = getCached(key);
            cachedNode.put("url", url);
            cachedNode.putLong("expires", System.currentTimeMillis() + META_LIFETIME);
            try {
                cachedNode.flush();
            } catch(BackingStoreException ignored) { }
        }

        @Override
        public String put(Coordinate key, String value) {
            try {
                return super.put(key, value);
            } finally {
                persist(key, value); // Update persistent cache
            }
        }
    };
    private static final Object                  mutex    = new Object();
    /** Cache of coordinates to pom documents */
    private static final Map<Coordinate, String> pomCache = new Cache<Coordinate, String>() {
        @Override
        protected String fill(Coordinate key) {
            try {
                LOG.log(Level.INFO, "Resolving POM (missed): {0}", key);
                return IOUtils.loadPage(new URL(resolve(key, "pom")));
            } catch(FileNotFoundException | MalformedURLException e) {
                LOG.log(Level.WARNING, "Resolving POM (failed): " + key, e);
                return null;
            }
        }
    };

    private MavenResolver() { }

    /**
     * Adds a repository
     *
     * @param url
     *         the URL
     */
    public static void addRepository(String url) { repositories.add(sanitize(url)); }

    /**
     * Sanitizes a repository URL:
     * <ul>
     * <li>Drops trailing slash</li>
     * </ul>
     *
     * @param url
     *         the URL
     *
     * @return the sanitized URL
     */
    private static String sanitize(String url) { return url.replaceAll("/$", ""); }

    /**
     * Resolves a pom to an InputStream
     *
     * @param c
     *         the maven coordinate
     *
     * @return an InputStream for the given coordinates
     *
     * @throws MalformedURLException
     */
    public static InputStream resolvePomStream(Coordinate c) throws MalformedURLException {
        synchronized(mutex) {
            LOG.log(Level.INFO, "Resolving pom: {0}", c);
            String pom = pomCache.get(c);
            if(pom == null) return null;
            return new BufferedInputStream(new ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Resolve an artifact with packaging
     *
     * @param c
     *         the maven coordinate
     * @param packaging
     *         the packaging
     *
     * @return the artifact URL ready for download
     *
     * @throws FileNotFoundException
     *         if unresolvable
     */
    public static String resolve(Coordinate c, String packaging) throws FileNotFoundException {
        String resolved = resolve(c);
        if(resolved == null) return null;
        return resolved + '.' + packaging;
    }

    /**
     * @param c
     *         the maven coordinate
     *
     * @return the absolute basename of the project coordinate (without the packaging element)
     *
     * @throws FileNotFoundException
     *         if unresolvable
     */
    public static String resolve(Coordinate c) throws FileNotFoundException {
        synchronized(mutex) {
            LOG.log(Level.INFO, "Resolving baseURL: {0}", c);
            String base = urlCache.get(c);
            if(base == null) throw new FileNotFoundException("Could not resolve " + c);
            return base;
        }
    }

    /**
     * @return the list of repositories ordered by priority
     */
    private static Collection<String> getRepositories() {
        LinkedHashSet<String> repositories = new LinkedHashSet<>();
        // To allow for changes at runtime, the local repository is not cached
        try {
            repositories.add(new File(getLocal()).toURI().toURL().toExternalForm());
        } catch(MalformedURLException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        repositories.addAll(MavenResolver.repositories);
        return Collections.unmodifiableCollection(repositories);
    }

    /**
     * @return the local repository location
     */
    public static String getLocal() {
        String local;
        local = Utils.SETTINGS.get("progStoreDir", new File(JARUtils.CURRENT_FILE.getParentFile(), "bin").getPath());
        // local = System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository");
        return sanitize(local);
    }

    public static class Coordinate {

        final String groupId, artifactId, version;
        /** Can be null */
        final String classifier;

        public Coordinate(String groupId, String artifactId, String version, String classifier) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.classifier = classifier;
            validate();
        }

        /**
         * Validates a coordinate
         *
         * @throws java.lang.IllegalArgumentException
         *         if a parameter is null
         */
        protected void validate() {
            String coordinate = toString();
            if(groupId == null) throw new IllegalArgumentException("groupId cannot be null: " + coordinate);
            if(artifactId == null) throw new IllegalArgumentException("artifactId cannot be null: " + coordinate);
            if(version == null) throw new IllegalArgumentException("version cannot be null: " + coordinate);
        }

        @Override
        public int hashCode() { return toString().hashCode(); }

        @Override
        public boolean equals(final Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            final Coordinate that = (Coordinate) o;
            return toString().equals(that.toString());
        }

        @Override
        public String toString() {
            return MessageFormat.format("{0}:{1}:{2}:{3}", groupId, artifactId, version, classifier);
        }
    }
}
