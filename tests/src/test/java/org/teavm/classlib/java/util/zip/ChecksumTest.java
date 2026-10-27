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

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ChecksumTest {
    private static final byte[] DATA = "hello, checksum".getBytes(StandardCharsets.US_ASCII);

    @Test
    public void updateByteBuffer() {
        for (ByteBuffer buffer : new ByteBuffer[] { ByteBuffer.allocate(DATA.length + 4),
                ByteBuffer.allocateDirect(DATA.length + 4) }) {
            buffer.put(new byte[2]).put(DATA).flip().position(2);
            CRC32 crc = new CRC32();
            crc.update(buffer);
            assertEquals(crcOf(DATA), crc.getValue());
            assertEquals(buffer.limit(), buffer.position());

            buffer.position(2);
            Adler32 adler = new Adler32();
            adler.update(buffer);
            assertEquals(adlerOf(DATA), adler.getValue());
        }
    }

    // Checksum.update(ByteBuffer): "Updates the current checksum with the bytes from the specified buffer. The
    // checksum is updated with the remaining bytes in the buffer, starting at the buffer's position." The
    // buffers below come from duplicate(), slice() and asReadOnlyBuffer() at non-zero positions, so their
    // arrayOffset() is non-zero or they have no accessible array.
    @Test
    public void updateDerivedByteBuffer() {
        byte[] backing = new byte[DATA.length + 7];
        System.arraycopy(DATA, 0, backing, 3, DATA.length);
        long expectedCrc = crcOf(DATA);
        long expectedAdler = adlerOf(DATA);
        for (int i = 0; i < 6; ++i) {
            ByteBuffer base = ByteBuffer.wrap(backing);
            base.position(1);
            ByteBuffer buffer;
            switch (i) {
                case 0:
                    buffer = base.duplicate();
                    buffer.position(3);
                    break;
                case 1:
                    buffer = base.slice();
                    buffer.position(2);
                    break;
                case 2:
                    buffer = base.duplicate().slice();
                    buffer.position(2);
                    break;
                case 3:
                    buffer = base.slice().duplicate();
                    buffer.position(2);
                    break;
                case 4:
                    buffer = base.asReadOnlyBuffer();
                    buffer.position(3);
                    break;
                default:
                    base.position(2);
                    buffer = base.slice();
                    buffer.position(1);
                    buffer = buffer.slice();
                    break;
            }
            buffer.limit(buffer.position() + DATA.length);

            CRC32 crc = new CRC32();
            crc.update(buffer);
            assertEquals(expectedCrc, crc.getValue(), "CRC32, case " + i);
            assertEquals(buffer.limit(), buffer.position(), "case " + i);

            buffer.position(buffer.limit() - DATA.length);
            Adler32 adler = new Adler32();
            adler.update(buffer);
            assertEquals(expectedAdler, adler.getValue(), "Adler32, case " + i);
        }
    }

    @Test
    public void defaultUpdateByteArray() {
        CountingChecksum checksum = new CountingChecksum();
        checksum.update(DATA);
        assertEquals(DATA.length, checksum.getValue());
    }

    private static long crcOf(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return crc.getValue();
    }

    private static long adlerOf(byte[] data) {
        Adler32 adler = new Adler32();
        adler.update(data, 0, data.length);
        return adler.getValue();
    }

    // Implements only the abstract methods, so update(byte[]) comes from the interface.
    static class CountingChecksum implements Checksum {
        private long count;

        @Override
        public void update(int b) {
            count++;
        }

        @Override
        public void update(byte[] b, int off, int len) {
            count += len;
        }

        @Override
        public long getValue() {
            return count;
        }

        @Override
        public void reset() {
            count = 0;
        }
    }
}
