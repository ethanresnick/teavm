/*
 *  Copyright 2026 Ethan Resnick.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
public class FileChannelTest {
    @Test
    public void randomAccessFileCreatesFileAndSharesPosition() throws IOException {
        var file = new File("test-file-channel-raf");
        try {
            try (var raf = new RandomAccessFile(file, "rw")) {
                assertTrue(file.exists());
                var channel = raf.getChannel();
                channel.write(ByteBuffer.wrap(new byte[] { 1, 2, 3, 4 }));
                assertEquals(4, raf.getFilePointer());
                raf.seek(1);
                assertEquals(1, channel.position());
                var buffer = ByteBuffer.allocate(2);
                assertEquals(2, channel.read(buffer));
                assertArrayEquals(new byte[] { 2, 3 }, buffer.array());
                assertEquals(4, channel.size());
                channel.truncate(2);
                assertEquals(2, channel.size());
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void openReadAndWrite() throws IOException {
        var path = Path.of("test-file-channel-open");
        try {
            try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(new byte[] { 5, 6, 7 }));
                channel.write(ByteBuffer.wrap(new byte[] { 9 }), 1);
            }
            assertArrayEquals(new byte[] { 5, 9, 7 }, Files.readAllBytes(path));
            try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
                var buffer = ByteBuffer.allocate(2);
                assertEquals(2, channel.read(buffer, 1));
                assertArrayEquals(new byte[] { 9, 7 }, buffer.array());
                assertEquals(0, channel.position());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void transferToStopsAfterShortWrite() throws IOException {
        var path = Path.of("test-file-channel-transfer");
        try {
            Files.write(path, new byte[] { 1, 2, 3, 4, 5, 6 });
            try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
                var target = new ShortWriteChannel(2);
                var transferred = channel.transferTo(1, 5, target);
                assertEquals(2, transferred);
                assertArrayEquals(new byte[] { 2, 3 }, target.written);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void closingEitherOwnerClosesTheSharedFile() throws IOException {
        var file = new File("test-file-channel-close");
        try {
            var raf = new RandomAccessFile(file, "rw");
            var channel = raf.getChannel();
            assertSame(channel, raf.getChannel());
            channel.close();
            assertThrows(IOException.class, raf::read);
            raf.close();
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
            raf.close();
            assertFalse(channel.isOpen());
            assertThrows(IOException.class, channel::size);
            var closedFile = new RandomAccessFile(file, "rw");
            closedFile.close();
            assertFalse(closedFile.getChannel().isOpen());
        } finally {
            file.delete();
        }
    }

    @Test
    public void scatterGatherAndDirectBuffers() throws IOException {
        var path = Path.of("test-file-channel-buffers");
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            var direct = ByteBuffer.allocateDirect(3);
            direct.put(new byte[] { 2, 3, 4 }).flip();
            assertEquals(4, channel.write(new ByteBuffer[] { ByteBuffer.wrap(new byte[] { 1 }), direct }));
            channel.position(0);
            var first = ByteBuffer.allocate(2);
            var second = ByteBuffer.allocateDirect(2);
            assertEquals(4, channel.read(new ByteBuffer[] { first, second }));
            assertArrayEquals(new byte[] { 1, 2 }, first.array());
            second.flip();
            assertEquals(3, second.get());
            assertEquals(4, second.get());
            assertThrows(IndexOutOfBoundsException.class, () -> channel.read(new ByteBuffer[0], 1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> channel.write(new ByteBuffer[0], 0, -1));
            assertThrows(IllegalArgumentException.class, () -> channel.read(ByteBuffer.allocate(2).asReadOnlyBuffer()));
            assertThrows(FileAlreadyExistsException.class,
                    () -> FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void transferFromBeyondEndGrowsFileWithoutMovingPosition() throws IOException {
        var path = Path.of("test-file-channel-from");
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            var bytes = new ByteArrayInputStream(new byte[] { 7, 8, 9 });
            var source = new ReadableByteChannel() {
                @Override
                public int read(ByteBuffer dst) {
                    int count = 0;
                    while (dst.hasRemaining() && bytes.available() > 0) {
                        dst.put((byte) bytes.read());
                        ++count;
                    }
                    return count > 0 ? count : -1;
                }

                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public void close() {
                }
            };
            assertEquals(3, channel.transferFrom(source, 1, 3));
            assertEquals(0, bytes.available());
            assertEquals(0, channel.position());
            assertEquals(4, channel.size());
            var data = ByteBuffer.allocate(3);
            channel.read(data, 1);
            assertArrayEquals(new byte[] { 7, 8, 9 }, data.array());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void appendWritesAtEndAfterExplicitPositionChange() throws IOException {
        var path = Path.of("test-file-channel-append");
        try {
            Files.write(path, new byte[] { 1, 2 });
            try (var channel = FileChannel.open(path, StandardOpenOption.APPEND)) {
                assertEquals(2, channel.position());
                channel.position(0);
                assertEquals(2, channel.position());
                channel.write(ByteBuffer.wrap(new byte[] { 3 }));
                assertEquals(3, channel.position());
            }
            assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void openingExistingFileForWritingPreservesItsContents() throws IOException {
        var path = Path.of("test-file-channel-existing");
        try {
            Files.write(path, new byte[] { 1, 2, 3 });
            try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                assertEquals(3, channel.size());
                channel.write(ByteBuffer.wrap(new byte[] { 9 }));
            }
            assertArrayEquals(new byte[] { 9, 2, 3 }, Files.readAllBytes(path));
            try (var file = new RandomAccessFile(path.toFile(), "rw")) {
                assertEquals(3, file.length());
                assertEquals(9, file.read());
            }
            Files.write(path, new byte[] { 4 });
            assertArrayEquals(new byte[] { 4 }, Files.readAllBytes(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void forceSynchronizesOpenFileWithoutMovingPosition() throws IOException {
        var path = Path.of("test-file-channel-force");
        try {
            try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(new byte[] { 4, 5, 6 }));
                channel.force(false);
                assertEquals(3, channel.position());
                channel.force(true);
                assertEquals(3, channel.position());
            }
            assertArrayEquals(new byte[] { 4, 5, 6 }, Files.readAllBytes(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    @SkipJVM
    @SkipPlatform({ TestPlatform.JAVASCRIPT, TestPlatform.WEBASSEMBLY_GC })
    public void nativeDirectoryForceFailsExplicitly() throws IOException {
        try (var channel = FileChannel.open(Path.of("."), StandardOpenOption.READ)) {
            assertThrows(UnsupportedOperationException.class, () -> channel.force(true));
        }
    }

    // Takes at most "limit" bytes per write and reads from a duplicate, so the buffer's position does not move.
    static class ShortWriteChannel implements WritableByteChannel {
        final int limit;
        byte[] written = new byte[0];

        ShortWriteChannel(int limit) {
            this.limit = limit;
        }

        @Override
        public int write(ByteBuffer src) {
            var n = Math.min(limit, src.remaining());
            var copy = new byte[n];
            src.duplicate().get(copy);
            var all = new byte[written.length + n];
            System.arraycopy(written, 0, all, 0, written.length);
            System.arraycopy(copy, 0, all, written.length, n);
            written = all;
            return n;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
