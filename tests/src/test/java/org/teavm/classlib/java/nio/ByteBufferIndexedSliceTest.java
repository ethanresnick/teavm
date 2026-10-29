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
package org.teavm.classlib.java.nio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.ReadOnlyBufferException;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ByteBufferIndexedSliceTest {
    // Java SE 21 ByteBuffer.slice(int, int): absolute index and shared content; independent state;
    // position zero, limit/capacity length, undefined mark, BIG_ENDIAN order, same direct/read-only status.
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html#slice(int,int)

    @Test
    public void usesAbsoluteIndexAndPreservesSourceState() {
        for (boolean direct : new boolean[] { false, true }) {
            ByteBuffer source = buffer(direct);
            source.position(2).mark().position(7).limit(10);
            source.order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer slice = source.slice(3, 4);
            assertEquals(0, slice.position());
            assertEquals(4, slice.limit());
            assertEquals(4, slice.capacity());
            assertEquals(direct, slice.isDirect());
            assertFalse(slice.isReadOnly());
            assertEquals(ByteOrder.BIG_ENDIAN, slice.order());
            assertThrows(InvalidMarkException.class, slice::reset);
            assertEquals(3, slice.get());
            assertEquals(4, slice.get());
            assertEquals(7, source.position());
            assertEquals(10, source.limit());
            assertEquals(ByteOrder.LITTLE_ENDIAN, source.order());
            source.reset();
            assertEquals(2, source.position());
            slice.limit(2).position(1);
            assertEquals(10, source.limit());
            assertEquals(2, source.position());
        }
    }

    @Test
    public void sharesContentAndSupportsNestedSlices() {
        for (boolean direct : new boolean[] { false, true }) {
            ByteBuffer source = buffer(direct);
            source.position(2).limit(11);
            ByteBuffer slice = source.slice().slice(3, 4).slice(1, 2);
            assertEquals(6, slice.get(0));
            assertEquals(7, slice.get(1));
            slice.put(0, (byte) 42);
            assertEquals(42, source.get(6));
            source.put(7, (byte) 43);
            assertEquals(43, slice.get(1));
            slice.clear();
            assertEquals(2, slice.limit());
            assertThrows(IndexOutOfBoundsException.class, () -> slice.get(2));
        }
    }

    @Test
    public void preservesReadOnlyStatus() {
        for (boolean direct : new boolean[] { false, true }) {
            ByteBuffer source = buffer(direct);
            ByteBuffer readOnly = source.asReadOnlyBuffer();
            readOnly.position(5).mark().limit(9).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer slice = readOnly.slice(2, 3);
            assertTrue(slice.isReadOnly());
            assertEquals(direct, slice.isDirect());
            assertFalse(slice.hasArray());
            assertEquals(ByteOrder.BIG_ENDIAN, slice.order());
            assertThrows(ReadOnlyBufferException.class, () -> slice.put(0, (byte) 1));
            source.put(2, (byte) 44);
            assertEquals(44, slice.get(0));
            assertEquals(5, readOnly.position());
            assertEquals(9, readOnly.limit());
            readOnly.reset();
            assertEquals(5, readOnly.position());
        }
    }

    @Test
    public void preservesHeapArrayOffset() {
        byte[] array = new byte[16];
        ByteBuffer source = ByteBuffer.wrap(array, 2, 12);
        ByteBuffer slice = source.slice().slice(3, 4);
        assertTrue(slice.hasArray());
        assertSame(array, slice.array());
        assertEquals(5, slice.arrayOffset());
        slice.put(0, (byte) 45);
        assertEquals(45, array[5]);
        ByteBuffer readOnly = slice.asReadOnlyBuffer().slice(1, 2);
        assertFalse(readOnly.hasArray());
        assertThrows(ReadOnlyBufferException.class, readOnly::array);
        assertThrows(ReadOnlyBufferException.class, readOnly::arrayOffset);
    }

    @Test
    public void allowsEmptySlicesAtLimit() {
        for (boolean direct : new boolean[] { false, true }) {
            for (boolean readOnly : new boolean[] { false, true }) {
                ByteBuffer source = buffer(direct);
                if (readOnly) {
                    source = source.asReadOnlyBuffer();
                }
                source.position(3).mark().limit(7);
                ByteBuffer empty = source.slice(7, 0);
                assertEquals(0, empty.capacity());
                assertEquals(0, empty.position());
                assertEquals(0, empty.limit());
                assertEquals(direct, empty.isDirect());
                assertEquals(readOnly, empty.isReadOnly());
                assertEquals(0, source.slice(0, 0).capacity());
                source.reset();
                assertEquals(3, source.position());
                assertEquals(7, source.limit());
                assertEquals(0, empty.slice(0, 0).capacity());
            }
        }
    }

    @Test
    public void checksRangeAgainstLimitWithoutOverflow() {
        for (boolean direct : new boolean[] { false, true }) {
            for (boolean readOnly : new boolean[] { false, true }) {
                ByteBuffer original = buffer(direct);
                ByteBuffer source = readOnly ? original.asReadOnlyBuffer() : original;
                source.position(3).mark().limit(7);
                int[][] ranges = { { -1, 0 }, { 0, -1 }, { 8, 0 }, { 7, 1 }, { 6, 2 },
                        { Integer.MAX_VALUE, 1 }, { 1, Integer.MAX_VALUE },
                        { Integer.MAX_VALUE, Integer.MAX_VALUE }, { Integer.MIN_VALUE, 0 } };
                for (int[] range : ranges) {
                    assertThrows(IndexOutOfBoundsException.class, () -> source.slice(range[0], range[1]));
                    assertEquals(3, source.position());
                    assertEquals(7, source.limit());
                    source.reset();
                }
            }
        }
    }

    @Test
    public void exhaustsSmallRangesAndSourcePositions() {
        // The specified range is bounded by limit, including an empty range at limit.
        for (boolean direct : new boolean[] { false, true }) {
            for (int limit = 0; limit <= 5; ++limit) {
                for (int position = 0; position <= limit; ++position) {
                    ByteBuffer source = buffer(direct).limit(limit).position(position).mark();
                    for (int index = -1; index <= 6; ++index) {
                        for (int length = -1; length <= 6; ++length) {
                            int start = index;
                            int size = length;
                            if (index < 0 || length < 0 || index + length > limit) {
                                assertThrows(IndexOutOfBoundsException.class, () -> source.slice(start, size));
                            } else {
                                ByteBuffer slice = source.slice(index, length);
                                assertEquals(length, slice.capacity());
                                assertEquals(length, slice.limit());
                                assertEquals(0, slice.position());
                                for (int i = 0; i < length; ++i) {
                                    assertEquals(index + i, slice.get(i));
                                }
                            }
                            assertEquals(position, source.position());
                            assertEquals(limit, source.limit());
                            source.reset();
                        }
                    }
                }
            }
        }
    }

    @Test
    public void noArgumentSliceKeepsItsContract() {
        // ByteBuffer.slice() starts at position and ends at limit, with the same new-view state.
        for (boolean direct : new boolean[] { false, true }) {
            for (boolean readOnly : new boolean[] { false, true }) {
                ByteBuffer source = buffer(direct);
                if (readOnly) {
                    source = source.asReadOnlyBuffer();
                }
                source.position(3).mark().position(5).limit(9).order(ByteOrder.LITTLE_ENDIAN);
                ByteBuffer slice = source.slice();
                assertEquals(4, slice.capacity());
                assertEquals(4, slice.limit());
                assertEquals(0, slice.position());
                assertEquals(5, slice.get(0));
                assertEquals(ByteOrder.BIG_ENDIAN, slice.order());
                assertEquals(direct, slice.isDirect());
                assertEquals(readOnly, slice.isReadOnly());
                assertThrows(InvalidMarkException.class, slice::reset);
                source.reset();
                assertEquals(3, source.position());
                assertEquals(9, source.limit());
            }
        }
    }

    @Test
    public void nestedDirectSliceRetainsStorageAcrossCollection() throws InterruptedException {
        // Shared storage must survive after callers drop their parent references.
        // Use an unrelated sentinel: implementations may retain any of the parent wrappers.
        ByteBuffer[] retained = new ByteBuffer[1];
        WeakReference<?>[] sentinels = new WeakReference<?>[1];
        Thread creator = new Thread(() -> {
            ByteBuffer parent = buffer(true).slice(3, 10);
            retained[0] = parent.slice(3, 5);
            sentinels[0] = new WeakReference<>(new Object());
        });
        creator.start();
        creator.join();
        GCSupport.tryToTriggerGC(sentinels[0]);
        assertNull(sentinels[0].get());
        ByteBuffer slice = retained[0];
        for (int i = 0; i < slice.capacity(); ++i) {
            assertEquals(i + 6, slice.get(i));
        }
        slice.putInt(0, 0x12345678);
        assertEquals(0x12345678, slice.getInt(0));
    }

    private static ByteBuffer buffer(boolean direct) {
        ByteBuffer result = direct ? ByteBuffer.allocateDirect(16) : ByteBuffer.allocate(16);
        for (int i = 0; i < result.capacity(); ++i) {
            result.put(i, (byte) i);
        }
        return result;
    }
}
