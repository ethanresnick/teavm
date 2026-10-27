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
package org.teavm.classlib.java.lang.ref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

// The C backend generates an empty body for ReferenceQueue.remove(), so a waiter there never blocks.
@TeaVMTest
@EachTestCompiledSeparately
@SkipPlatform(TestPlatform.C)
public class ReferenceQueueTest {
    // remove(): "InterruptedException - If the wait is interrupted". Thread.interrupt(): a thread blocked in
    // wait(), which the JDK's ReferenceQueue waits in, "will receive an InterruptedException", and "its interrupt
    // status will be cleared".
    @Test
    public void removeCanBeInterruptedAgain() throws InterruptedException {
        var names = List.of("remove()", "remove(10 s)");
        for (var i = 0; i < names.size(); ++i) {
            var queue = new ReferenceQueue<Object>();
            var interrupts = new int[1];
            var statusClearedEachTime = new boolean[] { true };
            var timed = i == 1;
            var thread = new Thread(() -> {
                for (var j = 0; j < 4; ++j) {
                    try {
                        if (timed) {
                            queue.remove(10_000);
                        } else {
                            queue.remove();
                        }
                    } catch (InterruptedException e) {
                        interrupts[0]++;
                        statusClearedEachTime[0] &= !Thread.currentThread().isInterrupted();
                    }
                }
            });
            thread.start();
            for (var j = 0; j < 4; ++j) {
                Thread.sleep(50);
                thread.interrupt();
            }
            thread.join(2000);
            assertFalse(thread.isAlive(), names.get(i) + ": the waiter did not end");
            assertEquals(4, interrupts[0], names.get(i));
            assertTrue(statusClearedEachTime[0], names.get(i) + ": InterruptedException left the interrupt status set");
        }
    }

    // Thread.currentThread(): "Returns the Thread object for the current thread." An interrupt that ends a
    // remove() must not resume the waiter inside Thread.interrupt(), on the interrupting thread.
    @Test
    public void currentThreadAfterInterruptingRemove() throws InterruptedException {
        var queue = new ReferenceQueue<Object>();
        var seenByWaiter = new Thread[1];
        var waiter = new Thread(() -> {
            try {
                queue.remove();
            } catch (InterruptedException e) {
                seenByWaiter[0] = Thread.currentThread();
            }
        });
        var current = Thread.currentThread();
        waiter.start();
        Thread.sleep(50);
        waiter.interrupt();
        assertSame(current, Thread.currentThread(), "the interrupting thread changed");
        waiter.join(2000);
        assertFalse(waiter.isAlive(), "the waiter did not end");
        assertSame(waiter, seenByWaiter[0], "the interrupted waiter did not run as itself");
        assertSame(current, Thread.currentThread(), "the interrupting thread changed after join()");
    }

    // Object.wait(), which the JDK's ReferenceQueue waits in: "InterruptedException - if any thread interrupted
    // the current thread before or while the current thread was waiting. The interrupted status of the current
    // thread is cleared when this exception is thrown."
    @Test
    public void removeThrowsForEarlierInterrupt() {
        var current = Thread.currentThread();
        var queue = new ReferenceQueue<Object>();
        var names = List.of("remove()", "remove(5 s)");
        for (var i = 0; i < names.size(); ++i) {
            current.interrupt();
            long start = System.currentTimeMillis();
            var watchdog = interruptLater(current);
            try {
                if (i == 0) {
                    queue.remove();
                } else {
                    queue.remove(5000);
                }
                fail(names.get(i) + " did not throw");
            } catch (InterruptedException e) {
                // expected
            }
            watchdog.interrupt();
            assertTrue(System.currentTimeMillis() - start < 2500, names.get(i) + " waited");
            assertFalse(current.isInterrupted(), names.get(i) + " left the interrupt status set");
        }
    }

    // remove(timeout): "Returns: A reference object, if one was available within the specified timeout period,
    // otherwise null".
    @Test
    public void timedRemoveReturnsNull() throws InterruptedException {
        var queue = new ReferenceQueue<Object>();
        long start = System.currentTimeMillis();
        assertNull(queue.remove(50));
        assertTrue(System.currentTimeMillis() - start >= 40, "remove(50) did not wait");
    }

    // A timed-out remove() ends from a timer, where the current thread can be another thread, which is blocked in
    // sleep(). The wait must clear only its own thread's interrupt handler: if it clears the sleeping thread's,
    // interrupt() no longer wakes that thread.
    @Test
    public void timedOutRemoveDoesNotClearAnotherThreadsInterruptHandler() throws InterruptedException {
        var queue = new ReferenceQueue<Object>();
        var interrupted = new boolean[1];
        var sleeper = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted[0] = true;
            }
        });
        // The sleeper starts after this thread blocks, so it is the last thread to run when the wait times out.
        sleeper.start();
        assertNull(queue.remove(100));
        sleeper.interrupt();
        sleeper.join(2000);
        assertTrue(interrupted[0], "after a timed-out remove(), interrupt() did not wake a sleeping thread");
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

    // remove(timeout): "If positive, block for up to timeout milliseconds". A timeout too long to add to the current
    // time must not wrap to the past and end the wait at once.
    @Test
    public void removeWithLongMaxValueTimeoutWaits() throws InterruptedException {
        removeWaits(Long.MAX_VALUE);
    }

    // A timeout longer than a host timer can hold (2^31 - 1 ms) must not end the wait at once either.
    @Test
    public void removeWithTimeoutBeyondIntRangeWaits() throws InterruptedException {
        removeWaits(3_000_000_000L);
    }

    private static void removeWaits(long timeout) throws InterruptedException {
        var queue = new ReferenceQueue<Object>();
        var result = new Object[] { "not ended" };
        var waiter = new Thread(() -> {
            try {
                result[0] = queue.remove(timeout);
            } catch (InterruptedException e) {
                result[0] = e;
            }
        });
        waiter.start();
        Thread.sleep(100);
        assertTrue(waiter.isAlive(), "remove(" + timeout + ") ended at once with " + result[0]);
        waiter.interrupt();
        waiter.join(2000);
        assertFalse(waiter.isAlive(), "the interrupted waiter did not end");
        assertTrue(result[0] instanceof InterruptedException, "remove(" + timeout + ") ended with " + result[0]);
    }
}
