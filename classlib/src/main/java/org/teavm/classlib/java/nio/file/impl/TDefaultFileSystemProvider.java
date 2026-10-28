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
package org.teavm.classlib.java.nio.file.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.teavm.classlib.java.io.TFileInputStream;
import org.teavm.classlib.java.io.TFileOutputStream;
import org.teavm.classlib.java.net.TURI;
import org.teavm.classlib.java.nio.file.TAccessMode;
import org.teavm.classlib.java.nio.file.TCopyOption;
import org.teavm.classlib.java.nio.file.TDirectoryIteratorException;
import org.teavm.classlib.java.nio.file.TDirectoryStream;
import org.teavm.classlib.java.nio.file.TFileAlreadyExistsException;
import org.teavm.classlib.java.nio.file.TFileSystem;
import org.teavm.classlib.java.nio.file.TFileSystemAlreadyExistsException;
import org.teavm.classlib.java.nio.file.TFileSystemException;
import org.teavm.classlib.java.nio.file.TLinkOption;
import org.teavm.classlib.java.nio.file.TNotDirectoryException;
import org.teavm.classlib.java.nio.file.TOpenOption;
import org.teavm.classlib.java.nio.file.TPath;
import org.teavm.classlib.java.nio.file.TStandardCopyOption;
import org.teavm.classlib.java.nio.file.TStandardOpenOption;
import org.teavm.classlib.java.nio.file.attribute.TBasicFileAttributes;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;
import org.teavm.classlib.java.nio.file.attribute.TFileTime;
import org.teavm.classlib.java.nio.file.spi.TFileSystemProvider;
import org.teavm.runtime.fs.VirtualFile;

public class TDefaultFileSystemProvider extends TFileSystemProvider {
    public static final TDefaultFileSystemProvider INSTANCE = new TDefaultFileSystemProvider();

    private TDefaultFileSystemProvider() {
    }

    @Override
    public String getScheme() {
        return "file";
    }

    @Override
    public TFileSystem newFileSystem(TURI uri, Map<String, ?> env) throws IOException {
        throw new TFileSystemAlreadyExistsException();
    }

    @Override
    public TFileSystem getFileSystem(TURI uri) {
        if (!uri.getScheme().equals("file") || uri.getAuthority() != null || uri.getQuery() != null
                || uri.getFragment() != null || !uri.getPath().equals("/")) {
            throw new IllegalArgumentException();
        }
        return TDefaultFileSystem.INSTANCE;
    }

    @Override
    public TPath getPath(TURI uri) {
        if (!uri.getScheme().equals("file") || uri.getAuthority() != null || uri.getQuery() != null
                || uri.getFragment() != null || uri.getPath().isEmpty()) {
            throw new IllegalArgumentException();
        }
        return TDefaultFileSystem.INSTANCE.getPath(uri.getPath());
    }

    @Override
    public TFileSystem newFileSystem(TPath path, Map<String, ?> env) throws IOException {
        throw new UnsupportedOperationException();
    }

    // kafka-wasm: for FileChannel.open, which lives in another package.
    public static VirtualFile virtualFile(TPath path) {
        var defaultPath = (TDefaultPath) path;
        return defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
    }

    public static VirtualFile createFileIfMissing(TPath path) throws IOException {
        createEntry((TDefaultPath) path, false);
        return virtualFile(path);
    }

    @Override
    public InputStream newInputStream(TPath path, TOpenOption... options) throws IOException {
        for (var option : options) {
            if (option != TStandardOpenOption.READ && option != TLinkOption.NOFOLLOW_LINKS) {
                throw new UnsupportedOperationException();
            }
        }
        var defaultPath = (TDefaultPath) path;
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        if (vfile == null || !vfile.isFile()) {
            throw new NoSuchFileException(defaultPath.pathString);
        }
        var accessor = vfile.createAccessor(true, false, false);
        if (accessor == null) {
            throw openFailure(vfile, defaultPath.pathString, true, false);
        }
        return new TFileInputStream(accessor);
    }

    @Override
    public OutputStream newOutputStream(TPath path, TOpenOption... options) throws IOException {
        var append = false;
        var create = false;
        var checkThatDoesNotExist = false;
        var truncate = false;
        if (options.length == 0) {
            truncate = true;
            create = true;
        } else {
            for (var option : options) {
                if (option instanceof TStandardOpenOption) {
                    switch ((TStandardOpenOption) option) {
                        case WRITE:
                            break;
                        case APPEND:
                            append = true;
                            break;
                        case CREATE:
                            create = true;
                            break;
                        case CREATE_NEW:
                            create = true;
                            checkThatDoesNotExist = true;
                            break;
                        case TRUNCATE_EXISTING:
                            truncate = true;
                            break;
                        case SYNC:
                        case DELETE_ON_CLOSE:
                        case DSYNC:
                        case SPARSE:
                            break;
                        case READ:
                            throw new IllegalArgumentException();
                    }
                } else if (option != TLinkOption.NOFOLLOW_LINKS) {
                    throw new UnsupportedOperationException();
                }
            }
            if (append && truncate) {
                throw new IllegalArgumentException();
            }
        }

        var defaultPath = (TDefaultPath) path;
        if (checkThatDoesNotExist) {
            // CREATE_NEW: "The check for the existence of the file and the creation of the file if it does not
            // exist is atomic". Only the exclusive create decides, never an earlier existence check, and a failed
            // create never falls through to opening whatever is at the path.
            createFile(defaultPath, false);
        } else {
            var existing = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
            if (existing == null || !existing.exists()) {
                if (!create) {
                    throw new NoSuchFileException(defaultPath.pathString);
                }
                // CREATE: "Create a new file if it does not exist", else open it, so an entry that appeared since
                // the check is opened.
                createEntry(defaultPath, false);
            } else if (existing.isDirectory()) {
                throw new FileAlreadyExistsException(defaultPath.pathString);
            }
        }
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        var accessor = vfile.createAccessor(false, true, append || !truncate);
        if (accessor == null) {
            throw openFailure(vfile, defaultPath.pathString, false, true);
        }
        if (!append) {
            accessor.seek(0);
        }
        return new TFileOutputStream(accessor);
    }

    /**
     * Creates a new, empty file, as {@code Files.createFile} does, and throws {@link FileAlreadyExistsException} if
     * anything exists at the path. With {@code ownerOnly}, a file system with POSIX permissions gives the file mode
     * 0600 (Files.createTempFile uses it).
     */
    public void createFile(TPath path, boolean ownerOnly) throws IOException {
        var defaultPath = (TDefaultPath) path;
        if (!createEntry(defaultPath, ownerOnly)) {
            throw new FileAlreadyExistsException(defaultPath.pathString);
        }
    }

    // Creates the file in one step that fails if the path exists (O_CREAT | O_EXCL on C), so that nothing created
    // at the path between an earlier check and this call is reused. It also fails for a symbolic link, even one
    // whose target does not exist, which an existence check that follows links misses.
    private static boolean createEntry(TDefaultPath path, boolean ownerOnly) throws IOException {
        var fileName = path.getFileName();
        if (fileName == null) {
            // a root, which always exists
            return false;
        }
        var parentPath = path.getParent();
        var parentVfile = path.getFileSystem().vfs.getFile(parentPath != null ? parentPath.pathString : ".");
        return parentVfile.createFile(fileName.toString(), ownerOnly);
    }

    @Override
    public TDirectoryStream<TPath> newDirectoryStream(TPath dir, TDirectoryStream.Filter<? super TPath> filter) throws
            IOException {
        var defaultPath = (TDefaultPath) dir;
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        if (vfile == null || !vfile.exists()) {
            throw new NoSuchFileException(defaultPath.pathString);
        }
        if (!vfile.isDirectory()) {
            throw new TNotDirectoryException(defaultPath.pathString);
        }
        return new TDirectoryStream<>() {
            @Override
            public Iterator<TPath> iterator() {
                var innerIter = List.of(vfile.listFiles()).iterator();
                return new Iterator<>() {
                    TPath nextPath;
                    private boolean endReached;

                    @Override
                    public boolean hasNext() {
                        advance();
                        return !endReached;
                    }

                    @Override
                    public TPath next() {
                        advance();
                        if (endReached) {
                            throw new NoSuchElementException();
                        }
                        var result = nextPath;
                        nextPath = null;
                        return result;
                    }

                    private void advance() {
                        if (nextPath != null || endReached) {
                            return;
                        }
                        while (innerIter.hasNext()) {
                            var file = defaultPath.resolve(innerIter.next());
                            try {
                                if (filter.accept(file)) {
                                    nextPath = file;
                                    return;
                                }
                            } catch (IOException e) {
                                throw new TDirectoryIteratorException(e);
                            }
                        }
                        endReached = true;
                    }
                };
            }

            @Override
            public void close() throws IOException {
            }
        };
    }

    @Override
    public void createDirectory(TPath dir, TFileAttribute<?>... attrs) throws IOException {
        checkNoAttributes(attrs);
        createDirectory(dir, false);
    }

    /**
     * Files.createFile, createDirectory, createDirectories, createTempFile and createTempDirectory throw
     * UnsupportedOperationException "if the array contains an attribute that cannot be set atomically when creating
     * the file". No attribute can be set here, so any attribute is rejected rather than ignored.
     */
    public static void checkNoAttributes(TFileAttribute<?>[] attrs) {
        if (attrs.length > 0) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * As {@link #createDirectory(TPath, TFileAttribute[])}. With {@code ownerOnly}, a file system with POSIX
     * permissions gives the directory mode 0700 (Files.createTempDirectory uses it).
     */
    public void createDirectory(TPath dir, boolean ownerOnly) throws IOException {
        var defaultPath = (TDefaultPath) dir;
        var parent = defaultPath.getParent();
        if (parent == null) {
            parent = defaultPath.getFileSystem().getPath(".");
        }
        var vfile = defaultPath.getFileSystem().vfs.getFile(parent.pathString);
        if (vfile == null || !vfile.isDirectory()) {
            throw new NoSuchFileException(parent.pathString);
        }
        var childVfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        // A symbolic link is an existing entry, also when its target does not exist.
        if (childVfile != null && (childVfile.exists() || childVfile.isSymbolicLink())) {
            throw new TFileAlreadyExistsException(defaultPath.pathString);
        }
        if (!vfile.createDirectory(defaultPath.getFileName().pathString, ownerOnly)) {
            if (childVfile != null && (childVfile.exists() || childVfile.isSymbolicLink())) {
                // created since the check above
                throw new TFileAlreadyExistsException(defaultPath.pathString);
            }
            throw new TFileSystemException("Failed to create directory " + defaultPath.pathString);
        }
    }

    @Override
    public void delete(TPath path) throws IOException {
        var defaultPath = (TDefaultPath) path;
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        // As in the JDK, delete removes a symbolic link itself, also one whose target does not exist.
        if (vfile == null || !vfile.exists() && !vfile.isSymbolicLink()) {
            throw new NoSuchFileException(defaultPath.pathString);
        }
        if (!vfile.delete()) {
            if (vfile.isDirectory() && vfile.listFiles().length > 0) {
                throw new DirectoryNotEmptyException(defaultPath.pathString);
            }
            throw new IOException();
        }
    }

    public void setLastModifiedTime(TPath path, TFileTime time) throws IOException {
        var defaultPath = (TDefaultPath) path;
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        if (vfile == null || !vfile.exists()) {
            throw new NoSuchFileException(defaultPath.pathString);
        }
        // Not File.setLastModified: it rejects times before the epoch, which Files accepts
        if (!vfile.setLastModified(time.toMillis())) {
            throw new IOException();
        }
    }

    @Override
    public void copy(TPath source, TPath target, TCopyOption... options) throws IOException {
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

        var defaultSource = (TDefaultPath) source;
        var defaultTarget = (TDefaultPath) target;

        var srcVfile = defaultSource.getFileSystem().vfs.getFile(defaultSource.pathString);
        if (srcVfile == null || !srcVfile.isFile()) {
            if (!srcVfile.exists()) {
                throw new NoSuchFileException(defaultSource.pathString);
            }
            throw new FileSystemException(defaultSource.pathString);
        }

        if (isSameFile(source, target)) {
            return;
        }

        var targetVfile = defaultTarget.getFileSystem().vfs.getFile(defaultTarget.pathString);
        if (targetVfile != null && targetVfile.exists()) {
            if (!replace) {
                throw new FileAlreadyExistsException(defaultTarget.pathString);
            } else if (!targetVfile.isFile()) {
                throw new FileSystemException(defaultTarget.pathString);
            }
        } else {
            if (!createEntry(defaultTarget, false) && !replace) {
                throw new FileAlreadyExistsException(defaultTarget.pathString);
            }
            targetVfile = defaultTarget.getFileSystem().vfs.getFile(defaultTarget.pathString);
        }

        var targetAccessor = targetVfile.createAccessor(false, true, false);
        if (targetAccessor == null) {
            throw new FileSystemException(defaultTarget.pathString);
        }
        try (var input = new TFileInputStream(srcVfile.createAccessor(true, false, false));
                var output = new TFileOutputStream(targetAccessor)) {
            input.transferTo(output);
        }
    }

    @Override
    public void move(TPath source, TPath target, TCopyOption... options) throws IOException {
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
                        break;
                }
            }
        }

        var defaultSource = (TDefaultPath) source;
        var defaultTarget = (TDefaultPath) target;

        var srcVfile = defaultSource.getFileSystem().vfs.getFile(defaultSource.pathString);
        if (srcVfile == null || !srcVfile.isFile()) {
            if (!srcVfile.exists()) {
                throw new NoSuchFileException(defaultSource.pathString);
            }
            throw new FileSystemException(defaultSource.pathString);
        }

        if (isSameFile(source, target)) {
            return;
        }

        var targetVfile = defaultTarget.getFileSystem().vfs.getFile(defaultTarget.pathString);
        if (targetVfile != null && targetVfile.exists()) {
            if (!replace) {
                throw new FileAlreadyExistsException(defaultTarget.pathString);
            } else if (!targetVfile.isFile()) {
                throw new FileSystemException(defaultTarget.pathString);
            }
        }
        var targetParent = defaultTarget.getParent();
        if (targetParent == null) {
            targetParent = defaultTarget.getFileSystem().getPath(".");
        }
        var parentVfile = defaultTarget.getFileSystem().vfs.getFile(targetParent.pathString);
        if (!parentVfile.isDirectory()) {
            throw new FileSystemException(defaultTarget.pathString);
        }
        parentVfile.adopt(srcVfile, defaultTarget.getFileName().toString());
    }

    @Override
    public boolean isSameFile(TPath path, TPath path2) throws IOException {
        var defaultPath = (TDefaultPath) path;
        var defaultPath2 = (TDefaultPath) path2;
        return defaultPath.getFileSystem().vfs == defaultPath2.getFileSystem().vfs
                && defaultPath.toAbsolutePath().pathString.equals(defaultPath2.toAbsolutePath().pathString);
    }

    @Override
    public boolean isHidden(TPath path) throws IOException {
        return false;
    }

    /**
     * kafka-wasm: the exception for a file that exists but did not open. As in the JDK, a missing permission is an
     * AccessDeniedException (Kafka's ConfigurationUtilsTest reads a file made unreadable). Upstream reported
     * NoSuchFileException for a read, and failed with a NullPointerException for a write.
     */
    public static IOException openFailure(VirtualFile vfile, String path, boolean read, boolean write) {
        if (read && !vfile.canRead() || write && !vfile.canWrite()) {
            return new AccessDeniedException(path);
        }
        return vfile.exists() ? new IOException() : new NoSuchFileException(path);
    }

    @Override
    public void checkAccess(TPath path, TAccessMode... modes) throws IOException {
        var defaultPath = (TDefaultPath) path;
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        if (vfile == null || !vfile.exists()) {
            throw new NoSuchFileException(defaultPath.pathString);
        }
        for (var mode : modes) {
            switch (mode) {
                case READ:
                    if (!vfile.canRead()) {
                        throw new AccessDeniedException(defaultPath.pathString);
                    }
                    break;
                case WRITE:
                    if (!vfile.canWrite()) {
                        throw new AccessDeniedException(defaultPath.pathString);
                    }
                    break;
                case EXECUTE:
                    if (!vfile.canExecute()) {
                        throw new AccessDeniedException(defaultPath.pathString);
                    }
                    break;
            }
        }
    }

    @Override
    public <A extends TBasicFileAttributes> A readAttributes(TPath path, Class<A> type, TLinkOption... options) throws
            IOException {
        if (type != TBasicFileAttributes.class) {
            throw new UnsupportedOperationException();
        }
        var defaultPath = (TDefaultPath) path;
        var vfile = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        // With NOFOLLOW_LINKS, a symbolic link describes itself (lstat): it exists even when its target does not,
        // and it is neither a directory nor a regular file. Its size is 0. Its times are its target's, because
        // VirtualFile has no lstat times.
        var isLink = vfile != null && Arrays.asList(options).contains(TLinkOption.NOFOLLOW_LINKS)
                && vfile.isSymbolicLink();
        if (vfile == null || !isLink && !vfile.exists()) {
            throw new NoSuchFileException(defaultPath.pathString);
        }
        //noinspection unchecked
        return (A) new TBasicFileAttributes() {
            @Override
            public long size() {
                return isLink ? 0 : vfile.length();
            }

            @Override
            public TFileTime lastModifiedTime() {
                return TFileTime.fromMillis(vfile.lastModified());
            }

            @Override
            public TFileTime lastAccessTime() {
                return TFileTime.fromMillis(vfile.lastModified());
            }

            @Override
            public boolean isSymbolicLink() {
                return isLink;
            }

            @Override
            public boolean isRegularFile() {
                return !isLink && vfile.isFile();
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public boolean isDirectory() {
                return !isLink && vfile.isDirectory();
            }

            @Override
            public Object fileKey() {
                return null;
            }

            @Override
            public TFileTime creationTime() {
                return TFileTime.fromMillis(vfile.lastModified());
            }
        };
    }
}
