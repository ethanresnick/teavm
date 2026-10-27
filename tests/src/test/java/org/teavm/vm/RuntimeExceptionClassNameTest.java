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
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

// The class name and simple name of each exception that the runtime creates, caught by a supertype, and
// catching it by its exact type. The program never names these classes otherwise.
@TeaVMTest
@EachTestCompiledSeparately
public class RuntimeExceptionClassNameTest {
    private static int index = 4;
    private static int negative = -1;
    private static Object nullObject;
    private static int[] nullArray;
    private static Object notString = 1;
    private static Holder nullHolder;
    private static int intSink;
    private static Object sink;

    static class Holder {
        int field;

        int get() {
            return field;
        }
    }

    private static String names(Throwable e) {
        return e.getClass().getName() + " " + e.getClass().getSimpleName();
    }

    @Test
    public void arrayLoadOutOfBounds() {
        var array = new int[4];
        String names = "no exception";
        try {
            intSink = array[index];
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException ArrayIndexOutOfBoundsException", names);

        var caught = false;
        try {
            intSink = array[-1];
        } catch (ArrayIndexOutOfBoundsException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void arrayStoreOutOfBounds() {
        var array = new long[4];
        String names = "no exception";
        try {
            array[index] = 1;
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException ArrayIndexOutOfBoundsException", names);
    }

    @Test
    public void thrownByCallee() {
        String names = "no exception";
        try {
            storeOutOfBounds(new Object[4]);
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException ArrayIndexOutOfBoundsException", names);
    }

    private static void storeOutOfBounds(Object[] array) {
        array[index] = null;
    }

    @Test
    public void fieldOfNull() {
        String names = "no exception";
        try {
            intSink = nullHolder.field;
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);

        var caught = false;
        try {
            nullHolder.field = 1;
        } catch (NullPointerException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void methodOfNull() {
        String names = "no exception";
        try {
            intSink = nullHolder.get();
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);
    }

    @Test
    public void virtualMethodOfNull() {
        String names = "no exception";
        try {
            intSink = nullObject.hashCode();
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);
    }

    @Test
    public void lengthOfNullArray() {
        String names = "no exception";
        try {
            intSink = nullArray.length;
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);
    }

    @Test
    public void elementOfNullArray() {
        String names = "no exception";
        try {
            intSink = nullArray[0];
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);
    }

    @Test
    public void synchronizeOnNull() {
        String names = "no exception";
        try {
            synchronized (nullObject) {
                intSink = 1;
            }
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);
    }

    @Test
    public void failedCast() {
        String names = "no exception";
        try {
            sink = (String) notString;
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.ClassCastException ClassCastException", names);

        var caught = false;
        try {
            sink = (Integer[]) new Object[0];
        } catch (ClassCastException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void negativeArraySize() {
        String names = "no exception";
        try {
            sink = new int[negative];
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NegativeArraySizeException NegativeArraySizeException", names);

        var caught = false;
        try {
            sink = new Object[2][negative];
        } catch (NegativeArraySizeException e) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void throwNull() {
        String names;
        try {
            throw (RuntimeException) nullObject;
        } catch (RuntimeException e) {
            names = names(e);
        }
        assertEquals("java.lang.NullPointerException NullPointerException", names);
    }
}
