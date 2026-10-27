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
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    // Javadoc of Deflater.setLevel: "If the compression level is changed, the next invocation of deflate will
    // compress the input available so far with the old level (and may be flushed); the new level will take effect
    // only after that invocation." setStrategy says the same of the strategy.
    @Test
    public void inputAvailableAtNextDeflateUsesOldLevel() throws DataFormatException {
        var first = repeated(1000);
        var second = new byte[10000];
        Arrays.fill(second, (byte) 'b');
        var expected = new byte[11000];
        System.arraycopy(first, 0, expected, 0, 1000);
        System.arraycopy(second, 0, expected, 1000, 10000);

        // The 10000 bytes are available at the first call after setLevel(0), so level 9 compresses them
        // (the JDK writes 40 bytes in all). Level 0 would store them.
        var deflater = new Deflater(Deflater.BEST_COMPRESSION);
        var out = new byte[20000];
        deflater.setInput(first);
        var length = deflater.deflate(out);
        deflater.setLevel(Deflater.NO_COMPRESSION);
        deflater.setInput(second);
        length += deflater.deflate(out, length, out.length - length);
        // That call read all the input, so level 0 now applies: the next input is stored.
        assertTrue(deflater.needsInput());
        deflater.setInput(first);
        deflater.finish();
        while (!deflater.finished()) {
            length += deflater.deflate(out, length, out.length - length);
        }
        assertTrue(length > 1000 && length < 1200, "length " + length);
        var all = new byte[12000];
        System.arraycopy(expected, 0, all, 0, 11000);
        System.arraycopy(first, 0, all, 11000, 1000);
        assertArrayEquals(all, inflate(Arrays.copyOf(out, length), all.length));

        // The same with a strategy: HUFFMAN_ONLY applies only after the call that reads the 10000 bytes.
        deflater = new Deflater();
        deflater.setInput(first);
        length = deflater.deflate(out);
        deflater.setStrategy(Deflater.HUFFMAN_ONLY);
        deflater.setInput(second);
        deflater.finish();
        while (!deflater.finished()) {
            length += deflater.deflate(out, length, out.length - length);
        }
        // The JDK writes 41 bytes; Huffman coding alone would need more than one bit for each of the 10000 bytes.
        assertTrue(length < 200, "length " + length);
        assertArrayEquals(expected, inflate(Arrays.copyOf(out, length), expected.length));
    }

    // ZipOutputStream.setLevel: "Sets the compression level for subsequent entries which are DEFLATED."
    @Test
    public void zipOutputStreamLevelAppliesToSubsequentEntries() throws DataFormatException, IOException {
        var data = repeated(5000);
        var bytes = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(bytes);
        var levels = new int[] { Deflater.DEFAULT_COMPRESSION, Deflater.NO_COMPRESSION, Deflater.BEST_COMPRESSION };
        var starts = new int[4];
        for (var i = 0; i < 3; ++i) {
            zip.setLevel(levels[i]);
            starts[i] = bytes.size();
            zip.putNextEntry(new ZipEntry("e" + i));
            zip.write(data);
            zip.closeEntry();
        }
        starts[3] = bytes.size();
        zip.close();

        // Each entry is the 30-byte local header, the 2-byte name, the deflate data and a data descriptor. The data
        // is inflated directly: TeaVM's ZipInputStream fails on the second entry of such a file (a separate bug).
        var zipped = bytes.toByteArray();
        for (var i = 0; i < 3; ++i) {
            var entry = Arrays.copyOfRange(zipped, starts[i] + 32, starts[i + 1]);
            assertArrayEquals(data, inflate(entry, data.length, true), "e" + i);
        }
        assertTrue(starts[1] - starts[0] < 150, "e0 " + (starts[1] - starts[0]));
        assertTrue(starts[2] - starts[1] > 5000, "e1 " + (starts[2] - starts[1]));
        assertTrue(starts[3] - starts[2] < 150, "e2 " + (starts[3] - starts[2]));
    }

    // Deflates random data with random setLevel, setStrategy, flush modes and output sizes (1, 2 and 7 bytes
    // among them), and checks that everything inflates back, that the counters match the delivered bytes, and
    // that the deflater never needs more calls than the cap: a livelock fails the test instead of hanging it.
    // Deflater.deflate with SYNC_FLUSH: "all pending output is flushed", so after a call that did not fill the
    // buffer, all input read so far must decode.
    @Test
    public void randomParameterChangesRoundTrip() throws DataFormatException {
        var random = new Random(91);
        for (var iteration = 0; iteration < 150; ++iteration) {
            roundTrip(random, iteration);
        }
    }

    private static void roundTrip(Random random, int iteration) throws DataFormatException {
        var input = randomData(random);
        var context = "iteration " + iteration;
        var nowrap = random.nextInt(4) == 0;
        var deflater = new Deflater(random.nextInt(11) - 1, nowrap);
        if (random.nextBoolean()) {
            deflater.setStrategy(random.nextInt(3));
        }
        if (random.nextBoolean()) {
            deflater.setLevel(random.nextInt(11) - 1);
        }
        var out = new byte[input.length * 2 + 1000];
        var length = 0;
        var fed = 0;
        var calls = 0;
        var maxCalls = 20 * input.length + 2000;
        var finishing = false;
        // The JDK applies a pending change in place of the caller's flush; skip the sync check until it applied.
        var changed = false;
        var check = new Inflater(nowrap);
        var checked = new byte[input.length + 10];
        var checkedLength = 0;
        var checkedIn = 0;
        while (!deflater.finished()) {
            if (!finishing && deflater.needsInput()) {
                if (fed == input.length) {
                    deflater.finish();
                    finishing = true;
                } else {
                    var n = Math.min(input.length - fed, random.nextInt(3) == 0 ? random.nextInt(20)
                            : random.nextInt(3000));
                    deflater.setInput(input, fed, n);
                    fed += n;
                }
            }
            if (!finishing) {
                var p = random.nextInt(100);
                if (p < 15) {
                    deflater.setLevel(random.nextInt(11) - 1);
                    changed = true;
                } else if (p < 22) {
                    deflater.setStrategy(random.nextInt(3));
                    changed = true;
                }
            }
            var size = OUT_SIZES[random.nextInt(OUT_SIZES.length)];
            size = Math.min(size, out.length - length);
            var flush = FLUSHES[random.nextInt(FLUSHES.length)];
            var n = flush < 0 ? deflater.deflate(out, length, size) : deflater.deflate(out, length, size, flush);
            assertTrue(n >= 0 && n <= size, context);
            length += n;
            assertTrue(++calls <= maxCalls, context + ": more than " + maxCalls + " deflate calls");
            assertEquals(length, deflater.getBytesWritten(), context);
            assertEquals(length, deflater.getTotalOut(), context);
            if (n < size) {
                if (flush == Deflater.SYNC_FLUSH && !changed && !finishing && deflater.needsInput()) {
                    check.setInput(out, checkedIn, length - checkedIn);
                    checkedIn = length;
                    int k;
                    while ((k = check.inflate(checked, checkedLength, checked.length - checkedLength)) > 0) {
                        checkedLength += k;
                    }
                    assertEquals(fed, checkedLength, context + ": decoded after SYNC_FLUSH");
                }
                changed = false;
            }
        }
        assertEquals(input.length, deflater.getBytesRead(), context);
        assertArrayEquals(input, inflate(Arrays.copyOf(out, length), input.length, nowrap), context);
    }

    private static final int[] OUT_SIZES = { 1, 2, 7, 1, 2, 7, 64, 500, 5000 };
    private static final int[] FLUSHES = { -1, -1, Deflater.NO_FLUSH, Deflater.SYNC_FLUSH, Deflater.FULL_FLUSH };

    private static byte[] randomData(Random random) {
        var length = random.nextInt(4) == 0 ? random.nextInt(20) : random.nextInt(6000);
        var result = new byte[length];
        var kind = random.nextInt(4);
        for (var i = 0; i < length; ++i) {
            switch (kind) {
                case 0:
                    // incompressible
                    result[i] = (byte) random.nextInt();
                    break;
                case 1:
                    result[i] = (byte) ('a' + random.nextInt(4));
                    break;
                case 2:
                    result[i] = (byte) (i % 17 < 10 ? 'x' : random.nextInt());
                    break;
                default:
                    result[i] = (byte) (random.nextInt(100) < 3 || i == 0 ? random.nextInt() : result[i - 1]);
                    break;
            }
        }
        return result;
    }

    // Streams that TeaVM's Deflater wrote for pseudoRandom(600, 16, 7), with level and strategy changes and
    // 1-, 2- and 7-byte output buffers, including the flush of a function change. On the JVM, this checks that
    // the JDK's Inflater accepts them.
    @Test
    public void teaVmStreamsWithParameterChangesInflate() throws DataFormatException {
        var input = pseudoRandom(600, 16, 7);
        for (var hex : TEAVM_STREAMS) {
            var stream = new byte[hex.length() / 2];
            for (var i = 0; i < stream.length; ++i) {
                stream[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            var inflater = new Inflater();
            inflater.setInput(stream);
            var result = new byte[700];
            var length = 0;
            while (!inflater.finished()) {
                var n = inflater.inflate(result, length, result.length - length);
                assertTrue(n > 0 || inflater.finished(), "stream ends early");
                length += n;
            }
            assertEquals(0, inflater.getRemaining());
            assertArrayEquals(input, Arrays.copyOf(result, length));
        }
    }

    private static final String[] TEAVM_STREAMS = {
        // level 6, then 1, 9 and 0 before calls 3, 5 and 7; output in 1, 2 and 7 byte steps
        "789c1591090a443108435dbf75bfff6dc7a114446c93676a845869109b879afc1b29b668308c4f874298df139bea998140dc"
            + "4e1c62f97af409c20b2e05290f1065cc84777d1f73d7bbf3003e23d72dd2ca951e4aacaad5bd2a5f96ae7eb69f47468aee62"
            + "21d1e37c46bb1a962322fc15b6e7271ebd3b7216eb0d4c97670cc77b098c3449af8f209f2efbca784476d67ebcadee827416"
            + "3feda2512e7990284bdf219cae3f58aab906577d5e732066f3657a7f2a1b16e9d46ba254eb53769c8cb5387647b963214955"
            + "4a031226d078d43e14df366f369ce5c698293a12e72020fc081bc1e01edf3ae296247c600a655008e8ddd1ef639c16fc9af5"
            + "1fcda5022114cfc0823cbbb2585443e9ca4f8b43524e7f6fd0003fd86721f882f89c0c94aab76dacdd5f1e5596ac79b20dcc"
            + "2d975c84e799ff73649a255c382b1549d25bcb69e3fccabed1997d5c3c83fcb07f75f1124e",
        // level 9, then 0, 9 and 3, with HUFFMAN_ONLY, FILTERED and the default strategy; 7-byte output
        "78da1491090a443108435dbf75bfff6dc7a114446c93676a845869109b879afc1b29b668308c4f874298df139bea998140dc"
            + "4e1c62f97af409c20b2e05290f1065cc84777d1f73d7bbf3003e23d72dd2ca951e4aacaad5bd2a5f96ae7eb69f47468aee62"
            + "21d1e37c46bb1a962322fc15b6e7271ebd3b7216eb0d4c97670cc77b098c3449af8f209f2efbca784476d67ebcadee827416"
            + "3feda2512e7990284bdf219cae3f58aab906577d5e732066f3657a7f2a1b16e9d46ba254eb53769c8cb5387647b963214955"
            + "4a031226d078d43e14df366f369ce5c698293a12e72020fc081bc1e01edf3ae296247c600a655008e8ddd1ef639c16fc9af5"
            + "1fcda5022114cfc0823cbbb2585443e9ca4f8b43524e7f6fd0003fd86721f882f89c0c94aab76dacdd5f1e5596ac79b20dcc"
            + "2d975c84e799ff73649a255c382b1549d25bcb69e3fccabed1997d5c3c83fcb07f02040000ffff75f1124e",
        // level 0, then 6, 0, 2, 0 and 5, with HUFFMAN_ONLY; output in 2 and 1 byte steps
        "78da04c187010020080020676efdffdba05788951671786928de4ab3e580613e5d4a617617db9edd8544bc295c6279b3ea82"
            + "e0c9ad201d09a28c55e0ea126b116a11eb00cf28f49ab4eb64960abbfbf496aabc5a4f9fdd8bac2cd13b6c24722e37bad3b4"
            + "5a11e1d738514f22e76e2507da17763a2a97d3bd8091b6c8a7d9caf5384e36326baaeff18d460812563d9da6556e712894a3"
            + "d70a871d0e47bd72c4dd2f7a5dc26c5f55cc53b9b4aca03913a5bed8b65863ecc3b5b5559e3c285295d684824d345eb58712"
            + "3716c3867b3c98bb4d91139c04848f70100c0eaa5af36484a743a10d1a016326c71fe38ee01bd6a74b2e0629946e604951d3"
            + "d52caaa9345d4f9b534a8cf7c4c0001f9c5b0a7a12c7f042abc6d8e519b944765bb156870e308fac4366943d844dac322bb8"
            + "c393ce2299ebe3b20df6b6b7ba7bcecdbbc88ef305080000ffff75f1124e"
    };

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
        return pseudoRandom(length, alphabet, 42);
    }

    private static byte[] pseudoRandom(int length, int alphabet, int seed) {
        var result = new byte[length];
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
        return inflate(compressed, maxLength, false);
    }

    private static byte[] inflate(byte[] compressed, int maxLength, boolean nowrap) throws DataFormatException {
        var inflater = new Inflater(nowrap);
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
