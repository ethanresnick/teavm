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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class PriorityQueueTest {
    @Test
    public void receivesElements() {
        PriorityQueue<Integer> queue = fillQueue();
        assertEquals(8, queue.size());
        List<Integer> list = new ArrayList<>();
        while (!queue.isEmpty()) {
            list.add(queue.poll());
        }
        assertNull(queue.poll());
        assertArrayEquals(new Integer[] { 0, 1, 2, 3, 4, 5, 6, 7 }, list.toArray(new Integer[0]));
    }

    @Test
    public void removeElements() {
        for (int i = 1; i < 7; ++i) {
            PriorityQueue<Integer> queue = fillQueue();
            List<Integer> expectedList = new ArrayList<>();
            for (int j = 0; j < queue.size(); ++j) {
                expectedList.add(j);
            }
            Iterator<Integer> iter = queue.iterator();
            int indexToRemove = -1;
            for (int j = 0; j < i; ++j) {
                indexToRemove = iter.next();
            }
            iter.remove();
            List<Integer> list = new ArrayList<>();
            while (!queue.isEmpty()) {
                list.add(queue.poll());
            }
            expectedList.remove(indexToRemove);
            assertArrayEquals(expectedList.toArray(), list.toArray());
        }
    }

    // PriorityQueue: "The head of this queue is the least element with respect to the specified ordering."
    // and "The queue retrieval operations poll, remove, peek, and element access the element at the head
    // of the queue." Removing an inner element may need to move the replacement up, not only down.
    @Test
    public void removeObjectKeepsPollOrder() {
        PriorityQueue<Integer> queue = queueOf(16, 1, 10, 22, 23, 3, 9);
        assertTrue(queue.remove(22));
        assertDrainsTo(queue, 1, 3, 9, 10, 16, 23);
    }

    @Test
    public void iteratorRemoveKeepsPollOrder() {
        PriorityQueue<Integer> queue = queueOf(16, 1, 10, 22, 23, 3, 9);
        for (Iterator<Integer> iter = queue.iterator(); iter.hasNext();) {
            if (iter.next() == 22) {
                iter.remove();
            }
        }
        assertDrainsTo(queue, 1, 3, 9, 10, 16, 23);
    }

    @Test
    public void removeIfKeepsPollOrder() {
        PriorityQueue<Integer> queue = queueOf(16, 1, 10, 22, 23, 3, 9);
        assertTrue(queue.removeIf(x -> x == 22));
        assertDrainsTo(queue, 1, 3, 9, 10, 16, 23);
    }

    @Test
    public void removeKeepsPollOrderWithComparator() {
        PriorityQueue<Integer> queue = new PriorityQueue<>(Comparator.reverseOrder());
        for (int i : new int[] { -16, -1, -10, -22, -23, -3, -9 }) {
            queue.offer(i);
        }
        assertTrue(queue.remove(-22));
        assertDrainsTo(queue, -1, -3, -9, -10, -16, -23);
    }

    // Collection.iterator: "Returns an iterator over the elements in this collection."
    // Iterator.remove: "Removes from the underlying collection the last element returned by this iterator".
    // The iterator still has to return every other element exactly once, even when the removal moves an
    // element that it has not returned yet to a position it has already passed.
    @Test
    public void iteratorVisitsEachElementOnceWhenRemovalMovesElementUp() {
        PriorityQueue<Integer> queue = queueOf(16, 1, 10, 22, 23, 3, 9);
        List<Integer> visited = new ArrayList<>();
        Iterator<Integer> iter = queue.iterator();
        int guard = 0;
        while (iter.hasNext()) {
            if (++guard > 100) {
                fail("Iterator does not terminate");
            }
            int value = iter.next();
            visited.add(value);
            if (value == 22) {
                iter.remove();
                assertThrows(IllegalStateException.class, iter::remove);
            }
        }
        assertThrows(NoSuchElementException.class, iter::next);
        Collections.sort(visited);
        assertEquals(List.of(1, 3, 9, 10, 16, 22, 23), visited);
        assertDrainsTo(queue, 1, 3, 9, 10, 16, 23);
    }

    // Same as above, but the element that the removal moved up is then removed through the iterator too.
    @Test
    public void iteratorCanRemoveElementMovedUp() {
        PriorityQueue<Integer> queue = queueOf(16, 1, 10, 22, 23, 3, 9);
        List<Integer> visited = new ArrayList<>();
        int guard = 0;
        for (Iterator<Integer> iter = queue.iterator(); iter.hasNext();) {
            if (++guard > 100) {
                fail("Iterator does not terminate");
            }
            int value = iter.next();
            visited.add(value);
            if (value == 22 || value == 9) {
                iter.remove();
            }
        }
        Collections.sort(visited);
        assertEquals(List.of(1, 3, 9, 10, 16, 22, 23), visited);
        assertEquals(5, queue.size());
        assertDrainsTo(queue, 1, 3, 10, 16, 23);
    }

    @Test
    public void randomOperationsMatchModel() {
        runRandomOperations(null, 1);
    }

    @Test
    public void randomOperationsMatchModelWithComparator() {
        // Compares only by floorDiv(x, 3), so distinct elements can tie
        runRandomOperations(Comparator.comparingInt(x -> Math.floorDiv(x, 3)), 2);
    }

    @Test
    public void randomOperationsMatchModelWithReverseComparator() {
        runRandomOperations(Comparator.reverseOrder(), 3);
    }

    // Applies random offer, poll, remove(Object), Iterator.remove, removeIf and clear operations to both a
    // queue and a list model. Every loop is bounded, so a hang in the queue fails the test instead.
    private static void runRandomOperations(Comparator<Integer> comparator, long seed) {
        Comparator<Integer> order = comparator != null ? comparator : Comparator.naturalOrder();
        Random random = new Random(seed);
        for (int round = 0; round < 150; ++round) {
            PriorityQueue<Integer> queue = comparator != null ? new PriorityQueue<>(comparator) : new PriorityQueue<>();
            List<Integer> model = new ArrayList<>();
            // Start most rounds with a larger heap, so removals reach inner nodes of deeper subtrees
            int initialSize = random.nextInt(30);
            for (int i = 0; i < initialSize; ++i) {
                int value = random.nextInt(40) - 10;
                queue.offer(value);
                model.add(value);
            }
            int steps = 1 + random.nextInt(40);
            for (int step = 0; step < steps; ++step) {
                String context = "seed " + seed + ", round " + round + ", step " + step;
                int op = random.nextInt(20);
                if (op < 8) {
                    int value = random.nextInt(40) - 10;
                    assertTrue(queue.offer(value), context);
                    model.add(value);
                } else if (op < 10) {
                    Integer polled = queue.poll();
                    if (model.isEmpty()) {
                        assertNull(polled, context);
                    } else {
                        Integer min = Collections.min(model, order);
                        assertEquals(0, order.compare(min, polled), context + ": polled " + polled);
                        assertTrue(model.remove(polled), context);
                    }
                } else if (op < 13) {
                    Integer value = random.nextInt(40) - 10;
                    assertEquals(model.remove(value), queue.remove(value), context);
                } else if (op < 16) {
                    int divisor = 2 + random.nextInt(4);
                    int remainder = random.nextInt(divisor);
                    List<Integer> visited = new ArrayList<>();
                    List<Integer> expected = new ArrayList<>(model);
                    int guard = 0;
                    for (Iterator<Integer> iter = queue.iterator(); iter.hasNext();) {
                        if (++guard > 1000) {
                            fail(context + ": iterator does not terminate");
                        }
                        Integer value = iter.next();
                        visited.add(value);
                        if (Math.floorMod(value, divisor) == remainder) {
                            iter.remove();
                            assertTrue(model.remove(value), context);
                        }
                    }
                    assertSameElements(expected, visited, context + ": iterator");
                } else if (op < 19) {
                    int divisor = 2 + random.nextInt(4);
                    int remainder = random.nextInt(divisor);
                    boolean expected = model.removeIf(x -> Math.floorMod(x, divisor) == remainder);
                    assertEquals(expected, queue.removeIf(x -> Math.floorMod(x, divisor) == remainder), context);
                } else {
                    queue.clear();
                    model.clear();
                }
                checkQueue(queue, model, order, context);
            }
        }
    }

    private static void checkQueue(PriorityQueue<Integer> queue, List<Integer> model, Comparator<Integer> order,
            String context) {
        assertEquals(model.size(), queue.size(), context + ": size");
        assertEquals(model.isEmpty(), queue.isEmpty(), context);
        if (model.isEmpty()) {
            assertNull(queue.peek(), context);
        } else {
            assertEquals(0, order.compare(Collections.min(model, order), queue.peek()), context + ": peek");
        }

        List<Integer> iterated = new ArrayList<>();
        int guard = 0;
        for (Integer value : queue) {
            if (++guard > 1000) {
                fail(context + ": iterator does not terminate");
            }
            iterated.add(value);
        }
        assertSameElements(model, iterated, context + ": contents");

        PriorityQueue<Integer> copy = new PriorityQueue<>(queue);
        List<Integer> drained = new ArrayList<>();
        while (!copy.isEmpty()) {
            if (drained.size() > model.size()) {
                fail(context + ": copy drains more elements than it holds");
            }
            drained.add(copy.poll());
        }
        for (int i = 1; i < drained.size(); ++i) {
            if (order.compare(drained.get(i - 1), drained.get(i)) > 0) {
                fail(context + ": drained out of order: " + drained);
            }
        }
        assertSameElements(model, drained, context + ": drained");
    }

    private static void assertSameElements(List<Integer> expected, List<Integer> actual, String message) {
        List<Integer> sortedExpected = new ArrayList<>(expected);
        List<Integer> sortedActual = new ArrayList<>(actual);
        Collections.sort(sortedExpected);
        Collections.sort(sortedActual);
        assertEquals(sortedExpected, sortedActual, message);
    }

    private static PriorityQueue<Integer> queueOf(int... values) {
        PriorityQueue<Integer> queue = new PriorityQueue<>();
        for (int value : values) {
            queue.offer(value);
        }
        return queue;
    }

    // AbstractQueue.addAll: "Attempts to addAll of a queue to itself result in IllegalArgumentException."
    // Throws: "IllegalArgumentException - ... or if the specified collection is this queue"
    @Test
    public void addAllOfItselfThrows() {
        PriorityQueue<Integer> queue = fillQueue();
        assertThrows(IllegalArgumentException.class, () -> queue.addAll(queue));
        assertEquals(8, queue.size());
        assertDrainsTo(queue, 0, 1, 2, 3, 4, 5, 6, 7);

        PriorityQueue<Integer> empty = new PriorityQueue<>();
        assertThrows(IllegalArgumentException.class, () -> empty.addAll(empty));
        assertEquals(0, empty.size());
    }

    @Test
    public void addAllOfEqualQueueAdds() {
        PriorityQueue<Integer> queue = new PriorityQueue<>(List.of(2, 1));
        assertTrue(queue.addAll(new PriorityQueue<>(queue)));
        assertDrainsTo(queue, 1, 1, 2, 2);
    }

    // A user subclass of AbstractQueue inherits the same rule.
    @Test
    public void addAllOfItselfThrowsInAbstractQueueSubclass() {
        Queue<Integer> queue = new ListQueue();
        queue.add(3);
        queue.add(4);
        assertThrows(IllegalArgumentException.class, () -> queue.addAll(queue));
        assertEquals(List.of(3, 4), new ArrayList<>(queue));
        assertTrue(queue.addAll(List.of(5)));
        assertEquals(List.of(3, 4, 5), new ArrayList<>(queue));
    }

    private static void assertDrainsTo(Queue<Integer> queue, Integer... expected) {
        List<Integer> list = new ArrayList<>();
        while (!queue.isEmpty()) {
            list.add(queue.poll());
        }
        assertEquals(List.of(expected), list);
    }

    static class ListQueue extends AbstractQueue<Integer> {
        private final List<Integer> list = new ArrayList<>();

        @Override
        public Iterator<Integer> iterator() {
            return list.iterator();
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public boolean offer(Integer e) {
            return list.add(e);
        }

        @Override
        public Integer poll() {
            return list.isEmpty() ? null : list.remove(0);
        }

        @Override
        public Integer peek() {
            return list.isEmpty() ? null : list.get(0);
        }
    }

    private PriorityQueue<Integer> fillQueue() {
        PriorityQueue<Integer> queue = new PriorityQueue<>();
        for (int i : new int[] { 5, 2, 4, 3, 6, 7, 0, 1 }) {
            queue.offer(i);
        }
        return queue;
    }
}
