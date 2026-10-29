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
            assertInflates(compressed.toByteArray(), input, nowrap);
        }
    }

    @Test
    public void storedBoundariesAcrossSeedsAndCompressionFunctions() throws Exception {
        int[] lengths = { 32767, 32768, 65530, 65531, 65532, 65535, 65536, 120000 };
        int[] levels = { 1, 3, 4, 6, 9, Deflater.DEFAULT_COMPRESSION };
        for (boolean nowrap : new boolean[] { false, true }) {
            for (int i = 0; i < lengths.length; ++i) {
                checkTransitions(nowrap, levels[i % levels.length], i % 3, 1828 + i,
                        i + 1, lengths[i], 128, 1);
            }
        }
    }

    @Test
    public void repeatedStoredTransitionsWithTinyOutput() throws Exception {
        for (boolean nowrap : new boolean[] { false, true }) {
            for (int size : new int[] { 1, 2, 7, 64 }) {
                checkTransitions(nowrap, 6, Deflater.DEFAULT_STRATEGY, 42, 4, 65532, size, 3);
            }
        }
    }

    // Java 21 Deflater.setLevel permits a flush while applying a new level after the next deflate call.
    // Check complete data and counters rather than an implementation-specific compressed representation.
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/Deflater.html#setLevel(int)
    private static void checkTransitions(boolean nowrap, int level, int strategy, int seed, int prefixLength,
            int storedLength, int outputSize, int repetitions) throws Exception {
        byte[] prefix = new byte[prefixLength];
        new Random(seed).nextBytes(prefix);
        byte[] stored = new byte[storedLength];
        byte[] output = new byte[outputSize];
        var expected = new ByteArrayOutputStream();
        var compressed = new ByteArrayOutputStream();
        var deflater = new Deflater(level, nowrap);
        try {
            deflater.setStrategy(strategy);
            for (int repetition = 0; repetition < repetitions; ++repetition) {
                deflater.setLevel(level);
                drain(deflater, compressed, output);
                deflater.setInput(prefix);
                expected.write(prefix);
                drain(deflater, compressed, output);
                deflater.setLevel(Deflater.NO_COMPRESSION);
                drain(deflater, compressed, output);
                deflater.setInput(stored);
                expected.write(stored);
                drain(deflater, compressed, output);
            }
            deflater.finish();
            int calls = 0;
            while (!deflater.finished()) {
                assertTrue(++calls < expected.size() * 2 + 1000);
                compressed.write(output, 0, deflater.deflate(output));
            }
            assertEquals(expected.size(), deflater.getBytesRead());
            assertEquals(compressed.size(), deflater.getBytesWritten());
            assertTrue(compressed.size() >= storedLength * repetitions);
        } finally {
            deflater.end();
        }
        assertInflates(compressed.toByteArray(), expected.toByteArray(), nowrap);
    }

    private static void assertInflates(byte[] compressed, byte[] expected, boolean nowrap) throws Exception {
        var inflater = new Inflater(nowrap);
        try {
            inflater.setInput(compressed);
            byte[] actual = new byte[expected.length + 1];
            int count = 0;
            while (!inflater.finished()) {
                int read = inflater.inflate(actual, count, actual.length - count);
                assertTrue(read > 0 || inflater.finished());
                count += read;
            }
            assertEquals(expected.length, count);
            assertArrayEquals(expected, java.util.Arrays.copyOf(actual, count));
            assertEquals(0, inflater.getRemaining());
        } finally {
            inflater.end();
        }
    }

    private static void drain(Deflater deflater, ByteArrayOutputStream compressed, byte[] output) {
        int calls = 0;
        int count;
        do {
            assertTrue(++calls < 200000);
            count = deflater.deflate(output);
            compressed.write(output, 0, count);
        } while (!deflater.needsInput() || count == output.length);
    }

}
