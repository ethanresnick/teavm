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
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class DeflaterParamsTest {
    @Test
    public void levelSetBeforeFirstInputTakesEffect() throws DataFormatException {
        var input = repeated(1000);
        var deflater = new Deflater();
        deflater.setLevel(Deflater.NO_COMPRESSION);
        var compressed = finish(deflater, input);
        // Level 0 stores the data, so the output is longer than the input (the JDK writes 1011 bytes).
        assertTrue(compressed.length > input.length, "length " + compressed.length);
        assertArrayEquals(input, inflate(compressed, input.length));
    }

    @Test
    public void strategySetBeforeFirstInputTakesEffect() throws DataFormatException {
        var input = repeated(1000);
        var deflater = new Deflater();
        deflater.setStrategy(Deflater.HUFFMAN_ONLY);
        var compressed = finish(deflater, input);
        // Huffman coding without matches needs about one bit for each byte (the JDK writes 144 bytes); the default
        // strategy writes 17.
        assertTrue(compressed.length > 100, "length " + compressed.length);
        assertArrayEquals(input, inflate(compressed, input.length));
    }

    @Test
    public void levelChangeTakesEffectAtNextDeflate() throws DataFormatException {
        var input = repeated(1000);
        var expected = new byte[2000];
        System.arraycopy(input, 0, expected, 0, 1000);
        System.arraycopy(input, 0, expected, 1000, 1000);

        // Default level, then level 0: the second half is stored (the JDK writes 1021 bytes in all).
        var deflater = new Deflater();
        var out = new byte[5000];
        deflater.setInput(input);
        var length = deflater.deflate(out);
        deflater.setLevel(Deflater.NO_COMPRESSION);
        length += deflater.deflate(out, length, out.length - length);
        deflater.setInput(input);
        deflater.finish();
        while (!deflater.finished()) {
            length += deflater.deflate(out, length, out.length - length);
        }
        assertTrue(length > 1000 && length < 1500, "length " + length);
        assertArrayEquals(expected, inflate(Arrays.copyOf(out, length), expected.length));

        // Level 0, then level 9: only the first half is stored (the JDK writes 1020 bytes in all).
        deflater = new Deflater(Deflater.NO_COMPRESSION);
        deflater.setInput(input);
        length = deflater.deflate(out);
        deflater.setLevel(Deflater.BEST_COMPRESSION);
        length += deflater.deflate(out, length, out.length - length);
        deflater.setInput(input);
        deflater.finish();
        while (!deflater.finished()) {
            length += deflater.deflate(out, length, out.length - length);
        }
        assertTrue(length > 1000 && length < 1500, "length " + length);
        assertArrayEquals(expected, inflate(Arrays.copyOf(out, length), expected.length));
    }

    // A change from a lazy level (4-9) to a fast level (1-3) or to level 0 switches zlib's compression function.
    // Before the switch, the stream must finish a flush that may not fit one call's output.

    @Test
    public void functionChangeWithSmallOutputKeepsAllData() throws DataFormatException {
        // Without a complete flush, the lazy function's pending literal is lost and the output is corrupt.
        var input = pseudoRandom(20000, 64);
        var compressed = deflateWithLevelChange(input, 6, 1, 7, 1);
        assertArrayEquals(input, inflate(compressed, input.length));
    }

    @Test
    public void changeToStoredWithSmallOutputKeepsAllData() throws DataFormatException {
        // Without a complete flush, jzlib's pending buffer overflows when the stored function writes its block.
        var input = pseudoRandom(100000, 16);
        var compressed = deflateWithLevelChange(input, 6, 0, 7, 1);
        assertArrayEquals(input, inflate(compressed, input.length));
    }

    @Test
    public void functionChangeWithOneByteOutputFinishes() throws DataFormatException {
        // A flush that is resumed in a one-byte buffer must not start over on each call. Each new flush adds an
        // empty block, so the stream then never ends. deflateWithLevelChange limits the number of calls.
        var input = pseudoRandom(100, 64);
        var compressed = deflateWithLevelChange(input, 6, 1, 1, 3);
        assertArrayEquals(input, inflate(compressed, input.length));
    }

    private static byte[] pseudoRandom(int length, int alphabet) {
        var result = new byte[length];
        var seed = 42;
        for (var i = 0; i < length; ++i) {
            seed = seed * 1103515245 + 12345;
            result[i] = (byte) ((seed >>> 16) & (alphabet - 1));
        }
        return result;
    }

    // Deflates input at fromLevel in chunks of outSize bytes, and calls setLevel(toLevel) after call switchAt.
    // Fails if the deflater needs more than 10 calls for each input byte: the output is never that large.
    private static byte[] deflateWithLevelChange(byte[] input, int fromLevel, int toLevel, int outSize,
            int switchAt) {
        var maxCalls = 10 * input.length + 1000;
        var deflater = new Deflater(fromLevel);
        deflater.setInput(input);
        var out = new byte[input.length * 2 + 1000];
        var length = 0;
        var calls = 0;
        while (!deflater.needsInput()) {
            length += deflater.deflate(out, length, Math.min(outSize, out.length - length));
            if (++calls == switchAt) {
                deflater.setLevel(toLevel);
            }
            assertTrue(calls <= maxCalls, "more than " + maxCalls + " deflate calls before the input was read");
        }
        deflater.finish();
        while (!deflater.finished()) {
            length += deflater.deflate(out, length, Math.min(outSize, out.length - length));
            assertTrue(++calls <= maxCalls, "more than " + maxCalls + " deflate calls, the stream did not finish");
        }
        return Arrays.copyOf(out, length);
    }

    private static byte[] repeated(int length) {
        var result = new byte[length];
        Arrays.fill(result, (byte) 'a');
        return result;
    }

    private static byte[] finish(Deflater deflater, byte[] input) {
        deflater.setInput(input);
        deflater.finish();
        var out = new byte[input.length * 2 + 100];
        var length = 0;
        while (!deflater.finished()) {
            length += deflater.deflate(out, length, out.length - length);
        }
        return Arrays.copyOf(out, length);
    }

    private static byte[] inflate(byte[] compressed, int maxLength) throws DataFormatException {
        var inflater = new Inflater();
        inflater.setInput(compressed);
        var result = new byte[maxLength + 10];
        var length = 0;
        while (!inflater.finished() && length < result.length) {
            var n = inflater.inflate(result, length, result.length - length);
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                break;
            }
            length += n;
        }
        return Arrays.copyOf(result, length);
    }
}
