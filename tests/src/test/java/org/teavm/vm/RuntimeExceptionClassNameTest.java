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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

// The class name of each exception that the runtime creates. The program never names these classes.
@TeaVMTest
@EachTestCompiledSeparately
public class RuntimeExceptionClassNameTest {
    private static int index = 4;
    private static int negative = -1;
    private static Object nullObject;
    private static int[] nullArray;
    private static Object notString = 1;
    private static Holder nullHolder;

    static class Holder {
        int field;

        int get() {
            return field;
        }
    }

    @Test
    public void arrayLoadOutOfBounds() {
        var array = new int[4];
        String name;
        try {
            name = "no exception " + array[index];
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException", name);
    }

    @Test
    public void thrownByCallee() {
        String name;
        try {
            storeOutOfBounds(new Object[4]);
            name = "no exception";
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException", name);
    }

    private static void storeOutOfBounds(Object[] array) {
        array[index] = null;
    }

    @Test
    public void fieldOfNull() {
        String name;
        try {
            name = "no exception " + nullHolder.field;
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NullPointerException", name);
    }

    @Test
    public void methodOfNull() {
        String name;
        try {
            name = "no exception " + nullHolder.get();
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NullPointerException", name);
    }

    @Test
    public void virtualMethodOfNull() {
        String name;
        try {
            name = "no exception " + nullObject.hashCode();
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NullPointerException", name);
    }

    @Test
    public void lengthOfNullArray() {
        String name;
        try {
            name = "no exception " + nullArray.length;
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NullPointerException", name);
    }

    @Test
    public void elementOfNullArray() {
        String name;
        try {
            name = "no exception " + nullArray[0];
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NullPointerException", name);
    }

    @Test
    public void failedCast() {
        String name;
        try {
            name = "no exception " + ((String) notString).length();
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.ClassCastException", name);
    }

    @Test
    public void negativeArraySize() {
        String name;
        try {
            name = "no exception " + new int[negative].length;
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NegativeArraySizeException", name);
    }

    @Test
    public void throwNull() {
        String name;
        try {
            throw (RuntimeException) nullObject;
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.NullPointerException", name);
    }
}
