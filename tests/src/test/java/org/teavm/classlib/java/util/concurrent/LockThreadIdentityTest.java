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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * TeaVM-specific checks of the green-thread LockSupport: a thread that parks is still the current thread when it
 * resumes, however it was woken, and an interrupt wakes only the thread it targets.
 */
@TeaVMTest
public class LockThreadIdentityTest {
    // Every way out of park: unpark, timeout, interrupt. After each, Thread.currentThread() must be the parked thread,
    // although the thread that woke it (or the main thread, for a timeout) ran last.
    @Test
    public void currentThreadAfterPark() throws InterruptedException {
        List<String> errors = new ArrayList<>();
        Thread[] threads = new Thread[3];
        for (int i = 0; i < threads.length; ++i) {
            int kind = i;
            threads[i] = new Thread(() -> {
                Thread self = Thread.currentThread();
                if (kind == 1) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                } else {
                    LockSupport.park();
                }
                if (Thread.currentThread() != self) {
                    errors.add("kind " + kind + ": current thread is " + Thread.currentThread().getName());
                }
            }, "parker" + i);
            threads[i].start();
        }
        Thread.sleep(20);
        LockSupport.unpark(threads[0]);
        threads[2].interrupt();
        // Keep this thread busy for a while without suspending, so the woken threads resume from the event loop
        // after it suspends below, with this thread as the last current thread.
        long end = System.currentTimeMillis() + 100;
        while (System.currentTimeMillis() < end) {
            // busy
        }
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(List.of(), errors);
    }

    // A contended lock: the waiter that gets the lock owns it, not the thread that released it.
    @Test
    public void ownerAfterContendedLock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();
        Thread[] owner = new Thread[2];
        boolean[] held = new boolean[2];
        lock.lock();
        Thread waiter = new Thread(() -> {
            lock.lock();
            try {
                owner[0] = Thread.currentThread();
                held[0] = lock.isHeldByCurrentThread();
                cond.awaitUninterruptibly();
                owner[1] = Thread.currentThread();
                held[1] = lock.isHeldByCurrentThread();
            } finally {
                lock.unlock();
            }
        });
        waiter.start();
        Thread.sleep(20);
        lock.unlock();
        Thread.sleep(20);
        lock.lock();
        cond.signal();
        lock.unlock();
        waiter.join();
        assertSame(waiter, owner[0]);
        assertSame(waiter, owner[1]);
        assertTrue(held[0]);
        assertTrue(held[1]);
        assertFalse(lock.isLocked());
    }

    // An interrupt of one parked thread wakes that thread only, and sets only its interrupt status.
    @Test
    public void interruptWakesOnlyTarget() throws InterruptedException {
        boolean[] woke = new boolean[2];
        Thread a = new Thread(() -> {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
            woke[0] = Thread.currentThread().isInterrupted();
        });
        Thread b = new Thread(() -> {
            long start = System.currentTimeMillis();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
            woke[1] = System.currentTimeMillis() - start < 250 || Thread.currentThread().isInterrupted();
        });
        a.start();
        b.start();
        Thread.sleep(30);
        a.interrupt();
        a.join();
        b.join();
        assertTrue(woke[0], "a wakes interrupted");
        assertFalse(woke[1], "b sleeps its full timeout, not interrupted");
        assertFalse(Thread.currentThread().isInterrupted());
    }

    // A thread that ends while holding a lock leaves it held, as in the JDK; tryLock fails, and a timed tryLock
    // times out.
    @Test
    public void lockHeldByDeadThread() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Thread t = new Thread(lock::lock);
        t.start();
        t.join();
        assertTrue(lock.isLocked());
        assertFalse(lock.tryLock());
        assertFalse(lock.tryLock(30, TimeUnit.MILLISECONDS));
    }
}
