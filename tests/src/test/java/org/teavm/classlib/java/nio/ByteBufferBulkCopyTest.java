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
                buffer.get(whole);
                assertArrayEquals(expected, whole);
                var result = new byte[128];
                Arrays.fill(result, (byte) 73);
                var expectedResult = result.clone();
                System.arraycopy(source, 7 - offset, expectedResult, 3 + offset, count);
                buffer.position(offset);
                buffer.get(result, 3 + offset, count);
                assertEquals(offset + count, buffer.position());
                assertArrayEquals(expectedResult, result);
            }
        }
    }
}
