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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.Reflectable;
import org.teavm.junit.TeaVMTest;

/**
 * Modifiers, {@code isDefault()}, {@code toString()} and {@code toGenericString()} of {@link Method}.
 * No test here calls {@code Method.invoke}. TeaVM keeps reflection metadata only for the methods that the program
 * can reach, so {@link #useMethods()} calls the static and non-virtual ones. The program never calls the other
 * instance methods, and the linker drops their bodies and marks them abstract: their modifiers must still be the
 * declared ones.
 */
@TeaVMTest
public class MethodDescriptionTest {
    private static final String P = "org.teavm.classlib.java.lang.reflect.MethodDescriptionTest$";

    // The modifiers the Java language defines for methods (Modifier.methodModifiers()). The JDK's getModifiers()
    // can also include the VM's bridge and varargs bits.
    private static final int METHOD_MODIFIERS = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE
            | Modifier.ABSTRACT | Modifier.STATIC | Modifier.FINAL | Modifier.SYNCHRONIZED | Modifier.NATIVE
            | Modifier.STRICT;

    @Test
    public void modifiersOfClassMethods() throws NoSuchMethodException {
        useMethods();
        check(Kinds.class, "staticM", Modifier.PUBLIC | Modifier.STATIC, "public static void " + P + "Kinds.staticM()");
        check(Kinds.class, "abstractM", Modifier.PUBLIC | Modifier.ABSTRACT,
                "public abstract void " + P + "Kinds.abstractM()");
        check(Kinds.class, "syncM", Modifier.PUBLIC | Modifier.SYNCHRONIZED,
                "public synchronized void " + P + "Kinds.syncM()");
        check(Kinds.class, "finalM", Modifier.PUBLIC | Modifier.FINAL, "public final void " + P + "Kinds.finalM()");
        check(Kinds.class, "protectedM", Modifier.PROTECTED, "protected void " + P + "Kinds.protectedM()");
        check(Kinds.class, "privateM", Modifier.PRIVATE, "private void " + P + "Kinds.privateM()");
        check(Kinds.class, "packageM", 0, "void " + P + "Kinds.packageM()");
        check(Kinds.class, "nativeM", Modifier.PUBLIC | Modifier.NATIVE, "public native void " + P + "Kinds.nativeM()");
        check(Kinds.class, "combined", Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL | Modifier.SYNCHRONIZED,
                "public static final synchronized int " + P + "Kinds.combined(int[],java.lang.Object[][])"
                        + " throws java.io.IOException,java.lang.IllegalStateException",
                int[].class, Object[][].class);
    }

    @Test
    public void modifiersOfInterfaceMethods() throws NoSuchMethodException {
        useMethods();
        // Javadoc of isDefault(): "A default method is a public non-abstract instance method, that is, a non-static
        // method with a body, declared in an interface."
        // Javadoc of toString(): "public, protected or private first, and then other modifiers in the following
        // order: abstract, default, static, final, synchronized, native, strictfp."
        check(Iface.class, "defaultM", Modifier.PUBLIC,
                "public default int[] " + P + "Iface.defaultM(java.lang.String[]) throws java.io.IOException",
                String[].class);
        check(Iface.class, "abstractI", Modifier.PUBLIC | Modifier.ABSTRACT,
                "public abstract void " + P + "Iface.abstractI()");
        check(Iface.class, "staticI", Modifier.PUBLIC | Modifier.STATIC, "public static void " + P + "Iface.staticI()");
        check(Iface.class, "privateI", Modifier.PRIVATE, "private void " + P + "Iface.privateI()");
    }

    @Test
    public void varargs() throws NoSuchMethodException {
        useMethods();
        var method = Kinds.class.getDeclaredMethod("varargsM", String[].class);
        assertTrue(method.isVarArgs());
        assertEquals(Modifier.PUBLIC, method.getModifiers() & METHOD_MODIFIERS);
        // toString() prints the array type, toGenericString() "Type...".
        assertEquals("public void " + P + "Kinds.varargsM(java.lang.String[])", method.toString());
        assertEquals("public void " + P + "Kinds.varargsM(java.lang.String...)", method.toGenericString());
        assertFalse(Kinds.class.getDeclaredMethod("staticM").isVarArgs());
    }

    @Test
    public void genericString() throws NoSuchMethodException {
        useMethods();
        var bounded = Generic.class.getDeclaredMethod("bounded", List.class, Object.class);
        assertEquals("public <T extends java.lang.Number & java.lang.Comparable<T>> T " + P
                + "Generic.bounded(java.util.List<? extends T>,E) throws java.io.IOException",
                bounded.toGenericString());
        assertEquals("public java.lang.Number " + P + "Generic.bounded(java.util.List,java.lang.Object)"
                + " throws java.io.IOException", bounded.toString());

        var listOf = Generic.class.getDeclaredMethod("listOf", Object[].class);
        assertEquals("public static <T> java.util.List<T> " + P + "Generic.listOf(T...)", listOf.toGenericString());
        assertEquals("public static java.util.List " + P + "Generic.listOf(java.lang.Object[])", listOf.toString());

        var two = Generic.class.getDeclaredMethod("two", Object.class, Object[].class);
        assertEquals("public <A,B extends A> void " + P + "Generic.two(A,B[])", two.toGenericString());

        var genericArray = Generic.class.getDeclaredMethod("genericArray", Comparable.class);
        assertEquals("public java.util.List<java.lang.String>[] " + P
                + "Generic.genericArray(java.lang.Comparable<? super E>)", genericArray.toGenericString());

        var plain = Generic.class.getDeclaredMethod("plain", int.class);
        assertEquals("public int " + P + "Generic.plain(int)", plain.toGenericString());
        assertEquals(plain.toString(), plain.toGenericString());
    }

    // Known gap: TeaVM keeps no generic exception types, so toGenericString() prints the erasure of a type
    // variable in the throws clause. Javadoc: "followed by a comma-separated list of the generic thrown exception
    // types".
    @Test
    @Disabled("TeaVM keeps no generic exception types: toGenericString() prints 'throws java.lang.Exception'")
    public void genericStringWithTypeVariableInThrows() throws NoSuchMethodException {
        useMethods();
        var method = Generic.class.getDeclaredMethod("throwsVariable");
        assertEquals("public <X extends java.lang.Exception> void " + P + "Generic.throwsVariable() throws X",
                method.toGenericString());
    }

    @Test
    public void genericParameterTypesOfNonGenericMethod() throws NoSuchMethodException {
        useMethods();
        var method = Kinds.class.getDeclaredMethod("combined", int[].class, Object[][].class);
        assertArrayEquals(new Type[] { int[].class, Object[][].class }, method.getGenericParameterTypes());
        var types = method.getGenericParameterTypes();
        types[0] = String.class;
        assertEquals(int[].class, method.getGenericParameterTypes()[0]);
        assertEquals(0, Kinds.class.getDeclaredMethod("staticM").getGenericParameterTypes().length);
        assertEquals(int.class, method.getGenericReturnType());
    }

    @Test
    public void bridge() throws NoSuchMethodException {
        useMethods();
        var bridge = Bridged.class.getDeclaredMethod("compareTo", Object.class);
        assertTrue(bridge.isBridge());
        assertTrue(bridge.isSynthetic());
        assertEquals(Modifier.PUBLIC, bridge.getModifiers() & METHOD_MODIFIERS);
        assertEquals("public int " + P + "Bridged.compareTo(java.lang.Object)", bridge.toString());
        var impl = Bridged.class.getDeclaredMethod("compareTo", Bridged.class);
        assertFalse(impl.isBridge());
        assertEquals("public int " + P + "Bridged.compareTo(" + P + "Bridged)", impl.toString());
    }

    // toString() prints types with Class.getTypeName(): "int[]" rather than the binary name "[I".
    @Test
    public void classTypeNames() {
        assertEquals("int", int.class.getTypeName());
        assertEquals("int[]", int[].class.getTypeName());
        assertEquals("long[][][]", long[][][].class.getTypeName());
        assertEquals("java.lang.String", String.class.getTypeName());
        assertEquals("java.lang.Object[][]", Object[][].class.getTypeName());
        assertEquals(P + "Kinds[]", Kinds[].class.getTypeName());
    }

    private static void useMethods() {
        Kinds.staticM();
        try {
            Kinds.combined(null, null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Kinds.callNonVirtual(new KindsImpl());
        Iface.staticI();
        Generic.listOf();
        new Generic<Object>().<RuntimeException>throwsVariable();
    }

    private static void check(Class<?> cls, String name, int modifiers, String string, Class<?>... params)
            throws NoSuchMethodException {
        var method = cls.getDeclaredMethod(name, params);
        assertEquals(modifiers, method.getModifiers() & METHOD_MODIFIERS, name);
        assertEquals(string, method.toString(), name);
        assertEquals(string, method.toGenericString(), name);
        var isDefault = cls.isInterface() && modifiers == Modifier.PUBLIC;
        assertEquals(isDefault, method.isDefault(), name);
        assertFalse(method.isBridge(), name);
        assertFalse(method.isSynthetic(), name);
    }

    abstract static class Kinds {
        @Reflectable
        public static void staticM() {
        }

        @Reflectable
        public abstract void abstractM();

        @Reflectable
        public synchronized void syncM() {
        }

        @Reflectable
        public final void finalM() {
        }

        @Reflectable
        protected void protectedM() {
        }

        @Reflectable
        private void privateM() {
        }

        @Reflectable
        void packageM() {
        }

        @Reflectable
        public native void nativeM();

        @Reflectable
        public void varargsM(String... values) {
        }

        static void callNonVirtual(Kinds kinds) {
            kinds.finalM();
            kinds.protectedM();
            kinds.privateM();
            kinds.packageM();
        }

        @Reflectable
        public static final synchronized int combined(int[] a, Object[][] b) throws IOException,
                IllegalStateException {
            return 0;
        }
    }

    static class KindsImpl extends Kinds {
        @Override
        public void abstractM() {
        }
    }

    interface Iface {
        @Reflectable
        default int[] defaultM(String[] names) throws IOException {
            return null;
        }

        @Reflectable
        void abstractI();

        @Reflectable
        static void staticI() {
        }

        @Reflectable
        private void privateI() {
        }
    }

    static class Bridged implements Comparable<Bridged> {
        @Reflectable
        @Override
        public int compareTo(Bridged o) {
            return 0;
        }
    }

    static class Generic<E> {
        @Reflectable
        public <T extends Number & Comparable<T>> T bounded(List<? extends T> list, E e) throws IOException {
            return null;
        }

        @Reflectable
        @SafeVarargs
        public static <T> List<T> listOf(T... items) {
            return null;
        }

        @Reflectable
        public <A, B extends A> void two(A a, B[] b) {
        }

        @Reflectable
        public List<String>[] genericArray(Comparable<? super E> c) {
            return null;
        }

        @Reflectable
        public int plain(int x) {
            return x;
        }

        @Reflectable
        public <X extends Exception> void throwsVariable() throws X {
        }
    }
}
