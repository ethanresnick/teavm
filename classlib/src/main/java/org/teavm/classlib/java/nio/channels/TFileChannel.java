/*
 *  Copyright 2026 kafka-wasm contributors.
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
package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.teavm.classlib.java.nio.channels.impl.TAccessorFileChannel;
import org.teavm.classlib.java.nio.file.TOpenOption;
import org.teavm.classlib.java.nio.file.TPath;
import org.teavm.classlib.java.nio.file.TStandardOpenOption;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;
import org.teavm.classlib.java.nio.file.impl.TDefaultFileSystemProvider;
import org.teavm.classlib.java.nio.file.impl.TDefaultPath;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;

/** File channels backed by the virtual file system, sharing an accessor with RandomAccessFile when requested. */
public abstract class TFileChannel implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel {
    protected TFileChannel() {
    }

    public static TFileChannel open(TPath path, TOpenOption... options) throws IOException {
        return open(path, new HashSet<>(Arrays.asList(options)));
    }

    public static TFileChannel open(TPath path, Set<? extends TOpenOption> options, TFileAttribute<?>... attrs)
            throws IOException {
        Objects.requireNonNull(path);
        if (!(path instanceof TDefaultPath)) {
            throw new UnsupportedOperationException();
        }
        TDefaultFileSystemProvider.checkNoAttributes(attrs);
        for (TOpenOption option : options) {
            Objects.requireNonNull(option);
            if (!(option instanceof TStandardOpenOption)) {
                throw new UnsupportedOperationException();
            }
            if (option == TStandardOpenOption.DELETE_ON_CLOSE || option == TStandardOpenOption.SYNC
                    || option == TStandardOpenOption.DSYNC) {
                throw new UnsupportedOperationException(option.toString());
            }
        }
        boolean write = options.contains(TStandardOpenOption.WRITE) || options.contains(TStandardOpenOption.APPEND);
        boolean read = options.contains(TStandardOpenOption.READ) || !write;
        boolean append = options.contains(TStandardOpenOption.APPEND);
        if (append && options.contains(TStandardOpenOption.READ)) {
            throw new IllegalArgumentException("READ + APPEND not allowed");
        }
        if (append && options.contains(TStandardOpenOption.TRUNCATE_EXISTING)) {
            throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
        }
        // As in the JDK, CREATE and CREATE_NEW apply only to a channel that writes.
        boolean createNew = write && options.contains(TStandardOpenOption.CREATE_NEW);
        boolean create = write && (createNew || options.contains(TStandardOpenOption.CREATE));
        String name = path.toString();
        TDefaultPath defaultPath = (TDefaultPath) path;
        var provider = (TDefaultFileSystemProvider) path.getFileSystem().provider();
        if (createNew) {
            provider.createFile(path, false);
        }
        VirtualFile file = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                if (write) {
                    throw new IOException("Is a directory: " + name);
                }
                // Directory reads are unsupported; force is a no-op only for the in-memory backend.
                return new TAccessorFileChannel(null, true, false, false);
            }
        } else {
            if (!create) {
                throw new NoSuchFileException(name);
            }
            try {
                provider.createFile(path, false);
            } catch (FileAlreadyExistsException e) {
                // CREATE permits another opener to create the file between the existence check and creation.
            }
            file = defaultPath.getFileSystem().vfs.getFile(defaultPath.pathString);
        }
        // Always readable at this level: the file system truncates a write-only accessor (FileOutputStream's
        // semantics). The channel enforces its own read and write modes.
        VirtualFileAccessor accessor = file.createAccessor(true, write, false);
        if (accessor == null) {
            throw new FileSystemException(name);
        }
        if (write && options.contains(TStandardOpenOption.TRUNCATE_EXISTING)) {
            try {
                accessor.resize(0);
            } catch (IOException e) {
                accessor.close();
                throw e;
            }
        }
        if (append) {
            accessor.seek(accessor.size());
        }
        return new TAccessorFileChannel(accessor, read, write, append);
    }

    @Override
    public abstract int read(ByteBuffer dst) throws IOException;

    @Override
    public abstract long read(ByteBuffer[] dsts, int offset, int length) throws IOException;

    @Override
    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public abstract int write(ByteBuffer src) throws IOException;

    @Override
    public abstract long write(ByteBuffer[] srcs, int offset, int length) throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public abstract long position() throws IOException;

    @Override
    public abstract TFileChannel position(long newPosition) throws IOException;

    @Override
    public abstract long size() throws IOException;

    @Override
    public abstract TFileChannel truncate(long size) throws IOException;

    public abstract void force(boolean metaData) throws IOException;

    public abstract long transferTo(long position, long count, WritableByteChannel target) throws IOException;

    public abstract long transferFrom(ReadableByteChannel src, long position, long count) throws IOException;

    public abstract int read(ByteBuffer dst, long position) throws IOException;

    public abstract int write(ByteBuffer src, long position) throws IOException;
}
