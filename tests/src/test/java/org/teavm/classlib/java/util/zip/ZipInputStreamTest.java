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
package org.teavm.classlib.java.util.zip;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.teavm.classlib.java.util.zip.ZipTestUtil.readHex;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * Multi-entry zips and multi-member gzip files, and reuse of an Inflater or a Deflater after reset().
 */
@TeaVMTest
public class ZipInputStreamTest {
    // Written by the JDK 25 ZipOutputStream and GZIPOutputStream from content(...) below. JDK_ZIP_ENTRIES lists the
    // entries. dir/d.txt and f.txt are DEFLATED with sizes and CRC set before putNextEntry, so they have no data
    // descriptor. The other DEFLATED entries have one. JDK_GZIP is three concatenated members, the second one empty.
    private static final String JDK_ZIP = ""
            + "504b0304140008080800544b2d5100000000000000000000000005000000612e747874edccb90d002110c0c05a59e078afff"
            + "1409f740e4296022ae8c828a0f0d1d03130b1b3f12c2dadadadadadafa4d7d00504b0708841303a02f000000d0070000504b"
            + "0304140008080800544b2d5100000000000000000000000005000000622e62696e012c01d3fe220f0f83d9cd5d153c6c8b4b"
            + "82c497289e6697e0f95581ea588eaeb6e508d0b7117da703091b7fb0dd441f6f5b5a163c3d4dcb51b67098250f94618f29d1"
            + "be6111174fee80c28e63b931405706a3bb0b1a140ca2000a6e30c8695466ae5253a2aeef595d38272a83ef5332ddd04e90b5"
            + "184d088a307dad42ff1f7d7301501453709116c6b4a2aeea9d1825a45dabd1f6ba39f1dcae8c0737518b3b003b94e416d71a"
            + "62087174f1aa5a09c409b016568db16a3b1017dd1bdf9632c36af6efe49f7fb2f478358fd43020a2c998a6c359b967742cec"
            + "5eb12b61b2245b64eca2ee27b2911633927bec4b127b37f84c700962fcfba4ff3763e40ce2b61c805201355cba98b80551e2"
            + "52da71a9da1e5d4aa2195151da7125089f46bd88c9c73d741ba92dc952338e2f465094cdf3fd504b07082681741a31010000"
            + "2c010000504b03040a0000080000544b2d51b857acba580200005802000005000000632e7478746464646464646465656565"
            + "656565666666666666666767676767676768686868686868696969696969696a6a6a6a6a6a6a6b6b6b6b6b6b6b6c6c6c6c6c"
            + "6c6c6d6d6d6d6d6d6d6161616161616162626262626262636363636363636464646464646465656565656565666666666666"
            + "666767676767676768686868686868696969696969696a6a6a6a6a6a6a6b6b6b6b6b6b6b6c6c6c6c6c6c6c6d6d6d6d6d6d6d"
            + "6161616161616162626262626262636363636363636464646464646465656565656565666666666666666767676767676768"
            + "686868686868696969696969696a6a6a6a6a6a6a6b6b6b6b6b6b6b6c6c6c6c6c6c6c6d6d6d6d6d6d6d616161616161616262"
            + "6262626262636363636363636464646464646465656565656565666666666666666767676767676768686868686868696969"
            + "696969696a6a6a6a6a6a6a6b6b6b6b6b6b6b6c6c6c6c6c6c6c6d6d6d6d6d6d6d616161616161616262626262626263636363"
            + "6363636464646464646465656565656565666666666666666767676767676768686868686868696969696969696a6a6a6a6a"
            + "6a6a6b6b6b6b6b6b6b6c6c6c6c6c6c6c6d6d6d6d6d6d6d616161616161616262626262626263636363636363646464646464"
            + "6465656565656565666666666666666767676767676768686868686868696969696969696a6a6a6a6a6a6a6b6b6b6b6b6b6b"
            + "6c6c6c6c6c6c6c6d6d6d6d6d6d6d616161616161616262626262626263636363636363646464646464646565656565656566"
            + "6666666666666767676767676768686868686868696969696969696a6a6a6a6a6a6a6b6b6b6b6b504b030414000808080054"
            + "4b2d510000000000000000000000000e000000656d7074792d6465666c617465640300504b07080000000002000000000000"
            + "00504b03040a0000080000544b2d510000000000000000000000000c000000656d7074792d73746f726564504b03040a0000"
            + "080000544b2d51000000000000000000000000040000006469722f504b0304140000080800544b2d51e8ef83e13700000088"
            + "130000090000006469722f642e747874edcc47118040000031ad94a3e3ffcb0c6b231190317e5bf61c3973e5ce933753e62c"
            + "5933d46ab55aad56abd56ab55aad56abd56a75f507504b03040a0000080000544b2d51a5c122db9001000090010000050000"
            + "00652e62696e9b8023239b17fa134956d800c7bfc57a0790e06099e1a1586474896a9c299adce06f1c27744b9484c73c36e7"
            + "c55dd98cffed6913a29f70a1f4679f490096161eb799d142b819048c05da9e795580ef624b4c911076f37bf01bfc943f25e7"
            + "139dacddb405b8110aa6b8b851cb84baf606870bc41d099f56be659938fadc1f6024aab228ed01149eb035c04a455ea37b5f"
            + "6e4d95cc2478f125ab9916744be50983988d3f882f32b6ef82418a1fab01f5edab79936ac8b29d21088268041836a5248db0"
            + "dc46d9244d9472f16a666c8ab1bae2b3c0ca72bc4710dfc7abf93cadef604d95caeec0af0f712368bd98b85fdea73361c15c"
            + "ca0c6b86712345d40886d16869fd54c40c8593b14d37630b4fcd708526299f3844fa2122a279ee2086f0c3f108b4981527b4"
            + "d012d6d6c2bd9a64a0f8e6d2e56ca12ba85b40f5e8e7dd9b4d4b87d1d25765cad974ebd45a14901884f3465e41ae0f7b94b5"
            + "63b1ec64fcbfd00e3845ce3a40e421636aab99bd3ed7fa4da840e7e4cfaf442cd24fcca57a9fbc89267d1dd257b1ea797bcf"
            + "3d47eedbe1d0504b0304140000080800544b2d51761bd401030000000100000005000000662e7478744b0700504b03041400"
            + "08080800544b2d5100000000000000000000000005000000672e747874edccb90d002110c0c05af917b8eb3f45c22d107a0a"
            + "98886b6261e3c38f848c828a868e81b0b6b6b6b6b6b6b6b6b6b67e531f504b070888922e5e32000000b80b0000504b010214"
            + "00140008080800544b2d51841303a02f000000d0070000050000000000000000000000000000000000612e747874504b0102"
            + "1400140008080800544b2d512681741a310100002c010000050000000000000000000000000062000000622e62696e504b01"
            + "020a000a0000080000544b2d51b857acba58020000580200000500000000000000000000000000c6010000632e747874504b"
            + "01021400140008080800544b2d510000000002000000000000000e0000000000000000000000000041040000656d7074792d"
            + "6465666c61746564504b01020a000a0000080000544b2d510000000000000000000000000c00000000000000000000000000"
            + "7f040000656d7074792d73746f726564504b01020a000a0000080000544b2d51000000000000000000000000040000000000"
            + "0000000000000000a90400006469722f504b01021400140000080800544b2d51e8ef83e13700000088130000090000000000"
            + "0000000000000000cb0400006469722f642e747874504b01020a000a0000080000544b2d51a5c122db900100009001000005"
            + "0000000000000000000000000029050000652e62696e504b01021400140000080800544b2d51761bd4010300000001000000"
            + "0500000000000000000000000000dc060000662e747874504b01021400140008080800544b2d5188922e5e32000000b80b00"
            + "00050000000000000000000000000002070000672e747874504b0506000000000a000a0011020000670700000000";
    private static final String JDK_GZIP = ""
            + "1f8b08000000000000ffedcc49110020100341addc1b0eff5faa180118480b68e99958d83848c828a868e81808c8b56bd7ff"
            + "fa02158c93b7840300001f8b08000000000000ff030000000000000000001f8b08000000000000ff01c80037fff06d79796d"
            + "5c72374522f5104c5d0610fd56bd69aaba71e67e8c6ca5f8aaf57c24f76905ce80e39f9579edf3722bdbfa5c458224105cd6"
            + "8b3e0f2a2c1c77b836301f23e69eda9b4934616d567c6bf72395f8cfa9d55a9f9f06bc3cf70c56509a0ffb0a451e81a50f16"
            + "d58c644a92c6dc57bbbacb1843b283e2c313de0bd4d8dcf46b355322f307dc8b1834383190a7aafeb340dc0248f7f93f77b3"
            + "e91189fc4083f653f214473d0e240a93ea23d95068c6f5715bc335acfd8f9dc496aef56d70a5b5527702e23d662fe641c0c8"
            + "000000";

    private static final Spec[] JDK_ZIP_ENTRIES = {
            new Spec("a.txt", ZipEntry.DEFLATED, content(1, 2000, true), true),
            new Spec("b.bin", ZipEntry.DEFLATED, content(2, 300, false), true),
            new Spec("c.txt", ZipEntry.STORED, content(3, 600, true), false),
            new Spec("empty-deflated", ZipEntry.DEFLATED, new byte[0], true),
            new Spec("empty-stored", ZipEntry.STORED, new byte[0], false),
            new Spec("dir/", ZipEntry.STORED, new byte[0], false),
            new Spec("dir/d.txt", ZipEntry.DEFLATED, content(4, 5000, true), false),
            new Spec("e.bin", ZipEntry.STORED, content(5, 400, false), false),
            new Spec("f.txt", ZipEntry.DEFLATED, content(6, 1, true), false),
            new Spec("g.txt", ZipEntry.DEFLATED, content(7, 3000, true), true),
    };

    @Test
    public void secondDeflatedEntryOfTeaVMZipIsRead() throws IOException {
        var specs = List.of(
                new Spec("first", ZipEntry.DEFLATED, content(20, 1000, true), true),
                new Spec("second", ZipEntry.DEFLATED, content(21, 1000, true), true));
        var zip = writeWithZipOutputStream(specs, new Random(0), false);
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var spec : specs) {
                assertEquals(spec.name, in.getNextEntry().getName());
                assertArrayEquals(spec.data, in.readAllBytes());
            }
            assertNull(in.getNextEntry());
        }
    }

    @Test
    public void zipsWrittenByZipOutputStreamRoundTrip() throws IOException {
        for (int seed = 0; seed < 120; ++seed) {
            var rnd = new Random(seed);
            var specs = randomSpecs(rnd, false);
            var zip = writeWithZipOutputStream(specs, rnd, true);
            readAndCheck(zip, specs, rnd, "seed " + seed);
        }
    }

    @Test
    public void zipsWithAndWithoutDataDescriptorsRoundTrip() throws IOException {
        for (int seed = 0; seed < 120; ++seed) {
            var rnd = new Random(1000 + seed);
            var specs = randomSpecs(rnd, true);
            var zip = writeByHand(specs, rnd);
            readAndCheck(zip, specs, rnd, "seed " + seed);
        }
    }

    @Test
    public void zipWrittenByJdkIsRead() throws IOException {
        var zip = readHex(JDK_ZIP);
        var specs = Arrays.asList(JDK_ZIP_ENTRIES);
        for (int seed = 0; seed < 40; ++seed) {
            readAndCheck(zip, specs, new Random(seed), "seed " + seed);
        }
        // Every entry read to the end, and every entry skipped without reading.
        for (int mode : new int[] { MODE_FULL, MODE_NONE, MODE_SINGLE_BYTES }) {
            try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
                for (var spec : specs) {
                    var entry = in.getNextEntry();
                    assertNotNull(entry, spec.name);
                    assertEquals(spec.name, entry.getName());
                    readEntry(in, spec, mode, new Random(mode), spec.name);
                }
                assertNull(in.getNextEntry());
            }
        }
    }

    @Test
    public void concatenatedGzipMembersWrittenByJdkAreRead() throws IOException {
        // RFC 1952, 2.2: "A gzip file consists of a series of "members" (compressed data sets)."
        var expected = concat(content(8, 900, true), content(10, 200, false));
        var gzip = readHex(JDK_GZIP);
        for (int bufSize : new int[] { 1, 7, 64, 512, 4096 }) {
            for (int seed = 0; seed < 5; ++seed) {
                var rnd = new Random(seed);
                var in = new GZIPInputStream(new ChunkedInputStream(gzip, rnd), bufSize);
                assertArrayEquals(expected, readRandomly(in, rnd), "buffer " + bufSize + ", seed " + seed);
                assertEquals(-1, in.read());
            }
        }
    }

    @Test
    public void concatenatedGzipMembersRoundTrip() throws IOException {
        for (int seed = 0; seed < 80; ++seed) {
            var rnd = new Random(seed);
            var gzip = new ByteArrayOutputStream();
            var expected = new ByteArrayOutputStream();
            int members = 1 + rnd.nextInt(5);
            for (int i = 0; i < members; ++i) {
                var data = content(rnd.nextInt(), randomSize(rnd), rnd.nextBoolean());
                expected.write(data);
                var member = new ByteArrayOutputStream();
                try (var out = new GZIPOutputStream(member)) {
                    writeInChunks(out, data, rnd);
                }
                gzip.write(member.toByteArray());
            }
            int bufSize = 1 + rnd.nextInt(rnd.nextBoolean() ? 16 : 2048);
            var in = new GZIPInputStream(new ChunkedInputStream(gzip.toByteArray(), rnd), bufSize);
            assertArrayEquals(expected.toByteArray(), readRandomly(in, rnd), "seed " + seed);
            assertEquals(-1, in.read());
        }
    }

    @Test
    public void inflaterResetDiscardsUnreadInput() throws DataFormatException {
        // Inflater.reset(): "Resets inflater so that a new set of input data can be processed."
        var firstData = content(11, 1000, true);
        var secondData = content(12, 1000, false);
        var first = zlib(firstData);
        var second = zlib(secondData);
        var input = concat(first, second);
        var inf = new Inflater();
        var out = new byte[3000];

        inf.setInput(input);
        assertEquals(firstData.length, inflateToEnd(inf, out));
        assertArrayEquals(firstData, Arrays.copyOf(out, firstData.length));
        assertEquals(second.length, inf.getRemaining());
        inf.reset();
        checkReset(inf, out);
        inf.setInput(input, first.length, second.length);
        assertEquals(secondData.length, inflateToEnd(inf, out));
        assertArrayEquals(secondData, Arrays.copyOf(out, secondData.length));

        // Reset in the middle of a stream.
        inf.reset();
        inf.setInput(input);
        assertTrue(inf.inflate(out, 0, 10) > 0);
        assertFalse(inf.needsInput());
        inf.reset();
        checkReset(inf, out);
        inf.setInput(first);
        assertEquals(firstData.length, inflateToEnd(inf, out));
        assertArrayEquals(firstData, Arrays.copyOf(out, firstData.length));
        inf.end();
    }

    private static void checkReset(Inflater inf, byte[] out) throws DataFormatException {
        assertTrue(inf.needsInput());
        assertEquals(0, inf.getRemaining());
        assertEquals(0, inf.inflate(out));
        assertFalse(inf.finished());
        assertEquals(0, inf.getBytesRead());
        assertEquals(0, inf.getBytesWritten());
    }

    @Test
    public void deflaterResetDiscardsUnreadInput() throws DataFormatException {
        // Deflater.reset(): "Resets deflater so that a new set of input data can be processed."
        var def = new Deflater();
        def.setInput(content(13, 200000, false));
        var out = new byte[100];
        assertEquals(out.length, def.deflate(out));
        assertFalse(def.needsInput());
        def.reset();
        assertTrue(def.needsInput());
        def.finish();
        var compressed = new ByteArrayOutputStream();
        while (!def.finished()) {
            compressed.write(out, 0, def.deflate(out));
        }
        def.end();
        var inf = new Inflater();
        inf.setInput(compressed.toByteArray());
        assertEquals(0, inflateToEnd(inf, new byte[100]));
        assertTrue(inf.finished());
    }

    @Test
    public void inflaterInputStreamReusesAResetInflater() throws IOException {
        var inf = new Inflater();
        var junk = new byte[50];
        Arrays.fill(junk, (byte) 0x55);
        for (int i = 0; i < 4; ++i) {
            var data = content(14 + i, 500 + i * 1000, i % 2 == 0);
            var in = new InflaterInputStream(new ByteArrayInputStream(concat(zlib(data), junk)), inf, 64);
            assertArrayEquals(data, in.readAllBytes(), "stream " + i);
            inf.reset();
        }
        inf.end();
    }

    @Test
    public void skipKeepsTheCompressedInput() throws IOException {
        // InflaterInputStream.skip: "Skips specified number of bytes of uncompressed data."
        for (int seed = 0; seed < 60; ++seed) {
            var rnd = new Random(seed);
            var data = content(rnd.nextInt(), randomSize(rnd), rnd.nextBoolean());
            boolean gzip = rnd.nextBoolean();
            byte[] compressed;
            if (gzip) {
                var bytes = new ByteArrayOutputStream();
                try (var out = new GZIPOutputStream(bytes)) {
                    out.write(data);
                }
                compressed = bytes.toByteArray();
            } else {
                compressed = zlib(data);
            }
            var source = new ChunkedInputStream(compressed, rnd);
            int bufSize = 1 + rnd.nextInt(600);
            InputStream in = gzip ? new GZIPInputStream(source, bufSize)
                    : new InflaterInputStream(source, new Inflater(), bufSize);
            int n = rnd.nextInt(data.length + 1);
            var label = "seed " + seed;
            assertEquals(n, in.skip(n), label);
            assertArrayEquals(Arrays.copyOfRange(data, n, data.length), readFully(in, 1 + rnd.nextInt(600)), label);
        }
    }

    private static final int MODE_FULL = 0;
    private static final int MODE_SINGLE_BYTES = 1;
    private static final int MODE_PREFIX = 2;
    private static final int MODE_SKIP = 3;
    private static final int MODE_NONE = 4;

    private static void readAndCheck(byte[] zip, List<Spec> specs, Random rnd, String label) throws IOException {
        InputStream source = rnd.nextBoolean() ? new ByteArrayInputStream(zip) : new ChunkedInputStream(zip, rnd);
        try (var in = new ZipInputStream(source)) {
            for (int i = 0; i < specs.size(); ++i) {
                var spec = specs.get(i);
                var entryLabel = label + ", entry " + i;
                var entry = in.getNextEntry();
                assertNotNull(entry, entryLabel);
                assertEquals(spec.name, entry.getName(), entryLabel);
                assertEquals(spec.method, entry.getMethod(), entryLabel);
                readEntry(in, spec, rnd.nextInt(5), rnd, entryLabel);
            }
            assertNull(in.getNextEntry(), label);
        }
    }

    private static void readEntry(ZipInputStream in, Spec spec, int mode, Random rnd, String label)
            throws IOException {
        var data = spec.data;
        switch (mode) {
            case MODE_FULL:
                assertArrayEquals(data, readFully(in, 1 + rnd.nextInt(4096)), label);
                assertEquals(-1, in.read(), label);
                break;
            case MODE_SINGLE_BYTES: {
                for (int j = 0; j < data.length; ++j) {
                    assertEquals(data[j] & 0xFF, in.read(), label + ", byte " + j);
                }
                assertEquals(-1, in.read(), label);
                break;
            }
            case MODE_PREFIX: {
                int n = rnd.nextInt(data.length + 1);
                var prefix = new byte[n];
                int read = 0;
                while (read < n) {
                    int r = in.read(prefix, read, n - read);
                    assertTrue(r > 0, label);
                    read += r;
                }
                assertArrayEquals(Arrays.copyOf(data, n), prefix, label);
                break;
            }
            case MODE_SKIP: {
                int n = rnd.nextInt(data.length + 1);
                assertEquals(n, in.skip(n), label);
                assertArrayEquals(Arrays.copyOfRange(data, n, data.length), readFully(in, 1 + rnd.nextInt(600)),
                        label);
                break;
            }
            default:
                break;
        }
    }

    private static byte[] readFully(InputStream in, int bufSize) throws IOException {
        var result = new ByteArrayOutputStream();
        var buf = new byte[bufSize];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                break;
            }
            result.write(buf, 0, n);
        }
        return result.toByteArray();
    }

    private static byte[] readRandomly(InputStream in, Random rnd) throws IOException {
        var result = new ByteArrayOutputStream();
        var buf = new byte[4096];
        while (true) {
            if (rnd.nextInt(8) == 0) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                result.write(b);
            } else {
                int n = in.read(buf, 0, 1 + rnd.nextInt(buf.length));
                if (n < 0) {
                    break;
                }
                result.write(buf, 0, n);
            }
        }
        return result.toByteArray();
    }

    private static List<Spec> randomSpecs(Random rnd, boolean withoutDescriptors) {
        var specs = new ArrayList<Spec>();
        int count = 1 + rnd.nextInt(8);
        for (int i = 0; i < count; ++i) {
            int method = rnd.nextInt(3) == 0 ? ZipEntry.STORED : ZipEntry.DEFLATED;
            var data = content(rnd.nextInt(), randomSize(rnd), rnd.nextBoolean());
            boolean descriptor = method == ZipEntry.DEFLATED && (!withoutDescriptors || rnd.nextBoolean());
            specs.add(new Spec("entry" + i + (method == ZipEntry.STORED ? ".stored" : ".deflated"), method, data,
                    descriptor));
        }
        return specs;
    }

    private static int randomSize(Random rnd) {
        switch (rnd.nextInt(6)) {
            case 0:
                return 0;
            case 1:
                return 1 + rnd.nextInt(16);
            case 2:
                return rnd.nextInt(600);
            case 3:
                // Around the 512-byte input buffer of ZipInputStream.
                return 400 + rnd.nextInt(700);
            case 4:
                return rnd.nextInt(5000);
            default:
                return rnd.nextInt(20000);
        }
    }

    private static byte[] writeWithZipOutputStream(List<Spec> specs, Random rnd, boolean randomLevels)
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            for (var spec : specs) {
                if (randomLevels) {
                    out.setLevel(rnd.nextInt(10));
                }
                var entry = new ZipEntry(spec.name);
                entry.setMethod(spec.method);
                if (spec.method == ZipEntry.STORED) {
                    entry.setSize(spec.data.length);
                    entry.setCompressedSize(spec.data.length);
                    entry.setCrc(crc(spec.data));
                }
                out.putNextEntry(entry);
                writeInChunks(out, spec.data, rnd);
                if (rnd.nextBoolean()) {
                    out.closeEntry();
                }
            }
        }
        return bytes.toByteArray();
    }

    // A minimal zip writer, so that DEFLATED entries can come with or without a data descriptor.
    private static byte[] writeByHand(List<Spec> specs, Random rnd) {
        var out = new ByteArrayOutputStream();
        var central = new ByteArrayOutputStream();
        for (var spec : specs) {
            int offset = out.size();
            var name = spec.name.getBytes(StandardCharsets.UTF_8);
            int crc = (int) crc(spec.data);
            var body = spec.method == ZipEntry.STORED ? spec.data : deflateRaw(spec.data, rnd.nextInt(10));
            int flags = spec.descriptor ? 8 : 0;

            writeInt(out, 0x04034b50);
            writeShort(out, 20);
            writeShort(out, flags);
            writeShort(out, spec.method);
            writeShort(out, 0);
            writeShort(out, 0x21);
            writeInt(out, spec.descriptor ? 0 : crc);
            writeInt(out, spec.descriptor ? 0 : body.length);
            writeInt(out, spec.descriptor ? 0 : spec.data.length);
            writeShort(out, name.length);
            writeShort(out, 0);
            out.write(name, 0, name.length);
            out.write(body, 0, body.length);
            if (spec.descriptor) {
                writeInt(out, 0x08074b50);
                writeInt(out, crc);
                writeInt(out, body.length);
                writeInt(out, spec.data.length);
            }

            writeInt(central, 0x02014b50);
            writeShort(central, 20);
            writeShort(central, 20);
            writeShort(central, flags);
            writeShort(central, spec.method);
            writeShort(central, 0);
            writeShort(central, 0x21);
            writeInt(central, crc);
            writeInt(central, body.length);
            writeInt(central, spec.data.length);
            writeShort(central, name.length);
            writeShort(central, 0);
            writeShort(central, 0);
            writeShort(central, 0);
            writeShort(central, 0);
            writeInt(central, 0);
            writeInt(central, offset);
            central.write(name, 0, name.length);
        }
        int centralOffset = out.size();
        var centralBytes = central.toByteArray();
        out.write(centralBytes, 0, centralBytes.length);
        writeInt(out, 0x06054b50);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, specs.size());
        writeShort(out, specs.size());
        writeInt(out, centralBytes.length);
        writeInt(out, centralOffset);
        writeShort(out, 0);
        return out.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >>> 8);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        writeShort(out, value);
        writeShort(out, value >>> 16);
    }

    private static void writeInChunks(OutputStream out, byte[] data, Random rnd) throws IOException {
        int pos = 0;
        while (pos < data.length) {
            int n = Math.min(data.length - pos, 1 + rnd.nextInt(3000));
            out.write(data, pos, n);
            pos += n;
        }
    }

    private static long crc(byte[] data) {
        var crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static byte[] deflateRaw(byte[] data, int level) {
        return deflate(data, level, true);
    }

    private static byte[] zlib(byte[] data) {
        return deflate(data, Deflater.DEFAULT_COMPRESSION, false);
    }

    private static byte[] deflate(byte[] data, int level, boolean nowrap) {
        var def = new Deflater(level, nowrap);
        def.setInput(data);
        def.finish();
        var out = new ByteArrayOutputStream();
        var buf = new byte[1024];
        while (!def.finished()) {
            out.write(buf, 0, def.deflate(buf));
        }
        def.end();
        return out.toByteArray();
    }

    private static int inflateToEnd(Inflater inf, byte[] out) throws DataFormatException {
        int n = 0;
        while (!inf.finished()) {
            int r = inf.inflate(out, n, out.length - n);
            assertTrue(r > 0 || inf.finished(), "no progress");
            n += r;
        }
        return n;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        var result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // Must match the generator of JDK_ZIP and JDK_GZIP.
    private static byte[] content(int seed, int size, boolean compressible) {
        var data = new byte[size];
        int x = seed * 0x9E3779B1 + 1;
        for (int i = 0; i < size; ++i) {
            if (compressible) {
                data[i] = (byte) ('a' + (i / 7 + seed) % 13);
            } else {
                x ^= x << 13;
                x ^= x >>> 17;
                x ^= x << 5;
                data[i] = (byte) x;
            }
        }
        return data;
    }

    private static final class Spec {
        final String name;
        final int method;
        final byte[] data;
        final boolean descriptor;

        Spec(String name, int method, byte[] data, boolean descriptor) {
            this.name = name;
            this.method = method;
            this.data = data;
            this.descriptor = descriptor;
        }
    }

    // Returns at most a random number of bytes from each read, so that the readers see short reads.
    private static class ChunkedInputStream extends ByteArrayInputStream {
        private final Random rnd;
        private final int maxChunk;

        ChunkedInputStream(byte[] data, Random rnd) {
            super(data);
            this.rnd = new Random(rnd.nextLong());
            int[] chunks = { 1, 7, 100, 1000 };
            maxChunk = chunks[rnd.nextInt(chunks.length)];
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, Math.min(len, 1 + rnd.nextInt(maxChunk)));
        }
    }
}
