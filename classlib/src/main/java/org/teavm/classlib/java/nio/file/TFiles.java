/*
 *  Copyright 2025 Alexey Andreev.
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
package org.teavm.classlib.java.nio.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.teavm.classlib.java.lang.TSystem;
import org.teavm.classlib.java.nio.file.attribute.TBasicFileAttributes;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;
import org.teavm.classlib.java.nio.file.attribute.TFileTime;
import org.teavm.classlib.java.nio.file.impl.TDefaultFileSystemProvider;

public final class TFiles {
    private TFiles() {
    }

    public static InputStream newInputStream(TPath path, TOpenOption... options) throws IOException {
        return path.getFileSystem().provider().newInputStream(path, options);
    }

    public static OutputStream newOutputStream(TPath path, TOpenOption... options) throws IOException {
        return path.getFileSystem().provider().newOutputStream(path, options);
    }

    public static TDirectoryStream<TPath> newDirectoryStream(TPath dir) throws IOException {
        return newDirectoryStream(dir, f -> true);
    }

    public static TDirectoryStream<TPath> newDirectoryStream(TPath dir, TDirectoryStream.Filter<? super TPath> filter)
            throws IOException {
        return dir.getFileSystem().provider().newDirectoryStream(dir, filter);
    }

    public static TPath createFile(TPath file, TFileAttribute<?>... attrs) throws IOException {
        TDefaultFileSystemProvider.checkNoAttributes(attrs);
        provider(file).createFile(file, false);
        return file;
    }

    // The default file system's provider is the only one.
    private static TDefaultFileSystemProvider provider(TPath path) {
        return (TDefaultFileSystemProvider) path.getFileSystem().provider();
    }

    public static TPath createDirectory(TPath dir, TFileAttribute<?>... attrs) throws IOException {
        dir.getFileSystem().provider().createDirectory(dir, attrs);
        return dir;
    }

    public static TPath createDirectories(TPath dir, TFileAttribute<?>... attrs) throws IOException {
        TDefaultFileSystemProvider.checkNoAttributes(attrs);
        if (dir.getNameCount() <= 1) {
            // As in the JDK, a directory that already exists is not an error, even through a symbolic link.
            try {
                return createDirectory(dir, attrs);
            } catch (FileAlreadyExistsException e) {
                if (!isDirectory(dir)) {
                    throw e;
                }
                return dir;
            }
        }

        var index = dir.getNameCount() + 1;
        for (; index > 1; --index) {
            if (exists(prefix(dir, index - 1))) {
                break;
            }
        }
        if (index > dir.getNameCount() && !isDirectory(dir)) {
            throw new FileAlreadyExistsException(dir.toString());
        }
        while (index <= dir.getNameCount()) {
            createDirectory(prefix(dir, index++));
        }
        return dir;
    }

    // The first count names of the path. Unlike subpath, it keeps the root of an absolute path.
    private static TPath prefix(TPath path, int count) {
        var names = path.subpath(0, count);
        var root = path.getRoot();
        return root != null ? root.resolve(names) : names;
    }

    public static TPath createTempFile(TPath dir, String prefix, String suffix, TFileAttribute<?>... attrs)
            throws IOException {
        // As in the JDK, there is no minimum prefix length (that rule is File.createTempFile's), and a null prefix
        // is empty.
        TDefaultFileSystemProvider.checkNoAttributes(attrs);
        return createTemp(dir, prefix, suffix != null ? suffix : ".tmp", false);
    }

    public static TPath createTempFile(String prefix, String suffix, TFileAttribute<?>... attrs)
            throws IOException {
        return createTempFile(defaultTempDir(), prefix, suffix, attrs);
    }

    public static TPath createTempDirectory(TPath dir, String prefix, TFileAttribute<?>... attrs)
            throws IOException {
        TDefaultFileSystemProvider.checkNoAttributes(attrs);
        return createTemp(dir, prefix, "", true);
    }

    // As the JDK does on POSIX, a file system with permissions makes temporary entries owner-only: mode 0600 for a
    // file and 0700 for a directory (the Javadoc leaves the default attributes to the file system). A name that is
    // taken is not an error: another one is drawn.
    private static TPath createTemp(TPath dir, String prefix, String suffix, boolean directory) throws IOException {
        while (true) {
            var sb = new StringBuilder();
            if (prefix != null) {
                sb.append(prefix);
            }
            generateRandomName(sb);
            sb.append(suffix);
            var path = dir.resolve(sb.toString());
            try {
                if (directory) {
                    provider(path).createDirectory(path, true);
                } else {
                    provider(path).createFile(path, true);
                }
                return path;
            } catch (FileAlreadyExistsException e) {
                // try another name
            }
        }
    }

    public static TPath createTempDirectory(String prefix, TFileAttribute<?>... attrs) throws IOException {
        return createTempDirectory(defaultTempDir(), prefix, attrs);
    }

    // The default temporary-file directory need not exist yet, for example in the in-memory file system. As
    // File.createTempFile does, create it.
    private static TPath defaultTempDir() throws IOException {
        return createDirectories(TPath.of(TSystem.getTempDir()));
    }

    private static void generateRandomName(StringBuilder sb) {
        var random = new Random();
        for (var i = 0; i < 10; ++i) {
            var digit = random.nextInt(62);
            char c;
            if (digit < 10) {
                c = (char) ('0' + digit);
            } else if (digit < 36) {
                c = (char) (digit - 10 + 'A');
            } else {
                c = (char) (digit - 36 + 'a');
            }
            sb.append(c);
        }
    }

    public static void delete(TPath path) throws IOException {
        path.getFileSystem().provider().delete(path);
    }

    public static boolean deleteIfExists(TPath path) throws IOException {
        return path.getFileSystem().provider().deleteIfExists(path);
    }

    public static TPath readSymbolicLink(TPath link) throws IOException {
        return link.getFileSystem().provider().readSymbolicLink(link);
    }

    public static TPath copy(TPath source, TPath target, TCopyOption... options) throws IOException {
        if (source.getFileSystem().provider() == target.getFileSystem().provider()) {
            source.getFileSystem().provider().copy(source, target, options);
        } else {
            copyFallback(source, target, options);
        }
        return target;
    }

    public static TPath move(TPath source, TPath target, TCopyOption... options) throws IOException {
        if (source.getFileSystem().provider() == target.getFileSystem().provider()) {
            source.getFileSystem().provider().move(source, target, options);
        } else {
            copyFallback(source, target, options);
            delete(source);
        }
        return target;
    }

    private static void copyFallback(TPath source, TPath target, TCopyOption... options) throws IOException {
        var replace = false;
        for (var option : options) {
            if (option instanceof TStandardCopyOption) {
                switch ((TStandardCopyOption) option) {
                    case REPLACE_EXISTING:
                        replace = true;
                        break;
                    case COPY_ATTRIBUTES:
                        break;
                    case ATOMIC_MOVE:
                        throw new UnsupportedOperationException();
                }
            }
        }
        var openOptions = replace
                ? new TOpenOption[] { TStandardOpenOption.CREATE, TStandardOpenOption.TRUNCATE_EXISTING }
                : new TOpenOption[] { TStandardOpenOption.CREATE_NEW };
        try (var input = newInputStream(source);
                var output = newOutputStream(target, openOptions)) {
            input.transferTo(output);
        }
    }

    public static boolean isSameFile(TPath path, TPath path2) throws IOException {
        if (Objects.equals(path, path2)) {
            return true;
        }
        if (path.getFileSystem().provider() != path2.getFileSystem().provider()) {
            return false;
        }
        return path.getFileSystem().provider().isSameFile(path, path2);
    }

    public static long mismatch(TPath path, TPath path2) throws IOException {
        if (isSameFile(path, path2)) {
            return -1;
        }
        var size1 = readAttributes(path, TBasicFileAttributes.class).size();
        var size2 = readAttributes(path2, TBasicFileAttributes.class).size();
        if (size1 != size2) {
            return Math.min(size1, size2);
        }
        var offset = 0L;
        var buf1 = new byte[2048];
        var buf2 = new byte[2048];
        try (var input1 = newInputStream(path);
                var input2 = newInputStream(path2)) {
            while (true) {
                var sz1 = input1.readNBytes(buf1, 0, buf1.length);
                var sz2 = input2.readNBytes(buf2, 0, buf2.length);
                if (sz1 != sz2) {
                    return offset + Math.min(sz1, sz2);
                }
                for (var i = 0; i < buf1.length; ++i) {
                    if (buf1[i] != buf2[i]) {
                        return offset + i;
                    }
                }
                offset += sz1;
                if (sz1 < buf1.length) {
                    break;
                }
            }
        }
        return -1;
    }

    public static boolean isHidden(TPath path) throws IOException {
        return path.getFileSystem().provider().isHidden(path);
    }

    public static <A extends TBasicFileAttributes> A readAttributes(TPath path, Class<A> type,
            TLinkOption... options) throws IOException {
        return path.getFileSystem().provider().readAttributes(path, type, options);
    }

    public static boolean isSymbolicLink(TPath path) {
        try {
            // As in the JDK, read the link itself, not its target.
            var attr = readAttributes(path, TBasicFileAttributes.class, TLinkOption.NOFOLLOW_LINKS);
            return attr.isSymbolicLink();
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isDirectory(TPath path, TLinkOption... options) {
        try {
            var attr = readAttributes(path, TBasicFileAttributes.class, options);
            return attr.isDirectory();
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isRegularFile(TPath path, TLinkOption... options) {
        try {
            var attr = readAttributes(path, TBasicFileAttributes.class, options);
            return attr.isRegularFile();
        } catch (IOException e) {
            return false;
        }
    }

    public static TFileTime getLastModifiedTime(TPath path, TLinkOption... options) throws IOException {
        return readAttributes(path, TBasicFileAttributes.class, options).lastModifiedTime();
    }

    public static TPath setLastModifiedTime(TPath path, TFileTime time) throws IOException {
        Objects.requireNonNull(time);
        ((TDefaultFileSystemProvider) path.getFileSystem().provider()).setLastModifiedTime(path, time);
        return path;
    }

    public static long size(TPath path) throws IOException {
        return readAttributes(path, TBasicFileAttributes.class).size();
    }

    public static boolean exists(TPath path, TLinkOption... options) {
        return path.getFileSystem().provider().exists(path, options);
    }

    public static boolean notExists(TPath path, TLinkOption... options) {
        try {
            // With NOFOLLOW_LINKS, ask about the link itself (lstat), as the JDK does. checkAccess follows the link.
            readAttributes(path, TBasicFileAttributes.class, options);
        } catch (NoSuchFileException e) {
            return true;
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public static boolean isReadable(TPath path) {
        try {
            path.getFileSystem().provider().checkAccess(path, TAccessMode.READ);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static boolean isWritable(TPath path) {
        try {
            path.getFileSystem().provider().checkAccess(path, TAccessMode.WRITE);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static boolean isExecutable(TPath path) {
        try {
            path.getFileSystem().provider().checkAccess(path, TAccessMode.EXECUTE);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static TPath walkFileTree(TPath start, Set<TFileVisitOption> options, int maxDepth,
            TFileVisitor<? super TPath> visitor) throws IOException {
        // "By default, symbolic links are not automatically followed by this method. If the options parameter
        // contains the FOLLOW_LINKS option then symbolic links are followed." A link to a directory is then visited
        // as a file. With FOLLOW_LINKS, this walk does not detect cycles (FileSystemLoopException), because
        // VirtualFile has no file keys.
        TLinkOption[] linkOptions = options.contains(TFileVisitOption.FOLLOW_LINKS)
                ? new TLinkOption[0]
                : new TLinkOption[] { TLinkOption.NOFOLLOW_LINKS };
        walkFileTreeRec(start, maxDepth, visitor, linkOptions);
        return start;
    }

    private static TFileVisitResult walkFileTreeRec(TPath path, int remainingDepth,
            TFileVisitor<? super TPath> visitor, TLinkOption[] linkOptions) throws IOException {
        TBasicFileAttributes attrs;
        IOException exc;
        try {
            attrs = readAttributes(path, TBasicFileAttributes.class, linkOptions);
            exc = null;
        } catch (IOException e) {
            exc = e;
            attrs = null;
            if (linkOptions.length == 0) {
                // "When following links, and the attributes of the target cannot be read, then this method attempts
                // to get the BasicFileAttributes of the link."
                try {
                    attrs = readAttributes(path, TBasicFileAttributes.class, TLinkOption.NOFOLLOW_LINKS);
                    exc = null;
                } catch (IOException e2) {
                    // report the first error
                }
            }
        }
        if (exc != null) {
            return visitor.visitFileFailed(path, exc);
        }

        if (remainingDepth == 0 || !attrs.isDirectory()) {
            return visitor.visitFile(path, attrs);
        }

        switch (visitor.preVisitDirectory(path, attrs)) {
            case CONTINUE:
                break;
            case SKIP_SIBLINGS:
                return TFileVisitResult.SKIP_SIBLINGS;
            case SKIP_SUBTREE:
                return TFileVisitResult.CONTINUE;
            case TERMINATE:
                return TFileVisitResult.TERMINATE;
        }
        try (var stream = newDirectoryStream(path)) {
            loop: for (var child : stream) {
                switch (walkFileTreeRec(child, remainingDepth - 1, visitor, linkOptions)) {
                    case CONTINUE:
                    case SKIP_SUBTREE:
                        break;
                    case TERMINATE:
                        return TFileVisitResult.TERMINATE;
                    case SKIP_SIBLINGS:
                        break loop;
                }
            }
        } catch (IOException e) {
            exc = e;
        }
        return visitor.postVisitDirectory(path, exc);
    }

    public static TPath walkFileTree(TPath start, TFileVisitor<? super TPath> visitor) throws IOException {
        return walkFileTree(start, EnumSet.noneOf(TFileVisitOption.class), Integer.MAX_VALUE, visitor);
    }

    public static BufferedReader newBufferedReader(TPath path, Charset cs) throws IOException {
        return new BufferedReader(new InputStreamReader(newInputStream(path), cs));
    }

    public static BufferedReader newBufferedReader(TPath path) throws IOException {
        return newBufferedReader(path, StandardCharsets.UTF_8);
    }

    public static BufferedWriter newBufferedWriter(TPath path, Charset cs, TOpenOption... options)
            throws IOException {
        return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, options), cs));
    }

    public static BufferedWriter newBufferedWriter(TPath path, TOpenOption... options) throws IOException {
        return newBufferedWriter(path, StandardCharsets.UTF_8, options);
    }

    public static long copy(InputStream in, TPath target, TCopyOption... options) throws IOException {
        var replace = false;
        for (var option : options) {
            if (option instanceof TStandardCopyOption) {
                switch ((TStandardCopyOption) option) {
                    case REPLACE_EXISTING:
                        replace = true;
                        break;
                    case COPY_ATTRIBUTES:
                    case ATOMIC_MOVE:
                        throw new UnsupportedOperationException();
                }
            }
        }

        var openOptions = replace
                ? new TOpenOption[] { TStandardOpenOption.CREATE, TStandardOpenOption.TRUNCATE_EXISTING }
                : new TOpenOption[] { TStandardOpenOption.CREATE_NEW };
        try (var output = newOutputStream(target, openOptions)) {
            return in.transferTo(output);
        }
    }

    public static long copy(TPath source, OutputStream out) throws IOException {
        try (var input = newInputStream(source)) {
            return input.transferTo(out);
        }
    }

    public static byte[] readAllBytes(TPath path) throws IOException {
        try (var input = newInputStream(path)) {
            return input.readAllBytes();
        }
    }

    public static String readString(TPath path) throws IOException {
        var out = new StringWriter();
        try (var reader = newBufferedReader(path)) {
            reader.transferTo(out);
        }
        return out.toString();
    }

    public static String readString(TPath path, Charset cs) throws IOException {
        var out = new StringWriter();
        try (var reader = newBufferedReader(path, cs)) {
            reader.transferTo(out);
        }
        return out.toString();
    }

    public static List<String> readAllLines(TPath path, Charset cs) throws IOException {
        try (var reader = TFiles.newBufferedReader(path, cs)) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    public static List<String> readAllLines(TPath path) throws IOException {
        return readAllLines(path, StandardCharsets.UTF_8);
    }

    public static TPath write(TPath path, byte[] bytes, TOpenOption... options) throws IOException {
        try (var output = newOutputStream(path, options)) {
            output.write(bytes);
        }
        return path;
    }

    public static TPath write(TPath path, Iterable<? extends CharSequence> lines, Charset cs, TOpenOption... options)
            throws IOException {
        try (var writer = TFiles.newBufferedWriter(path, cs, options)) {
            for (var line : lines) {
                writer.write(line.toString());
                writer.newLine();
            }
        }
        return path;
    }

    public static TPath write(TPath path, Iterable<? extends CharSequence> lines, TOpenOption... options)
            throws IOException {
        return write(path, lines, StandardCharsets.UTF_8, options);
    }

    public static TPath writeString(TPath path, CharSequence csq, TOpenOption... options) throws IOException {
        return writeString(path, csq, StandardCharsets.UTF_8, options);
    }

    public static TPath writeString(TPath path, CharSequence csq, Charset cs, TOpenOption... options)
            throws IOException {
        try (var writer = TFiles.newBufferedWriter(path, cs, options)) {
            writer.write(csq.toString());
        }
        return path;
    }

    public static Stream<TPath> list(TPath dir) throws IOException {
        var dirStream = newDirectoryStream(dir);
        return StreamSupport.stream(dirStream.spliterator(), false).onClose(() -> {
            try {
                dirStream.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static Stream<TPath> walk(TPath start, int maxDepth, TFileVisitOption... options) throws IOException {
        return walkRec(start, maxDepth);
    }

    private static Stream<TPath> walkRec(TPath path, int maxDepth) throws IOException {
        var result = Stream.of(path);
        if (maxDepth > 0 && isDirectory(path)) {
            result = Stream.concat(result, list(path).flatMap(child -> {
                try {
                    return walkRec(child, maxDepth - 1);
                } catch (IOException e) {
                    return Stream.empty();
                }
            }));
        }
        return result;
    }

    public static Stream<TPath> walk(TPath start, TFileVisitOption... options) throws IOException {
        return walk(start, Integer.MAX_VALUE, options);
    }

    public static Stream<TPath> find(TPath start, int maxDepth, BiPredicate<TPath, TBasicFileAttributes> matcher,
            TFileVisitOption... options) throws IOException {
        return findRec(start, maxDepth, matcher);
    }

    private static Stream<TPath> findRec(TPath path, int maxDepth, BiPredicate<TPath, TBasicFileAttributes> matcher)
            throws IOException {
        var attr = readAttributes(path, TBasicFileAttributes.class);
        var result = matcher.test(path, attr) ? Stream.of(path) : null;
        if (maxDepth > 0 && attr.isDirectory()) {
            var next = Stream.concat(result, list(path).flatMap(child -> {
                try {
                    return findRec(child, maxDepth - 1, matcher);
                } catch (IOException e) {
                    return Stream.empty();
                }
            }));
            result = result != null ? Stream.concat(result, next) : next;
        }
        return result;
    }

    public static Stream<String> lines(TPath path, Charset cs) throws IOException {
        var reader = TFiles.newBufferedReader(path, cs);
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static Stream<String> lines(TPath path) throws IOException {
        return lines(path, StandardCharsets.UTF_8);
    }
}
