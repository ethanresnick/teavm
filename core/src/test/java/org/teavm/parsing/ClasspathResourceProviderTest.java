/*
 *  Copyright 2012 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.parsing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClasspathResourceProviderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void excludesHostResourcesWithoutMaskingLaterClasspathEntries() throws Exception {
        var first = temporaryFolder.newFile("first.class").toPath();
        var second = temporaryFolder.newFile("second.class").toPath();
        Files.write(first, new byte[] { 1 });
        Files.write(second, new byte[] { 2 });
        var host = ClassLoader.getPlatformClassLoader().getResource("java/lang/Object.class");
        assertEquals("jrt", host.getProtocol());
        var loader = new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                try {
                    return Collections.enumeration(List.of(host, first.toUri().toURL(), host,
                            second.toUri().toURL(), host));
                } catch (java.net.MalformedURLException e) {
                    throw new AssertionError(e);
                }
            }

            @Override
            public URL getResource(String name) {
                throw new AssertionError("Must use each enumerated resource, not resolve its name again");
            }
        };
        try (var provider = new ClasspathResourceProvider(loader)) {
            var resources = provider.getResources("java/lang/Object.class");
            for (var path : List.of(first, second)) {
                assertTrue(resources.hasNext());
                assertTrue(resources.hasNext());
                var resource = resources.next();
                assertEquals(new Date(Files.getLastModifiedTime(path).toMillis()), resource.getModificationDate());
                try (var input = resource.open()) {
                    assertEquals(Files.readAllBytes(path)[0], input.read());
                    assertEquals(-1, input.read());
                }
            }
            assertFalse(resources.hasNext());
            assertThrows(NoSuchElementException.class, resources::next);
        }
        try (var provider = new ClasspathResourceProvider(loader, true)) {
            var resources = provider.getResources("java/lang/Object.class");
            int count = 0;
            while (resources.hasNext()) {
                resources.next();
                count++;
            }
            assertEquals(5, count);
        }
    }

    @Test
    public void runtimeModulesAreExcludedByDefault() {
        try (var provider = new ClasspathResourceProvider(ClassLoader.getPlatformClassLoader())) {
            assertNull(provider.getResource("java/util/concurrent/Future.class"));
        }
    }

    @Test
    public void canExcludeRuntimeModulesWithoutExcludingClasspath() {
        var loader = ClasspathResourceProviderTest.class.getClassLoader();
        try (var provider = new ClasspathResourceProvider(loader, false)) {
            assertNull(provider.getResource("java/util/concurrent/Future.class"));
            assertNotNull(provider.getResource("org/teavm/parsing/ClasspathResourceProviderTest.class"));
        }
    }

    @Test
    public void runtimeModulesRequireExplicitOptIn() {
        try (var provider = new ClasspathResourceProvider(ClassLoader.getPlatformClassLoader(), true)) {
            assertNotNull(provider.getResource("java/util/concurrent/Future.class"));
        }
    }
}
