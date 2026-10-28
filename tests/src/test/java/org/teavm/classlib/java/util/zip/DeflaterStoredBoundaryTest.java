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
package org.teavm.classlib.java.util.zip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class DeflaterStoredBoundaryTest {
    @Test
    public void changingToStoredBlocksAlignsPendingBits() throws Exception {
        for (boolean nowrap : new boolean[] { false, true }) {
            byte[] prefix = new byte[4];
            new Random(42).nextBytes(prefix);
            byte[] input = new byte[120004];
            System.arraycopy(prefix, 0, input, 0, prefix.length);
            byte[] buffer = new byte[128];
            var compressed = new ByteArrayOutputStream();
            var deflater = new Deflater(6, nowrap);
            try {
                deflater.setInput(prefix);
                int calls = 0;
                while (!deflater.needsInput()) {
                    assertTrue(++calls < 10000);
                    compressed.write(buffer, 0, deflater.deflate(buffer));
                }
                deflater.setLevel(Deflater.NO_COMPRESSION);
                compressed.write(buffer, 0, deflater.deflate(buffer));
                while (!deflater.needsInput()) {
                    assertTrue(++calls < 10000);
                    compressed.write(buffer, 0, deflater.deflate(buffer));
                }
                // Exceeds jzlib's pending-buffer capacity after a partial compressed-block header.
                deflater.setInput(input, prefix.length, input.length - prefix.length);
                deflater.finish();
                while (!deflater.finished()) {
                    assertTrue(++calls < 10000);
                    compressed.write(buffer, 0, deflater.deflate(buffer));
                }
                assertEquals(input.length, deflater.getBytesRead());
                assertEquals(compressed.size(), deflater.getBytesWritten());
                assertTrue(compressed.size() > input.length - prefix.length);
            } finally {
                deflater.end();
            }
            var inflater = new Inflater(nowrap);
            try {
                inflater.setInput(compressed.toByteArray());
                byte[] actual = new byte[input.length + 1];
                int count = 0;
                int calls = 0;
                while (!inflater.finished()) {
                    assertTrue(++calls < 10000);
                    int read = inflater.inflate(actual, count, actual.length - count);
                    assertTrue(read > 0 || inflater.finished());
                    count += read;
                }
                assertEquals(input.length, count);
                assertArrayEquals(input, java.util.Arrays.copyOf(actual, count));
            } finally {
                inflater.end();
            }
        }
    }
}
