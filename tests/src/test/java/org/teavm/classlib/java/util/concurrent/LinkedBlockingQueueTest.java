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
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

/*
 * TeaVM port of jsr166's LinkedBlockingQueueTest for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/LinkedBlockingQueueTest.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * JUnit 3 test methods have @Test; the base class JSR166TestCase is the cut-down TeaVM port.
 * Removed:
 * - testSerialization: no Java serialization on TeaVM
 * - testOfferInExecutor: uses Executors, which a later change adds
 * - testPollInExecutor: uses Executors, which a later change adds
 * - testToArray1_BadArg: TeaVM does not throw ArrayStoreException (no backend checks array stores)
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ImportOrder|NeedBraces|OperatorWrap|UnusedImports|WhitespaceAfter|WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;


@TeaVMTest
public class LinkedBlockingQueueTest extends JSR166TestCase {

    // android-note: These tests have been moved into their own separate
    // classes to work around CTS issues.
    //
    // public static class Unbounded extends BlockingQueueTest {
    //     protected BlockingQueue emptyCollection() {
    //         return new LinkedBlockingQueue();
    //     }
    // }

    // public static class Bounded extends BlockingQueueTest {
    //     protected BlockingQueue emptyCollection() {
    //         return new LinkedBlockingQueue(SIZE);
    //     }
    // }

    // android-note: Removed because the CTS runner does a bad job of
    // retrying tests that have suite() declarations.
    //
    // public static void main(String[] args) {
    //     main(suite(), args);
    // }
    // public static Test suite() {
    //     return newTestSuite(LinkedBlockingQueueTest.class,
    //                         new Unbounded().testSuite(),
    //                         new Bounded().testSuite());
    // }

    /**
     * Returns a new queue of given size containing consecutive
     * Integers 0 ... n.
     */
    private LinkedBlockingQueue<Integer> populatedQueue(int n) {
        LinkedBlockingQueue<Integer> q =
            new LinkedBlockingQueue<Integer>(n);
        assertTrue(q.isEmpty());
        for (int i = 0; i < n; i++)
            assertTrue(q.offer(Integer.valueOf(i)));
        assertFalse(q.isEmpty());
        assertEquals(0, q.remainingCapacity());
        assertEquals(n, q.size());
        return q;
    }

    /**
     * A new queue has the indicated capacity, or Integer.MAX_VALUE if
     * none given
     */
    @Test
    public void testConstructor1() {
        assertEquals(SIZE, new LinkedBlockingQueue(SIZE).remainingCapacity());
        assertEquals(Integer.MAX_VALUE, new LinkedBlockingQueue().remainingCapacity());
    }

    /**
     * Constructor throws IllegalArgumentException if capacity argument nonpositive
     */
    @Test
    public void testConstructor2() {
        try {
            new LinkedBlockingQueue(0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Initializing from null Collection throws NullPointerException
     */
    @Test
    public void testConstructor3() {
        try {
            new LinkedBlockingQueue(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws NullPointerException
     */
    @Test
    public void testConstructor4() {
        Collection<Integer> elements = Arrays.asList(new Integer[SIZE]);
        try {
            new LinkedBlockingQueue(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection with some null elements throws
     * NullPointerException
     */
    @Test
    public void testConstructor5() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = Integer.valueOf(i);
        Collection<Integer> elements = Arrays.asList(ints);
        try {
            new LinkedBlockingQueue(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements of collection used to initialize
     */
    @Test
    public void testConstructor6() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = Integer.valueOf(i);
        LinkedBlockingQueue q = new LinkedBlockingQueue(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * Queue transitions from empty to full when elements added
     */
    @Test
    public void testEmptyFull() {
        LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        assertTrue(q.isEmpty());
        assertEquals("should have room for 2", 2, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        q.add(two);
        assertFalse(q.isEmpty());
        assertEquals(0, q.remainingCapacity());
        assertFalse(q.offer(three));
    }

    /**
     * remainingCapacity decreases on add, increases on remove
     */
    @Test
    public void testRemainingCapacity() {
        BlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.remainingCapacity());
            assertEquals(SIZE, q.size() + q.remainingCapacity());
            assertEquals(i, q.remove());
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(SIZE - i, q.remainingCapacity());
            assertEquals(SIZE, q.size() + q.remainingCapacity());
            assertTrue(q.add(i));
        }
    }

    /**
     * Offer succeeds if not full; fails if full
     */
    @Test
    public void testOffer() {
        LinkedBlockingQueue q = new LinkedBlockingQueue(1);
        assertTrue(q.offer(zero));
        assertFalse(q.offer(one));
    }

    /**
     * add succeeds if not full; throws IllegalStateException if full
     */
    @Test
    public void testAdd() {
        LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i)
            assertTrue(q.add(Integer.valueOf(i)));
        assertEquals(0, q.remainingCapacity());
        try {
            q.add(Integer.valueOf(SIZE));
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    @Test
    public void testAddAllSelf() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        try {
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with any null elements throws NPE after
     * possibly adding some elements
     */
    @Test
    public void testAddAll3() {
        LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE - 1; ++i)
            ints[i] = Integer.valueOf(i);
        Collection<Integer> elements = Arrays.asList(ints);
        try {
            q.addAll(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll throws IllegalStateException if not enough room
     */
    @Test
    public void testAddAll4() {
        LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE - 1);
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = Integer.valueOf(i);
        Collection<Integer> elements = Arrays.asList(ints);
        try {
            q.addAll(elements);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * Queue contains all elements, in traversal order, of successful addAll
     */
    @Test
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = Integer.valueOf(i);
        LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * all elements successfully put are contained
     */
    @Test
    public void testPut() throws InterruptedException {
        LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            Integer x = Integer.valueOf(i);
            q.put(x);
            assertTrue(q.contains(x));
        }
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * put blocks interruptibly if full
     */
    @Test
    public void testBlockingPut() throws InterruptedException {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i)
                    q.put(i);
                assertEquals(SIZE, q.size());
                assertEquals(0, q.remainingCapacity());

                Thread.currentThread().interrupt();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        assertEquals(SIZE, q.size());
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * put blocks interruptibly waiting for take when full
     */
    @Test
    public void testPutWithTake() throws InterruptedException {
        final int capacity = 2;
        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        final CountDownLatch pleaseTake = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < capacity; i++)
                    q.put(i);
                pleaseTake.countDown();
                q.put(86);

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseTake);
        assertEquals(0, q.remainingCapacity());
        assertEquals(0, q.take());

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * timed offer times out if full and elements not taken
     */
    @Test
    public void testTimedOffer() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(new Object());
                q.put(new Object());
                long startTime = System.nanoTime();
                assertFalse(q.offer(new Object(), timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                pleaseInterrupt.countDown();
                try {
                    q.offer(new Object(), 2 * LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * take retrieves elements in FIFO order
     */
    @Test
    public void testTake() throws InterruptedException {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.take());
        }
    }

    /**
     * Take removes existing elements until empty, then blocks interruptibly
     */
    @Test
    public void testBlockingTake() throws InterruptedException {
        final BlockingQueue q = populatedQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i) {
                    assertEquals(i, q.take());
                }

                Thread.currentThread().interrupt();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * poll succeeds unless empty
     */
    @Test
    public void testPoll() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.poll());
        }
        assertNull(q.poll());
    }

    /**
     * timed poll with zero timeout succeeds when non-empty, else times out
     */
    @Test
    public void testTimedPoll0() throws InterruptedException {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.poll(0, MILLISECONDS));
        }
        assertNull(q.poll(0, MILLISECONDS));
    }

    /**
     * timed poll with nonzero timeout succeeds when non-empty, else times out
     */
    @Test
    public void testTimedPoll() throws InterruptedException {
        LinkedBlockingQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            long startTime = System.nanoTime();
            assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        }
        long startTime = System.nanoTime();
        assertNull(q.poll(timeoutMillis(), MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        checkEmpty(q);
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    @Test
    public void testInterruptedTimedPoll() throws InterruptedException {
        final BlockingQueue<Integer> q = populatedQueue(SIZE);
        final CountDownLatch aboutToWait = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                for (int i = 0; i < SIZE; ++i) {
                    assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
                }
                aboutToWait.countDown();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {
                    assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
                }
            }});

        await(aboutToWait);
        waitForThreadToEnterWaitState(t, LONG_DELAY_MS);
        t.interrupt();
        awaitTermination(t);
        checkEmpty(q);
    }

    /**
     * peek returns next element, or null if empty
     */
    @Test
    public void testPeek() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.peek());
            assertEquals(i, q.poll());
            assertTrue(q.peek() == null ||
                       !q.peek().equals(i));
        }
        assertNull(q.peek());
    }

    /**
     * element returns next element, or throws NSEE if empty
     */
    @Test
    public void testElement() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.element());
            assertEquals(i, q.poll());
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove removes next element, or throws NSEE if empty
     */
    @Test
    public void testRemove() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.remove());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * An add following remove(x) succeeds
     */
    @Test
    public void testRemoveElementAndAdd() throws InterruptedException {
        LinkedBlockingQueue q = new LinkedBlockingQueue();
        assertTrue(q.add(Integer.valueOf(1)));
        assertTrue(q.add(Integer.valueOf(2)));
        assertTrue(q.remove(Integer.valueOf(1)));
        assertTrue(q.remove(Integer.valueOf(2)));
        assertTrue(q.add(Integer.valueOf(3)));
        assertNotNull(q.take());
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    @Test
    public void testContains() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(Integer.valueOf(i)));
            q.poll();
            assertFalse(q.contains(Integer.valueOf(i)));
        }
    }

    /**
     * clear removes all elements
     */
    @Test
    public void testClear() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(SIZE, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(one));
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    @Test
    public void testContainsAll() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        LinkedBlockingQueue p = new LinkedBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(Integer.valueOf(i));
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true if changed
     */
    @Test
    public void testRetainAll() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        LinkedBlockingQueue p = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0)
                assertFalse(changed);
            else
                assertTrue(changed);

            assertTrue(q.containsAll(p));
            assertEquals(SIZE - i, q.size());
            p.remove();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true if changed
     */
    @Test
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            LinkedBlockingQueue q = populatedQueue(SIZE);
            LinkedBlockingQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer x = (Integer)(p.remove());
                assertFalse(q.contains(x));
            }
        }
    }

    /**
     * toArray contains all elements in FIFO order
     */
    @Test
    public void testToArray() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        Object[] o = q.toArray();
        for (int i = 0; i < o.length; i++)
            assertSame(o[i], q.poll());
    }

    /**
     * toArray(a) contains all elements in FIFO order
     */
    @Test
    public void testToArray2() throws InterruptedException {
        LinkedBlockingQueue<Integer> q = populatedQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        Integer[] array = q.toArray(ints);
        assertSame(ints, array);
        for (int i = 0; i < ints.length; i++)
            assertSame(ints[i], q.poll());
    }

    /**
     * iterator iterates through all elements
     */
    @Test
    public void testIterator() throws InterruptedException {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        Iterator it = q.iterator();
        int i;
        for (i = 0; it.hasNext(); i++)
            assertTrue(q.contains(it.next()));
        assertEquals(i, SIZE);
        assertIteratorExhausted(it);

        it = q.iterator();
        for (i = 0; it.hasNext(); i++)
            assertEquals(it.next(), q.take());
        assertEquals(i, SIZE);
        assertIteratorExhausted(it);
    }

    /**
     * iterator of empty collection has no elements
     */
    @Test
    public void testEmptyIterator() {
        assertIteratorExhausted(new LinkedBlockingQueue().iterator());
    }

    /**
     * iterator.remove removes current element
     */
    @Test
    public void testIteratorRemove() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(3);
        q.add(two);
        q.add(one);
        q.add(three);

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertSame(it.next(), one);
        assertSame(it.next(), three);
        assertFalse(it.hasNext());
    }

    /**
     * iterator ordering is FIFO
     */
    @Test
    public void testIteratorOrdering() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(3);
        q.add(one);
        q.add(two);
        q.add(three);
        assertEquals(0, q.remainingCapacity());
        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            assertEquals(++k, it.next());
        }
        assertEquals(3, k);
    }

    /**
     * Modifications do not cause iterators to fail
     */
    @Test
    public void testWeaklyConsistentIteration() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(3);
        q.add(one);
        q.add(two);
        q.add(three);
        for (Iterator it = q.iterator(); it.hasNext();) {
            q.remove();
            it.next();
        }
        assertEquals(0, q.size());
    }

    /**
     * toString contains toStrings of elements
     */
    @Test
    public void testToString() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * drainTo(c) empties queue into another collection c
     */
    @Test
    public void testDrainTo() {
        LinkedBlockingQueue q = populatedQueue(SIZE);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(SIZE, l.size());
        for (int i = 0; i < SIZE; ++i)
            assertEquals(l.get(i), Integer.valueOf(i));
        q.add(zero);
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(zero));
        assertTrue(q.contains(one));
        l.clear();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(2, l.size());
        for (int i = 0; i < 2; ++i)
            assertEquals(l.get(i), Integer.valueOf(i));
    }

    /**
     * drainTo empties full queue, unblocking a waiting put.
     */
    @Test
    public void testDrainToWithActivePut() throws InterruptedException {
        final LinkedBlockingQueue q = populatedQueue(SIZE);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(Integer.valueOf(SIZE + 1));
            }});

        t.start();
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertTrue(l.size() >= SIZE);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(l.get(i), Integer.valueOf(i));
        t.join();
        assertTrue(q.size() + l.size() >= SIZE);
    }

    /**
     * drainTo(c, n) empties first min(n, size) elements of queue into c
     */
    @Test
    public void testDrainToN() {
        LinkedBlockingQueue q = new LinkedBlockingQueue();
        for (int i = 0; i < SIZE + 2; ++i) {
            for (int j = 0; j < SIZE; j++)
                assertTrue(q.offer(Integer.valueOf(j)));
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(k, l.size());
            assertEquals(SIZE - k, q.size());
            for (int j = 0; j < k; ++j)
                assertEquals(l.get(j), Integer.valueOf(j));
            do {} while (q.poll() != null);
        }
    }

    /**
     * remove(null), contains(null) always return false
     */
    @Test
    public void testNeverContainsNull() {
        Collection<?>[] qs = {
            new LinkedBlockingQueue<Object>(),
            populatedQueue(2),
        };

        for (Collection<?> q : qs) {
            assertFalse(q.contains(null));
            assertFalse(q.remove(null));
        }
    }

}
