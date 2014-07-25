package com.timepath.maven;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author TimePath
 */
public class Coordinate {

    private static final Logger                  LOG   = Logger.getLogger(Coordinate.class.getName());
    private static       Map<String, Coordinate> cache = new HashMap<>();
    final String groupId, artifactId, version;
    /** Nullable */
    final String classifier;

    private Coordinate(String groupId, String artifactId, String version, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        validate();
    }

    /**
     * Validates a coordinate
     *
     * @throws IllegalArgumentException
     *         if a parameter is null
     */
    protected void validate() {
        if(groupId == null) throw new IllegalArgumentException("groupId cannot be null: " + this);
        if(artifactId == null) throw new IllegalArgumentException("artifactId cannot be null: " + this);
        if(version == null) throw new IllegalArgumentException("version cannot be null: " + this);
    }

    public synchronized static Coordinate from(String groupId, String artifactId, String version, String classifier) {
        String s = format(groupId, artifactId, version, classifier);
        Coordinate c = cache.get(s);
        if(c == null) {
            LOG.log(Level.INFO, "Creating {0}", s);
            c = new Coordinate(groupId, artifactId, version, classifier);
            cache.put(s, c);
        }
        return c;
    }

    private static String format(String groupId, String artifactId, String version, String classifier) {
        return groupId + ':' + artifactId + ':' + version + ':' + classifier;
    }

    @Override
    public int hashCode() { return toString().hashCode(); }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Coordinate that = (Coordinate) o;
        return toString().equals(that.toString());
    }

    @Override
    public String toString() { return format(groupId, artifactId, version, classifier); }
}
