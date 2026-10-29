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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.Reflectable;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
// Each compiled test gets fresh class initialization state; the JVM runner shares fixtures.
@SkipJVM
@EachTestCompiledSeparately
public class ReflectiveInitializationTest {
    @Test
    public void customLoaderCannotBeFoldedAway() {
        int[] calls = { 0 };
        var loader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                calls[0]++;
                throw new ClassNotFoundException(name);
            }
        };
        assertThrows(ClassNotFoundException.class, () -> Class.forName("java.lang.String", false, loader));
        assertEquals(1, calls[0]);
        assertThrows(ClassNotFoundException.class, () -> Class.forName("not.present.Class", false, loader));
        assertEquals(2, calls[0]);
    }

    @Test
    public void successfulCustomLoaderStillRunsWithoutInitialization() throws Exception {
        int[] calls = { 0 };
        var loader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                calls[0]++;
                return ToInitialize.class;
            }
        };
        assertEquals(ToInitialize.class, Class.forName(ToInitialize.class.getName(), false, loader));
        assertEquals(1, calls[0]);
        assertEquals(0, Initialization.counter);
        assertEquals(ToInitialize.class, Class.forName(ToInitialize.class.getName(), true, loader));
        assertEquals(2, calls[0]);
        assertEquals(1, Initialization.counter);
    }

    @Test
    public void loaderAliasIsRejected() {
        var loader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                return Integer.class;
            }
        };
        assertThrows(ClassNotFoundException.class, () -> Class.forName("java.lang.String", false, loader));
    }

    @Test
    public void bootstrapLookupRejectsApplicationClassesWithoutInitializing() {
        // Java SE 21 Class.forName: a null loader denotes the bootstrap loader.
        assertEquals(0, Initialization.counter);
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(ToInitialize.class.getName(), false, null));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(ToInitialize.class.getName(), true, null));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("java.lang.NotPresent", false, null));
        assertEquals(0, Initialization.counter);
    }

    @Test
    public void initializationFlagIsHonored() throws Exception {
        assertEquals(0, Initialization.counter);
        String name = ToInitialize.class.getName();
        var loader = getClass().getClassLoader();
        assertEquals(ToInitialize.class, Class.forName(name, false, loader));
        assertEquals(0, Initialization.counter);
        assertEquals(ToInitialize.class, Class.forName(name, true, loader));
        assertEquals(1, Initialization.counter);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void initializationCanSuspend() throws Exception {
        assertEquals(0, AsyncInitialization.counter);
        String name = AsyncInitialize.class.getName();
        Class.forName(name, false, getClass().getClassLoader());
        assertEquals(0, AsyncInitialization.counter);
        Class.forName(name, true, getClass().getClassLoader());
        assertEquals(1, AsyncInitialization.counter);
    }

    @Test
    public void initializerErrorReachesReflectiveCaller() throws Exception {
        String name = BadInitialize.class.getName();
        var loader = getClass().getClassLoader();
        Class.forName(name, false, loader);
        assertThrows(InitializerError.class, () -> Class.forName(name, true, loader));
        assertThrows(NoClassDefFoundError.class, () -> Class.forName(name, true, loader));
    }

    public static class InitializerError extends Error {
        @Override
        public String getMessage() {
            return "custom initializer error";
        }
    }

    public static class BadInitialize {
        static {
            if (Boolean.parseBoolean("true")) {
                throw new InitializerError();
            }
        }
    }

    @Test
    public void dynamicLookupInitializes() throws Exception {
        Class<?> kept = ToInitialize.class;
        String name = new String(
                "org.teavm.classlib.java.lang.ReflectiveInitializationTest$ToInitialize".toCharArray());
        assertEquals(0, Initialization.counter);
        assertEquals(kept, Class.forName(name));
        assertEquals(1, Initialization.counter);
    }

    @Test
    public void loaderDoesNotInitialize() throws Exception {
        String name = ToInitialize.class.getName();
        assertEquals(ToInitialize.class, Class.forName(name, false, getClass().getClassLoader()));
        assertEquals(ToInitialize.class, getClass().getClassLoader().loadClass(name));
        assertEquals(0, Initialization.counter);
    }

    @Test
    public void arraysDoNotInitializeComponent() throws Exception {
        assertEquals(ToInitialize.class,
                Class.forName(ToInitialize.class.getName(), false, getClass().getClassLoader()));
        String name = "[[Lorg.teavm.classlib.java.lang.ReflectiveInitializationTest$ToInitialize;";
        assertEquals(ToInitialize[][].class, Class.forName(name, true, getClass().getClassLoader()));
        assertEquals(int[][].class, Class.forName("[[I", true, null));
        assertEquals(0, Initialization.counter);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void reflectiveFieldWaitsForInitialization() throws Exception {
        assertEquals(0, AsyncInitialization.counter);
        assertEquals(42, AsyncInitialize.class.getField("value").getInt(null));
        assertEquals(1, AsyncInitialization.counter);
    }

    @Test
    public void constantFlagIsHonored() throws Exception {
        String name = "org.teavm.classlib.java.lang.ReflectiveInitializationTest$ToInitialize";
        Class.forName(name, false, getClass().getClassLoader());
        assertEquals(0, Initialization.counter);
        Class.forName(name, true, getClass().getClassLoader());
        assertEquals(1, Initialization.counter);
    }

    @Test
    public void initializationIsRecursiveAndRunsOnce() throws Exception {
        Class.forName(Recursive.class.getName(), true, getClass().getClassLoader());
        Class.forName(Recursive.class.getName(), true, getClass().getClassLoader());
        assertEquals(1, Initialization.counter);
    }

    public static class Recursive {
        static {
            try {
                Class.forName(Recursive.class.getName(), true, Recursive.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            Initialization.counter++;
        }
    }

    @Test
    public void initializerExceptionIsWrapped() throws Exception {
        String name = BadException.class.getName();
        Class.forName(name, false, getClass().getClassLoader());
        var error = assertThrows(ExceptionInInitializerError.class,
                () -> Class.forName(name, true, getClass().getClassLoader()));
        assertEquals(IllegalStateException.class, error.getException().getClass());
        assertThrows(NoClassDefFoundError.class, () -> Class.forName(name, true, getClass().getClassLoader()));
    }

    public static class BadException {
        static {
            if (Boolean.parseBoolean("true")) {
                throw new IllegalStateException("initializer");
            }
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void reflectiveFieldWriteWaitsForInitialization() throws Exception {
        AsyncInitialize.class.getField("value").setInt(null, 73);
        assertEquals(1, AsyncInitialization.counter);
        assertEquals(73, AsyncInitialize.value);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void reflectiveMethodWaitsForInitialization() throws Exception {
        assertEquals(42, AsyncInitialize.class.getMethod("read").invoke(null));
        assertEquals(1, AsyncInitialization.counter);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void enumConstantsWaitForInitialization() {
        assertEquals(0, AsyncInitialization.counter);
        assertEquals(1, AsyncEnum.class.getEnumConstants().length);
        assertEquals(1, AsyncInitialization.counter);
    }

    enum AsyncEnum {
        ITEM;

        static {
            pause();
            AsyncInitialization.counter++;
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void initializationWaitsForOtherThread() throws Exception {
        Throwable[] failures = new Throwable[2];
        int[] observed = new int[2];
        Thread first = new Thread(() -> {
            try {
                Class.forName(AsyncInitialize.class.getName(), true, getClass().getClassLoader());
                observed[0] = AsyncInitialization.counter;
            } catch (Throwable e) {
                failures[0] = e;
            }
        });
        Thread second = new Thread(() -> {
            try {
                Class.forName(AsyncInitialize.class.getName(), true, getClass().getClassLoader());
                observed[1] = AsyncInitialization.counter;
            } catch (Throwable e) {
                failures[1] = e;
            }
        });
        first.start();
        second.start();
        first.join();
        second.join();
        assertNull(failures[0]);
        assertNull(failures[1]);
        assertEquals(1, observed[0]);
        assertEquals(1, observed[1]);
        assertEquals(1, AsyncInitialization.counter);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void failingInitializerReleasesWaitingThread() throws Exception {
        Throwable[] failures = new Throwable[2];
        Thread first = new Thread(() -> initializeFailingClass(failures, 0));
        Thread second = new Thread(() -> initializeFailingClass(failures, 1));
        first.start();
        while (FailureState.started == 0) {
            Thread.sleep(1);
        }
        second.start();
        first.join();
        second.join();
        // JLS 12.4.2: the initializing thread gets the original Error; waiters observe erroneous state.
        assertEquals(InitializerError.class, failures[0].getClass());
        assertEquals(NoClassDefFoundError.class, failures[1].getClass());
        assertEquals(1, FailureState.started);
    }

    private void initializeFailingClass(Throwable[] failures, int index) {
        try {
            Class.forName(ConcurrentFailure.class.getName(), true, getClass().getClassLoader());
        } catch (Throwable e) {
            failures[index] = e;
        }
    }

    static class FailureState {
        static int started;
    }

    public static class ConcurrentFailure {
        static {
            FailureState.started++;
            pause();
            if (Boolean.parseBoolean("true")) {
                throw new InitializerError();
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void literalArrayRetainsComponentAndResultTypes() throws Exception {
        var array = Class.forName("[Lorg.teavm.classlib.java.lang.ReflectiveInitializationTest$OnlyArray;",
                false, getClass().getClassLoader());
        assertEquals("org.teavm.classlib.java.lang.ReflectiveInitializationTest$OnlyArray",
                array.getComponentType().getName());
        assertEquals(73, array.getComponentType().getField("number").getInt(null));
    }

    @Test
    public void dynamicArrayLookupFailsExplicitly() {
        String name = new String("[Ljava.lang.String;".toCharArray());
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(name, false, getClass().getClassLoader()));
    }

    public static class OnlyArray {
        @Reflectable
        public static int number = 73;
    }

    @Test
    public void primitiveNamesAreNotClasses() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(int.class.getName()));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(void.class.getName()));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(int.class.getName(), false, getClass().getClassLoader()));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(void.class.getName(), false, getClass().getClassLoader()));
        var loader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) {
                return int.class;
            }
        };
        assertThrows(ClassNotFoundException.class, () -> Class.forName("int", false, loader));
    }

    @Test
    public void initializerErrorKeepsVirtualType() {
        try {
            Class.forName(BadInitialize.class.getName());
            throw new AssertionError("initializer should fail");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        } catch (Error e) {
            assertEquals("custom initializer error", e.getMessage());
        }
    }

    @Test
    public void runtimeInitializationFlagIsHonored() throws Exception {
        boolean[] flags = { false, true };
        for (boolean flag : flags) {
            Class.forName(ToInitialize.class.getName(), flag, getClass().getClassLoader());
            assertEquals(flag ? 1 : 0, Initialization.counter);
        }
    }

    // Java SE 21 Class.forName specifies bootstrap lookup for a null loader.
    @Test
    public void bootstrapLookupFindsPlatformClass() throws Exception {
        assertEquals(String.class, Class.forName("java.lang.String", false, null));
        assertEquals(String.class, Class.forName("java.lang.String", true, null));
        assertEquals(String[][].class, Class.forName("[[Ljava.lang.String;", true, null));
    }

    @Test
    public void malformedAndMissingArrayDescriptorsAreRejected() {
        // Java SE 21 Class.forName throws ClassNotFoundException when the named class cannot be located.
        for (String name : new String[] { "[V", "[Q", "[[", "[Ljava.lang.NotPresent;", "[Ljava.lang.String" }) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName(name, false, getClass().getClassLoader()));
        }
    }

    @Test
    @Disabled("Known gap: dynamic array Class.forName lookup is unsupported")
    public void dynamicArrayLookupFindsArrayClass() throws Exception {
        String name = new String("[Ljava.lang.String;".toCharArray());
        assertEquals(String[].class, Class.forName(name, false, getClass().getClassLoader()));
    }

    @Test
    public void typedBooleanSetterInitializes() throws Exception {
        // Java SE 21 Field.setBoolean: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("booleanValue").setBoolean(null, true);
        assertEquals(1, Initialization.counter);
        assertEquals(true, TypedFields.booleanValue);
    }

    @Test
    public void typedByteSetterInitializes() throws Exception {
        // Java SE 21 Field.setByte: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("byteValue").setByte(null, (byte) 7);
        assertEquals(1, Initialization.counter);
        assertEquals((byte) 7, TypedFields.byteValue);
    }

    @Test
    public void typedShortSetterInitializes() throws Exception {
        // Java SE 21 Field.setShort: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("shortValue").setShort(null, (short) 7);
        assertEquals(1, Initialization.counter);
        assertEquals((short) 7, TypedFields.shortValue);
    }

    @Test
    public void typedCharSetterInitializes() throws Exception {
        // Java SE 21 Field.setChar: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("charValue").setChar(null, 'x');
        assertEquals(1, Initialization.counter);
        assertEquals('x', TypedFields.charValue);
    }

    @Test
    public void typedIntSetterInitializes() throws Exception {
        // Java SE 21 Field.setInt: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("intValue").setInt(null, 7);
        assertEquals(1, Initialization.counter);
        assertEquals(7, TypedFields.intValue);
    }

    @Test
    public void typedLongSetterInitializes() throws Exception {
        // Java SE 21 Field.setLong: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("longValue").setLong(null, 7L);
        assertEquals(1, Initialization.counter);
        assertEquals(7L, TypedFields.longValue);
    }

    @Test
    public void typedFloatSetterInitializes() throws Exception {
        // Java SE 21 Field.setFloat: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("floatValue").setFloat(null, 7F);
        assertEquals(1, Initialization.counter);
        assertEquals(7F, TypedFields.floatValue);
    }

    @Test
    public void typedDoubleSetterInitializes() throws Exception {
        // Java SE 21 Field.setDouble: a static field's declaring class is initialized if necessary.
        assertEquals(0, Initialization.counter);
        TypedFields.class.getField("doubleValue").setDouble(null, 7D);
        assertEquals(1, Initialization.counter);
        assertEquals(7D, TypedFields.doubleValue);
    }

    public static class TypedFields {
        @Reflectable
        public static boolean booleanValue;
        @Reflectable
        public static byte byteValue;
        @Reflectable
        public static short shortValue;
        @Reflectable
        public static char charValue;
        @Reflectable
        public static int intValue;
        @Reflectable
        public static long longValue;
        @Reflectable
        public static float floatValue;
        @Reflectable
        public static double doubleValue;
        static {
            Initialization.counter++;
        }
    }

    static class Initialization {
        static int counter;
    }

    public static class ToInitialize {
        static {
            Initialization.counter++;
        }
    }

    static class AsyncInitialization {
        static int counter;
    }

    public static class AsyncInitialize {
        @Reflectable
        public static int value;

        @Reflectable
        public static int read() {
            return value;
        }

        @Reflectable
        public AsyncInitialize() {
        }

        static {
            pause();
            AsyncInitialization.counter++;
            value = 42;
        }
    }
}
