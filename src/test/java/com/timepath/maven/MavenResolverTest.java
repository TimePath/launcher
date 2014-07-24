package com.timepath.maven;

import com.timepath.maven.MavenResolver.Coordinate;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MavenResolverTest {

    @Test
    public void testResolve() throws Exception {
        String resolved = MavenResolver.resolve(new Coordinate("com.timepath", "launcher", "1.0-SNAPSHOT", null));
        assertTrue(resolved.contains("/com/timepath/launcher/1.0-SNAPSHOT/launcher-1.0-"));
    }
}
