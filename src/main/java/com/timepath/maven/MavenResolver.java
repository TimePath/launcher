package com.timepath.maven;

import com.timepath.launcher.util.IOUtils;
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

    public static final String REPO_CENTRAL = "http://repo.maven.apache.org/maven2";
    public static final String REPO_CUSTOM  = "https://dl.dropboxusercontent.com/u/42745598/maven2";
    public static final String REPO_LOCAL;
    private static final Pattern RE_VERSION = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
    private static final Collection<String> repos;
    private static final Logger              LOG      = Logger.getLogger(MavenResolver.class.getName());
    private static       Map<String, String> pomCache = Collections.synchronizedMap(new HashMap<String, String>());

    static {
        repos = new LinkedHashSet<>();
        addRepository(REPO_CENTRAL);
        addRepository(REPO_CUSTOM);
        String temp;
        try {
            temp = new File(getLocal()).toURI().toURL().toExternalForm();
        } catch(MalformedURLException e) {
            LOG.log(Level.SEVERE, "Error creating URL to local repo: {0}", e);
            temp = "bin";
        }
        REPO_LOCAL = temp;
    }

    private MavenResolver() {}

    public static void main(String[] args) throws IOException {
        System.out.println(resolve("com.timepath", "launcher", "1.0-SNAPSHOT", null));
    }

    /**
     * @param groupId
     * @param artifactId
     * @param version
     * @param classifier
     *
     * @return The absolute basename of the project coordinate (without the packaging element)
     */
    public static String resolve(String groupId, String artifactId, String version, String classifier) {
        groupId = groupId.replace("${project.groupId}", "com.timepath"); // TODO: variables
        LOG.log(Level.INFO, "artifact: {0}:{1}:{2}", new Object[] { groupId, artifactId, version, classifier });
        if(groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null");
        }
        if(artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null");
        }
        if(version == null) {
            throw new IllegalArgumentException("version cannot be null");
        }
        classifier = ( ( classifier == null ) || classifier.isEmpty() ) ? "" : '-' + classifier;
        groupId = '/' + groupId.replace('.', '/') + '/';
        for(String repository : getRepositories()) {
            String baseArtifact = repository + groupId + artifactId + '/';
            // TODO: Check version ranges at new URL(baseArtifact + "maven-metadata.xml")
            String baseVersion = baseArtifact + version + '/';
            if(version.endsWith("-SNAPSHOT")) {
                Node meta;
                // TODO: Handle when using REPO_LOCAL
                try {
                    meta = XMLUtils.rootNode(new URL(baseVersion + "maven-metadata.xml").openStream(), "metadata");
                } catch(IOException | ParserConfigurationException | SAXException e) {
                    if(!( e instanceof FileNotFoundException )) LOG.log(Level.WARNING, "{0}", e.toString());
                    continue;
                }
                Node snap = XMLUtils.last(XMLUtils.getElements(meta, "versioning/snapshot"));
                return MessageFormat.format("{0}{1}-{2}-{3}-{4}{5}",
                                            baseVersion,
                                            artifactId,
                                            version.substring(0, version.lastIndexOf("-SNAPSHOT")),
                                            XMLUtils.get(snap, "timestamp"),
                                            XMLUtils.get(snap, "buildNumber"),
                                            classifier);
            } else {
                return MessageFormat.format("{0}{1}-{2}{3}", baseVersion, artifactId, version, classifier);
            }
        }
        return null;
    }

    public static Collection<String> getRepositories() {
        LinkedHashSet<String> repos = new LinkedHashSet<>();
        repos.add(REPO_LOCAL); // To allow for changes at runtime
        repos.addAll(MavenResolver.repos);
        return Collections.unmodifiableCollection(repos);
    }

    public static void addRepository(String url) {
        repos.add(url.replaceAll("/$", ""));
    }

    public static String getLocal() {
        return System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository");
    }

    public static Node resolvePom(String groupId, String artifactId, String version, String classifier)
    throws IOException, SAXException, ParserConfigurationException
    {
        return XMLUtils.rootNode(resolvePomStream(groupId, artifactId, version, classifier), "project");
    }

    public static InputStream resolvePomStream(String groupId, String artifactId, String version, String classifier)
    throws MalformedURLException
    {
        String key = MessageFormat.format("{0}:{1}:{2}:{3}", groupId, artifactId, version, classifier);
        LOG.log(Level.INFO, "Resolving POM: {0}", key);
        String pom = pomCache.get(key);
        if(pom == null) {
            LOG.log(Level.INFO, "Resolving POM: Missed cache");
            pom = IOUtils.loadPage(new URL(resolve(groupId, artifactId, version, classifier, "pom")));
            if(pom == null) {
                return null;
            }
            pomCache.put(key, pom);
        } else {
            LOG.log(Level.INFO, "Resolving POM: Hit cache");
        }
        LOG.log(Level.INFO, "Resolved POM: {0}", key);
        byte[] bytes = pom.getBytes(StandardCharsets.UTF_8);
        return new BufferedInputStream(new ByteArrayInputStream(bytes));
    }

    public static String resolve(String groupId, String artifactId, String version, String classifier, String packaging) {
        String ret = resolve(groupId, artifactId, version, classifier);
        if(ret != null) ret += '.' + packaging;
        return ret;
    }
}
