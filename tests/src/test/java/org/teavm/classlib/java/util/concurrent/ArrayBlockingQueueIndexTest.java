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
public class ArrayBlockingQueueIndexTest {
    private static final int CAPACITY = 4;

    // Every head offset, every element count and every limit, so the cases include a queue whose head is at index 0,
    // a full queue, a wrapped queue, and a limit below, at and above the size.
    @Test
    public void drainToAtEveryOffset() {
        var failures = new ArrayList<String>();
        for (var start = 0; start < CAPACITY; ++start) {
            for (var count = 0; count <= CAPACITY; ++count) {
                for (var max = 0; max <= CAPACITY + 1; ++max) {
                    var drained = Math.min(max, count);
                    var expected = "result=" + drained + " target=" + range(0, drained) + " rest="
                            + range(drained, count);
                    String actual;
                    try {
                        var queue = queue(start, count);
                        var target = new ArrayList<Integer>();
                        var result = queue.drainTo(target, max);
                        actual = "result=" + result + " target=" + target + " rest=" + pollAll(queue);
                    } catch (RuntimeException e) {
                        actual = QueueExceptions.describe(e);
                    }
                    if (!actual.equals(expected)) {
                        failures.add("start=" + start + " count=" + count + " max=" + max + ": expected " + expected
                                + ", got " + actual);
                    }
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    // A full queue has head == tail, so iteration must stop after "size" elements, not at the first null slot.
    @Test
    public void iteratesFullQueueAtEveryOffset() {
        var failures = new ArrayList<String>();
        for (var start = 0; start < CAPACITY; ++start) {
            var iterated = new ArrayList<Integer>();
            var it = queue(start, CAPACITY).iterator();
            while (it.hasNext() && iterated.size() <= CAPACITY) {
                iterated.add(it.next());
            }
            if (!iterated.equals(range(0, CAPACITY))) {
                failures.add("start=" + start + ": got " + iterated);
            }
        }
        assertEquals(List.of(), failures);
    }

    // Iterator.remove() shifts the later elements into the removed slot. Iteration must go on with the next element,
    // not skip it.
    @Test
    public void iteratorContinuesAfterRemoveAtEveryPosition() {
        var failures = new ArrayList<String>();
        for (var start = 0; start < CAPACITY; ++start) {
            for (var count = 1; count <= CAPACITY; ++count) {
                for (var removed = 0; removed < count; ++removed) {
                    var expected = new ArrayList<Integer>();
                    for (var i = 0; i < count; ++i) {
                        expected.add(i);
                    }
                    var expectedRest = new ArrayList<>(expected);
                    expectedRest.remove(removed);
                    var expectedText = "seen=" + expected + " rest=" + expectedRest;
                    String actual;
                    try {
                        var queue = queue(start, count);
                        var seen = new ArrayList<Integer>();
                        var it = queue.iterator();
                        while (it.hasNext() && seen.size() <= CAPACITY) {
                            var e = it.next();
                            seen.add(e);
                            if (e == removed) {
                                it.remove();
                            }
                        }
                        actual = "seen=" + seen + " rest=" + pollAll(queue);
                    } catch (RuntimeException e) {
                        actual = QueueExceptions.describe(e);
                    }
                    if (!actual.equals(expectedText)) {
                        failures.add("start=" + start + " count=" + count + " removed=" + removed + ": expected "
                                + expectedText + ", got " + actual);
                    }
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    // A queue whose head is at index "start" and that holds 0, 1, ..., count - 1.
    private static ArrayBlockingQueue<Integer> queue(int start, int count) {
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
        return queue;
    }

    private static List<Integer> pollAll(ArrayBlockingQueue<Integer> queue) {
        var result = new ArrayList<Integer>();
        for (var e = queue.poll(); e != null && result.size() <= CAPACITY; e = queue.poll()) {
            result.add(e);
        }
        return result;
    }

    private static List<Integer> range(int from, int to) {
        var result = new ArrayList<Integer>();
        for (var i = from; i < to; ++i) {
            result.add(i);
        }
        return result;
    }
}
