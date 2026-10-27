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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * Random sequences of operations, compared with {@link LinkedList} as a model. Iterators stay open across
 * operations, so the ports' CAS helpers see stale expected values, as they would under real concurrency.
 *
 * <p>Javadoc of ConcurrentLinkedQueue: "Iterators are weakly consistent, returning elements reflecting the
 * state of the queue at some point at or since the creation of the iterator. They do not throw
 * ConcurrentModificationException, and may proceed concurrently with other operations." So the test checks
 * that an iterator never returns an element twice, returns only elements that were added, and (for the FIFO
 * queue) returns them in insertion order. After every operation the collection must equal the model.
 */
@TeaVMTest
public class ConcurrentLinkedRandomOpsTest {
    private static final int SEEDS = 20;
    private static final int OPS = 600;

    @Test
    public void queueMatchesModel() {
        for (var seed = 0; seed < SEEDS; ++seed) {
            runQueue(new Random(seed), new ConcurrentLinkedQueue<>(), false);
        }
    }

    @Test
    public void dequeAsQueueMatchesModel() {
        for (var seed = 0; seed < SEEDS; ++seed) {
            runQueue(new Random(seed), new ConcurrentLinkedDeque<>(), false);
        }
    }

    @Test
    public void dequeMatchesModel() {
        for (var seed = 0; seed < SEEDS; ++seed) {
            runQueue(new Random(seed), new ConcurrentLinkedDeque<>(), true);
        }
    }

    private static final class OpenIterator {
        final Iterator<Integer> it;
        final boolean fifo;
        final Set<Integer> seen = new HashSet<>();
        int last = -1;
        boolean canRemove;

        OpenIterator(Iterator<Integer> it, boolean fifo) {
            this.it = it;
            this.fifo = fifo;
        }
    }

    private static void runQueue(Random random, Queue<Integer> queue, boolean dequeOps) {
        var model = new LinkedList<Integer>();
        var iterators = new ArrayList<OpenIterator>();
        var next = 0;
        for (var op = 0; op < OPS; ++op) {
            var kind = random.nextInt(dequeOps ? 14 : 10);
            switch (kind) {
                case 0:
                case 1:
                case 2: {
                    var v = next++;
                    assertTrue(queue.offer(v));
                    model.addLast(v);
                    break;
                }
                case 3:
                    assertEquals(model.pollFirst(), queue.poll());
                    break;
                case 4: {
                    // remove(Object) of a random value that may or may not be present
                    Integer v = next == 0 ? 0 : random.nextInt(next);
                    assertEquals(model.remove(v), queue.remove(v));
                    break;
                }
                case 5:
                    if (iterators.size() < 4) {
                        var descending = dequeOps && random.nextBoolean();
                        iterators.add(new OpenIterator(descending
                                ? ((Deque<Integer>) queue).descendingIterator()
                                : queue.iterator(), !descending && !dequeOps));
                    }
                    break;
                case 6:
                case 7:
                    if (!iterators.isEmpty()) {
                        var oi = iterators.get(random.nextInt(iterators.size()));
                        if (oi.it.hasNext()) {
                            int v = oi.it.next();
                            assertTrue(v >= 0 && v < next, "returned a value never added: " + v);
                            assertTrue(oi.seen.add(v), "returned twice: " + v);
                            if (oi.fifo) {
                                assertTrue(v > oi.last, "out of order: " + v + " after " + oi.last);
                            }
                            oi.last = v;
                            oi.canRemove = true;
                        } else {
                            iterators.remove(oi);
                        }
                    }
                    break;
                case 8:
                    if (!iterators.isEmpty()) {
                        var oi = iterators.get(random.nextInt(iterators.size()));
                        if (oi.canRemove) {
                            oi.it.remove();
                            model.remove((Integer) oi.last);
                            oi.canRemove = false;
                        }
                    }
                    break;
                case 9:
                    if (random.nextInt(20) == 0) {
                        queue.clear();
                        model.clear();
                    } else {
                        assertEquals(model.peekFirst(), queue.peek());
                    }
                    break;
                case 10: {
                    var v = next++;
                    ((Deque<Integer>) queue).offerFirst(v);
                    model.addFirst(v);
                    break;
                }
                case 11:
                    assertEquals(model.pollLast(), ((Deque<Integer>) queue).pollLast());
                    break;
                case 12: {
                    Integer v = next == 0 ? 0 : random.nextInt(next);
                    assertEquals(model.removeLastOccurrence(v), ((Deque<Integer>) queue).removeLastOccurrence(v));
                    break;
                }
                case 13:
                    assertEquals(model.peekLast(), ((Deque<Integer>) queue).peekLast());
                    break;
                default:
                    throw new AssertionError();
            }
            check(model, queue, dequeOps);
        }
    }

    private static void check(LinkedList<Integer> model, Queue<Integer> queue, boolean dequeOps) {
        assertArrayEquals(model.toArray(), queue.toArray());
        assertEquals(model.size(), queue.size());
        assertEquals(model.isEmpty(), queue.isEmpty());
        List<Integer> iterated = new ArrayList<>();
        for (var v : queue) {
            iterated.add(v);
        }
        assertEquals(model, iterated);
        if (dequeOps) {
            List<Integer> backwards = new ArrayList<>();
            var it = ((Deque<Integer>) queue).descendingIterator();
            while (it.hasNext()) {
                backwards.add(it.next());
            }
            List<Integer> expected = new ArrayList<>(model);
            Collections.reverse(expected);
            assertEquals(expected, backwards);
        }
    }
}
