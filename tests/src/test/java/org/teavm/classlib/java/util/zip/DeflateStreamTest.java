/*
 *  Copyright 2014 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.teavm.classlib.java.util.zip.ZipTestUtil.readHex;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class DeflateStreamTest {
    private static final String longString = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
            + "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
            + "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea "
            + "commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse "
            + "cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, "
            + "sunt in culpa qui officia deserunt mollit anim id est laborum.";

    @Test
    public void gzipInputWorks() throws IOException {
        byte[] data = readHex("1f8b08086c1d59540003746561766d2d7a6970000b494d0cf355708ff20c5028cf2fca2e5648cbcc"
                + "4b05005727a59115000000");
        GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(data));
        byte[] uncompressed = new byte[500];
        int length = readFully(input, uncompressed);
        assertEquals(21, length);
        assertEquals("TeaVM GZIP works fine", new String(uncompressed, 0, 21));
    }

    @Test
    public void zipInputWorks() throws IOException {
        byte[] data = readHex("504b03040a00000000002e7c695220303a36060000000600000008000000746573742e74787468656c6c"
                + "6f0a504b01023f030a00000000002e7c695220303a360600000006000000080000000000000000000000b48100000000"
                + "746573742e747874504b05060000000001000100360000002c0000000000");
        ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry entry = input.getNextEntry();
        assertEquals("test.txt", entry.getName());
        var uncompressed = input.readAllBytes();
        assertEquals("hello\n", new String(uncompressed));
        assertNull(input.getNextEntry());
    }

    @Test
    public void zipOutputWorks() throws IOException {
        ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
        ZipOutputStream zipOutput = new ZipOutputStream(byteArrayOutput);
        zipOutput.putNextEntry(new ZipEntry("test.txt"));
        zipOutput.write(longString.getBytes(StandardCharsets.UTF_8));
        zipOutput.close();
        byte[] data = byteArrayOutput.toByteArray();

        ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry entry = input.getNextEntry();
        assertEquals("test.txt", entry.getName());
        var uncompressed = input.readAllBytes();
        assertEquals(longString, new String(uncompressed, StandardCharsets.UTF_8));
        assertNull(input.getNextEntry());
    }

    private static int readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (true) {
            int read = input.read(target, offset, target.length - offset);
            if (read <= 0) {
                break;
            }
            offset += read;
        }
        return offset;
    }

    // A second flush with nothing new to flush made zlib report Z_BUF_ERROR, which used to throw.
    @Test
    public void gzipFlushTwiceRoundTrips() throws IOException {
        var bytes = new ByteArrayOutputStream();
        var gzip = new GZIPOutputStream(bytes);
        var data = longString.getBytes(StandardCharsets.UTF_8);
        gzip.write(data, 0, 100);
        gzip.flush();
        gzip.flush();
        gzip.write(data, 100, data.length - 100);
        gzip.flush();
        gzip.close();
        var input = new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertArrayEquals(data, input.readAllBytes());
    }

    // With syncFlush = true and more pending output than the 512-byte buffer, one deflate call left the rest of the
    // flush in the deflater, so the flushed bytes did not decode to all the data written.
    @Test
    public void gzipFlushWritesAllPendingOutput() throws IOException, DataFormatException {
        var data = new byte[20000];
        var random = new Random(1);
        random.nextBytes(data);
        var bytes = new ByteArrayOutputStream();
        var gzip = new GZIPOutputStream(bytes, true);
        gzip.write(data);
        gzip.flush();
        var flushed = bytes.toByteArray();
        // Skip the 10-byte header and inflate the raw deflate data written so far.
        var inflater = new Inflater(true);
        inflater.setInput(flushed, 10, flushed.length - 10);
        var result = new byte[data.length];
        var length = 0;
        int n;
        while (length < result.length && (n = inflater.inflate(result, length, result.length - length)) > 0) {
            length += n;
        }
        assertEquals(data.length, length);
        assertArrayEquals(data, result);
        gzip.close();
        assertArrayEquals(data, new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray())).readAllBytes());
    }

    @Test
    public void streamsRoundTripWithSmallBuffersAndOneByteReads() throws IOException {
        var data = (longString + longString).getBytes(StandardCharsets.UTF_8);
        for (int bufSize : new int[] { 1, 2, 7, 512 }) {
            var bytes = new ByteArrayOutputStream();
            var out = new DeflaterOutputStream(bytes, new Deflater(), bufSize);
            for (int i = 0; i < data.length; i += 13) {
                out.write(data, i, Math.min(13, data.length - i));
            }
            out.close();
            var in = new InflaterInputStream(new OneByteInputStream(bytes.toByteArray()), new Inflater(), bufSize);
            assertArrayEquals(data, in.readAllBytes(), "buffer " + bufSize);
        }
    }

    // A stream cut short at any position must end in EOFException, not loop or return a short result.
    @Test
    public void truncatedStreamsThrowAtEveryPosition() throws IOException {
        var data = longString.getBytes(StandardCharsets.UTF_8);
        var zlibBytes = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(zlibBytes)) {
            out.write(data);
        }
        var zlib = zlibBytes.toByteArray();
        for (int len = 0; len < zlib.length; len++) {
            var truncated = Arrays.copyOf(zlib, len);
            var in = new InflaterInputStream(new ByteArrayInputStream(truncated));
            assertThrows(EOFException.class, () -> readToEnd(in), "zlib length " + len);
        }

        var gzipBytes = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(gzipBytes)) {
            out.write(data);
        }
        var gzip = gzipBytes.toByteArray();
        for (int len = 0; len < gzip.length; len++) {
            var truncated = Arrays.copyOf(gzip, len);
            assertThrows(EOFException.class, () -> readToEnd(new GZIPInputStream(new ByteArrayInputStream(truncated))),
                    "gzip length " + len);
        }

        var zipBytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(zipBytes)) {
            out.putNextEntry(new ZipEntry("test.txt"));
            out.write(data);
        }
        var zip = zipBytes.toByteArray();
        int dataStart = 30 + (zip[26] & 0xFF) + ((zip[27] & 0xFF) << 8) + (zip[28] & 0xFF) + ((zip[29] & 0xFF) << 8);
        // The entry's compressed length is what a raw inflater consumes before it finishes.
        var raw = new Inflater(true);
        raw.setInput(zip, dataStart, zip.length - dataStart);
        try {
            raw.inflate(new byte[data.length + 10]);
        } catch (DataFormatException e) {
            throw new AssertionError(e);
        }
        assertTrue(raw.finished());
        int dataEnd = dataStart + (int) raw.getBytesRead();
        for (int len = dataStart; len < dataEnd; len++) {
            var in = new ZipInputStream(new EndOnceInputStream(Arrays.copyOf(zip, len)));
            assertEquals("test.txt", in.getNextEntry().getName());
            assertThrows(EOFException.class, () -> readToEnd(in), "zip length " + len);
        }
    }

    // Reads to -1, and fails rather than spinning if read keeps returning 0.
    private static void readToEnd(InputStream in) throws IOException {
        var buf = new byte[64];
        int reads = 0;
        while (in.read(buf) != -1) {
            if (++reads > 10000) {
                throw new AssertionError("read made no progress");
            }
        }
    }

    @Test
    public void damagedZlibHeaderFailsTheStream() {
        var in = new InflaterInputStream(new ByteArrayInputStream(new byte[] { 0x78, 0x00, 0x03, 0x00 }));
        assertThrows(IOException.class, in::readAllBytes);
    }

    // Fails a reader that keeps reading after the end, so a loop that ignores the end fails instead of hanging.
    private static class EndOnceInputStream extends ByteArrayInputStream {
        private int ends;

        EndOnceInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            int n = super.read(b, off, len);
            if (n == -1 && ++ends > 100) {
                throw new AssertionError("read past the end of the input " + ends + " times");
            }
            return n;
        }
    }

    private static class OneByteInputStream extends ByteArrayInputStream {
        OneByteInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, Math.min(len, 1));
        }
    }

    // Byte 9 of the header is the OS field. RFC 1952, 2.3.1: "255 - unknown". The JDK writes 255 since JDK 16.
    // DeflaterOutputStream.flush Javadoc: without syncFlush, "this method only flushes the output stream without
    // flushing the compressor", so flush() adds nothing after the header here.
    @Test
    public void gzipOutputIsTheJdkOne() throws IOException {
        var output = new ByteArrayOutputStream();
        var gzip = new GZIPOutputStream(output);
        gzip.write("hello".getBytes(StandardCharsets.UTF_8));
        gzip.flush();
        assertEquals(10, output.size());
        gzip.close();
        assertArrayEquals(new byte[] { 31, -117, 8, 0, 0, 0, 0, 0, 0, -1, -53, 72, -51, -55, -55, 7, 0, -122, -90, 16,
                54, 5, 0, 0, 0 }, output.toByteArray());
    }

    @Test
    public void gzipSyncFlush() throws IOException {
        var output = new ByteArrayOutputStream();
        var gzip = new GZIPOutputStream(output, true);
        gzip.write("hello".getBytes(StandardCharsets.UTF_8));
        gzip.flush();
        var bytes = output.toByteArray();
        assertArrayEquals(new byte[] { 0, 0, -1, -1 }, Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length));
        gzip.close();
        try (var input = new GZIPInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals("hello", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // DeflaterOutputStream.flush Javadoc: "If syncFlush is true when this compressed output stream is constructed,
    // this method first flushes the underlying compressor with the flush mode Deflater.SYNC_FLUSH to force all
    // pending data to be flushed out to the output stream". So after every flush, the bytes written so far must
    // inflate to all the data written so far, for every level, strategy and buffer size, including buffers smaller
    // than a sync marker.
    @Test
    public void syncFlushWritesAllPendingOutput() throws IOException, DataFormatException {
        var random = new Random(2);
        var noise = new byte[3000];
        random.nextBytes(noise);
        var text = (longString + longString + longString).getBytes(StandardCharsets.UTF_8);
        int[] levels = { Deflater.NO_COMPRESSION, Deflater.BEST_SPEED, Deflater.DEFAULT_COMPRESSION,
                Deflater.BEST_COMPRESSION };
        int[] strategies = { Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY };
        for (var data : new byte[][] { noise, text }) {
            for (int bufSize : new int[] { 1, 2, 6, 7, 512 }) {
                var label = "data " + data.length + ", buffer " + bufSize;
                checkSyncFlushes(true, data, bufSize, 0, 0, label + ", gzip");
                for (int level : levels) {
                    for (int strategy : strategies) {
                        checkSyncFlushes(false, data, bufSize, level, strategy,
                                label + ", level " + level + ", strategy " + strategy);
                    }
                }
            }
        }
    }

    // A GZIPOutputStream if gzip is true, otherwise a raw DeflaterOutputStream with the given level and strategy.
    private static void checkSyncFlushes(boolean gzip, byte[] data, int bufSize, int level, int strategy, String label)
            throws IOException, DataFormatException {
        var bytes = new CappedOutputStream(data.length * 4 + 10000);
        DeflaterOutputStream out;
        int headerSize;
        if (gzip) {
            out = new GZIPOutputStream(bytes, bufSize, true);
            headerSize = 10;
        } else {
            var deflater = new Deflater(level, true);
            deflater.setStrategy(strategy);
            out = new DeflaterOutputStream(bytes, deflater, bufSize, true);
            headerSize = 0;
        }
        int written = 0;
        // Flush after an empty prefix, a single byte, mid-way and at the end, and twice in a row.
        for (int end : new int[] { 0, 1, 1, data.length / 3, data.length }) {
            out.write(data, written, end - written);
            written = end;
            out.flush();
            var flushed = bytes.toByteArray();
            var inflater = new Inflater(true);
            inflater.setInput(flushed, headerSize, flushed.length - headerSize);
            var result = new byte[written + 1];
            int length = 0;
            int n;
            while ((n = inflater.inflate(result, length, result.length - length)) > 0) {
                length += n;
            }
            assertEquals(written, length, label + ", flush at " + written);
            assertArrayEquals(Arrays.copyOf(data, written), Arrays.copyOf(result, length),
                    label + ", flush at " + written);
            assertTrue(inflater.needsInput(), label + ", flush at " + written);
        }
        out.close();
        var compressed = bytes.toByteArray();
        InputStream in = gzip
                ? new GZIPInputStream(new ByteArrayInputStream(compressed))
                : new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true));
        assertArrayEquals(data, in.readAllBytes(), label);
    }

    // DeflaterOutputStream.flush Javadoc: without syncFlush, "this method only flushes the output stream without
    // flushing the compressor".
    @Test
    public void flushWithoutSyncFlushOnlyFlushesTheStream() throws IOException {
        var data = longString.getBytes(StandardCharsets.UTF_8);
        for (int kind = 0; kind < 4; kind++) {
            var bytes = new FlushCountingOutputStream();
            DeflaterOutputStream out;
            switch (kind) {
                case 0:
                    out = new DeflaterOutputStream(bytes);
                    break;
                case 1:
                    out = new DeflaterOutputStream(bytes, false);
                    break;
                case 2:
                    out = new DeflaterOutputStream(bytes, new Deflater(), 7, false);
                    break;
                default:
                    out = new GZIPOutputStream(bytes, false);
                    break;
            }
            out.write(data);
            int size = bytes.size();
            out.flush();
            assertEquals(size, bytes.size(), "kind " + kind);
            assertEquals(1, bytes.flushes, "kind " + kind);
            out.close();
            InputStream in = kind == 3
                    ? new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))
                    : new InflaterInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            assertArrayEquals(data, in.readAllBytes(), "kind " + kind);
        }
    }

    // After finish() the compressor has finished, so a sync flush has nothing to add, and must not fail.
    @Test
    public void syncFlushAfterFinishWritesNothing() throws IOException {
        var data = longString.getBytes(StandardCharsets.UTF_8);
        for (boolean gzip : new boolean[] { false, true }) {
            var bytes = new FlushCountingOutputStream();
            DeflaterOutputStream out = gzip
                    ? new GZIPOutputStream(bytes, true)
                    : new DeflaterOutputStream(bytes, true);
            out.write(data);
            out.finish();
            var finished = bytes.toByteArray();
            out.flush();
            out.flush();
            assertArrayEquals(finished, bytes.toByteArray(), "gzip " + gzip);
            assertEquals(2, bytes.flushes, "gzip " + gzip);
            // close() ends the deflater; a flush after it must not touch the deflater either.
            out.close();
            out.flush();
            assertArrayEquals(finished, bytes.toByteArray(), "gzip " + gzip);
            InputStream in = gzip
                    ? new GZIPInputStream(new ByteArrayInputStream(finished))
                    : new InflaterInputStream(new ByteArrayInputStream(finished));
            assertArrayEquals(data, in.readAllBytes(), "gzip " + gzip);
        }
    }

    // Fails instead of hanging when a flush loop keeps writing.
    private static class CappedOutputStream extends ByteArrayOutputStream {
        private final int cap;

        CappedOutputStream(int cap) {
            this.cap = cap;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (size() + len > cap) {
                throw new AssertionError("more than " + cap + " bytes written");
            }
            super.write(b, off, len);
        }

        @Override
        public synchronized void write(int b) {
            if (size() + 1 > cap) {
                throw new AssertionError("more than " + cap + " bytes written");
            }
            super.write(b);
        }
    }

    private static class FlushCountingOutputStream extends ByteArrayOutputStream {
        int flushes;

        @Override
        public void flush() {
            flushes++;
        }
    }
}
