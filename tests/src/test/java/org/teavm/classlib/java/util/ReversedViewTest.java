/*
 *  Copyright 2026 TeaVM contributors.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.SequencedCollection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * SequencedCollection.reversed(): "Returns a reverse-ordered view of this collection. The encounter order
 * of elements in the returned view is the inverse of the encounter order of elements in this collection.
 * [...] Modifications to the view write through to the underlying collection."
 */
@TeaVMTest
public class ReversedViewTest {
    private static List<SequencedCollection<Integer>> reversedViewsOf1To4() {
        List<SequencedCollection<Integer>> result = new ArrayList<>();
        List<Integer> elements = List.of(1, 2, 3, 4);
        result.add(new ArrayDeque<>(elements).reversed());
        result.add(new ConcurrentLinkedDeque<>(elements).reversed());
        result.add(new LinkedBlockingDeque<>(elements).reversed());
        result.add(new LinkedList<>(elements).reversed());
        result.add(new ArrayList<>(elements).reversed());
        result.add(new LinkedHashSet<>(elements).reversed());
        result.add(new TreeSet<>(elements).reversed());
        result.add(new PlainSortedSet<>(elements).reversed());
        var map = new LinkedHashMap<Integer, String>();
        for (var e : elements) {
            map.put(e, "v" + e);
        }
        result.add(map.reversed().sequencedKeySet());
        result.add(map.sequencedKeySet().reversed());
        return result;
    }

    @Test
    public void containsAll() {
        // Collection.containsAll: "Returns true if this collection contains all of the elements in the
        // specified collection."
        for (var view : reversedViewsOf1To4()) {
            String name = view.getClass().getName();
            assertTrue(view.containsAll(List.of()), name);
            assertTrue(view.containsAll(List.of(1)), name);
            assertTrue(view.containsAll(List.of(4, 1)), name);
            assertTrue(view.containsAll(List.of(1, 2, 3, 4)), name);
            assertTrue(view.containsAll(List.of(2, 2, 3)), name);
            assertTrue(view.containsAll(view), name);
            assertTrue(view.containsAll(new ArrayList<>(view)), name);
            assertFalse(view.containsAll(List.of(5)), name);
            assertFalse(view.containsAll(List.of(1, 5)), name);
            assertFalse(view.containsAll(List.of(5, 1)), name);
            assertFalse(view.containsAll(List.of(1, 2, 3, 4, 0)), name);
        }
    }

    @Test
    public void containsAllOnEmptyView() {
        List<SequencedCollection<Integer>> views = List.of(
                new ArrayDeque<Integer>().reversed(),
                new LinkedList<Integer>().reversed(),
                new ArrayList<Integer>().reversed(),
                new LinkedHashSet<Integer>().reversed(),
                new TreeSet<Integer>().reversed());
        for (var view : views) {
            assertTrue(view.containsAll(List.of()));
            assertFalse(view.containsAll(List.of(1)));
        }
    }

    @Test
    public void reversedDequeWritesInReverseOrder() {
        Deque<Integer> base = new ArrayDeque<>(List.of(1, 2));
        Deque<Integer> reversed = base.reversed();
        assertTrue(reversed.addAll(List.of(3, 4)));
        assertFalse(reversed.addAll(List.of()));
        assertEquals(List.of(4, 3, 1, 2), new ArrayList<>(base));
        assertEquals(List.of(2, 1, 3, 4), new ArrayList<>(reversed));
        assertEquals("[2, 1, 3, 4]", reversed.toString());
        assertArrayEquals(new Object[] { 2, 1, 3, 4 }, reversed.toArray());
        assertArrayEquals(new Integer[] { 2, 1, 3, 4 }, reversed.toArray(new Integer[0]));

        // remove(Object) removes the first occurrence in the view, that is the last one in the base deque
        base = new ArrayDeque<>(List.of(1, 2, 1, 3));
        reversed = base.reversed();
        assertTrue(reversed.remove(1));
        assertEquals(List.of(1, 2, 3), new ArrayList<>(base));
        assertFalse(reversed.remove(9));

        base = new ArrayDeque<>(List.of(1, 2, 3, 4, 5));
        reversed = base.reversed();
        assertTrue(reversed.removeAll(List.of(2, 4)));
        assertFalse(reversed.removeAll(List.of(9)));
        assertEquals(List.of(1, 3, 5), new ArrayList<>(base));
        assertTrue(reversed.retainAll(List.of(5, 1)));
        assertFalse(reversed.retainAll(List.of(5, 1)));
        assertEquals(List.of(5, 1), new ArrayList<>(reversed));
    }

    @Test
    public void reversedLinkedListWritesInReverseOrder() {
        LinkedList<Integer> base = new LinkedList<>(List.of(1, 2, 3));
        LinkedList<Integer> reversed = base.reversed();

        // Collection.add / Queue.offer append to the end of the view, which is the start of the base list
        assertTrue(reversed.add(4));
        assertEquals(List.of(4, 1, 2, 3), base);
        assertTrue(reversed.offer(5));
        assertEquals(List.of(5, 4, 1, 2, 3), base);
        assertEquals(List.of(3, 2, 1, 4, 5), reversed);

        // Queue.remove() removes the head of the view, which is the end of the base list
        assertEquals(3, reversed.remove());
        assertEquals(List.of(5, 4, 1, 2), base);
        assertEquals("[2, 1, 4, 5]", reversed.toString());
    }

    @Test
    public void reversedLinkedListIndexOf() {
        // List.indexOf / lastIndexOf: "or -1 if this list does not contain the element"
        LinkedList<Integer> reversed = new LinkedList<>(List.of(0, 1, 2, 1, 3)).reversed();
        // reversed: [3, 1, 2, 1, 0]
        assertEquals(-1, reversed.indexOf(9));
        assertEquals(-1, reversed.lastIndexOf(9));
        assertEquals(-1, reversed.indexOf(null));
        assertEquals(0, reversed.indexOf(3));
        assertEquals(1, reversed.indexOf(1));
        assertEquals(3, reversed.lastIndexOf(1));
        assertEquals(4, reversed.lastIndexOf(0));
        assertEquals(-1, new LinkedList<Integer>().reversed().indexOf(1));
        assertEquals(-1, new LinkedList<Integer>().reversed().lastIndexOf(1));
    }

    @Test
    public void reversedListIteratorAdd() {
        // ListIterator.add: "The element is inserted immediately before the element that would be returned
        // by next(), if any, and after the element that would be returned by previous(), if any. [...]
        // a subsequent call to next would be unaffected, and a subsequent call to previous would return
        // the new element."
        List<List<Integer>> bases = List.of(new ArrayList<>(List.of(1, 2, 3)), new LinkedList<>(List.of(1, 2, 3)));
        for (List<Integer> base : bases) {
            List<Integer> reversed = base.reversed();
            ListIterator<Integer> it = reversed.listIterator(1);
            it.add(9);
            assertEquals(List.of(1, 2, 9, 3), base);
            assertEquals(List.of(3, 9, 2, 1), reversed);
            assertEquals(2, it.nextIndex());
            assertEquals(1, it.previousIndex());
            assertEquals(2, it.next());
            assertEquals(3, it.nextIndex());

            it = reversed.listIterator(1);
            it.add(8);
            assertEquals(8, it.previous());
            assertEquals(3, it.previous());
            assertFalse(it.hasPrevious());

            it = reversed.listIterator();
            it.add(7);
            it.add(6);
            assertEquals(List.of(7, 6, 3, 8, 9, 2, 1), reversed);
            it = reversed.listIterator(reversed.size());
            it.add(0);
            assertFalse(it.hasNext());
            assertEquals(List.of(7, 6, 3, 8, 9, 2, 1, 0), reversed);
        }
    }

    @Test
    public void listIteratorAddKeepsNextElement() {
        // The reversed list iterator delegates add() to the base list iterator, so check the base one too.
        for (List<Integer> list : List.of(new ArrayList<>(List.of(1, 2)), new Vector<>(List.of(1, 2)),
                new LinkedList<>(List.of(1, 2)))) {
            ListIterator<Integer> it = list.listIterator();
            it.add(0);
            assertEquals(1, it.nextIndex());
            assertEquals(0, it.previousIndex());
            assertTrue(it.hasNext());
            assertEquals(1, it.next());
            assertEquals(2, it.next());
            assertFalse(it.hasNext());
            it.add(3);
            assertFalse(it.hasNext());
            assertEquals(3, it.previous());
            assertEquals(List.of(0, 1, 2, 3), list);
        }
    }

    @Test
    public void listIteratorRemoveKeepsNeighbours() {
        // The reversed list iterator delegates remove() to the base list iterator.
        for (List<String> list : List.of(new ArrayList<>(List.of("a", "b")), new LinkedList<>(List.of("a", "b")))) {
            // remove after next() at the end: the previous element is still there
            ListIterator<String> it = list.listIterator();
            it.next();
            it.next();
            it.remove();
            assertFalse(it.hasNext());
            assertTrue(it.hasPrevious());
            assertEquals(1, it.nextIndex());
            assertEquals("a", it.previous());
            list.add("b");

            // remove after previous() at the start: the next element is still there
            it = list.listIterator(1);
            assertEquals("a", it.previous());
            it.remove();
            assertFalse(it.hasPrevious());
            assertTrue(it.hasNext());
            assertEquals(0, it.nextIndex());
            assertEquals("b", it.next());
            assertEquals(List.of("b"), list);
        }

        List<String> reversed = new LinkedList<>(List.of("a", "b", "c")).reversed();
        ListIterator<String> it = reversed.listIterator();
        assertEquals("c", it.next());
        it.remove();
        assertEquals("b", it.next());
        assertEquals("a", it.next());
        it.remove();
        assertTrue(it.hasPrevious());
        assertEquals("b", it.previous());
        assertEquals(List.of("b"), reversed);
    }

    @Test
    public void reversedListRemoveFirstAndLastOnEmpty() {
        // SequencedCollection.removeFirst: "Throws: NoSuchElementException - if this collection is empty"
        for (List<Integer> reversed : List.of(new ArrayList<Integer>().reversed(),
                new LinkedList<Integer>().reversed())) {
            assertThrows(NoSuchElementException.class, reversed::removeFirst);
            assertThrows(NoSuchElementException.class, reversed::removeLast);
        }
        List<Integer> base = new ArrayList<>(List.of(1, 2, 3));
        assertEquals(3, base.reversed().removeFirst());
        assertEquals(1, base.reversed().removeLast());
        assertEquals(List.of(2), base);
    }

    /** A SortedSet that is not a NavigableSet, so reversed() uses the SortedSet default. */
    private static class PlainSortedSet<E> extends AbstractSet<E> implements SortedSet<E> {
        private final TreeSet<E> set;

        PlainSortedSet(Collection<E> elements) {
            set = new TreeSet<>(elements);
        }

        @Override
        public Iterator<E> iterator() {
            return set.iterator();
        }

        @Override
        public int size() {
            return set.size();
        }

        @Override
        public boolean add(E e) {
            return set.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return set.remove(o);
        }

        @Override
        public boolean contains(Object o) {
            return set.contains(o);
        }

        @Override
        public Comparator<? super E> comparator() {
            return set.comparator();
        }

        @Override
        public SortedSet<E> subSet(E fromElement, E toElement) {
            return set.subSet(fromElement, toElement);
        }

        @Override
        public SortedSet<E> headSet(E toElement) {
            return set.headSet(toElement);
        }

        @Override
        public SortedSet<E> tailSet(E fromElement) {
            return set.tailSet(fromElement);
        }

        @Override
        public E first() {
            return set.first();
        }

        @Override
        public E last() {
            return set.last();
        }
    }
}
