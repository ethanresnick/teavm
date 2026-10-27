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
package org.teavm.classlib.java.lang.reflect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.Reflectable;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

/**
 * Reflection initializes a class before it uses it (JLS 12.4.1): Method.invoke of a static method, Field.get and
 * Field.set of a static field, Constructor.newInstance, Class.newInstance, Class.forName with initialize set and
 * Enum.valueOf. JLS 12.4.2: if the initializer throws, the first use throws ExceptionInInitializerError and every
 * later use of the class throws NoClassDefFoundError.
 * <p>
 * The tests with an async initializer pause in it, so it suspends. The C backend does not support async methods in
 * reflection (see MethodTest.asyncReflection). Each test has classes of its own, since a class is initialized once.
 */
@TeaVMTest
@EachTestCompiledSeparately
public class ReflectiveClassInitializationTest {
    // Field.get and Field.set, Constructor.newInstance, Class.newInstance, Class.forName and Enum.valueOf initialize
    // the class through ClassInfo.initialize, which AsyncMethodFinder never marks async, so the call is not a
    // suspend point. When the initializer suspends, JS hangs (Field, forName) and Wasm GC loses the rest of the
    // initializer or traps. Static Method.invoke is fixed: the method initializes its class itself.
    static final String ASYNC_INITIALIZE_GAP = "Known gap: reflective initialization does not suspend";

    static void pause() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean failInitializer() {
        return true;
    }

    // Method.invoke

    @Test
    @SkipPlatform(TestPlatform.C)
    public void syncStaticMethodOfAsyncInitializer() throws Exception {
        var method = MethodOfAsyncInitializer.class.getMethod("get");
        assertEquals(42, method.invoke(null));
        assertEquals(42, method.invoke(null));
        assertEquals(1, MethodOfAsyncInitializer.initializations);
    }

    static class MethodOfAsyncInitializer {
        static int initializations;
        static int value;

        static {
            pause();
            value = 42;
            initializations++;
        }

        @Reflectable
        public static int get() {
            return value;
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void syncStaticMethodOfFailingAsyncInitializer() throws Exception {
        var method = MethodOfFailingAsyncInitializer.class.getMethod("get");
        assertThrows(ExceptionInInitializerError.class, () -> method.invoke(null));
        assertThrows(NoClassDefFoundError.class, () -> method.invoke(null));
        assertThrows(NoClassDefFoundError.class, () -> MethodOfFailingAsyncInitializer.value++);
    }

    static class MethodOfFailingAsyncInitializer {
        static int value;

        static {
            pause();
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }

        @Reflectable
        public static int get() {
            return value;
        }
    }

    @Test
    public void staticMethodOfFailingInitializer() throws Exception {
        var method = MethodOfFailingInitializer.class.getMethod("get");
        assertThrows(ExceptionInInitializerError.class, () -> method.invoke(null));
        assertThrows(NoClassDefFoundError.class, () -> method.invoke(null));
        assertThrows(NoClassDefFoundError.class, () -> MethodOfFailingInitializer.value++);
    }

    static class MethodOfFailingInitializer {
        static int value;

        static {
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }

        @Reflectable
        public static int get() {
            return value;
        }
    }

    // Field.get and Field.set

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void staticFieldGetOfAsyncInitializer() throws Exception {
        var field = FieldGetOfAsyncInitializer.class.getDeclaredField("value");
        assertEquals(42, field.get(null));
        assertEquals(42, field.get(null));
        assertEquals(1, FieldGetOfAsyncInitializer.initializations);
    }

    static class FieldGetOfAsyncInitializer {
        static int initializations;
        @Reflectable static int value;

        static {
            pause();
            value = 42;
            initializations++;
        }
    }

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void staticFieldSetOfAsyncInitializer() throws Exception {
        var field = FieldSetOfAsyncInitializer.class.getDeclaredField("value");
        field.set(null, 23);
        // The initializer ran before the write, so it did not overwrite the value
        assertEquals(23, FieldSetOfAsyncInitializer.value);
        assertEquals(1, FieldSetOfAsyncInitializer.initializations);
    }

    static class FieldSetOfAsyncInitializer {
        static int initializations;
        @Reflectable static int value;

        static {
            pause();
            value = 42;
            initializations++;
        }
    }

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void staticFieldOfFailingAsyncInitializer() throws Exception {
        var field = FieldOfFailingAsyncInitializer.class.getDeclaredField("value");
        assertThrows(ExceptionInInitializerError.class, () -> field.get(null));
        assertThrows(NoClassDefFoundError.class, () -> field.get(null));
        assertThrows(NoClassDefFoundError.class, () -> field.set(null, 23));
        assertThrows(NoClassDefFoundError.class, () -> FieldOfFailingAsyncInitializer.value++);
    }

    static class FieldOfFailingAsyncInitializer {
        @Reflectable static int value;

        static {
            pause();
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }
    }

    @Test
    public void staticFieldOfFailingInitializer() throws Exception {
        var field = FieldOfFailingInitializer.class.getDeclaredField("value");
        assertThrows(ExceptionInInitializerError.class, () -> field.get(null));
        assertThrows(NoClassDefFoundError.class, () -> field.get(null));
        assertThrows(NoClassDefFoundError.class, () -> field.set(null, 23));
        assertThrows(NoClassDefFoundError.class, () -> FieldOfFailingInitializer.value++);
    }

    static class FieldOfFailingInitializer {
        @Reflectable static int value;

        static {
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }
    }

    // Constructor.newInstance

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void constructorOfAsyncInitializer() throws Exception {
        var constructor = ConstructorOfAsyncInitializer.class.getDeclaredConstructor();
        assertEquals(42, constructor.newInstance().copy);
        assertEquals(42, constructor.newInstance().copy);
        assertEquals(1, ConstructorOfAsyncInitializer.initializations);
    }

    static class ConstructorOfAsyncInitializer {
        static int initializations;
        static int value;
        int copy;

        static {
            pause();
            value = 42;
            initializations++;
        }

        @Reflectable
        ConstructorOfAsyncInitializer() {
            copy = value;
        }
    }

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void constructorOfFailingAsyncInitializer() throws Exception {
        var constructor = ConstructorOfFailingAsyncInitializer.class.getDeclaredConstructor();
        assertThrows(ExceptionInInitializerError.class, constructor::newInstance);
        assertThrows(NoClassDefFoundError.class, constructor::newInstance);
        assertThrows(NoClassDefFoundError.class, () -> ConstructorOfFailingAsyncInitializer.value++);
    }

    static class ConstructorOfFailingAsyncInitializer {
        static int value;

        static {
            pause();
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }

        @Reflectable
        ConstructorOfFailingAsyncInitializer() {
        }
    }

    @Test
    public void constructorOfFailingInitializer() throws Exception {
        var constructor = ConstructorOfFailingInitializer.class.getDeclaredConstructor();
        assertThrows(ExceptionInInitializerError.class, constructor::newInstance);
        assertThrows(NoClassDefFoundError.class, constructor::newInstance);
        assertThrows(NoClassDefFoundError.class, () -> ConstructorOfFailingInitializer.value++);
    }

    static class ConstructorOfFailingInitializer {
        static int value;

        static {
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }

        @Reflectable
        ConstructorOfFailingInitializer() {
        }
    }

    // Class.newInstance

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    @SuppressWarnings("deprecation")
    public void classNewInstanceOfAsyncInitializer() throws Exception {
        assertEquals(42, NewInstanceOfAsyncInitializer.class.newInstance().copy);
        assertEquals(42, NewInstanceOfAsyncInitializer.class.newInstance().copy);
        assertEquals(1, NewInstanceOfAsyncInitializer.initializations);
    }

    public static class NewInstanceOfAsyncInitializer {
        static int initializations;
        static int value;
        int copy;

        static {
            pause();
            value = 42;
            initializations++;
        }

        public NewInstanceOfAsyncInitializer() {
            copy = value;
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    @SuppressWarnings("deprecation")
    public void classNewInstanceOfAsyncConstructor() throws Exception {
        assertEquals(42, NewInstanceOfAsyncConstructor.class.newInstance().value);
    }

    public static class NewInstanceOfAsyncConstructor {
        int value;

        public NewInstanceOfAsyncConstructor() {
            pause();
            value = 42;
        }
    }

    // Class.forName

    // The initializers of the classes that Class.forName finds count their runs here, since a read of their own
    // field would initialize them
    static int forNameInitializations;
    static int forNameByClassNameInitializations;

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void forNameOfAsyncInitializer() throws Exception {
        Class.forName("org.teavm.classlib.java.lang.reflect.ReflectiveClassInitializationTest$"
                + "ForNameOfAsyncInitializer", true, ReflectiveClassInitializationTest.class.getClassLoader());
        assertEquals(1, forNameInitializations);
        assertEquals(42, ForNameOfAsyncInitializer.value);
        assertEquals(1, forNameInitializations);
    }

    static class ForNameOfAsyncInitializer {
        static int value;

        static {
            pause();
            value = 42;
            forNameInitializations++;
        }
    }

    // The name comes from getName here, not from a string constant
    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void forNameByClassNameOfAsyncInitializer() throws Exception {
        Class.forName(ForNameByClassNameOfAsyncInitializer.class.getName());
        assertEquals(1, forNameByClassNameInitializations);
        assertEquals(42, ForNameByClassNameOfAsyncInitializer.value);
        assertEquals(1, forNameByClassNameInitializations);
    }

    static class ForNameByClassNameOfAsyncInitializer {
        static int value;

        static {
            pause();
            value = 42;
            forNameByClassNameInitializations++;
        }
    }

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void forNameOfFailingAsyncInitializer() {
        // TeaVM finds a class by name only when the name is a constant at the call
        assertThrows(ExceptionInInitializerError.class, () -> Class.forName(
                "org.teavm.classlib.java.lang.reflect.ReflectiveClassInitializationTest$"
                + "ForNameOfFailingAsyncInitializer"));
        assertThrows(NoClassDefFoundError.class, () -> Class.forName(
                "org.teavm.classlib.java.lang.reflect.ReflectiveClassInitializationTest$"
                + "ForNameOfFailingAsyncInitializer"));
        assertThrows(NoClassDefFoundError.class, () -> ForNameOfFailingAsyncInitializer.value++);
    }

    static class ForNameOfFailingAsyncInitializer {
        static int value;

        static {
            pause();
            if (failInitializer()) {
                throw new IllegalStateException();
            }
            value = 42;
        }
    }

    // Enum.valueOf reads the enum constants through Class.getEnumConstants

    @Test
    @Disabled(ASYNC_INITIALIZE_GAP)
    @SkipPlatform(TestPlatform.C)
    public void enumValueOfAsyncInitializer() {
        assertEquals("B", Enum.valueOf(EnumOfAsyncInitializer.class, "B").name());
        assertEquals(1, EnumOfAsyncInitializer.initializations);
    }

    enum EnumOfAsyncInitializer {
        A,
        B;

        static int initializations;

        static {
            pause();
            initializations++;
        }
    }
}
