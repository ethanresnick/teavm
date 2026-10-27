/*
 *  Copyright 2020 konsoletyper.
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
package org.teavm.classlib.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ListTest {
    @Test
    public void of() {
        testOf(new String[0], List.of());
        testOf(new String[] { "q" }, List.of("q"));
        testOf(new String[] { "q", "w" }, List.of("q", "w"));
        testOf(new String[] { "q", "w", "e" }, List.of("q", "w", "e"));
        testOf(new String[] { "q", "w", "e", "r" }, List.of("q", "w", "e", "r"));
        testOf(new String[] { "q", "w", "e", "r", "t" }, List.of("q", "w", "e", "r", "t"));
        testOf(new String[] { "q", "w", "e", "r", "t", "y" }, List.of("q", "w", "e", "r", "t", "y"));
        testOf(new String[] { "q", "w", "e", "r", "t", "y", "u" }, List.of("q", "w", "e", "r", "t", "y", "u"));
        testOf(new String[] { "q", "w", "e", "r", "t", "y", "u", "i" },
                List.of("q", "w", "e", "r", "t", "y", "u", "i"));
        testOf(new String[] { "q", "w", "e", "r", "t", "y", "u", "i", "o" },
                List.of("q", "w", "e", "r", "t", "y", "u", "i", "o"));
        testOf(new String[] { "q", "w", "e", "r", "t", "y", "u", "i", "o", "p" },
                List.of("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"));
        testOf(new String[] { "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a" },
                List.of("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a"));
    }

    @Test
    public void copyOfWorks() {
        testOf(new String[0], List.copyOf(new ArrayList<>()));
        testOf(new String[] { "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a" },
                List.copyOf(Arrays.asList("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a")));

        try {
            // copyOf() must throw a NullPointerException on any 'null' element.
            List<String> listWithNull = new ArrayList<>(1);
            listWithNull.add(null);

            List.copyOf(listWithNull);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // ok
        }
    }

    private void testOf(String[] expected, List<String> actual) {
        if (actual.size() != expected.length) {
            fail("Expected size is " + expected.length + ", actual size is " + actual.size());
        }

        for (int i = 0; i < expected.length; ++i) {
            assertEquals(expected[i], actual.get(i), "Element #" + i);
        }

        try {
            actual.get(-1);
            fail("get out of bounds does not throw exception");
        } catch (IndexOutOfBoundsException e) {
            // ok;
        }

        try {
            actual.get(expected.length);
            fail("get out of bounds does not throw exception");
        } catch (IndexOutOfBoundsException e) {
            // ok;
        }

        try {
            actual.set(0, "1");
            fail("set should not work");
        } catch (UnsupportedOperationException e) {
            // ok;
        }

        try {
            actual.remove(0);
            fail("remove should not work");
        } catch (UnsupportedOperationException e) {
            // ok;
        }

        try {
            actual.add("2");
            fail("add should not work");
        } catch (UnsupportedOperationException e) {
            // ok;
        }

        try {
            actual.clear();
            fail("clear should not work");
        } catch (UnsupportedOperationException e) {
            // ok;
        }

        for (int i = 0; i < expected.length; ++i) {
            assertEquals(i, actual.indexOf(expected[i]), "indexOf of element #" + i + " is correct");
        }

        for (String value : expected) {
            assertTrue(actual.contains(value), "contains returns true for existing elements");
        }

        assertFalse(actual.contains("*"), "contains return false for non-existing element");

        assertEquals(expected.length == 0, actual.isEmpty(), "isEmpty works properly");
    }

    @Test
    public void testSequencedCollection() {
        List<String> list = List.of("0", "1", "2", "3", "4", "5", "6");
        List<String> reversed = list.reversed();
        assertEquals("1", reversed.get(5));
        Iterator<String> it = reversed.iterator();
        assertEquals("6", it.next());
        assertEquals("5", it.next());
        assertEquals("6", reversed.getFirst());
        assertEquals("0", reversed.getLast());
        ListIterator<String> lit = reversed.listIterator();
        assertFalse(lit.hasPrevious());
        assertTrue(lit.hasNext());
        assertEquals("6", lit.next());
        assertEquals("5", lit.next());
        assertEquals("5", lit.previous());
        lit = reversed.listIterator(2);
        assertEquals("4", lit.next());
        lit.previous();
        assertEquals("5", lit.previous());
        assertSame(list, reversed.reversed());
        List<String> subList = reversed.subList(3, 5);
        assertEquals("2", subList.getLast());
        assertEquals("3", subList.listIterator().next());
        StringBuilder sb = new StringBuilder();
        subList.forEach(sb::append);
        assertEquals("32", sb.toString());
        List<Integer> duplicates = List.of(0, 1, 2, 3, 2, 1, 0, 0).reversed();
        assertEquals(2, duplicates.indexOf(1));
        assertEquals(6, duplicates.lastIndexOf(1));
        try {
            list.removeFirst();
            fail();
        } catch (UnsupportedOperationException e) {
            // ok
        }
        try {
            reversed.removeLast();
            fail();
        } catch (UnsupportedOperationException e) {
            // ok
        }
    }

    // List: "Unmodifiable Lists ... disallow null elements. Attempts to create them with null elements result in
    // NullPointerException." Checked for every overload of List.of and every position of the null.
    @Test
    public void ofRejectsNull() {
        for (int size = 1; size <= 11; ++size) {
            for (int nullAt = 0; nullAt < size; ++nullAt) {
                Object[] elements = new Object[size];
                for (int i = 0; i < size; ++i) {
                    elements[i] = i == nullAt ? null : "e" + i;
                }
                try {
                    listOf(elements);
                    fail("List.of with " + size + " elements, null at " + nullAt);
                } catch (NullPointerException e) {
                    // ok
                }
                try {
                    List.copyOf(Arrays.asList(elements));
                    fail("List.copyOf with " + size + " elements, null at " + nullAt);
                } catch (NullPointerException e) {
                    // ok
                }
            }
        }
        assertThrows(NullPointerException.class, () -> List.of((Object[]) null));
        assertThrows(NullPointerException.class, () -> List.copyOf(null));
        // Collections.singletonList and Collections.emptyList are separate lists and still accept null.
        assertEquals(null, Collections.singletonList(null).get(0));
        assertTrue(Collections.singletonList(null).contains(null));
        assertFalse(Collections.emptyList().contains(null));
    }

    // Collection.contains, List.indexOf and List.lastIndexOf: "Throws: NullPointerException - if the specified
    // element is null and this list does not permit null elements (optional)". As in OpenJDK, the lists of
    // List.of and List.copyOf throw.
    @Test
    public void queriesRejectNull() {
        for (int size = 0; size <= 11; ++size) {
            Object[] elements = new Object[size];
            for (int i = 0; i < size; ++i) {
                elements[i] = "e" + i;
            }
            checkQueriesRejectNull(listOf(elements), size);
            checkQueriesRejectNull(List.copyOf(Arrays.asList(elements)), size);
        }
    }

    // Stream.toList: "The returned List may contain null elements", so its queries accept null.
    @Test
    public void streamToListAcceptsNull() {
        List<String> list = Stream.of("a", null).toList();
        assertTrue(list.contains(null));
        assertEquals(1, list.indexOf(null));
        assertEquals(1, list.lastIndexOf(null));
        assertFalse(Stream.of("a").toList().contains(null));
    }

    private static void checkQueriesRejectNull(List<Object> list, int size) {
        assertThrows(NullPointerException.class, () -> list.contains(null), "contains, size " + size);
        assertThrows(NullPointerException.class, () -> list.indexOf(null), "indexOf, size " + size);
        assertThrows(NullPointerException.class, () -> list.lastIndexOf(null), "lastIndexOf, size " + size);
        if (size > 0) {
            assertTrue(list.contains("e" + (size - 1)));
            assertEquals(size - 1, list.indexOf("e" + (size - 1)));
            assertEquals(0, list.lastIndexOf("e0"));
        }
        assertFalse(list.contains("x"));
    }

    private static List<Object> listOf(Object[] e) {
        switch (e.length) {
            case 0:
                return List.of();
            case 1:
                return List.of(e[0]);
            case 2:
                return List.of(e[0], e[1]);
            case 3:
                return List.of(e[0], e[1], e[2]);
            case 4:
                return List.of(e[0], e[1], e[2], e[3]);
            case 5:
                return List.of(e[0], e[1], e[2], e[3], e[4]);
            case 6:
                return List.of(e[0], e[1], e[2], e[3], e[4], e[5]);
            case 7:
                return List.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6]);
            case 8:
                return List.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7]);
            case 9:
                return List.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8]);
            case 10:
                return List.of(e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7], e[8], e[9]);
            default:
                return List.of(e);
        }
    }
}
