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
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.teavm.classlib.java.nio.channels.impl.TAccessorFileChannel;
import org.teavm.classlib.java.nio.file.TOpenOption;
import org.teavm.classlib.java.nio.file.TPath;
import org.teavm.classlib.java.nio.file.TStandardOpenOption;
import org.teavm.classlib.java.nio.file.attribute.TFileAttribute;
import org.teavm.classlib.java.nio.file.impl.TDefaultFileSystemProvider;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;

/**
 * kafka-wasm: java.nio.channels.FileChannel, which TeaVM lacks. Kafka keeps log segments in FileChannels
 * (FileRecords). The implementation ({@link TAccessorFileChannel}) works on the same VirtualFileAccessor as
 * RandomAccessFile, so a channel from RandomAccessFile.getChannel shares the file's position, as in the JDK.
 * map, lock, and tryLock throw UnsupportedOperationException: Kafka's clients do not use them.
 *
 * <p>The channel interfaces are named by their java.* names: TeaVM maps them to its own classes, and javac then
 * checks the JDK's signatures (for example, the JDK's position() declares IOException; TeaVM's does not).
 */
public abstract class TFileChannel implements SeekableByteChannel, GatheringByteChannel, ScatteringByteChannel {
    protected TFileChannel() {
    }

    public static TFileChannel open(TPath path, TOpenOption... options) throws IOException {
        return open(path, new HashSet<>(Arrays.asList(options)));
    }

    public static TFileChannel open(TPath path, Set<? extends TOpenOption> options, TFileAttribute<?>... attrs)
            throws IOException {
        boolean write = options.contains(TStandardOpenOption.WRITE) || options.contains(TStandardOpenOption.APPEND);
        boolean read = options.contains(TStandardOpenOption.READ) || !write;
        boolean append = options.contains(TStandardOpenOption.APPEND);
        if (append && options.contains(TStandardOpenOption.READ)) {
            throw new IllegalArgumentException();
        }
        if (append && options.contains(TStandardOpenOption.TRUNCATE_EXISTING)) {
            throw new IllegalArgumentException();
        }
        // As in the JDK, CREATE and CREATE_NEW apply only to a channel that writes.
        boolean createNew = write && options.contains(TStandardOpenOption.CREATE_NEW);
        boolean create = write && (createNew || options.contains(TStandardOpenOption.CREATE));
        TDefaultFileSystemProvider.checkNoAttributes(attrs);
        String name = path.toString();
        if (createNew) {
            TDefaultFileSystemProvider.INSTANCE.createFile(path, false);
        }
        VirtualFile file = TDefaultFileSystemProvider.virtualFile(path);
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                if (write) {
                    throw new IOException();
                }
                // Kafka's Utils.flushDir opens a directory to force it.
                return new TAccessorFileChannel(null, true, false, false);
            }
        } else {
            if (!create) {
                throw new NoSuchFileException(name);
            }
            file = TDefaultFileSystemProvider.createFileIfMissing(path);
        }
        // Always readable at this level: the file system truncates a write-only accessor (FileOutputStream's
        // semantics). The channel enforces its own read and write modes.
        VirtualFileAccessor accessor = file.createAccessor(true, write, false);
        if (accessor == null) {
            throw TDefaultFileSystemProvider.openFailure(file, name, read, write);
        }
        if (write && options.contains(TStandardOpenOption.TRUNCATE_EXISTING)) {
            accessor.resize(0);
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
