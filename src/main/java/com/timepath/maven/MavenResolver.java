package com.timepath.maven;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author TimePath
 */
public class MavenResolver {

    public static final  String  REPO_CENTRAL = "http://repo.maven.apache.org/maven2";
    public static final  String  REPO_CUSTOM  = "https://dl.dropboxusercontent.com/u/42745598/maven2";
    public static final  String  REPO_LOCAL   = "file://" + getLocal();
    private static final Pattern RE_VERSION   = Pattern.compile("(\\d*)\\.(\\d*)\\.(\\d*)");
    private static final Collection<String> repos;

    static {
        repos = new LinkedHashSet<>();
        addRepository(REPO_CENTRAL);
        addRepository(REPO_CUSTOM);
    }

    private static final Logger LOG = Logger.getLogger(MavenResolver.class.getName());

    private MavenResolver() {}

    public static void main(String[] args) throws IOException, XmlPullParserException {
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
        classifier = ( ( classifier == null ) || classifier.isEmpty() ) ? "" : '-' + classifier;
        groupId = '/' + groupId.replace('.', '/') + '/';
        for(String repository : getRepositories()) {
            String baseArtifact = repository + groupId + artifactId + '/';
            // TODO: Check version ranges
            // Metadata meta = new MetadataXpp3Reader().read(new URL(baseArtifact + "maven-metadata.xml").openStream());
            String baseVersion = baseArtifact + version + '/';
            if(version.endsWith("-SNAPSHOT")) {
                Metadata meta;
                // TODO: Handle when using REPO_LOCAL
                try {
                    meta = new MetadataXpp3Reader().read(new URL(baseVersion + "maven-metadata.xml").openStream());
                } catch(IOException | XmlPullParserException e) {
                    LOG.log(Level.WARNING, "{0}", e.toString());
                    continue;
                }
                Snapshot snap = meta.getVersioning().getSnapshot();
                return MessageFormat.format("{0}{1}-{2}-{3}-{4}{5}",
                                            baseVersion,
                                            artifactId,
                                            version.substring(0, version.lastIndexOf("-SNAPSHOT")),
                                            snap.getTimestamp(),
                                            snap.getBuildNumber(),
                                            classifier
                                           );
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
}
