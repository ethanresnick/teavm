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
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
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
