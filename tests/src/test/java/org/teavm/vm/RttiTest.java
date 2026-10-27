/*
 *  Copyright 2019 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class RttiTest {
    @Test
    public void instanceOfInterface() {
        checkImplements(new A(), true, false, false);
        checkImplements(new B(), false, true, true);
        checkImplements(new C(), false, true, false);
        checkImplements(new D(), false, true, true);
        checkImplements(new E(), true, false, false);
        checkImplements(new F(), false, true, true);
        checkImplements(new G(), true, true, false);
    }

    @Test
    public void objectIsNotSupertypeOfPrimitive() {
        assertFalse(Object.class.isAssignableFrom(int.class));
    }

    @Test
    public void instanceOfObjectArray() {
        List<Object> list = new ArrayList<>();
        list.add("123");
        list.add(new Object[0]);
        list.add(new String[0]);
        list.add(new int[0]);
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            sb.append(item instanceof Object[] ? 't' : 'f');
        }
        assertEquals("fttf", sb.toString());
    }

    @Test
    public void instanceOfSpecificReferenceArray() {
        Object[] arrays = { new String[0], new Integer[0], new Object[0], new int[0], new String[0][] };
        StringBuilder sb = new StringBuilder();
        for (Object o : arrays) {
            sb.append(o instanceof String[] ? 't' : 'f');
        }
        assertEquals("tffff", sb.toString());
    }

    @Test
    public void instanceOfMultiDimReferenceArray() {
        Object[] arrays = { new String[0][], new Integer[0][], new Object[0][], new int[0][], new String[0] };
        StringBuilder sb = new StringBuilder();
        for (Object o : arrays) {
            sb.append(o instanceof String[][] ? 't' : 'f');
            sb.append(o instanceof Object[][] ? 't' : 'f');
        }
        assertEquals("ttftftffff", sb.toString());
    }

    @Test
    public void castToWrongReferenceArrayFails() {
        Object o = new String[] { "a" };
        assertThrows(ClassCastException.class, () -> {
            Integer[] integers = (Integer[]) o;
            assertEquals(0, integers.length);
        });
    }

    @Test
    @SuppressWarnings("unused")
    public void castWithUnusedResultFails() {
        List<String> list = makeList();
        assertThrows(ClassCastException.class, () -> {
            ArrayList<String> arrayList = (ArrayList<String>) list;
        });
    }

    private static List<String> makeList() {
        return List.of("a");
    }

    // JLS 5.5: a cast checks the type of the value at run time and throws ClassCastException if it is not
    // assignable, even when nothing reads the result. null passes every reference cast.
    @Test
    @SuppressWarnings("unused")
    public void unusedCastChecksEveryValueInLoop() {
        Object[] values = { "a", 1, null, new ArrayList<>(), new Object[0], new String[0], 2L, null };
        var log = new StringBuilder();
        for (Object value : values) {
            log.append(outcome(() -> {
                String s = (String) value;
            }));
            log.append(outcome(() -> {
                Number n = (Number) value;
            }));
            log.append(outcome(() -> {
                Object[] a = (Object[]) value;
            }));
            log.append(outcome(() -> {
                String[] a = (String[]) value;
            }));
            log.append(outcome(() -> {
                Runnable r = (Runnable) value;
            }));
            log.append(' ');
        }
        assertEquals("+---- -+--- +++++ ----- --+-- --++- -+--- +++++ ", log.toString());
    }

    @Test
    @SuppressWarnings("unused")
    public void unusedCastInLoopThrowsAtFirstBadValue() {
        Object[] values = { "a", "b", 3, "d" };
        int index = 0;
        try {
            for (; index < values.length; ++index) {
                String s = (String) values[index];
            }
        } catch (ClassCastException e) {
            assertEquals(2, index);
            return;
        }
        fail("expected ClassCastException");
    }

    @Test
    @SuppressWarnings("unused")
    public void unusedCastInInlinedMethodThrows() {
        assertThrows(ClassCastException.class, () -> castToString(1));
        castToString("a");
        castToString(null);
    }

    private static void castToString(Object value) {
        String s = (String) value;
    }

    // javac casts the erased result of a generic method to the type argument, even when the result is ignored.
    @Test
    @SuppressWarnings({ "unused", "unchecked" })
    public void unusedGenericResultIsChecked() {
        List<String> list = (List<String>) (List<?>) List.of(1);
        assertThrows(ClassCastException.class, () -> {
            String s = list.get(0);
        });
        assertThrows(ClassCastException.class, () -> {
            String s = RttiTest.<String>uncheckedCast(1);
        });
        String s = RttiTest.<String>uncheckedCast(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object value) {
        return (T) value;
    }

    @Test
    @SuppressWarnings("unused")
    public void unusedCastInLambdaThrows() {
        Object value = 1;
        Runnable failing = () -> {
            String s = (String) value;
        };
        assertThrows(ClassCastException.class, failing::run);
        Object nullValue = null;
        Runnable passing = () -> {
            String s = (String) nullValue;
        };
        passing.run();
    }

    @Test
    @SuppressWarnings("unused")
    public void handlerSeesStateBeforeFailedUnusedCast() {
        Object value = 1;
        int state = 0;
        try {
            state = 1;
            String s = (String) value;
            state = 2;
        } catch (ClassCastException e) {
            assertEquals(1, state);
            return;
        }
        fail("expected ClassCastException");
    }

    private static char outcome(Runnable action) {
        try {
            action.run();
            return '+';
        } catch (ClassCastException e) {
            return '-';
        }
    }

    private void checkImplements(Object o, boolean i, boolean j, boolean k) {
        assertTrue(!i ^ o instanceof I, predicate(o, i, "I"));
        assertTrue(!j ^ o instanceof J, predicate(o, j, "J"));
        assertTrue(!k ^ o instanceof K, predicate(o, k, "K"));
    }

    private String predicate(Object o, boolean b, String name) {
        return o.getClass().getName() + " should" + (b ? " not" : "") + " implement " + name;
    }

    static class A implements I {
    }

    static class B implements K {
    }

    static class C implements J {
    }

    static class D extends C implements K {
    }

    static class E extends A implements I {
    }

    static class F extends B implements J {
    }

    static class G extends C implements I {
    }

    interface I {
    }

    interface J {
    }

    interface K extends J {
    }
}
