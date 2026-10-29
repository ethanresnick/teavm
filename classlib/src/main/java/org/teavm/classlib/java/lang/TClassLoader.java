/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.impl.Base64Impl;
import org.teavm.classlib.java.net.TMalformedURLException;
import org.teavm.classlib.java.net.TURL;
import org.teavm.classlib.java.net.TURLConnection;
import org.teavm.classlib.java.net.TURLStreamHandler;
import org.teavm.classlib.java.util.TArrayList;
import org.teavm.classlib.java.util.TCollections;
import org.teavm.classlib.java.util.TEnumeration;
import org.teavm.classlib.java.util.TList;
import org.teavm.interop.NoSideEffects;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

public abstract class TClassLoader extends TObject {
    private TClassLoader parent;
    private static TSystemClassLoader systemClassLoader = new TSystemClassLoader();
    private static ResourceContainer resources;

    // As in the JDK, the default parent is the system class loader.
    protected TClassLoader() {
        this(getSystemClassLoader());
    }

    protected TClassLoader(TClassLoader parent) {
        this.parent = parent;
    }

    public TClassLoader getParent() {
        return parent;
    }

    // TeaVM has one class space, and the system loader finds every class that the compiled program kept; any other
    // name throws ClassNotFoundException, as a JVM does for a class that is not on the class path. A subclass
    // delegates as in the JDK: the parent (or, with no parent, the bootstrap classes) first, then findClass.
    public TClass<?> loadClass(String name) throws TClassNotFoundException {
        return loadClass(name, false);
    }

    protected TClass<?> loadClass(String name, boolean resolve) throws TClassNotFoundException {
        TClass<?> c = null;
        try {
            c = parent != null ? parent.loadClass(name, false) : findBootstrapClassOrNull(name);
        } catch (TClassNotFoundException e) {
            // As in the JDK: the parent does not have it, so this loader's findClass tries next.
        }
        if (c == null) {
            c = findClass(name);
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    protected TClass<?> findClass(String name) throws TClassNotFoundException {
        throw new TClassNotFoundException();
    }

    // A loader can not define classes in TeaVM, and the compiled program never resolves a class reference through
    // a user loader, so no loader is ever recorded as a class's defining or initiating loader.
    protected final TClass<?> findLoadedClass(String name) {
        return null;
    }

    // TeaVM links every class at compile time, so there is nothing left to link.
    protected final void resolveClass(TClass<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }
    }

    /**
     * The JDK's bootstrap loader holds the platform classes. Here that is the classes in the JDK's packages, of
     * those the program kept.
     */
    static TClass<?> findBootstrapClassOrNull(String name) {
        if (!(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("com.sun.") || name.startsWith("org.w3c.")
                || name.startsWith("org.xml.") || name.startsWith("org.ietf."))) {
            return null;
        }
        try {
            return TClass.lookupClass((TString) (Object) name);
        } catch (TClassNotFoundException e) {
            return null;
        }
    }

    public static TClassLoader getSystemClassLoader() {
        return systemClassLoader;
    }

    public InputStream getResourceAsStream(String name) {
        if (resources == null) {
            resources = supplyResources();
        }
        String dataString = getResourceData(resources, name);
        if (dataString == null) {
            return null;
        }
        byte[] bytes = new byte[dataString.length()];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) dataString.charAt(i);
        }
        return new ByteArrayInputStream(Base64Impl.decode(bytes));
    }

    public static InputStream getSystemResourceAsStream(String name) {
        return getSystemClassLoader().getResourceAsStream(name);
    }

    // As the Javadoc specifies: the parent first, then findResource. With no parent, the JDK searches the
    // built-in loader, which here, as for classes, holds only the platform's own, and TeaVM embeds no platform
    // resources. The program's resources belong to the system loader, which finds them in its findResource.
    public TURL getResource(String name) {
        Objects.requireNonNull(name);
        TURL url = parent != null ? parent.getResource(name) : null;
        return url != null ? url : findResource(name);
    }

    public TEnumeration<TURL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);
        TList<TURL> urls = parent != null ? TCollections.list(parent.getResources(name)) : new TArrayList<>();
        urls.addAll(TCollections.list(findResources(name)));
        return TCollections.enumeration(urls);
    }

    protected TURL findResource(String name) {
        return null;
    }

    protected TEnumeration<TURL> findResources(String name) throws IOException {
        return TCollections.emptyEnumeration();
    }

    public static TURL getSystemResource(String name) {
        return getSystemClassLoader().getResource(name);
    }

    public static TEnumeration<TURL> getSystemResources(String name) throws IOException {
        return getSystemClassLoader().getResources(name);
    }

    // The program's resources live in one embedded container, so a URL with the "resource" scheme names one, and
    // opening it reads the container. The name is the URL's path, with '%', '#' and '?' percent-encoded, so that
    // a name that contains them does not turn into a query or a fragment.
    static TURL findEmbeddedResource(String name) {
        if (!hasEmbeddedResource(name)) {
            return null;
        }
        try {
            return new TURL("resource", null, -1, "/" + encodeResourceName(name), ResourceHandler.INSTANCE);
        } catch (TMalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    // On JS, opening a resource decodes all of its Base64 data, so only look the name up. On Wasm GC and C,
    // getResourceAsStream is a map lookup that creates a small stream.
    private static boolean hasEmbeddedResource(String name) {
        if (PlatformDetector.isJavaScript()) {
            if (resources == null) {
                resources = supplyResources();
            }
            return getResourceData(resources, name) != null;
        }
        return getSystemResourceAsStream(name) != null;
    }

    private static String encodeResourceName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); ++i) {
            char c = name.charAt(i);
            switch (c) {
                case '%':
                    sb.append("%25");
                    break;
                case '#':
                    sb.append("%23");
                    break;
                case '?':
                    sb.append("%3F");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    // Reverses encodeResourceName. A '%' that does not start one of its three escapes stays as it is.
    static String decodeResourceName(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); ++i) {
            char c = path.charAt(i);
            if (c == '%' && i + 2 < path.length()) {
                switch (path.substring(i + 1, i + 3)) {
                    case "25":
                        c = '%';
                        i += 2;
                        break;
                    case "23":
                        c = '#';
                        i += 2;
                        break;
                    case "3F":
                        c = '?';
                        i += 2;
                        break;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static final class ResourceHandler extends TURLStreamHandler {
        static final ResourceHandler INSTANCE = new ResourceHandler();

        @Override
        protected TURLConnection openConnection(TURL u) {
            return new TURLConnection(u) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    String path = getURL().getPath();
                    InputStream in = path.startsWith("/")
                            ? getSystemResourceAsStream(decodeResourceName(path.substring(1)))
                            : null;
                    if (in == null) {
                        throw new FileNotFoundException(getURL().toString());
                    }
                    return in;
                }
            };
        }
    }

    // Only the container's own properties are resources: a name such as "constructor" or "__proto__" would otherwise
    // find a property that every JavaScript object inherits.
    @JSBody(params = { "resources", "name" }, script = "return Object.prototype.hasOwnProperty.call(resources, name)"
            + " ? resources[name] : null;")
    private static native String getResourceData(ResourceContainer resources, String name);

    @InjectedBy(ClassLoaderNativeGenerator.class)
    @NoSideEffects
    private static native ResourceContainer supplyResources();

    interface ResourceContainer extends JSObject {
    }
}
