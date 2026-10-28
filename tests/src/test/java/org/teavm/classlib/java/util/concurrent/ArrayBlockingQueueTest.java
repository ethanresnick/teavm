/*
 *  Copyright 2018 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ArrayBlockingQueueTest {
    @Test
    public void constructed() {
        List<Integer> list = Arrays.asList(2, 3, 5);

        try {
            new ArrayBlockingQueue<>(2, false, list);
            fail("IAE expected");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            new ArrayBlockingQueue<>(0);
            fail("IAE expected");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            new ArrayBlockingQueue<>(1, false, null);
            fail("IAE expected");
        } catch (NullPointerException e) {
            // Expected
        }

        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5, false, list);
        assertEquals(3, queue.size());
        assertEquals(2, queue.remainingCapacity());

        assertEquals(2, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertEquals(5, queue.poll().intValue());
        assertNull(queue.poll());
    }

    @Test
    public void singleThread() {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertEquals(1, queue.poll().intValue());
        queue.add(4);
        assertEquals(2, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertEquals(4, queue.poll().intValue());
        assertNull(queue.poll());

        queue.add(5);
        assertEquals(5, queue.poll().intValue());
    }

    @Test
    public void blockingAddition() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        assertTrue(queue.offer(1));
        assertFalse(queue.offer(2));

        new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                // Do nothing
            }
            queue.poll();
        }).start();

        long start = System.currentTimeMillis();
        queue.put(3);
        long end = System.currentTimeMillis();
        assertTrue(start + 50 < end && start + 5000 > end, "Wait time " + (end - start));

        assertEquals(3, queue.remove().intValue());
    }

    @Test
    public void blockingRemoval() throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        assertNull(queue.poll());

        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // Do nothing
            }
            queue.add(1);
            queue.add(2);
        }).start();

        long start = System.currentTimeMillis();
        int a = queue.take();
        long end = System.currentTimeMillis();
        int b = queue.take();

        assertTrue(start + 100 < end && start + 5000 > end, "Wait time " + (end - start));
        assertEquals(1, a);
        assertEquals(2, b);
    }

    @Test
    public void shiftQueueSize() {
        assertEquals(6, shiftQueue().size());
    }

    @Test
    public void remove() {
        BlockingQueue<Integer> queue = simpleQueue();
        assertTrue(queue.remove(2));
        assertEquals(2, queue.size());
        assertEquals(1, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertNull(queue.poll());

        queue = shiftQueue();
        assertTrue(queue.remove(1));
        assertEquals(5, queue.size());
        assertEquals(0, queue.poll().intValue());
        assertEquals(2, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertEquals(4, queue.poll().intValue());
        assertEquals(5, queue.poll().intValue());
        assertNull(queue.poll());

        queue = shiftQueue();
        assertTrue(queue.remove(4));
        assertEquals(5, queue.size());
        assertEquals(0, queue.poll().intValue());
        assertEquals(1, queue.poll().intValue());
        assertEquals(2, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertEquals(5, queue.poll().intValue());
        assertNull(queue.poll());
    }

    @Test
    public void dumpsToArray() {
        BlockingQueue<Integer> queue = simpleQueue();
        assertArrayEquals(new Integer[] { 1, 2, 3 }, queue.toArray());

        queue = shiftQueue();
        assertArrayEquals(new Integer[] { 0, 1, 2, 3, 4, 5 }, queue.toArray());
    }

    @Test
    public void dumpsToTypedArray() {
        BlockingQueue<Integer> queue = simpleQueue();
        assertArrayEquals(new Integer[] { 1, 2, 3 }, queue.toArray(new Integer[3]));
        assertArrayEquals(new Integer[] { 1, 2, 3 }, queue.toArray(new Integer[1]));

        Integer[] array = new Integer[] { 10, 11, 12, 13, 14, 15 };
        assertArrayEquals(new Integer[] { 1, 2, 3, null, 14, 15 }, queue.toArray(array));
    }

    @Test
    public void drains() {
        BlockingQueue<Integer> queue = simpleQueue();
        List<Integer> target = new ArrayList<>();
        assertEquals(2, queue.drainTo(target, 2));
        assertEquals(Arrays.asList(1, 2), target);

        queue = simpleQueue();
        target.clear();
        assertEquals(3, queue.drainTo(target, 4));
        assertEquals(Arrays.asList(1, 2, 3), target);

        queue = shiftQueue();
        target.clear();
        assertEquals(2, queue.drainTo(target, 2));
        assertEquals(Arrays.asList(0, 1), target);

        queue = shiftQueue();
        target.clear();
        assertEquals(4, queue.drainTo(target, 4));
        assertEquals(Arrays.asList(0, 1, 2, 3), target);
    }

    @Test
    public void iterator() {
        BlockingQueue<Integer> queue = simpleQueue();
        assertEquals(Arrays.asList(1, 2, 3), new ArrayList<>(queue));

        queue = shiftQueue();
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), new ArrayList<>(queue));
    }

    private BlockingQueue<Integer> simpleQueue() {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        queue.add(0);
        queue.remove();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        return queue;
    }

    private BlockingQueue<Integer> shiftQueue() {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        for (int i = 0; i < 7; ++i) {
            queue.add(i);
        }
        while (!queue.isEmpty()) {
            queue.remove();
        }

        for (int i = 0; i < 6; ++i) {
            queue.add(i);
        }
        return queue;
    }

    // Thread.interrupt(): a thread blocked in wait(), join() or sleep() "will receive an InterruptedException",
    // and "its interrupt status will be cleared". The JDK's ArrayBlockingQueue waits in Condition.await(), which
    // also clears the status when it throws.
    @Test
    public void takeCanBeInterruptedAgain() throws InterruptedException {
        var queue = new ArrayBlockingQueue<Integer>(1);
        var interrupts = new int[1];
        var statusClearedEachTime = new boolean[] { true };
        var taken = new Integer[1];
        var thread = new Thread(() -> {
            while (true) {
                try {
                    taken[0] = queue.take();
                    return;
                } catch (InterruptedException e) {
                    interrupts[0]++;
                    statusClearedEachTime[0] &= !Thread.currentThread().isInterrupted();
                }
            }
        });
        thread.start();
        for (var i = 0; i < 4; ++i) {
            Thread.sleep(50);
            thread.interrupt();
        }
        Thread.sleep(50);
        queue.add(42);
        thread.join(2000);
        assertFalse(thread.isAlive());
        assertEquals(4, interrupts[0]);
        assertTrue(statusClearedEachTime[0], "InterruptedException left the interrupt status set");
        assertEquals(Integer.valueOf(42), taken[0], "the wait after the interrupts did not get the element");
    }

    // BlockingQueue's blocking methods throw "InterruptedException - if interrupted while waiting".
    // Condition.await(), which the JDK's ArrayBlockingQueue waits in, throws if the thread "has its interrupted
    // status set on entry to this method", and clears the status.
    @Test
    public void blockingCallsThrowForEarlierInterrupt() {
        var current = Thread.currentThread();
        var names = List.of("take()", "poll(5 s)", "put()", "offer(5 s)");
        var actions = List.<WaitAction>of(
                BlockingQueue::take,
                queue -> queue.poll(5, TimeUnit.SECONDS),
                queue -> queue.put(2),
                queue -> queue.offer(2, 5, TimeUnit.SECONDS));
        for (var i = 0; i < actions.size(); ++i) {
            // An empty queue for take() and poll(), a full one for put() and offer().
            var queue = new ArrayBlockingQueue<Integer>(1);
            if (i >= 2) {
                queue.add(1);
            }
            current.interrupt();
            long start = System.currentTimeMillis();
            var watchdog = interruptLater(current);
            try {
                actions.get(i).run(queue);
                fail(names.get(i) + " did not throw");
            } catch (InterruptedException e) {
                // expected
            }
            watchdog.interrupt();
            assertTrue(System.currentTimeMillis() - start < 2500, names.get(i) + " waited");
            assertFalse(current.isInterrupted(), names.get(i) + " left the interrupt status set");
            assertEquals(i >= 2 ? List.of(1) : List.of(), new ArrayList<>(queue), names.get(i) + " changed the queue");
        }
    }

    // A wait that ends must clear only its own thread's interrupt handler. A timed-out wait ends from a timer, and
    // a wait that add() wakes ends from a posted event; there, the current thread can be another thread, which is
    // blocked in sleep(). If that thread's handler is cleared, interrupt() no longer wakes it.
    // Thread.currentThread(): "Returns the Thread object for the current thread." An interrupt that ends a take()
    // must not resume the waiter inside Thread.interrupt(), on the interrupting thread: the waiter must run as
    // itself, and the interrupting thread must stay itself. A pool worker blocks in take() again after the
    // interrupt, so cover that too.
    @Test
    public void currentThreadAfterInterruptingTake() throws InterruptedException {
        for (var blocksAgain : new boolean[] { false, true }) {
            var name = blocksAgain ? "take() again after the interrupt" : "one take()";
            var queue = new ArrayBlockingQueue<Integer>(1);
            var seenByWaiter = new Thread[2];
            var taken = new Integer[1];
            var waiter = new Thread(() -> {
                try {
                    queue.take();
                } catch (InterruptedException e) {
                    seenByWaiter[0] = Thread.currentThread();
                }
                if (blocksAgain) {
                    try {
                        taken[0] = queue.take();
                    } catch (InterruptedException e) {
                        // fails the test below
                    }
                    seenByWaiter[1] = Thread.currentThread();
                }
            });
            var current = Thread.currentThread();
            waiter.start();
            Thread.sleep(50);
            waiter.interrupt();
            assertSame(current, Thread.currentThread(), name + ": the interrupting thread changed");
            Thread.sleep(50);
            assertSame(current, Thread.currentThread(), name + ": the interrupting thread changed after a sleep");
            if (blocksAgain) {
                queue.add(1);
            }
            waiter.join(2000);
            assertFalse(waiter.isAlive(), name + ": the waiter did not end");
            assertSame(waiter, seenByWaiter[0], name + ": the interrupted waiter did not run as itself");
            if (blocksAgain) {
                assertEquals(Integer.valueOf(1), taken[0], name + ": the second take() did not get the element");
                assertSame(waiter, seenByWaiter[1], name + ": the waiter did not run as itself after take()");
            }
            assertSame(current, Thread.currentThread(), name + ": the interrupting thread changed after join()");
        }
    }

    @Test
    public void endedWaitDoesNotClearAnotherThreadsInterruptHandler() throws InterruptedException {
        var names = List.of("poll(100 ms) timed out", "offer(100 ms) timed out", "take() woken by add()");
        for (var i = 0; i < names.size(); ++i) {
            var queue = new ArrayBlockingQueue<Integer>(1);
            if (i == 1) {
                queue.add(1);
            }
            var interrupted = new boolean[1];
            var addFirst = i == 2;
            var sleeper = new Thread(() -> {
                if (addFirst) {
                    queue.add(1);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    interrupted[0] = true;
                }
            });
            // The sleeper starts after this thread blocks, so it is the last thread to run when the wait ends.
            sleeper.start();
            switch (i) {
                case 0:
                    assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
                    break;
                case 1:
                    assertFalse(queue.offer(2, 100, TimeUnit.MILLISECONDS));
                    break;
                default:
                    assertEquals(Integer.valueOf(1), queue.take());
                    break;
            }
            sleeper.interrupt();
            sleeper.join(2000);
            assertTrue(interrupted[0], "after " + names.get(i) + ", interrupt() did not wake a sleeping thread");
        }
    }

    @Test
    public void endedWaitsDoNotKeepThreadsAlive() throws InterruptedException {
        // Each case waits on its own queue: a change to a queue wakes and removes all of its waiters, which would
        // hide a dead waiter of an earlier case.
        var queues = new ArrayList<BlockingQueue<Integer>>();
        var names = new ArrayList<String>();
        var refs = new ArrayList<WeakReference<Thread>>();
        names.add("never waited");
        refs.add(runWaiter(newQueue(queues, 0), queue -> { }, null));
        names.add("poll(1 ms) timed out");
        refs.add(runWaiter(newQueue(queues, 0), queue -> queue.poll(1, TimeUnit.MILLISECONDS), null));
        names.add("offer(1 ms) timed out");
        refs.add(runWaiter(newQueue(queues, 1), queue -> queue.offer(2, 1, TimeUnit.MILLISECONDS), null));
        names.add("a polling loop of timed-out polls");
        refs.add(runWaiter(newQueue(queues, 0), queue -> {
            for (var i = 0; i < 5; ++i) {
                queue.poll(1, TimeUnit.MILLISECONDS);
            }
        }, null));
        // interruptedWaitsDoNotKeepThreadsAlive covers interrupted waits.
        names.add("take() woken by add()");
        var queue = newQueue(queues, 0);
        refs.add(runWaiter(queue, BlockingQueue::take, () -> queue.add(1)));
        assertAllCollected(refs, names);
        // The queues must stay reachable until here: a dead waiter in a queue is what kept a thread alive.
        assertEquals(refs.size(), queues.size());
    }

    @Test
    public void interruptedWaitsDoNotKeepThreadsAlive() throws InterruptedException {
        var queues = new ArrayList<BlockingQueue<Integer>>();
        var names = new ArrayList<String>();
        var refs = new ArrayList<WeakReference<Thread>>();
        names.add("take() interrupted");
        refs.add(runWaiter(newQueue(queues, 0), BlockingQueue::take, INTERRUPT));
        names.add("put() interrupted");
        refs.add(runWaiter(newQueue(queues, 1), queue -> queue.put(2), INTERRUPT));
        names.add("poll(10 s) interrupted");
        refs.add(runWaiter(newQueue(queues, 0), queue -> queue.poll(10, TimeUnit.SECONDS), INTERRUPT));
        assertAllCollected(refs, names);
        assertEquals(refs.size(), queues.size());
    }

    // A queue of capacity 1 that holds the given number of elements.
    private static BlockingQueue<Integer> newQueue(List<BlockingQueue<Integer>> queues, int size) {
        var queue = new ArrayBlockingQueue<Integer>(1);
        for (var i = 0; i < size; ++i) {
            queue.add(1);
        }
        queues.add(queue);
        return queue;
    }

    // A collection that clears one reference usually clears them all, and each tryToTriggerGC call is slow, so
    // trigger one only for a reference that is still set.
    private static void assertAllCollected(List<WeakReference<Thread>> refs, List<String> names) {
        for (var ref : refs) {
            if (ref.get() != null) {
                GCSupport.tryToTriggerGC(ref);
            }
        }
        for (var i = 0; i < refs.size(); ++i) {
            assertNull(refs.get(i).get(), "a thread that ended after " + names.get(i) + " was kept alive");
        }
    }

    // AbstractQueue.addAll: "Attempts to addAll of a queue to itself result in IllegalArgumentException."
    // Throws: "IllegalArgumentException - ... or if the specified collection is this queue"
    @Test
    public void addAllOfItselfThrows() {
        // Empty, partly filled, and full queues of capacity 3.
        for (int size = 0; size <= 3; ++size) {
            ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(3);
            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < size; ++i) {
                queue.add(i);
                expected.add(i);
            }
            assertThrows(IllegalArgumentException.class, () -> queue.addAll(queue));
            assertEquals(expected, new ArrayList<>(queue));
            assertEquals(3 - size, queue.remainingCapacity());
        }
    }

    interface WaitAction {
        void run(BlockingQueue<Integer> queue) throws InterruptedException;
    }

    private static final Runnable INTERRUPT = () -> { };

    private static WeakReference<Thread> runWaiter(BlockingQueue<Integer> queue, WaitAction action,
            Runnable wake) throws InterruptedException {
        Thread waiter = new Thread(() -> {
            try {
                action.run(queue);
            } catch (InterruptedException e) {
                // ends the thread
            }
        });
        waiter.start();
        if (wake != null) {
            // Gives the waiter time to block.
            Thread.sleep(50);
            if (wake == INTERRUPT) {
                waiter.interrupt();
            } else {
                wake.run();
            }
        }
        waiter.join(2000);
        assertFalse(waiter.isAlive(), "the waiter did not end");
        return new WeakReference<>(waiter);
    }

    // Ends a call that blocks although it should throw at once, so that the test fails on its time check instead
    // of hanging. Stop the returned thread with interrupt().
    private static Thread interruptLater(Thread target) {
        var watchdog = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return;
            }
            target.interrupt();
        });
        watchdog.start();
        return watchdog;
    }

    // poll(timeout, unit): "waiting up to the specified wait time if necessary for an element to become available".
    // offer(e, timeout, unit): "waiting up to the specified wait time if necessary for space to become available".
    // A timeout longer than a host timer can hold (2^31 - 1 ms), or too long to add to the current time, must not
    // end the wait at once.
    @Test
    public void timedPollAndOfferWithLongTimeoutsWait() throws InterruptedException {
        long[] timeouts = { 3_000_000_000L, Long.MAX_VALUE - 1, Long.MAX_VALUE };
        TimeUnit[] units = { TimeUnit.MILLISECONDS, TimeUnit.NANOSECONDS, TimeUnit.DAYS };
        for (long timeout : timeouts) {
            for (TimeUnit unit : units) {
                if (unit == TimeUnit.NANOSECONDS && timeout < Long.MAX_VALUE - 1) {
                    // 3 s, not a long timeout
                    continue;
                }
                timedWaitWaits(true, timeout, unit);
                timedWaitWaits(false, timeout, unit);
            }
        }
    }

    private static void timedWaitWaits(boolean poll, long timeout, TimeUnit unit) throws InterruptedException {
        var queue = new ArrayBlockingQueue<Integer>(1);
        if (!poll) {
            queue.add(0);
        }
        String call = (poll ? "poll(" : "offer(1, ") + timeout + " " + unit + ")";
        var result = new Object[] { "not ended" };
        var waiter = new Thread(() -> {
            try {
                result[0] = poll ? queue.poll(timeout, unit) : queue.offer(1, timeout, unit);
            } catch (InterruptedException e) {
                result[0] = e;
            }
        });
        waiter.start();
        Thread.sleep(100);
        assertTrue(waiter.isAlive(), call + " ended at once with " + result[0]);
        if (poll) {
            queue.add(1);
        } else {
            assertEquals(0, queue.take());
        }
        waiter.join(2000);
        assertFalse(waiter.isAlive(), call + " did not end when the queue changed");
        assertEquals(poll ? (Object) 1 : (Object) Boolean.TRUE, result[0], call);
        if (!poll) {
            assertEquals(1, queue.poll());
        }
    }

    // A timeout "less than or equal to zero" does not wait, and a short one ends after about that time.
    @Test
    public void timedPollAndOfferTimeOut() throws InterruptedException {
        long[] timeouts = { 0, -1, Long.MIN_VALUE, 1, 30 };
        TimeUnit[] units = { TimeUnit.MILLISECONDS, TimeUnit.NANOSECONDS };
        for (long timeout : timeouts) {
            for (TimeUnit unit : units) {
                long millis = Math.max(0, unit.toMillis(timeout));
                var empty = new ArrayBlockingQueue<Integer>(1);
                long start = System.nanoTime();
                assertNull(empty.poll(timeout, unit));
                checkElapsed("poll(" + timeout + " " + unit + ")", start, millis);

                var full = new ArrayBlockingQueue<Integer>(1);
                full.add(0);
                start = System.nanoTime();
                assertFalse(full.offer(1, timeout, unit));
                checkElapsed("offer(1, " + timeout + " " + unit + ")", start, millis);
                assertEquals(1, full.size());
            }
        }
    }

    private static void checkElapsed(String call, long startNanos, long expectedMillis) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        // The queue's clock counts whole milliseconds, so a wait can end up to 1 ms short of its timeout.
        assertTrue(elapsedMillis >= expectedMillis - 1, call + " ended after " + elapsedMillis + " ms");
        assertTrue(elapsedMillis < expectedMillis + 2000, call + " ended after " + elapsedMillis + " ms");
    }

    // put, take, and the timed offer and poll are blocking calls. When they do not block, they must still let
    // other threads run, because green threads switch only when a thread blocks or yields.
    @Test
    public void putAndTakeLetOtherThreadsRun() throws InterruptedException {
        var queue = new ArrayBlockingQueue<Integer>(1);
        assertSpinningLetsOtherThreadsRun(() -> {
            queue.put(1);
            queue.take();
        });
    }

    @Test
    public void timedOfferAndPollLetOtherThreadsRun() throws InterruptedException {
        var queue = new ArrayBlockingQueue<Integer>(1);
        assertSpinningLetsOtherThreadsRun(() -> {
            assertTrue(queue.offer(1, 10, TimeUnit.SECONDS));
            assertEquals(1, queue.poll(10, TimeUnit.SECONDS));
        });
    }

    interface BlockingAction {
        void run() throws InterruptedException;
    }

    private static void assertSpinningLetsOtherThreadsRun(BlockingAction spin) throws InterruptedException {
        var ran = new boolean[1];
        var other = new Thread(() -> ran[0] = true);
        other.start();
        long start = System.nanoTime();
        while (!ran[0] && System.nanoTime() - start < 5_000_000_000L) {
            spin.run();
        }
        assertTrue(ran[0], "the other thread did not run");
        other.join();
    }
}
