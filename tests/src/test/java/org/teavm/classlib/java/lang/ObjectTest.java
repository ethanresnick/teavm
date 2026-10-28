/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ObjectTest {
    @Test
    public void objectCreated() {
        Object a = new Object();
        assertNotNull(a);
    }

    @Test
    public void differentInstancesNotEqual() {
        Object a = new Object();
        Object b = new Object();
        assertNotEquals(a, b);
    }

    @Test
    public void sameInstancesAreEqual() {
        Object a = new Object();
        Object b = a;
        assertEquals(a, b);
    }

    @Test
    public void multipleGetClassCallsReturnSameValue() {
        Object a = new Object();
        assertSame(a.getClass(), a.getClass());
    }

    @Test
    public void sameClassesAreEqual() {
        Object a = new Object();
        Object b = new Object();
        assertSame(a.getClass(), b.getClass());
    }

    @Test
    public void properInstanceDetected() {
        assertTrue(Object.class.isInstance(new Object()));
    }

    @Test
    public void toStringWorks() {
        assertTrue(new Object().toString().startsWith("java.lang.Object@"));
        assertTrue(new Object[2].toString().startsWith("[Ljava.lang.Object;@"));
        assertTrue(new byte[3].toString().startsWith("[B@"));
    }

    @Test
    public void primitiveArrayCloneCopiesOnlyTheArray() {
        // Clones of byte, short, char and int arrays, mixed with garbage and GCs, so that copies land just before
        // other live arrays. A clone that copied more than the array (as the C backend once did, sizing every
        // array element as a pointer) overwrote those arrays and the heap's own bookkeeping.
        var live = new Object[3000];
        for (var i = 0; i < live.length; ++i) {
            live[i] = filled(i, i % 41);
        }
        for (var round = 0; round < 6; ++round) {
            for (var i = round % 2; i < live.length; i += 2) {
                var copy = cloneOf(live[(i + 1) % live.length]);
                live[i] = filled(i, ((i + round) * 7) % 41);
                assertTrue(sameContents(copy, live[(i + 1) % live.length]), "clone at " + i);
            }
            System.gc();
            for (var i = 0; i < live.length; ++i) {
                assertTrue(holds(live[i], i), "array " + i + " in round " + round);
            }
        }
    }

    private static Object filled(int index, int length) {
        switch (index % 4) {
            case 0: {
                var a = new byte[length];
                Arrays.fill(a, (byte) index);
                return a;
            }
            case 1: {
                var a = new short[length];
                Arrays.fill(a, (short) index);
                return a;
            }
            case 2: {
                var a = new char[length];
                Arrays.fill(a, (char) index);
                return a;
            }
            default: {
                var a = new int[length];
                Arrays.fill(a, index);
                return a;
            }
        }
    }

    private static Object cloneOf(Object array) {
        if (array instanceof byte[]) {
            return ((byte[]) array).clone();
        } else if (array instanceof short[]) {
            return ((short[]) array).clone();
        } else if (array instanceof char[]) {
            return ((char[]) array).clone();
        } else {
            return ((int[]) array).clone();
        }
    }

    private static boolean sameContents(Object a, Object b) {
        if (a instanceof byte[] && b instanceof byte[]) {
            return Arrays.equals((byte[]) a, (byte[]) b);
        } else if (a instanceof short[] && b instanceof short[]) {
            return Arrays.equals((short[]) a, (short[]) b);
        } else if (a instanceof char[] && b instanceof char[]) {
            return Arrays.equals((char[]) a, (char[]) b);
        } else if (a instanceof int[] && b instanceof int[]) {
            return Arrays.equals((int[]) a, (int[]) b);
        }
        return false;
    }

    // Whether every element of the array still has the value filled() gave it.
    private static boolean holds(Object array, int index) {
        if (array instanceof byte[]) {
            for (var v : (byte[]) array) {
                if (v != (byte) index) {
                    return false;
                }
            }
        } else if (array instanceof short[]) {
            for (var v : (short[]) array) {
                if (v != (short) index) {
                    return false;
                }
            }
        } else if (array instanceof char[]) {
            for (var v : (char[]) array) {
                if (v != (char) index) {
                    return false;
                }
            }
        } else {
            for (var v : (int[]) array) {
                if (v != index) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    public void waitWorks() throws InterruptedException {
        long start = System.currentTimeMillis();
        final Object lock = new Object();
        synchronized (lock) {
            lock.wait(110);
        }
        long end = System.currentTimeMillis();
        assertTrue(end - start > 100);
    }

    @Test
    public void allQueuedThreadsEnterMonitor() throws InterruptedException {
        var lock = new Object();
        var entered = new int[1];
        var threads = new Thread[3];
        synchronized (lock) {
            for (var i = 0; i < threads.length; ++i) {
                threads[i] = new Thread(() -> {
                    synchronized (lock) {
                        entered[0]++;
                        // Makes this method async, so the synchronized block can wait for the monitor.
                        Thread.yield();
                    }
                });
                threads[i].start();
            }
            // Let every thread block on the monitor while this thread holds it.
            Thread.sleep(100);
        }
        for (var thread : threads) {
            thread.join(2000);
        }
        assertEquals(threads.length, entered[0]);
    }

    @Test
    public void contendedMonitorBlocksUntilReleased() throws InterruptedException {
        var lock = new Object();
        var events = new ArrayList<String>();
        var error = new RuntimeException[1];
        var other = new Thread(() -> {
            try {
                synchronized (lock) {
                    events.add("other");
                }
            } catch (RuntimeException e) {
                error[0] = e;
            }
        });
        synchronized (lock) {
            other.start();
            Thread.sleep(50);
            events.add("main");
        }
        other.join();
        assertNull(error[0]);
        assertEquals(List.of("main", "other"), events);
    }

    @Test
    public void notifyAfterTimedOutWaitWakesWaiter() throws InterruptedException {
        Object lock = new Object();
        // [0]: the waiter's timed wait has ended and it waits again; [1]: the main thread has notified.
        boolean[] state = new boolean[2];
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait(20);
                    state[0] = true;
                    while (!state[1]) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    // ends the thread
                }
            }
        });
        waiter.start();
        while (true) {
            synchronized (lock) {
                // The waiter holds the lock from setting state[0] until it waits, so it waits now.
                if (state[0]) {
                    state[1] = true;
                    lock.notify();
                    break;
                }
            }
            Thread.sleep(10);
        }
        waiter.join(2000);
        boolean woken = !waiter.isAlive();
        if (!woken) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        assertTrue(woken, "notify() after a timed-out wait() did not wake the waiting thread");
    }

    @Test
    public void interruptedWaitHoldsMonitorAndDoesNotAbsorbNotify() throws InterruptedException {
        Object lock = new Object();
        // [0]: in the first wait; [1]: interrupted, waits again; [2]: notified; [3]: second wait returned.
        boolean[] state = new boolean[4];
        boolean[] heldLockAfterInterrupt = new boolean[1];
        boolean[] flagClearedAfterInterrupt = new boolean[1];
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                try {
                    state[0] = true;
                    lock.wait();
                } catch (InterruptedException e) {
                    heldLockAfterInterrupt[0] = Thread.holdsLock(lock);
                    flagClearedAfterInterrupt[0] = !Thread.currentThread().isInterrupted();
                }
                state[1] = true;
                try {
                    while (!state[2]) {
                        lock.wait();
                    }
                    state[3] = true;
                } catch (InterruptedException e) {
                    // ends the thread
                }
            }
        });
        waiter.start();
        waitUntil(lock, state, 0);
        waiter.interrupt();
        waitUntil(lock, state, 1);
        synchronized (lock) {
            state[2] = true;
            lock.notify();
        }
        waiter.join(2000);
        if (waiter.isAlive()) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        assertTrue(heldLockAfterInterrupt[0], "wait() did not hold the monitor when it threw InterruptedException");
        assertTrue(flagClearedAfterInterrupt[0], "InterruptedException from wait() left the interrupt status set");
        assertTrue(state[3], "notify() after an interrupted wait() did not wake the waiting thread");
    }

    // wait(): "InterruptedException - if any thread interrupted the current thread before or while the current
    // thread was waiting. The interrupted status of the current thread is cleared when this exception is thrown."
    @Test
    public void waitThrowsForEarlierInterrupt() {
        var current = Thread.currentThread();
        var lock = new Object();
        var names = List.of("wait()", "wait(5000)", "wait(5000, 1)");
        var actions = List.<WaitAction>of(Object::wait, o -> o.wait(5000), o -> o.wait(5000, 1));
        for (var i = 0; i < actions.size(); ++i) {
            synchronized (lock) {
                current.interrupt();
                long start = System.currentTimeMillis();
                var watchdog = interruptLater(current);
                var threw = false;
                try {
                    actions.get(i).run(lock);
                } catch (InterruptedException e) {
                    threw = true;
                }
                watchdog.interrupt();
                assertTrue(threw, names.get(i) + " did not throw");
                assertTrue(System.currentTimeMillis() - start < 2500, names.get(i) + " waited");
                assertFalse(current.isInterrupted(), names.get(i) + " left the interrupt status set");
                assertTrue(Thread.holdsLock(lock), names.get(i) + " released the monitor");
            }
        }
    }

    @Test
    public void endedWaitsDoNotKeepThreadsAlive() throws InterruptedException {
        // Each case waits on its own lock: a notify() on a lock removes the dead listeners in front of the one it
        // wakes, which would hide a dead listener of an earlier case.
        var locks = new ArrayList<Object>();
        var names = new ArrayList<String>();
        var refs = new ArrayList<WeakReference<Thread>>();
        names.add("never waited");
        refs.add(runWaiter(newLock(locks), lock -> { }, false));
        names.add("wait(1) timed out");
        refs.add(runWaiter(newLock(locks), lock -> lock.wait(1), false));
        names.add("wait(0, 1) timed out");
        refs.add(runWaiter(newLock(locks), lock -> lock.wait(0, 1), false));
        names.add("a polling loop of timed-out waits");
        refs.add(runWaiter(newLock(locks), lock -> {
            for (var i = 0; i < 5; ++i) {
                lock.wait(1);
            }
        }, false));
        names.add("wait() interrupted");
        refs.add(runWaiter(newLock(locks), Object::wait, true));
        names.add("wait(10000) interrupted");
        refs.add(runWaiter(newLock(locks), lock -> lock.wait(10000), true));
        names.add("wait() notified");
        refs.add(runNotifiedWaiter(newLock(locks)));
        assertAllCollected(refs, names);
        // The locks must stay reachable until here: a dead listener in a lock's queue is what kept a thread alive.
        assertEquals(refs.size(), locks.size());
    }

    private Thread keptWaiter;

    @Test
    public void notifiedWaitDoesNotKeepItsLock() throws InterruptedException {
        var ref = runNotifiedWaiterAndDropLock();
        if (ref.get() != null) {
            GCSupport.tryToTriggerGC(ref);
        }
        assertNotNull(keptWaiter);
        assertNull(ref.get(), "an ended thread whose wait notify() ended kept the lock alive");
    }

    // Only the waiter's interrupt handler may keep the lock: the waiter takes the lock from a holder that it clears,
    // so neither its Runnable nor the test refers to the lock once the waiter has ended.
    private WeakReference<Object> runNotifiedWaiterAndDropLock() throws InterruptedException {
        Object[] holder = { new Object() };
        var ref = new WeakReference<>(holder[0]);
        boolean[] state = new boolean[2];
        keptWaiter = new Thread(() -> waitUntilNotified(holder, state));
        keptWaiter.start();
        waitUntil(ref.get(), state, 0);
        synchronized (ref.get()) {
            state[1] = true;
            ref.get().notify();
        }
        keptWaiter.join(2000);
        assertTrue(!keptWaiter.isAlive(), "the waiter did not end");
        assertNull(holder[0]);
        return ref;
    }

    private static void waitUntilNotified(Object[] holder, boolean[] state) {
        Object lock = holder[0];
        holder[0] = null;
        synchronized (lock) {
            state[0] = true;
            try {
                while (!state[1]) {
                    lock.wait();
                }
            } catch (InterruptedException e) {
                // ends the thread
            }
        }
    }

    private static Object newLock(List<Object> locks) {
        var lock = new Object();
        locks.add(lock);
        return lock;
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

    interface WaitAction {
        void run(Object lock) throws InterruptedException;
    }

    private static WeakReference<Thread> runWaiter(Object lock, WaitAction action, boolean interrupt)
            throws InterruptedException {
        boolean[] state = new boolean[1];
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                state[0] = true;
                try {
                    action.run(lock);
                } catch (InterruptedException e) {
                    // ends the thread
                }
            }
        });
        waiter.start();
        if (interrupt) {
            waitUntil(lock, state, 0);
            waiter.interrupt();
        }
        waiter.join(2000);
        assertTrue(!waiter.isAlive(), "the waiter did not end");
        return new WeakReference<>(waiter);
    }

    private static WeakReference<Thread> runNotifiedWaiter(Object lock) throws InterruptedException {
        boolean[] state = new boolean[2];
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                state[0] = true;
                try {
                    while (!state[1]) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    // ends the thread
                }
            }
        });
        waiter.start();
        waitUntil(lock, state, 0);
        synchronized (lock) {
            state[1] = true;
            lock.notify();
        }
        waiter.join(2000);
        assertTrue(!waiter.isAlive(), "the waiter did not end");
        return new WeakReference<>(waiter);
    }

    // The waiter holds the lock from setting a flag until it waits, so once the flag is set, it waits.
    private static void waitUntil(Object lock, boolean[] state, int index) throws InterruptedException {
        while (true) {
            synchronized (lock) {
                if (state[index]) {
                    return;
                }
            }
            Thread.sleep(10);
        }
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
}
