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
package org.teavm.classlib.java.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ArrayBlockingQueueRemoveTest {
    private static final int CAPACITY = 4;

    // Every start offset of the head, every element count and every removed position. So the cases include a full
    // queue, a queue whose tail is at index 0, a wrapped queue (head after tail), and removal at the head, in the
    // middle and just before the tail.
    @Test
    public void removeObjectAtEveryPosition() {
        assertEquals(List.of(), checkAllPositions(false));
    }

    @Test
    public void iteratorRemoveAtEveryPosition() {
        assertEquals(List.of(), checkAllPositions(true));
    }

    private static List<String> checkAllPositions(boolean useIterator) {
        var failures = new ArrayList<String>();
        for (var start = 0; start < CAPACITY; ++start) {
            for (var count = 1; count <= CAPACITY; ++count) {
                for (var removed = 0; removed < count; ++removed) {
                    var result = removeAndDrain(start, count, removed, useIterator);
                    var expected = expectedAfterRemoval(count, removed);
                    if (!result.equals(expected)) {
                        failures.add("start=" + start + " count=" + count + " removed=" + removed + ": expected "
                                + expected + ", got " + result);
                    }
                }
            }
        }
        return failures;
    }

    // Puts the head at index "start", adds "count" elements (0, 1, ...), removes the element at position "removed",
    // then reports size(), the elements that iteration sees, and the elements that poll() returns. After the
    // removal it also adds one element, to check where the tail is.
    private static String removeAndDrain(int start, int count, int removed, boolean useIterator) {
        try {
            var queue = new ArrayBlockingQueue<Integer>(CAPACITY);
            for (var i = 0; i < start; ++i) {
                queue.add(-1);
            }
            for (var i = 0; i < start; ++i) {
                queue.poll();
            }
            for (var i = 0; i < count; ++i) {
                queue.add(i);
            }
            if (useIterator) {
                var it = queue.iterator();
                for (var i = 0; i < removed; ++i) {
                    it.next();
                }
                it.next();
                it.remove();
            } else if (!queue.remove(Integer.valueOf(removed))) {
                return "remove returned false";
            }
            var size = queue.size();
            var iterated = new ArrayList<Integer>();
            for (var e : queue) {
                iterated.add(e);
            }
            var offered = queue.offer(100);
            var polled = new ArrayList<Integer>();
            for (var e = queue.poll(); e != null; e = queue.poll()) {
                polled.add(e);
            }
            return "size=" + size + " iterated=" + iterated + " offered=" + offered + " polled=" + polled;
        } catch (RuntimeException e) {
            return QueueExceptions.describe(e);
        }
    }

    private static String expectedAfterRemoval(int count, int removed) {
        var rest = new ArrayList<Integer>();
        for (var i = 0; i < count; ++i) {
            if (i != removed) {
                rest.add(i);
            }
        }
        var polled = new ArrayList<>(rest);
        polled.add(100);
        return "size=" + rest.size() + " iterated=" + rest + " offered=true polled=" + polled;
    }
}
