package com.timepath.maven;

import com.timepath.launcher.util.IOUtils;
import com.timepath.launcher.util.JARUtils;
import com.timepath.launcher.util.Utils;
import com.timepath.launcher.util.XMLUtils;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author TimePath
 */
public class MavenResolver {

    public static final  String  REPO_CENTRAL = "http://repo.maven.apache.org/maven2";
    public static final  String  REPO_CUSTOM  = "https://dl.dropboxusercontent.com/u/42745598/maven2";
    private static final Pattern RE_VERSION   = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
    private static final Collection<String> repositories;
    private static final Logger              LOG      = Logger.getLogger(MavenResolver.class.getName());
    private static       Map<String, String> pomCache = Collections.synchronizedMap(new HashMap<String, String>());
    private static       Map<String, String> urlCache = Collections.synchronizedMap(new HashMap<String, String>());

    static {
        repositories = new LinkedHashSet<>();
        addRepository(REPO_CUSTOM);
        addRepository(REPO_CENTRAL);
    }

    private MavenResolver() {}

    /**
     * Add a repository
     *
     * @param url
     *         the URL
     */
    public static void addRepository(String url) {
        repositories.add(sanitize(url));
    }

    /**
     * Sanitize a repository URL
     *
     * @param url
     *         the URL
     *
     * @return the sanitized URL
     */
    private static String sanitize(final String url) {
        return url.replaceAll("/$", "");
    }

    public static InputStream resolvePomStream(String groupId, String artifactId, String version, String classifier)
    throws MalformedURLException
    {
        validate(groupId, artifactId, version);
        String key = coordinate(groupId, artifactId, version, classifier);
        LOG.log(Level.INFO, "Resolving POM: {0}", key);
        String pom = pomCache.get(key);
        if(pom == null) {
            LOG.log(Level.INFO, "Resolving POM (missed): {0}", key);
            try {
                pom = IOUtils.loadPage(new URL(resolve(groupId, artifactId, version, classifier, "pom")));
            } catch(FileNotFoundException e) {
                LOG.log(Level.INFO, "Resolving POM (failed)", e);
                return null;
            }
            pomCache.put(key, pom);
            LOG.log(Level.INFO, "Resolved POM: {0}", key);
        } else {
            LOG.log(Level.INFO, "Resolved POM (cached): {0}", key);
        }
        if(pom == null) return null;
        return new BufferedInputStream(new ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Validates a coordinate
     *
     * @param groupId
     *         the group id
     * @param artifactId
     *         the artifact id
     * @param version
     *         the version
     *
     * @throws java.lang.IllegalArgumentException
     *         if a parameter is null
     */
    private static void validate(String groupId, String artifactId, String version) {
        String coordinate = coordinate(groupId, artifactId, version, null);
        if(groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null: " + coordinate);
        }
        if(artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null: " + coordinate);
        }
        if(version == null) {
            throw new IllegalArgumentException("version cannot be null: " + coordinate);
        }
    }

    /**
     * Formats a maven coordinate
     *
     * @param groupId
     *         the group id
     * @param artifactId
     *         the artifact id
     * @param version
     *         the version
     * @param classifier
     *         the classifier
     *
     * @return formatted maven coordinate
     */
    private static String coordinate(String groupId, String artifactId, String version, String classifier) {
        return MessageFormat.format("{0}:{1}:{2}:{3}", groupId, artifactId, version, classifier);
    }

    /**
     * Resolve an artifact with packaging
     *
     * @param groupId
     *         the group id. Cannot be null
     * @param artifactId
     *         the artifact id. Cannot be null
     * @param version
     *         the version. Cannot be null
     * @param classifier
     *         the classifier. Can be null
     * @param packaging
     *         the packaging. Should not be null
     *
     * @return the artifact URL ready for download
     *
     * @throws FileNotFoundException
     *         if unresolvable
     */
    public static String resolve(String groupId, String artifactId, String version, String classifier, String packaging)
            throws FileNotFoundException
    {
        String resolved = resolve(groupId, artifactId, version, classifier);
        if(resolved == null) return null;
        return resolved + '.' + packaging;
    }

    /**
     * @param groupId
     *         the group id. cannot be null
     * @param artifactId
     *         the artifact id. cannot be null
     * @param version
     *         the verison. cannot be null
     * @param classifier
     *         the classifier. can be null
     *
     * @return the absolute basename of the project coordinate (without the packaging element)
     *
     * @throws FileNotFoundException
     *         if unresolvable
     */
    public static String resolve(String groupId, String artifactId, String version, String classifier)
            throws FileNotFoundException
    {
        validate(groupId, artifactId, version);
        groupId = groupId.replace("${project.groupId}", "com.timepath"); // TODO: variables
        String coordinate = coordinate(groupId, artifactId, version, classifier);
        LOG.log(Level.INFO, "Resolving artifact: {0}", coordinate);
        String cached = urlCache.get(coordinate);
        if(cached != null) {
            LOG.log(Level.INFO, "Resolved artifact (cached): {0}", coordinate);
            return cached;
        }
        LOG.log(Level.INFO, "Resolving artifact (missed): {0}", coordinate);
        classifier = ( classifier == null || classifier.isEmpty() ) ? "" : '-' + classifier;
        String groupFragment = '/' + groupId.replace('.', '/') + '/';
        for(String repository : getRepositories()) {
            String baseArtifact = repository + groupFragment + artifactId + '/';
            // TODO: Check version ranges at new URL(baseArtifact + "maven-metadata.xml")
            String baseVersion = baseArtifact + ( version + '/' );
            String url;
            if(version.endsWith("-SNAPSHOT")) {
                Node metadata;
                // TODO: Handle when using REPO_LOCAL
                try {
                    metadata = XMLUtils.rootNode(new URL(baseVersion + "maven-metadata.xml").openStream(), "metadata");
                } catch(IOException | ParserConfigurationException | SAXException e) {
                    if(e instanceof FileNotFoundException) {
                        LOG.log(Level.WARNING,
                                "Metadata not found for {0} in {1}",
                                new Object[] { coordinate, repository });
                    } else {
                        LOG.log(Level.WARNING, "Unable to resolve {0}\n{1}", new Object[] { coordinate, e });
                    }
                    continue;
                }
                Node snapshot = XMLUtils.last(XMLUtils.getElements(metadata, "versioning/snapshot"));
                url = MessageFormat.format("{0}{1}-{2}-{3}-{4}{5}",
                                           baseVersion,
                                           artifactId,
                                           version.substring(0, version.lastIndexOf("-SNAPSHOT")),
                                           XMLUtils.get(snapshot, "timestamp"),
                                           XMLUtils.get(snapshot, "buildNumber"),
                                           classifier);
            } else {
                url = MessageFormat.format("{0}{1}-{2}{3}", baseVersion, artifactId, version, classifier);
                try {
                    String pom = pomCache.get(coordinate);
                    if(pom == null) {
                        pom = IOUtils.loadPage(new URL(url + ".pom"));
                        if(pom == null) continue;
                    }
                    pomCache.put(coordinate, pom);
                } catch(MalformedURLException e) {
                    continue;
                }
            }
            urlCache.put(coordinate, url);
            return url;
        }
        throw new FileNotFoundException("Could not resolve " + coordinate);
    }

    /**
     * @return the list of repositories ordered by priority
     */
    private static Collection<String> getRepositories() {
        LinkedHashSet<String> repositories = new LinkedHashSet<>();
        repositories.add(getLocal()); // To allow for changes at runtime
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
        try {
            local = new File(local).toURI().toURL().toExternalForm();
        } catch(MalformedURLException e) {
            LOG.log(Level.SEVERE, null, e);
        }
        return sanitize(local);
    }
}
