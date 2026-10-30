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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.teavm.parsing.resource.Resource;
import org.teavm.parsing.resource.ResourceProvider;

public class ClasspathResourceProvider implements ResourceProvider {
    private final ClassLoader classLoader;
    private final boolean includeSystemModules;

    /**
     * Reads classpath resources without implicitly exposing the host JVM's runtime modules.
     */
    public ClasspathResourceProvider(ClassLoader classLoader) {
        this(classLoader, false);
    }

    /**
     * @param includeSystemModules whether resources with the {@code jrt} protocol are included
     */
    public ClasspathResourceProvider(ClassLoader classLoader, boolean includeSystemModules) {
        this.classLoader = classLoader;
        this.includeSystemModules = includeSystemModules;
    }

    @Override
    public Iterator<Resource> getResources(String name) {
        try {
            var enumeration = classLoader.getResources(name);
            if (enumeration == null) {
                return Collections.emptyIterator();
            }
            return new Iterator<>() {
                private URL next;

                @Override
                public boolean hasNext() {
                    while (next == null && enumeration.hasMoreElements()) {
                        var candidate = enumeration.nextElement();
                        if (includeSystemModules || !candidate.getProtocol().equals("jrt")) {
                            next = candidate;
                        }
                    }
                    return next != null;
                }

                @Override
                public Resource next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    var resource = new URLResource(next);
                    next = null;
                    return resource;
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class URLResource implements Resource {
        private final URL url;

        URLResource(URL url) {
            this.url = url;
        }

        @Override
        public InputStream open() {
            try {
                return url.openStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Date getModificationDate() {
            if (url.getProtocol().equals("file")) {
                try {
                    File file = new File(url.toURI());
                    return file.exists() ? new Date(file.lastModified()) : null;
                } catch (URISyntaxException e) {
                    // If URI is invalid, we just report that class should be reparsed
                    return null;
                }
            } else if (url.getProtocol().equals("jar") && url.getPath().startsWith("file:")) {
                int exclIndex = url.getPath().indexOf('!');
                String jarFileName = exclIndex >= 0 ? url.getPath().substring(0, exclIndex) : url.getPath();
                File file = new File(jarFileName.substring("file:".length()));
                return file.exists() ? new Date(file.lastModified()) : null;
            } else {
                return null;
            }
        }
    }

    @Override
    public void close() {
    }
}
