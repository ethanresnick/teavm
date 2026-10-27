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
 * TeaVM port of jsr166's SynchronousQueueTest for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/SynchronousQueueTest.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * JUnit 3 test methods have @Test; the base class JSR166TestCase is the cut-down TeaVM port.
 * Removed:
 * - testSerialization: no Java serialization on TeaVM
 * - testOfferInExecutor: uses Executors, which a later change adds
 * - testOfferInExecutor_fair: uses Executors, which a later change adds
 * - testPollInExecutor: uses Executors, which a later change adds
 * - testPollInExecutor_fair: uses Executors, which a later change adds
 * Changed: spin loops call spinWait() (a 1 ms sleep) instead of Thread.yield(), because TeaVM's
 * Thread.yield() switches threads only after a 100 ms time slice.
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ImportOrder|LeftCurly|NeedBraces|RightCurly|UnusedImports|WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;


@TeaVMTest
public class SynchronousQueueTest extends JSR166TestCase {

    // android-note: These tests have been moved into their own separate
    // classes to work around CTS issues.
    //
    // public static class Fair extends BlockingQueueTest {
    //     protected BlockingQueue emptyCollection() {
    //         return new SynchronousQueue(true);
    //     }
    // }

    // public static class NonFair extends BlockingQueueTest {
    //     protected BlockingQueue emptyCollection() {
    //         return new SynchronousQueue(false);
    //     }
    // }

    // android-note: Removed because the CTS runner does a bad job of
    // retrying tests that have suite() declarations.
    //
    // public static void main(String[] args) {
    //     main(suite(), args);
    // }
    // public static Test suite() {
    //     return newTestSuite(SynchronousQueueTest.class,
    //                         new Fair().testSuite(),
    //                         new NonFair().testSuite());
    // }

    /**
     * Any SynchronousQueue is both empty and full
     */
    @Test
    public void testEmptyFull()      { testEmptyFull(false); }
    @Test
    public void testEmptyFull_fair() { testEmptyFull(true); }
    public void testEmptyFull(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(0, q.remainingCapacity());
        assertFalse(q.offer(zero));
    }

    /**
     * offer fails if no active taker
     */
    @Test
    public void testOffer()      { testOffer(false); }
    @Test
    public void testOffer_fair() { testOffer(true); }
    public void testOffer(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        assertFalse(q.offer(one));
    }

    /**
     * add throws IllegalStateException if no active taker
     */
    @Test
    public void testAdd()      { testAdd(false); }
    @Test
    public void testAdd_fair() { testAdd(true); }
    public void testAdd(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        assertEquals(0, q.remainingCapacity());
        try {
            q.add(one);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    @Test
    public void testAddAll_self()      { testAddAll_self(false); }
    @Test
    public void testAddAll_self_fair() { testAddAll_self(true); }
    public void testAddAll_self(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        try {
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll throws ISE if no active taker
     */
    @Test
    public void testAddAll_ISE()      { testAddAll_ISE(false); }
    @Test
    public void testAddAll_ISE_fair() { testAddAll_ISE(true); }
    public void testAddAll_ISE(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] ints = new Integer[1];
        for (int i = 0; i < ints.length; i++)
            ints[i] = i;
        Collection<Integer> coll = Arrays.asList(ints);
        try {
            q.addAll(coll);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * put blocks interruptibly if no active taker
     */
    @Test
    public void testBlockingPut()      { testBlockingPut(false); }
    @Test
    public void testBlockingPut_fair() { testBlockingPut(true); }
    public void testBlockingPut(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
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
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * put blocks interruptibly waiting for take
     */
    @Test
    public void testPutWithTake()      { testPutWithTake(false); }
    @Test
    public void testPutWithTake_fair() { testPutWithTake(true); }
    public void testPutWithTake(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseTake = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                pleaseTake.countDown();
                q.put(one);

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseTake);
        assertEquals(0, q.remainingCapacity());
        try { assertSame(one, q.take()); }
        catch (InterruptedException e) { threadUnexpectedException(e); }

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * timed offer times out if elements not taken
     */
    @Test
    public void testTimedOffer()      { testTimedOffer(false); }
    @Test
    public void testTimedOffer_fair() { testTimedOffer(true); }
    public void testTimedOffer(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
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
     * poll return null if no active putter
     */
    @Test
    public void testPoll()      { testPoll(false); }
    @Test
    public void testPoll_fair() { testPoll(true); }
    public void testPoll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertNull(q.poll());
    }

    /**
     * timed poll with zero timeout times out if no active putter
     */
    @Test
    public void testTimedPoll0()      { testTimedPoll0(false); }
    @Test
    public void testTimedPoll0_fair() { testTimedPoll0(true); }
    public void testTimedPoll0(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try { assertNull(q.poll(0, MILLISECONDS)); }
        catch (InterruptedException e) { threadUnexpectedException(e); }
    }

    /**
     * timed poll with nonzero timeout times out if no active putter
     */
    @Test
    public void testTimedPoll()      { testTimedPoll(false); }
    @Test
    public void testTimedPoll_fair() { testTimedPoll(true); }
    public void testTimedPoll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        long startTime = System.nanoTime();
        try { assertNull(q.poll(timeoutMillis(), MILLISECONDS)); }
        catch (InterruptedException e) { threadUnexpectedException(e); }
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
    }

    /**
     * timed poll before a delayed offer times out, returning null;
     * after offer succeeds; on interruption throws
     */
    @Test
    public void testTimedPollWithOffer()      { testTimedPollWithOffer(false); }
    @Test
    public void testTimedPollWithOffer_fair() { testTimedPollWithOffer(true); }
    public void testTimedPollWithOffer(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseOffer = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                assertNull(q.poll(timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());

                pleaseOffer.countDown();
                startTime = System.nanoTime();
                assertSame(zero, q.poll(LONG_DELAY_MS, MILLISECONDS));

                Thread.currentThread().interrupt();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
            }});

        await(pleaseOffer);
        long startTime = System.nanoTime();
        try { assertTrue(q.offer(zero, LONG_DELAY_MS, MILLISECONDS)); }
        catch (InterruptedException e) { threadUnexpectedException(e); }
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * peek() returns null if no active putter
     */
    @Test
    public void testPeek()      { testPeek(false); }
    @Test
    public void testPeek_fair() { testPeek(true); }
    public void testPeek(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertNull(q.peek());
    }

    /**
     * element() throws NoSuchElementException if no active putter
     */
    @Test
    public void testElement()      { testElement(false); }
    @Test
    public void testElement_fair() { testElement(true); }
    public void testElement(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove() throws NoSuchElementException if no active putter
     */
    @Test
    public void testRemove()      { testRemove(false); }
    @Test
    public void testRemove_fair() { testRemove(true); }
    public void testRemove(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * contains returns false
     */
    @Test
    public void testContains()      { testContains(false); }
    @Test
    public void testContains_fair() { testContains(true); }
    public void testContains(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertFalse(q.contains(zero));
    }

    /**
     * clear ensures isEmpty
     */
    @Test
    public void testClear()      { testClear(false); }
    @Test
    public void testClear_fair() { testClear(true); }
    public void testClear(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll returns false unless empty
     */
    @Test
    public void testContainsAll()      { testContainsAll(false); }
    @Test
    public void testContainsAll_fair() { testContainsAll(true); }
    public void testContainsAll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] empty = new Integer[0];
        assertTrue(q.containsAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    /**
     * retainAll returns false
     */
    @Test
    public void testRetainAll()      { testRetainAll(false); }
    @Test
    public void testRetainAll_fair() { testRetainAll(true); }
    public void testRetainAll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] empty = new Integer[0];
        assertFalse(q.retainAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.retainAll(Arrays.asList(ints)));
    }

    /**
     * removeAll returns false
     */
    @Test
    public void testRemoveAll()      { testRemoveAll(false); }
    @Test
    public void testRemoveAll_fair() { testRemoveAll(true); }
    public void testRemoveAll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] empty = new Integer[0];
        assertFalse(q.removeAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    /**
     * toArray is empty
     */
    @Test
    public void testToArray()      { testToArray(false); }
    @Test
    public void testToArray_fair() { testToArray(true); }
    public void testToArray(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Object[] o = q.toArray();
        assertEquals(0, o.length);
    }

    /**
     * toArray(Integer array) returns its argument with the first
     * element (if present) nulled out
     */
    @Test
    public void testToArray2()      { testToArray2(false); }
    @Test
    public void testToArray2_fair() { testToArray2(true); }
    public void testToArray2(boolean fair) {
        final SynchronousQueue<Integer> q
            = new SynchronousQueue<Integer>(fair);
        Integer[] a;

        a = new Integer[0];
        assertSame(a, q.toArray(a));

        a = new Integer[3];
        Arrays.fill(a, 42);
        assertSame(a, q.toArray(a));
        assertNull(a[0]);
        for (int i = 1; i < a.length; i++)
            assertEquals(42, (int) a[i]);
    }

    /**
     * toArray(null) throws NPE
     */
    @Test
    public void testToArray_null()      { testToArray_null(false); }
    @Test
    public void testToArray_null_fair() { testToArray_null(true); }
    public void testToArray_null(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try {
            Object[] o = q.toArray((Object[]) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * iterator does not traverse any elements
     */
    @Test
    public void testIterator()      { testIterator(false); }
    @Test
    public void testIterator_fair() { testIterator(true); }
    public void testIterator(boolean fair) {
        assertIteratorExhausted(new SynchronousQueue(fair).iterator());
    }

    /**
     * iterator remove throws ISE
     */
    @Test
    public void testIteratorRemove()      { testIteratorRemove(false); }
    @Test
    public void testIteratorRemove_fair() { testIteratorRemove(true); }
    public void testIteratorRemove(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Iterator it = q.iterator();
        try {
            it.remove();
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * toString returns a non-null string
     */
    @Test
    public void testToString()      { testToString(false); }
    @Test
    public void testToString_fair() { testToString(true); }
    public void testToString(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        String s = q.toString();
        assertNotNull(s);
    }

    /**
     * drainTo(c) of empty queue doesn't transfer elements
     */
    @Test
    public void testDrainTo()      { testDrainTo(false); }
    @Test
    public void testDrainTo_fair() { testDrainTo(true); }
    public void testDrainTo(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(0, q.size());
        assertEquals(0, l.size());
    }

    /**
     * drainTo empties queue, unblocking a waiting put.
     */
    @Test
    public void testDrainToWithActivePut()      { testDrainToWithActivePut(false); }
    @Test
    public void testDrainToWithActivePut_fair() { testDrainToWithActivePut(true); }
    public void testDrainToWithActivePut(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(one);
            }});

        ArrayList l = new ArrayList();
        long startTime = System.nanoTime();
        while (l.isEmpty()) {
            q.drainTo(l);
            if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                fail("timed out");
            spinWait();
        }
        assertTrue(l.size() == 1);
        assertSame(one, l.get(0));
        awaitTermination(t);
    }

    /**
     * drainTo(c, n) empties up to n elements of queue into c
     */
    @Test
    public void testDrainToN() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(one);
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(two);
            }});

        ArrayList l = new ArrayList();
        int drained;
        while ((drained = q.drainTo(l, 1)) == 0) spinWait();
        assertEquals(1, drained);
        assertEquals(1, l.size());
        while ((drained = q.drainTo(l, 1)) == 0) spinWait();
        assertEquals(1, drained);
        assertEquals(2, l.size());
        assertTrue(l.contains(one));
        assertTrue(l.contains(two));
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * remove(null), contains(null) always return false
     */
    @Test
    public void testNeverContainsNull() {
        Collection<?> q = new SynchronousQueue();
        assertFalse(q.contains(null));
        assertFalse(q.remove(null));
    }

}
