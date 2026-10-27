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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

// A handler receives the errors of a failed class initializer: ExceptionInInitializerError, NoClassDefFoundError
// and the Errors that the initializer throws. No instruction creates the first two, and the initializer runs from
// the code that triggers it, not as part of the method that the try block calls. The optimizer must not call
// CustomError.getMessage on them, as it would if it saw only the CustomError that the try block can throw otherwise.
// The handlers catch Error, so that the exceptions that the backends' own checks throw do not reach them and hide a
// missing type. A missing type shows only in optimized builds. Each test fails a class of its own, because the JVM
// runs all of them in one class loader.
@TeaVMTest
@EachTestCompiledSeparately
public class InitializerExceptionHandlerTest {
    private boolean throwCustom;
    private boolean fail = true;
    private int intSink;
    private Object sink;
    private Error caught;

    static class CustomError extends Error {
        @Override
        public String getMessage() {
            return "custom";
        }
    }

    static class OtherError extends Error {
    }

    static void failIf(boolean fail) {
        if (fail) {
            throw new IllegalStateException();
        }
    }

    // The error is created outside the try block: on Wasm GC, creating one can throw other Errors, which would hide
    // a missing type.
    static void throwCustomIf(CustomError error) {
        if (error != null) {
            throw error;
        }
    }

    private CustomError custom() {
        return throwCustom ? new CustomError() : null;
    }

    private String kind(Error e) {
        caught = e;
        return "custom".equals(e.getMessage()) ? "custom" : "other";
    }

    static class FailsOnStaticCall {
        static {
            failIf(true);
        }

        static void run(CustomError error) {
            throwCustomIf(error);
        }
    }

    @Test
    public void staticCall() {
        var custom = custom();
        String kind;
        try {
            FailsOnStaticCall.run(custom);
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof ExceptionInInitializerError);
    }

    static class FailsOnStaticField {
        static int value;

        static {
            failIf(true);
            value = 1;
        }
    }

    @Test
    public void staticFieldRead() {
        var custom = custom();
        String kind;
        try {
            throwCustomIf(custom);
            intSink = FailsOnStaticField.value;
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof ExceptionInInitializerError);
    }

    static class FailsOnStaticFieldWrite {
        static int value;

        static {
            failIf(true);
        }
    }

    @Test
    public void staticFieldWrite() {
        var custom = custom();
        String kind;
        try {
            throwCustomIf(custom);
            FailsOnStaticFieldWrite.value = 2;
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof ExceptionInInitializerError);
    }

    static class FailsOnConstruction {
        static {
            failIf(true);
        }
    }

    @Test
    public void construction() {
        var custom = custom();
        String kind;
        try {
            throwCustomIf(custom);
            sink = new FailsOnConstruction();
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof ExceptionInInitializerError);
    }

    static class FailingSuperclass {
        static {
            failIf(true);
        }
    }

    static class SubclassOfFailing extends FailingSuperclass {
    }

    // Constructing an object runs the initializer of its superclass.
    @Test
    public void superclassInitializer() {
        var custom = custom();
        String kind;
        try {
            throwCustomIf(custom);
            sink = new SubclassOfFailing();
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof ExceptionInInitializerError);
    }

    static class FailsTwice {
        static int value;

        static {
            failIf(true);
        }
    }

    // A class whose initializer failed throws NoClassDefFoundError on every later use.
    @Test
    public void laterUse() {
        try {
            intSink = FailsTwice.value;
        } catch (ExceptionInInitializerError e) {
            // expected
        }
        var custom = custom();
        String kind;
        try {
            throwCustomIf(custom);
            intSink = FailsTwice.value;
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof NoClassDefFoundError);
    }

    static class ThrowsError {
        static int value;

        static {
            if (true) {
                throw new OtherError();
            }
        }
    }

    // An Error from the initializer reaches the handler itself, unwrapped.
    @Test
    public void errorFromInitializer() {
        var custom = custom();
        String kind;
        try {
            throwCustomIf(custom);
            intSink = ThrowsError.value;
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("other", kind);
        assertTrue(caught instanceof OtherError);
    }

    static class ThrowsCustomError {
        static int value;

        static {
            if (true) {
                throw new CustomError();
            }
        }
    }

    // The other way round: the handler must still see the initializer's own Error as it is.
    @Test
    public void customErrorFromInitializer() {
        var fail = this.fail;
        String kind;
        try {
            if (fail) {
                intSink = ThrowsCustomError.value;
            }
            kind = "no exception";
        } catch (Error e) {
            kind = kind(e);
        }
        assertEquals("custom", kind);
    }

    static class FailingSuperclassOfPlain {
        static {
            failIf(true);
        }
    }

    static class PlainSubclassOfFailing extends FailingSuperclassOfPlain {
        static void run() {
        }
    }

    // JLS 12.4.2: initializing a class first initializes its superclass, also when a static method of the class
    // triggers it.
    @Test
    @Disabled("TeaVM initializes a superclass only from a constructor, not when a static member of a subclass "
            + "initializes the subclass")
    public void superclassOfClassWithoutInitializer() {
        String kind;
        try {
            PlainSubclassOfFailing.run();
            kind = "no exception";
        } catch (ExceptionInInitializerError e) {
            kind = "initializer error";
        }
        assertEquals("initializer error", kind);
    }
}
