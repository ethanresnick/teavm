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
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.Reflectable;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
@EachTestCompiledSeparately
public class ClassReflectionAPITest {
    @Test
    public void nongenericSupertypes() {
        assertEquals(Object.class, Plain.class.getGenericSuperclass());
        assertEquals(Runnable.class, Plain.class.getGenericInterfaces()[0]);
        assertEquals(0, Runnable.class.getGenericInterfaces().length);
    }

    @Test
    public void packageNames() {
        assertEquals("java.lang", String.class.getPackage().getName());
        assertEquals("org.teavm.classlib.java.lang", Plain.class.getPackage().getName());
        assertNull(String[].class.getPackage());
        assertNull(int.class.getPackage());
        assertNull(int[].class.getPackage());
    }

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
    public void bootstrapLookupIsRestricted() throws Exception {
        assertEquals(String.class, Class.forName("java.lang.String", false, null));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(Plain.class.getName(), false, null));
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
    }

    public static class BadInitialize {
        static {
            if (Boolean.parseBoolean("true")) {
                throw new InitializerError();
            }
        }
    }

    @Test
    public void lateFoundClassHasNameAndReflectiveResultType() throws Exception {
        var object = new FoundLate();
        String name = new String("org.teavm.classlib.java.lang.ClassReflectionAPITest$FoundLate".toCharArray());
        var type = Class.forName(name);
        assertEquals(73, type.getDeclaredField("value").getInt(object));
    }

    public static class Plain implements Runnable {
        @Override
        public void run() {
        }
    }

    public static class FoundLate {
        @Reflectable
        public int value = 73;
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
        static {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            AsyncInitialization.counter++;
        }
    }
}
