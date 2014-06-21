package com.timepath.maven;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MavenResolverTest {

    @Test
    public void testResolve() throws Exception {
        String resolved = MavenResolver.resolve("com.timepath", "launcher", "1.0-SNAPSHOT", null);
        assertTrue(resolved.startsWith(
                "https://dl.dropboxusercontent.com/u/42745598/maven2/com/timepath/launcher/1.0-SNAPSHOT/launcher-1.0-"));
    }
}
