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
package org.teavm.classlib.java.nio.channels.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.nio.channels.TFileChannel;
import org.teavm.runtime.fs.VirtualFileAccessor;

/**
 * A FileChannel over a VirtualFileAccessor. The accessor's position is the channel's position, so a
 * channel from RandomAccessFile.getChannel moves with the file pointer. A positioned read or write saves and
 * restores it. A null accessor represents a directory; native directory synchronization is unsupported.
 */
public final class TAccessorFileChannel extends TFileChannel {
    // Large enough for few host calls per log segment read, small enough for a short-lived copy.
    private static final int CHUNK = 64 * 1024;

    private final VirtualFileAccessor accessor;
    private final boolean readable;
    private final boolean writable;
    private final boolean append;
    private boolean open;

    public TAccessorFileChannel(VirtualFileAccessor accessor, boolean readable, boolean writable, boolean append) {
        this(accessor, readable, writable, append, true);
    }

    public TAccessorFileChannel(VirtualFileAccessor accessor, boolean readable, boolean writable, boolean append,
            boolean initiallyOpen) {
        open = initiallyOpen;
        this.accessor = accessor;
        this.readable = readable;
        this.writable = writable;
        this.append = append;
    }

    private VirtualFileAccessor file() throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
        if (accessor == null) {
            throw new IOException("Is a directory");
        }
        return accessor;
    }

    private VirtualFileAccessor readableFile() throws IOException {
        VirtualFileAccessor a = file();
        if (!readable) {
            throw new NonReadableChannelException();
        }
        return a;
    }

    private VirtualFileAccessor writableFile() throws IOException {
        VirtualFileAccessor a = file();
        if (!writable) {
            throw new NonWritableChannelException();
        }
        return a;
    }

    private static int toInt(long value) throws IOException {
        if (value > Integer.MAX_VALUE) {
            throw new IOException("Files over 2 GB are not supported");
        }
        return (int) value;
    }

    /** Reads at the accessor's position into dst; -1 at the end of the file. */
    private static int readInto(VirtualFileAccessor a, ByteBuffer dst) throws IOException {
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("Read-only destination");
        }
        int want = dst.remaining();
        if (want == 0) {
            return 0;
        }
        int n;
        if (dst.hasArray()) {
            n = a.read(dst.array(), dst.arrayOffset() + dst.position(), want);
            if (n > 0) {
                dst.position(dst.position() + n);
            }
        } else {
            byte[] tmp = new byte[Math.min(want, CHUNK)];
            n = a.read(tmp, 0, tmp.length);
            if (n > 0) {
                dst.put(tmp, 0, n);
            }
        }
        return n <= 0 ? -1 : n;
    }

    /** Writes all of src at the accessor's position. */
    private static int writeFrom(VirtualFileAccessor a, ByteBuffer src) throws IOException {
        int n = src.remaining();
        if (src.hasArray()) {
            a.write(src.array(), src.arrayOffset() + src.position(), n);
            src.position(src.position() + n);
        } else {
            byte[] tmp = new byte[Math.min(n, CHUNK)];
            while (src.hasRemaining()) {
                int count = Math.min(src.remaining(), tmp.length);
                src.get(tmp, 0, count);
                a.write(tmp, 0, count);
            }
        }
        return n;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return readInto(readableFile(), dst);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, dsts.length);
        VirtualFileAccessor a = readableFile();
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            if (!dsts[i].hasRemaining()) {
                continue;
            }
            int n = readInto(a, dsts[i]);
            if (n < 0) {
                return total == 0 ? -1 : total;
            }
            total += n;
            if (dsts[i].hasRemaining()) {
                break;
            }
        }
        return total;
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        VirtualFileAccessor a = readableFile();
        int saved = a.tell();
        try {
            a.seek(toInt(position));
            return readInto(a, dst);
        } finally {
            a.seek(saved);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        VirtualFileAccessor a = writableFile();
        if (append) {
            a.seek(a.size());
        }
        return writeFrom(a, src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, srcs.length);
        writableFile();
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += write(srcs[i]);
        }
        return total;
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        VirtualFileAccessor a = writableFile();
        int saved = a.tell();
        try {
            a.seek(toInt(position));
            return writeFrom(a, src);
        } finally {
            a.seek(saved);
        }
    }

    @Override
    public long position() throws IOException {
        VirtualFileAccessor a = file();
        return append ? a.size() : a.tell();
    }

    @Override
    public TFileChannel position(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IllegalArgumentException();
        }
        file().seek(toInt(newPosition));
        return this;
    }

    @Override
    public long size() throws IOException {
        return file().size();
    }

    @Override
    public TFileChannel truncate(long size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Negative size");
        }
        VirtualFileAccessor a = writableFile();
        if (size < a.size()) {
            a.resize(toInt(size));
        }
        if (a.tell() > size) {
            a.seek(toInt(size));
        }
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
        if (accessor != null) {
            accessor.sync();
        } else if (PlatformDetector.isC()) {
            throw new UnsupportedOperationException("Directory synchronization is not supported");
        }
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        if (position < 0 || count < 0) {
            throw new IllegalArgumentException();
        }
        VirtualFileAccessor a = readableFile();
        long done = 0;
        int saved = a.tell();
        try {
            a.seek(toInt(position));
            byte[] chunk = new byte[(int) Math.min(CHUNK, Math.max(count, 1))];
            while (done < count) {
                int n = a.read(chunk, 0, (int) Math.min(chunk.length, count - done));
                if (n <= 0) {
                    break;
                }
                // Count the return value and stop after a short write, including a zero-byte write.
                int w = target.write(ByteBuffer.wrap(chunk, 0, n));
                done += w;
                if (w != n) {
                    break;
                }
            }
        } finally {
            a.seek(saved);
        }
        return done;
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        if (position < 0 || count < 0) {
            throw new IllegalArgumentException();
        }
        VirtualFileAccessor a = writableFile();
        long done = 0;
        int saved = a.tell();
        try {
            a.seek(toInt(position));
            ByteBuffer b = ByteBuffer.allocate((int) Math.min(CHUNK, Math.max(count, 1)));
            while (done < count) {
                b.clear();
                b.limit((int) Math.min(b.capacity(), count - done));
                int n = src.read(b);
                if (n <= 0) {
                    break;
                }
                a.write(b.array(), 0, n);
                done += n;
            }
        } finally {
            a.seek(saved);
        }
        return done;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            if (accessor != null) {
                accessor.close();
            }
        }
    }
}
