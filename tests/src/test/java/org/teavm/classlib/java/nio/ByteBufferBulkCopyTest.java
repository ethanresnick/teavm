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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ByteBufferBulkCopyTest {
    @Test
    public void unalignedBulkCopiesPreserveBytesAndBoundaries() {
        var source = new byte[128];
        for (int i = 0; i < source.length; i++) {
            source[i] = (byte) (i * 79 + 131);
        }
        var buffer = ByteBuffer.allocateDirect(128);
        for (int offset = 0; offset < 8; offset++) {
            for (int count = 0; count <= 80; count++) {
                buffer.clear();
                var expected = new byte[128];
                Arrays.fill(expected, (byte) 42);
                buffer.put(expected);
                buffer.position(offset);
                buffer.put(source, 7 - offset, count);
                assertEquals(offset + count, buffer.position());
                System.arraycopy(source, 7 - offset, expected, offset, count);
                var whole = new byte[128];
                buffer.clear();
                for (int i = 0; i < whole.length; i++) {
                    whole[i] = buffer.get(i);
                }
                assertArrayEquals(expected, whole);
                var result = new byte[128];
                Arrays.fill(result, (byte) 73);
                var expectedResult = result.clone();
                System.arraycopy(source, 7 - offset, expectedResult, 3 + offset, count);
                for (int i = 0; i < expected.length; i++) {
                    buffer.put(i, expected[i]);
                }
                buffer.position(offset);
                buffer.get(result, 3 + offset, count);
                assertEquals(offset + count, buffer.position());
                assertArrayEquals(expectedResult, result);
            }
        }
    }

    @Test
    public void everyByteValueInEveryPackedLane() {
        // Bulk get/put transfer the same bytes as repeated scalar get/put:
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
        var root = ByteBuffer.allocateDirect(48);
        var source = new byte[24];
        var result = new byte[24];
        for (int offset = 0; offset < 8; offset++) {
            root.clear().position(offset);
            var buffer = root.slice().duplicate();
            var readOnly = buffer.asReadOnlyBuffer();
            for (var order : new ByteOrder[] { ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN }) {
                buffer.order(order);
                for (int lane = 0; lane < 8; lane++) {
                    for (int value = 0; value < 256; value++) {
                        Arrays.fill(source, (byte) 0);
                        source[lane] = (byte) value;
                        source[8 + lane] = (byte) ~value;
                        buffer.clear();
                        buffer.put(source);
                        for (int i = 0; i < source.length; i++) {
                            assertEquals(source[i], buffer.get(i));
                            buffer.put(i, (byte) (source[i] ^ 0xA5));
                        }
                        System.arraycopy(source, 0, result, 0, source.length);
                        readOnly.clear().get(result);
                        for (int i = 0; i < source.length; i++) {
                            assertEquals((byte) (source[i] ^ 0xA5), result[i]);
                        }
                    }
                }
            }
        }
    }


    @Test
    public void heapBufferSourcesAndOverlappingDirectViews() {
        var source = new byte[96];
        for (int i = 0; i < source.length; i++) {
            source[i] = (byte) (i * 131 + 17);
        }
        var direct = ByteBuffer.allocateDirect(96);
        for (int offset = 0; offset < 8; offset++) {
            for (int count = 0; count <= 64; count++) {
                var heap = ByteBuffer.wrap(source);
                heap.position(7 - offset);
                heap = heap.slice().asReadOnlyBuffer();
                heap.limit(count);
                direct.clear().position(offset);
                direct.put(heap);
                assertEquals(count, heap.position());
                assertEquals(offset + count, direct.position());
                for (int i = 0; i < count; i++) {
                    assertEquals(source[7 - offset + i], direct.get(offset + i));
                }
                for (int direction : new int[] { -1, 1 }) {
                    direct.clear();
                    for (int i = 0; i < source.length; i++) {
                        direct.put(i, source[i]);
                    }
                    int from = direction < 0 ? offset + 1 : offset;
                    int to = direction < 0 ? offset : offset + 1;
                    var expected = source.clone();
                    System.arraycopy(expected, from, expected, to, count);
                    var input = direct.duplicate();
                    input.position(from).limit(from + count);
                    direct.position(to);
                    direct.put(input);
                    for (int i = 0; i < source.length; i++) {
                        assertEquals(expected[i], direct.get(i));
                    }
                    for (int i = 0; i < source.length; i++) {
                        direct.put(i, source[i]);
                    }
                    direct.position(direct.limit());
                    direct.put(to, direct, from, count);
                    assertEquals(direct.limit(), direct.position());
                    for (int i = 0; i < source.length; i++) {
                        assertEquals(expected[i], direct.get(i));
                    }
                }
            }
        }
    }

}
