/*
 *  Copyright 2025 Alexey Andreev.
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
package org.teavm.classlib.java.nio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.ShortBuffer;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class BufferBulkBoundsTest {
    // Absolute bulk operations must validate both ranges against the respective limits before copying.
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
    // The other primitive buffers specify the same range requirements.

    @Test
    public void byteReducedLimits() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocateDirect(32), ByteBuffer.allocate(32) }) {
            var array = new byte[32];
            var source = ByteBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (byte) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((byte) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void byteWrapRanges() {
        var wrapped = new byte[16];
        assertThrows(IndexOutOfBoundsException.class, () -> ByteBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ByteBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> ByteBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> ByteBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, ByteBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void byteExtremeAndEmptyRanges() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocateDirect(32), ByteBuffer.allocate(32) }) {
            var array = new byte[32];
            var source = ByteBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void charReducedLimits() {
        for (var buffer : new CharBuffer[] { ByteBuffer.allocateDirect(64).asCharBuffer(), CharBuffer.allocate(32) }) {
            var array = new char[32];
            var source = CharBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (char) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((char) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void charWrapRanges() {
        var wrapped = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> CharBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> CharBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> CharBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> CharBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, CharBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void charExtremeAndEmptyRanges() {
        for (var buffer : new CharBuffer[] { ByteBuffer.allocateDirect(64).asCharBuffer(), CharBuffer.allocate(32) }) {
            var array = new char[32];
            var source = CharBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void shortReducedLimits() {
        for (var buffer : new ShortBuffer[] {
                ByteBuffer.allocateDirect(64).asShortBuffer(), ShortBuffer.allocate(32) }) {
            var array = new short[32];
            var source = ShortBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (short) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((short) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void shortWrapRanges() {
        var wrapped = new short[16];
        assertThrows(IndexOutOfBoundsException.class, () -> ShortBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ShortBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> ShortBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> ShortBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, ShortBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void shortExtremeAndEmptyRanges() {
        for (var buffer : new ShortBuffer[] {
                ByteBuffer.allocateDirect(64).asShortBuffer(), ShortBuffer.allocate(32) }) {
            var array = new short[32];
            var source = ShortBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void intReducedLimits() {
        for (var buffer : new IntBuffer[] { ByteBuffer.allocateDirect(128).asIntBuffer(), IntBuffer.allocate(32) }) {
            var array = new int[32];
            var source = IntBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (int) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((int) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void intWrapRanges() {
        var wrapped = new int[16];
        assertThrows(IndexOutOfBoundsException.class, () -> IntBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> IntBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> IntBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> IntBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, IntBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void intExtremeAndEmptyRanges() {
        for (var buffer : new IntBuffer[] { ByteBuffer.allocateDirect(128).asIntBuffer(), IntBuffer.allocate(32) }) {
            var array = new int[32];
            var source = IntBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void longReducedLimits() {
        for (var buffer : new LongBuffer[] { ByteBuffer.allocateDirect(256).asLongBuffer(), LongBuffer.allocate(32) }) {
            var array = new long[32];
            var source = LongBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (long) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((long) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void longWrapRanges() {
        var wrapped = new long[16];
        assertThrows(IndexOutOfBoundsException.class, () -> LongBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> LongBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> LongBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> LongBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, LongBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void longExtremeAndEmptyRanges() {
        for (var buffer : new LongBuffer[] { ByteBuffer.allocateDirect(256).asLongBuffer(), LongBuffer.allocate(32) }) {
            var array = new long[32];
            var source = LongBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void floatReducedLimits() {
        for (var buffer : new FloatBuffer[] {
                ByteBuffer.allocateDirect(128).asFloatBuffer(), FloatBuffer.allocate(32) }) {
            var array = new float[32];
            var source = FloatBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (float) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((float) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void floatWrapRanges() {
        var wrapped = new float[16];
        assertThrows(IndexOutOfBoundsException.class, () -> FloatBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> FloatBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> FloatBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> FloatBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, FloatBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void floatExtremeAndEmptyRanges() {
        for (var buffer : new FloatBuffer[] {
                ByteBuffer.allocateDirect(128).asFloatBuffer(), FloatBuffer.allocate(32) }) {
            var array = new float[32];
            var source = FloatBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void doubleReducedLimits() {
        for (var buffer : new DoubleBuffer[] {
                ByteBuffer.allocateDirect(256).asDoubleBuffer(), DoubleBuffer.allocate(32) }) {
            var array = new double[32];
            var source = DoubleBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12).position(2);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, array, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(15, source, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 11, 2));
            assertEquals(3, buffer.position());
            assertEquals(2, source.position());
            buffer.put(14, array, 0, 2);
            buffer.get(14, array, 0, 2);
            buffer.put(14, source, 10, 2);
            source.position(source.limit());
            source.put(0, (double) 42);
            buffer.put(0, source, 0, 1);
            assertEquals((double) 42, buffer.get(0));
            assertEquals(3, buffer.position());
            assertEquals(12, source.position());
        }
    }

    @Test
    public void doubleWrapRanges() {
        var wrapped = new double[16];
        assertThrows(IndexOutOfBoundsException.class, () -> DoubleBuffer.wrap(wrapped, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> DoubleBuffer.wrap(wrapped, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> DoubleBuffer.wrap(wrapped, 15, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> DoubleBuffer.wrap(wrapped, Integer.MAX_VALUE, 8));
        assertEquals(0, DoubleBuffer.wrap(wrapped, wrapped.length, 0).remaining());
    }

    @Test
    public void doubleExtremeAndEmptyRanges() {
        for (var buffer : new DoubleBuffer[] {
                ByteBuffer.allocateDirect(256).asDoubleBuffer(), DoubleBuffer.allocate(32) }) {
            var array = new double[32];
            var source = DoubleBuffer.allocate(32);
            buffer.limit(16).position(3);
            source.limit(12);
            for (int invalid : new int[] { -1, Integer.MIN_VALUE, Integer.MAX_VALUE - 7, Integer.MAX_VALUE }) {
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, source, 0, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, invalid, 8));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(array, invalid, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(invalid, array, 0, 0));
                assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, invalid, 0));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, array, 0, Integer.MAX_VALUE));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.put(0, source, 0, Integer.MAX_VALUE));
            var readOnly = buffer.asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, array, 0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> readOnly.put(0, source, 0, 0));
            buffer.get(array, array.length, 0);
            buffer.put(array, array.length, 0);
            buffer.get(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), array, array.length, 0);
            buffer.put(buffer.limit(), source, source.limit(), 0);
            assertEquals(3, buffer.position());
            assertEquals(0, source.position());
        }
    }

    @Test
    public void byteRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new ByteBuffer[] {
                ByteBuffer.allocateDirect(16), ByteBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (byte) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((byte) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }

    @Test
    public void charRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new CharBuffer[] {
                ByteBuffer.allocateDirect(32).asCharBuffer(), CharBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (char) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((char) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }

    @Test
    public void shortRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new ShortBuffer[] {
                ByteBuffer.allocateDirect(32).asShortBuffer(), ShortBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (short) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((short) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }

    @Test
    public void intRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new IntBuffer[] {
                ByteBuffer.allocateDirect(64).asIntBuffer(), IntBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (int) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((int) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }

    @Test
    public void longRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new LongBuffer[] {
                ByteBuffer.allocateDirect(128).asLongBuffer(), LongBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (long) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((long) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }

    @Test
    public void floatRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new FloatBuffer[] {
                ByteBuffer.allocateDirect(64).asFloatBuffer(), FloatBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (float) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((float) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }

    @Test
    public void doubleRelativeSelfCopy() {
        // Relative put(buffer) rejects identity; absolute put(index, buffer, offset, length) permits it.
        for (var buffer : new DoubleBuffer[] {
                ByteBuffer.allocateDirect(128).asDoubleBuffer(), DoubleBuffer.allocate(16) }) {
            for (int position : new int[] { 0, 8, 16 }) {
                buffer.position(position);
                assertThrows(IllegalArgumentException.class, () -> buffer.put(buffer));
                assertEquals(position, buffer.position());
            }
            buffer.put(1, (double) 42);
            buffer.put(0, buffer, 1, 1);
            assertEquals((double) 42, buffer.get(0));
            assertEquals(16, buffer.position());
        }
    }
}
