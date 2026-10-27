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
package org.teavm.classlib.java.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ReaderTest {
    // Sizes around the buffer sizes an implementation is likely to use, plus 0 and 1.
    private static final int[] SIZES = { 0, 1, 2, 1023, 1024, 1025, 2047, 2048, 2049, 4096, 8191, 8192, 8193,
            10003, 20000 };

    // Reader.transferTo: "Throws: NullPointerException - if out is null". This holds even when there is
    // nothing to transfer.
    @Test
    public void transferToNullThrows() {
        assertThrows(NullPointerException.class, () -> new StringReader("").transferTo(null));
        assertThrows(NullPointerException.class, () -> new ChunkedReader("", 3).transferTo(null));
        assertThrows(NullPointerException.class, () -> new StringReader("abc").transferTo(null));
        assertThrows(NullPointerException.class, () -> new ChunkedReader(text(5000), 3).transferTo(null));
    }

    // "Reads all characters from this reader and writes the characters to the given writer in the order that
    // they are read." "Returns: the number of characters transferred". "On return, this reader will be at
    // end of the stream."
    @Test
    public void transferToCopiesEverythingAndReturnsCharCount() throws IOException {
        for (var size : SIZES) {
            var text = text(size);
            var reader = new StringReader(text);
            var out = new StringWriter();
            assertEquals(size, reader.transferTo(out), "size " + size);
            assertEquals(text, out.toString(), "size " + size);
            assertEquals(-1, reader.read(), "size " + size);
        }
    }

    // The same, for a reader whose read(char[], int, int) returns fewer characters than requested.
    @Test
    public void transferToHandlesShortReads() throws IOException {
        for (var chunk : new int[] { 1, 2, 7, 1000, 2047, 2049 }) {
            for (var size : SIZES) {
                var text = text(size);
                var reader = new ChunkedReader(text, chunk);
                var out = new StringWriter();
                assertEquals(size, reader.transferTo(out), "size " + size + ", chunk " + chunk);
                assertEquals(text, out.toString(), "size " + size + ", chunk " + chunk);
                assertEquals(-1, reader.read(), "size " + size + ", chunk " + chunk);
            }
        }
    }

    // "Reads all characters from this reader": the characters that remain from the current position.
    @Test
    public void transferToStartsAtCurrentPosition() throws IOException {
        var text = text(3000);
        var reader = new StringReader(text);
        assertEquals(text.charAt(0), reader.read());
        assertEquals(text.charAt(1), reader.read());
        var out = new StringWriter();
        assertEquals(2998, reader.transferTo(out));
        assertEquals(text.substring(2), out.toString());
        assertEquals(0, reader.transferTo(out));
        assertEquals(text.substring(2), out.toString());
    }

    // "This method does not close either reader or writer."
    @Test
    public void transferToClosesNeither() throws IOException {
        for (var size : new int[] { 0, 5, 10003 }) {
            var reader = new ChunkedReader(text(size), 100);
            var out = new TrackingWriter();
            assertEquals(size, reader.transferTo(out));
            assertFalse(reader.closed, "reader closed, size " + size);
            assertFalse(out.closed, "writer closed, size " + size);
            assertEquals(text(size), out.sb.toString());
            // StringReader.read throws IOException once closed, so this also shows that it is still open.
            var stringReader = new StringReader(text(size));
            stringReader.transferTo(new StringWriter());
            assertEquals(-1, stringReader.read());
        }
    }

    private static String text(int size) {
        var sb = new StringBuilder(size);
        for (var i = 0; i < size; ++i) {
            sb.append((char) ('a' + i % 26 + (i / 26 % 3) * 0x100));
        }
        return sb.toString();
    }

    static class ChunkedReader extends Reader {
        private final String text;
        private final int chunk;
        private int pos;
        boolean closed;

        ChunkedReader(String text, int chunk) {
            this.text = text;
            this.chunk = chunk;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("closed");
            }
            if (len == 0) {
                return 0;
            }
            if (pos == text.length()) {
                return -1;
            }
            var n = Math.min(Math.min(len, chunk), text.length() - pos);
            text.getChars(pos, pos + n, cbuf, off);
            pos += n;
            return n;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static class TrackingWriter extends Writer {
        final StringBuilder sb = new StringBuilder();
        boolean closed;

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("closed");
            }
            sb.append(cbuf, off, len);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
