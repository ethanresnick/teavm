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

// A handler receives the exceptions that the backends' own checks throw, besides the one that the program throws.
// The optimizer must not call CustomException.getMessage on them, as it would if it saw only the thrown one. A
// missing type shows only in optimized builds. Each try block reads its inputs from locals, so that nothing else in it
// can throw.
@TeaVMTest
@EachTestCompiledSeparately
public class ImplicitExceptionHandlerTest {
    private int index = 4;
    private int negative = -1;
    private Object notString = 1;
    private Holder nullHolder;
    private boolean throwCustom;
    private int intSink;
    private Object sink;

    static class Holder {
        int field;
    }

    static class CustomException extends RuntimeException {
        @Override
        public String getMessage() {
            return "custom";
        }
    }

    private CustomException customOrNull() {
        return throwCustom ? new CustomException() : null;
    }

    @Test
    public void arrayIndexOutOfBounds() {
        var array = new int[4];
        var i = index;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            intSink = array[i];
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    @Test
    public void nullPointer() {
        var holder = nullHolder;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            intSink = holder.field;
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    @Test
    public void classCast() {
        var value = notString;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            sink = (String) value;
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    @Test
    public void negativeArraySize() {
        var size = negative;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            sink = new int[size];
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    @Test
    public void negativeMultiArraySize() {
        var size = negative;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            sink = new int[2][size];
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    // The same, with the failing operation in a method that the optimizer inlines into the try block, after
    // which it infers the handler's types from the instructions in the block.
    @Test
    public void arrayIndexOutOfBoundsInlined() {
        var array = new int[4];
        var i = index;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            intSink = load(array, i);
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    private static int load(int[] array, int i) {
        return array[i];
    }

    @Test
    public void nullPointerInlined() {
        var holder = nullHolder;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            intSink = read(holder);
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    private static int read(Holder holder) {
        return holder.field;
    }

    @Test
    public void classCastInlined() {
        var value = notString;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            sink = cast(value);
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    private static String cast(Object value) {
        return (String) value;
    }

    @Test
    public void negativeArraySizeInlined() {
        var size = negative;
        var custom = customOrNull();
        String kind;
        try {
            if (custom != null) {
                throw custom;
            }
            sink = allocate(size);
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("other", kind);
    }

    private static int[] allocate(int size) {
        return new int[size];
    }

    // The other way round: the program's exception, thrown beside an array access, reaches the handler. The
    // backend checks the thrown value for null, and the check must keep the value's type.
    @Test
    public void customThrownBesideArrayAccess() {
        var array = new int[4];
        var i = index - 4;
        var custom = new CustomException();
        String kind;
        try {
            intSink = load(array, i);
            if (intSink == 0) {
                throw custom;
            }
            kind = "no exception";
        } catch (RuntimeException e) {
            kind = "custom".equals(e.getMessage()) ? "custom" : "other";
        }
        assertEquals("custom", kind);
    }
}
