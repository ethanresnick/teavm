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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class DeflaterTest {
    @Test
    public void callWithoutProgressReturnsZero() {
        var deflater = new Deflater();
        var out = new byte[100];
        deflater.setInput("hello hello hello".getBytes(StandardCharsets.UTF_8));
        var n = deflater.deflate(out, 0, out.length, Deflater.SYNC_FLUSH);
        assertTrue(n > 4);
        assertArrayEquals(new byte[] { 0, 0, -1, -1 }, Arrays.copyOfRange(out, n - 4, n));
        assertEquals(0, deflater.deflate(out, 0, out.length, Deflater.SYNC_FLUSH));
        assertEquals(0, deflater.deflate(out));
    }
}
