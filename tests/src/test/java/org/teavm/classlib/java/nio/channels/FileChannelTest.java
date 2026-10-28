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
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

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
