/*
 *  Copyright 2026 Alexey Andreev.
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
package org.teavm.classlib.java.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class RandomAccessFileTest {
    @Test
    public void readByte() throws IOException {
        var file = new File("test-randomAccess.bin");
        try {
            try (var writer = new FileOutputStream(file)) {
                writer.write(new byte[] { 0, 1, (byte) 254, (byte) 255 });
            }
            try (var raf = new RandomAccessFile(file, "r")) {
                assertEquals(0, raf.read());
                assertEquals(1, raf.read());
                assertEquals(254, raf.read());
                assertEquals(255, raf.read());
                assertEquals(-1, raf.read());
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void writableModesCreateMissingFilesAndPreserveExistingContents() throws IOException {
        // RandomAccessFile constructors: writable modes create a missing file and permit reads and writes.
        for (String mode : new String[] { "rw", "rwd", "rws" }) {
            var file = new File("random-access-create-" + mode);
            try {
                try (var raf = new RandomAccessFile(file, mode)) {
                    assertTrue(file.exists());
                    raf.write(new byte[] { 1, 2, 3 });
                }
                try (var raf = new RandomAccessFile(file, mode)) {
                    assertEquals(3, raf.length());
                    assertEquals(0, raf.getFilePointer());
                    assertEquals(1, raf.read());
                    raf.write(9);
                    raf.seek(0);
                    assertEquals(1, raf.read());
                    assertEquals(9, raf.read());
                    assertEquals(3, raf.read());
                }
            } finally {
                file.delete();
            }
        }
    }

    @Test
    public void readModeDoesNotCreateAndCannotWriteOrResize() throws IOException {
        var file = new File("random-access-read-only");
        try {
            assertThrows(FileNotFoundException.class, () -> new RandomAccessFile(file, "r"));
            try (var out = new FileOutputStream(file)) {
                out.write(new byte[] { 1, 2, 3 });
            }
            try (var raf = new RandomAccessFile(file, "r")) {
                assertThrows(IOException.class, () -> raf.write(4));
                assertThrows(IOException.class, () -> raf.write(new byte[] { 4 }));
                assertThrows(IOException.class, () -> raf.setLength(1));
                assertEquals(0, raf.getFilePointer());
                assertEquals(3, raf.length());
                assertEquals(1, raf.read());
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void resizingPreservesOrClampsPosition() throws IOException {
        var file = new File("random-access-resize");
        try (var out = new FileOutputStream(file)) {
            out.write(0);
        }
        try (var raf = new RandomAccessFile(file, "rw")) {
            // RandomAccessFile.setLength: preserve the pointer unless it exceeds the new length.
            for (int position = 0; position <= 8; ++position) {
                for (int length = 0; length <= 8; ++length) {
                    raf.setLength(4);
                    raf.seek(position);
                    raf.setLength(length);
                    assertEquals(length, raf.length());
                    assertEquals(Math.min(position, length), raf.getFilePointer());
                }
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void closeIsIdempotentAndPreventsFurtherAccess() throws IOException {
        var file = new File("random-access-close");
        try {
            var raf = new RandomAccessFile(file, "rw");
            raf.write(1);
            raf.close();
            raf.close();
            assertThrows(IOException.class, raf::read);
            assertThrows(IOException.class, () -> raf.write(2));
            assertThrows(IOException.class, raf::length);
            assertThrows(IOException.class, () -> raf.seek(0));
            assertThrows(IOException.class, () -> raf.setLength(0));
        } finally {
            file.delete();
        }
    }

    @Test
    public void invalidNegativeOffsetsDoNotChangeFile() throws IOException {
        var file = new File("random-access-negative");
        try (var out = new FileOutputStream(file)) {
            out.write(0);
        }
        try (var raf = new RandomAccessFile(file, "rw")) {
            raf.write(new byte[] { 1, 2, 3 });
            assertThrows(IOException.class, () -> raf.seek(-1));
            assertThrows(IOException.class, () -> raf.setLength(-1));
            assertEquals(3, raf.getFilePointer());
            assertEquals(3, raf.length());
        } finally {
            file.delete();
        }
    }

    @Test
    @SkipJVM
    public void offsetsBeyondAccessorRangeFailWithoutWrapping() throws IOException {
        var file = new File("random-access-range");
        try (var out = new FileOutputStream(file)) {
            out.write(0);
        }
        try (var raf = new RandomAccessFile(file, "rw")) {
            raf.write(1);
            for (long value : new long[] { 1L + Integer.MAX_VALUE, 1L << 32, Long.MAX_VALUE }) {
                assertThrows(IOException.class, () -> raf.seek(value));
                assertThrows(IOException.class, () -> raf.setLength(value));
                assertEquals(1, raf.getFilePointer());
                assertEquals(1, raf.length());
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void readsAndWritesAlternateAtTheSharedPointer() throws IOException {
        var file = new File("random-access-alternate");
        try {
            try (var out = new FileOutputStream(file)) {
                out.write(0);
            }
            try (var raf = new RandomAccessFile(file, "rw")) {
                raf.write(new byte[] { 1, 2, 3, 4 });
                for (int i = 0; i < 8; ++i) {
                    raf.seek(0);
                    assertEquals(1, raf.read());
                    raf.write(9);
                    assertEquals(3, raf.read());
                    raf.write(8);
                    assertEquals(4, raf.getFilePointer());
                }
                raf.seek(0);
                assertEquals(1, raf.read());
                assertEquals(9, raf.read());
                assertEquals(3, raf.read());
                assertEquals(8, raf.read());
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void readWriteOpenPreservesExistingContents() throws IOException {
        var file = new File("random-access-preserve");
        try {
            try (var out = new FileOutputStream(file)) {
                out.write(new byte[] { 4, 5, 6 });
            }
            try (var raf = new RandomAccessFile(file, "rw")) {
                assertEquals(3, raf.length());
                assertEquals(4, raf.read());
                assertEquals(5, raf.read());
                assertEquals(6, raf.read());
            }
        } finally {
            file.delete();
        }
    }
}
