/*
 *  Copyright 2014 Alexey Andreev.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ArrayDequeTest {
    @Test
    public void addsToFront() {
        Deque<Integer> deque = new ArrayDeque<>();
        deque.addFirst(1);
        deque.addFirst(2);
        Iterator<Integer> iter = deque.iterator();
        assertEquals(2, deque.size());
        assertEquals((Integer) 2, iter.next());
        assertEquals((Integer) 1, iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void addsToBack() {
        Deque<Integer> deque = new ArrayDeque<>();
        deque.addLast(1);
        deque.addLast(2);
        Iterator<Integer> iter = deque.iterator();
        assertEquals(2, deque.size());
        assertEquals((Integer) 1, iter.next());
        assertEquals((Integer) 2, iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void addsManyToFront() {
        Deque<Integer> deque = new ArrayDeque<>();
        for (int i = 0; i < 1000; ++i) {
            deque.addFirst(i);
        }
        assertEquals(1000, deque.size());
        Iterator<Integer> iter = deque.iterator();
        assertEquals((Integer) 999, iter.next());
        for (int i = 2; i < 500; ++i) {
            iter.next();
        }
        assertEquals((Integer) 500, iter.next());
        for (int i = 1; i < 500; ++i) {
            iter.next();
        }
        assertEquals((Integer) 0, iter.next());
    }

    @Test
    public void addsManyToBack() {
        Deque<Integer> deque = new ArrayDeque<>();
        for (int i = 0; i < 1000; ++i) {
            deque.addLast(i);
        }
        assertEquals(1000, deque.size());
        Iterator<Integer> iter = deque.iterator();
        assertEquals((Integer) 0, iter.next());
        for (int i = 1; i < 500; ++i) {
            iter.next();
        }
        assertEquals((Integer) 500, iter.next());
        for (int i = 2; i < 500; ++i) {
            iter.next();
        }
        assertEquals((Integer) 999, iter.next());
    }

    @Test
    public void removesFromFront() {
        Deque<Integer> deque = new ArrayDeque<>();
        deque.addFirst(1);
        deque.addFirst(2);
        assertEquals((Integer) 2, deque.removeFirst());
        assertEquals((Integer) 1, deque.removeFirst());
        assertEquals(0, deque.size());
    }

    @Test
    public void removesFromBack() {
        Deque<Integer> deque = new ArrayDeque<>();
        deque.addFirst(1);
        deque.addFirst(2);
        assertEquals((Integer) 1, deque.removeLast());
        assertEquals((Integer) 2, deque.removeLast());
        assertEquals(0, deque.size());
    }

    @Test
    public void addAndRemoves() {
        Deque<Integer> deque = new ArrayDeque<>();
        for (int i = 0; i < 100; ++i) {
            deque.addLast(i);
        }
        assertEquals((Integer) 0, deque.removeFirst());
        for (int i = 1; i < 20; ++i) {
            deque.removeFirst();
        }
        assertEquals((Integer) 20, deque.removeFirst());
        for (int i = 101; i < 111; ++i) {
            deque.addLast(i);
        }
        assertEquals((Integer) 110, deque.removeLast());
        for (int i = 2; i < 40; ++i) {
            deque.removeLast();
        }
        assertEquals((Integer) 70, deque.removeLast());
    }

    @Test
    public void eachRemovedObjectShouldReduceTheSizeByOne() {
        ArrayDeque<Object> arrayDeque = new ArrayDeque<>();
        Object object1 = new Object();
        Object object2 = new Object();
        Object object3 = new Object();
        arrayDeque.add(object1);
        assertEquals(1, arrayDeque.size());
        arrayDeque.remove(object1);
        assertEquals(0, arrayDeque.size());
        arrayDeque.add(object1);
        arrayDeque.add(object2);
        arrayDeque.add(object3);
        assertEquals(3, arrayDeque.size());
        arrayDeque.remove(object1);
        arrayDeque.remove(object2);
        arrayDeque.remove(object3);
        assertEquals(0, arrayDeque.size());
        arrayDeque.remove(object1);
        assertEquals(0, arrayDeque.size());
    }

    @Test
    public void removeFirstShouldNotContainTheFirstAddedObject() {
        ArrayDeque<Object> arrayDeque1 = new ArrayDeque<>();
        Object object1 = new Object();
        Object object2 = new Object();
        Object object3 = new Object();
        arrayDeque1.add(object1);
        arrayDeque1.add(object2);
        arrayDeque1.add(object3);
        arrayDeque1.removeFirst();
        assertEquals(2, arrayDeque1.size());
        assertTrue(arrayDeque1.contains(object2));
        assertTrue(arrayDeque1.contains(object3));

        ArrayDeque<Object> arrayDeque2 = new ArrayDeque<>();
        arrayDeque2.add(object1);
        arrayDeque2.add(object2);
        arrayDeque2.add(object3);
        arrayDeque2.remove(object1);
        arrayDeque2.removeFirst();
        assertEquals(1, arrayDeque2.size());
        assertTrue(arrayDeque2.contains(object3));

        ArrayDeque<Object> arrayDeque3 = new ArrayDeque<>();
        arrayDeque3.add(object1);
        arrayDeque3.add(object2);
        arrayDeque3.add(object3);
        arrayDeque3.remove(object2);
        arrayDeque3.removeFirst();
        assertEquals(1, arrayDeque3.size());
        assertTrue(arrayDeque3.contains(object3));

        ArrayDeque<Object> arrayDeque4 = new ArrayDeque<>();
        arrayDeque4.add(object1);
        arrayDeque4.add(object2);
        arrayDeque4.add(object3);
        arrayDeque4.remove(object3);
        arrayDeque4.removeFirst();
        assertEquals(1, arrayDeque4.size());
        assertTrue(arrayDeque4.contains(object2));
    }

    @Test
    public void removeLastShouldNotContainTheLastAddedObject() {
        ArrayDeque<Object> arrayDeque1 = new ArrayDeque<>();
        Object object1 = new Object();
        Object object2 = new Object();
        Object object3 = new Object();
        arrayDeque1.add(object1);
        arrayDeque1.add(object2);
        arrayDeque1.add(object3);
        arrayDeque1.removeLast();
        assertEquals(2, arrayDeque1.size());
        assertTrue(arrayDeque1.contains(object1));
        assertTrue(arrayDeque1.contains(object2));

        ArrayDeque<Object> arrayDeque2 = new ArrayDeque<>();
        arrayDeque2.add(object1);
        arrayDeque2.add(object2);
        arrayDeque2.add(object3);
        arrayDeque2.remove(object3);
        arrayDeque2.removeLast();
        assertEquals(1, arrayDeque2.size());
        assertTrue(arrayDeque2.contains(object1));

        ArrayDeque<Object> arrayDeque3 = new ArrayDeque<>();
        arrayDeque3.add(object1);
        arrayDeque3.add(object2);
        arrayDeque3.add(object3);
        arrayDeque3.remove(object2);
        arrayDeque3.removeLast();
        assertEquals(1, arrayDeque3.size());
        assertTrue(arrayDeque3.contains(object1));

        ArrayDeque<Object> arrayDeque4 = new ArrayDeque<>();
        arrayDeque4.add(object1);
        arrayDeque4.add(object2);
        arrayDeque4.add(object3);
        arrayDeque4.remove(object3);
        arrayDeque4.removeLast();
        assertEquals(1, arrayDeque4.size());
        assertTrue(arrayDeque4.contains(object1));
    }

    @Test
    public void removeElementInWrappedArray() {
        ArrayDeque<Object> arrayDeque = new ArrayDeque<>(8);
        for (int i = 0; i < 4; ++i) {
            arrayDeque.addLast(0);
        }
        for (int i = 0; i < 4; ++i) {
            arrayDeque.removeFirst();
        }
        for (int i = 0; i < 6; ++i) {
            arrayDeque.addLast(i);
        }
        for (int i = 5; i >= 0; --i) {
            arrayDeque.remove(i);
            arrayDeque.addLast(23);
            arrayDeque.removeLast();
        }
    }

    @Test
    public void testSequencedCollectionReadOnly() {
        Deque<String> deque = new ArrayDeque<>(List.of("0", "1", "2", "3", "4", "5", "6"));
        Deque<String> reversed = deque.reversed();
        Iterator<String> it = reversed.iterator();
        assertEquals("6", it.next());
        assertEquals("5", it.next());
        assertEquals("6", reversed.getFirst());
        assertEquals("0", reversed.getLast());
        assertEquals("6", reversed.peek());
        assertEquals("6", reversed.peekFirst());
        assertEquals("0", reversed.peekLast());
    }

    @Test
    public void testSequencedCollectionMutations() {
        Deque<String> deque = new ArrayDeque<>(List.of("a", "b", "c", "d"));
        assertEquals("a", deque.removeFirst());
        assertEquals("d", deque.removeLast());
        deque.addFirst("u");
        deque.addLast("e");
        assertArrayEquals(new String[] { "u", "b", "c", "e" }, deque.toArray());
        deque = new ArrayDeque<>(List.of("a", "b", "c", "d")).reversed();
        assertEquals("d", deque.removeFirst());
        assertEquals("a", deque.removeLast());
        deque.addFirst("u");
        deque.addLast("e");
        assertEquals("u", deque.remove());
        deque.add("f");
        assertArrayEquals(new String[] { "c", "b", "e", "f" }, deque.toArray());
    }

    @Test
    public void testSequencedCollectionIterator() {
        Deque<String> deque = new ArrayDeque<>(List.of("a", "b", "c", "d"));
        deque.addLast("e");
        deque.addLast("f");
        Iterator<String> it = deque.iterator();
        assertEquals("a", it.next());
        it.remove();
        assertEquals("b", it.next());
        it.remove();
        assertEquals("c", it.next());
        it.remove();
        assertEquals("d", it.next());
        it.remove();
        assertEquals("e", it.next());
        it.remove();
        assertEquals("f", it.next());
        it.remove();
        assertTrue(deque.isEmpty());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
        deque = new ArrayDeque<>(List.of("a", "b", "c", "d")).reversed();
        it = deque.iterator();
        assertEquals("d", it.next());
        assertEquals("c", it.next());
        it.remove();
        assertArrayEquals(new String[] { "d", "b", "a" }, deque.toArray());
        deque = new ArrayDeque<>(List.of("a", "b", "c", "d")).reversed();
        it = deque.iterator();
        assertEquals("d", it.next());
        assertEquals("c", it.next());
        it.remove();
        assertEquals("b", it.next());
        it.remove();
        assertEquals("a", it.next());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
        assertArrayEquals(new String[] { "d", "a" }, deque.toArray());
    }

    @Test
    public void sequenceCollectionMethodsOnEmpty() {
        var empty = new ArrayDeque<>();

        try {
            empty.getFirst();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
        try {
            empty.getLast();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }

        try {
            empty.removeFirst();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
        try {
            empty.removeLast();
            fail();
        } catch (NoSuchElementException e) {
            // ok
        }
    }

    static class CustomDeque extends ArrayDeque<String> {
        String tag;
    }

    // ArrayDeque.clone: "Returns a copy of this deque". Object.clone: by convention, the copy comes from
    // super.clone(), so "x.clone().getClass() == x.getClass()", and it is independent of the original.
    @Test
    public void cloneKeepsClassAndIsIndependent() {
        var deque = new CustomDeque();
        deque.tag = "t";
        // Wrap around the end of the array.
        deque.addLast("b");
        deque.addLast("c");
        deque.addFirst("a");
        var clone = (CustomDeque) deque.clone();
        assertEquals(CustomDeque.class, clone.getClass());
        assertEquals("t", clone.tag);
        assertEquals(List.of("a", "b", "c"), List.copyOf(clone));
        for (var i = 0; i < 20; ++i) {
            clone.addLast("x" + i);
        }
        clone.removeFirst();
        deque.removeLast();
        assertEquals(List.of("a", "b"), List.copyOf(deque));
        assertEquals(22, clone.size());
        assertEquals("b", clone.peekFirst());
        assertEquals("x19", clone.peekLast());
    }

    // Iterator.remove(): "Removes from the underlying collection the last element returned by this iterator". The
    // iteration then goes on with the element after it, so it sees every element exactly once. Every subset of the
    // elements is removed, in both directions, at every head position and every size that fits without growing, so
    // removals close to either end, next to each other and across the end of the array are covered.
    @Test
    public void iteratorRemoveEverySubsetAtEveryOffset() {
        var failures = new ArrayList<String>();
        for (var capacity = 1; capacity <= 5; ++capacity) {
            for (var start = 0; start <= capacity; ++start) {
                for (var count = 0; count <= capacity; ++count) {
                    for (var mask = 0; mask < 1 << count; ++mask) {
                        for (var descending : new boolean[] { false, true }) {
                            var failure = removeSubset(capacity, start, count, mask, descending);
                            if (failure != null) {
                                failures.add("capacity=" + capacity + " start=" + start + " count=" + count
                                        + " mask=" + mask + " descending=" + descending + ": " + failure);
                            }
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    private static String removeSubset(int capacity, int start, int count, int mask, boolean descending) {
        var deque = new ArrayDeque<Integer>(capacity);
        for (var i = 0; i < start; ++i) {
            deque.addLast(-1);
            deque.pollFirst();
        }
        var expectedSeen = new ArrayList<Integer>();
        var expectedRest = new ArrayList<Integer>();
        for (var i = 0; i < count; ++i) {
            deque.addLast(i);
            expectedSeen.add(descending ? count - 1 - i : i);
            if ((mask & (1 << i)) == 0) {
                expectedRest.add(i);
            }
        }
        try {
            var seen = new ArrayList<Integer>();
            var it = descending ? deque.descendingIterator() : deque.iterator();
            while (it.hasNext() && seen.size() <= count) {
                var e = it.next();
                seen.add(e);
                if ((mask & (1 << e)) != 0) {
                    it.remove();
                }
            }
            deque.addLast(100);
            deque.addFirst(-100);
            expectedRest.add(100);
            expectedRest.add(0, -100);
            var rest = List.copyOf(deque);
            if (!seen.equals(expectedSeen) || !rest.equals(expectedRest)) {
                return "expected seen=" + expectedSeen + " rest=" + expectedRest + ", got seen=" + seen
                        + " rest=" + rest;
            }
            return null;
        } catch (RuntimeException e) {
            return e instanceof NoSuchElementException ? "NoSuchElementException" : "RuntimeException";
        }
    }
}
