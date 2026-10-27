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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

/**
 * Array creation that the compiler implements through per-class array metadata (multi-dimensional
 * arrays, enum constants, stream toArray). Each test is compiled on its own, so that no other test
 * links {@code java.lang.reflect.Array.newInstance} and thereby makes the metadata available.
 */
@TeaVMTest
@EachTestCompiledSeparately
public class ArrayCreationMetadataTest {
    @Test
    public void intArray2D() {
        var array = new int[3][4];
        assertEquals(3, array.length);
        for (var row : array) {
            assertEquals(4, row.length);
        }
        array[2][3] = 23;
        array[0][0] = 1;
        assertEquals(23, array[2][3]);
        assertEquals(1, array[0][0]);
        assertEquals(0, array[1][2]);
        assertEquals(int[][].class, array.getClass());
        assertEquals(int[].class, array[1].getClass());
    }

    @Test
    public void allPrimitiveArrays2D() {
        var bytes = new byte[2][3];
        var shorts = new short[2][3];
        var chars = new char[2][3];
        var ints = new int[2][3];
        var longs = new long[2][3];
        var floats = new float[2][3];
        var doubles = new double[2][3];
        var booleans = new boolean[2][3];
        bytes[1][2] = 1;
        shorts[1][2] = 2;
        chars[1][2] = 'c';
        ints[1][2] = 4;
        longs[1][2] = 5L;
        floats[1][2] = 6f;
        doubles[1][2] = 7.0;
        booleans[1][2] = true;
        assertEquals(1, bytes[1][2]);
        assertEquals(2, shorts[1][2]);
        assertEquals('c', chars[1][2]);
        assertEquals(4, ints[1][2]);
        assertEquals(5L, longs[1][2]);
        assertEquals(6f, floats[1][2]);
        assertEquals(7.0, doubles[1][2]);
        assertTrue(booleans[1][2]);
        assertFalse(booleans[0][0]);
        assertEquals(byte[].class, bytes[0].getClass());
        assertEquals(short[].class, shorts[0].getClass());
        assertEquals(char[].class, chars[0].getClass());
        assertEquals(long[].class, longs[0].getClass());
        assertEquals(float[].class, floats[0].getClass());
        assertEquals(double[].class, doubles[0].getClass());
        assertEquals(boolean[].class, booleans[0].getClass());
        assertEquals(3, booleans[1].length);
    }

    @Test
    public void intArray3D() {
        var array = new int[2][3][4];
        assertEquals(2, array.length);
        assertEquals(3, array[1].length);
        assertEquals(4, array[1][2].length);
        array[1][2][3] = 123;
        assertEquals(123, array[1][2][3]);
        assertEquals(0, array[0][0][0]);
        assertEquals(int[][][].class, array.getClass());
        assertEquals(int[].class, array[1][2].getClass());
    }

    @Test
    public void longArray4D() {
        var array = new long[2][2][2][3];
        array[1][1][1][2] = 7L;
        assertEquals(3, array[0][0][0].length);
        assertEquals(7L, array[1][1][1][2]);
    }

    @Test
    public void objectArray2D() {
        var array = new String[2][3];
        assertEquals(2, array.length);
        assertEquals(3, array[1].length);
        assertNull(array[1][2]);
        array[1][2] = "x";
        assertEquals("x", array[1][2]);
        assertEquals(String[][].class, array.getClass());
        assertEquals(String[].class, array[0].getClass());
    }

    @Test
    public void objectArray3D() {
        var array = new Object[2][3][4];
        array[1][2][3] = "y";
        assertEquals(4, array[0][0].length);
        assertEquals("y", array[1][2][3]);
        assertEquals(Object[][][].class, array.getClass());
        assertEquals(Object[].class, array[1][1].getClass());
    }

    @Test
    public void partiallySpecifiedArrays() {
        var ints = new int[3][];
        assertEquals(3, ints.length);
        assertNull(ints[2]);
        ints[2] = new int[5];
        assertEquals(5, ints[2].length);

        var ints3 = new int[2][3][];
        assertEquals(3, ints3[1].length);
        assertNull(ints3[1][2]);

        var strings = new String[4][];
        assertEquals(4, strings.length);
        assertNull(strings[0]);
        assertEquals(String[][].class, strings.getClass());

        var strings3 = new String[2][2][];
        assertNull(strings3[1][1]);
        assertEquals(String[][][].class, strings3.getClass());
    }

    @Test
    public void zeroDimensions() {
        var empty = new int[0][5];
        assertEquals(0, empty.length);
        var emptyRows = new int[3][0];
        assertEquals(3, emptyRows.length);
        assertEquals(0, emptyRows[2].length);
        var empty3 = new String[2][0][4];
        assertEquals(0, empty3[1].length);
    }

    @Test
    public void enumValueOf() {
        assertSame(Color.GREEN, Color.valueOf("GREEN"));
        assertSame(Color.RED, Enum.valueOf(Color.class, "RED"));
    }

    @Test
    public void enumGetEnumConstants() {
        var constants = Color.class.getEnumConstants();
        assertEquals(3, constants.length);
        assertSame(Color.BLUE, constants[2]);
        assertEquals(Color[].class, constants.getClass());
    }

    @Test
    public void enumSetNoneOf() {
        var set = EnumSet.noneOf(Color.class);
        assertTrue(set.isEmpty());
        set.add(Color.BLUE);
        assertTrue(set.contains(Color.BLUE));
        assertFalse(set.contains(Color.RED));
        assertEquals(1, set.size());
    }

    @Test
    public void enumSetAllOf() {
        var set = EnumSet.allOf(Color.class);
        assertEquals(3, set.size());
        assertEquals(Color.RED, set.iterator().next());
    }

    @Test
    public void streamToArray() {
        var array = List.of("a", "b", "c").stream().toArray(String[]::new);
        assertEquals(3, array.length);
        assertEquals("c", array[2]);
        assertEquals(String[].class, array.getClass());

        var objects = List.of(1, 2).stream().toArray();
        assertEquals(2, objects.length);
        assertEquals(2, objects[1]);
    }

    @Test
    public void streamCollect() {
        var list = List.of(3, 1, 2).stream().map(x -> x * 2).collect(Collectors.toList());
        assertEquals(List.of(6, 2, 4), list);
        var joined = List.of("a", "b").stream().collect(Collectors.joining(","));
        assertEquals("a,b", joined);
    }

    enum Color {
        RED,
        GREEN,
        BLUE
    }
}
