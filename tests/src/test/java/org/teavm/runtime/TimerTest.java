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
package org.teavm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;
import org.teavm.platform.Platform;

@TeaVMTest
@SkipJVM
@OnlyPlatform({TestPlatform.JAVASCRIPT, TestPlatform.WEBASSEMBLY_GC})
@EachTestCompiledSeparately
public class TimerTest {
    @Test
    public void timerChecksClockOncePerHostCallback() throws InterruptedException {
        Thread.sleep(0);
        installTimers(7);
        try {
            int reads = timerClockReads(30);
            assertTrue(elapsed() >= 30);
            assertTrue(firings() >= 5);
            // One scheduling read, then one per host callback. Sample before resuming the thread, since
            // Thread.setCurrentThread separately reads the clock to reset its time slice.
            assertEquals(firings() + 1, reads);
            assertEquals(0, wallClockReads());
        } finally {
            restoreTimers();
        }
    }

    @Async
    private static native int timerClockReads(long delay);

    private static void timerClockReads(long delay, AsyncCallback<Integer> callback) {
        EventQueue.Event action = () -> callback.complete(clockReads());
        if (PlatformDetector.isWebAssemblyGC()) {
            EventQueue.scheduleAfter(action, delay);
        } else {
            Platform.scheduleAfter(action, delay);
        }
    }

    @Test
    public void everyTimedWaitRearms() throws InterruptedException {
        // Thread.sleep: "specified number of milliseconds", subject to timer/scheduler precision.
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#sleep(long)
        // ReferenceQueue.remove: waits until a reference is available or its timeout expires.
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ref/ReferenceQueue.html#remove(long)
        // Object.wait and LockSupport permit spurious wakes; this helper deliberately does not manufacture them
        // when the host timer fires early, and notifications, interrupts and permits still end those waits.
        Object monitor = new Object();
        var references = new ReferenceQueue<>();
        var empty = new ArrayBlockingQueue<Integer>(1);
        var full = new ArrayBlockingQueue<Integer>(1);
        full.add(1);
        Wait[] waits = {
                () -> Thread.sleep(30),
                () -> {
                    synchronized (monitor) {
                        monitor.wait(30);
                    }
                },
                () -> LockSupport.parkNanos(30_000_000),
                () -> LockSupport.parkNanos(monitor, 30_000_000),
                () -> LockSupport.parkUntil(System.currentTimeMillis() + 30),
                () -> LockSupport.parkUntil(monitor, System.currentTimeMillis() + 30),
                () -> assertNull(references.remove(30)),
                () -> assertNull(empty.poll(30, TimeUnit.MILLISECONDS)),
                () -> assertFalse(full.offer(2, 30, TimeUnit.MILLISECONDS))
        };
        for (int i = 0; i < waits.length; ++i) {
            installTimers(7);
            try {
                waits[i].run();
                assertTrue(elapsed() >= 30, "wait #" + i + " ended at " + elapsed());
                assertTrue(firings() >= 5, "wait #" + i);
            } finally {
                restoreTimers();
            }
        }
    }

    @Test
    public void deadlineArithmeticDoesNotWrap() {
        installTimers(7);
        try {
            assertEquals(Long.MAX_VALUE, EventQueue.deadlineAfter(Long.MAX_VALUE));
            assertEquals(1000, EventQueue.deadlineAfter(Long.MIN_VALUE));
            assertEquals(0, EventQueue.remainingMillis(Long.MIN_VALUE));
            assertEquals(0, EventQueue.remainingMillis(999));
            assertEquals(Long.MAX_VALUE - 1000, EventQueue.remainingMillis(Long.MAX_VALUE));
        } finally {
            restoreTimers();
        }
    }

    @Test
    public void fractionalMillisecondWaitsRoundUp() throws InterruptedException {
        Object monitor = new Object();
        Wait[] waits = {
                () -> Thread.sleep(0, 1),
                () -> {
                    synchronized (monitor) {
                        monitor.wait(0, 1);
                    }
                },
                () -> LockSupport.parkNanos(1)
        };
        for (Wait wait : waits) {
            installTimers(7);
            try {
                wait.run();
                assertTrue(elapsed() >= 1);
            } finally {
                restoreTimers();
            }
        }
    }

    @Test
    public void parkUntilPastDeadlineDoesNotOverflow() throws InterruptedException {
        Object blocker = new Object();
        Runnable[] parks = {
                () -> LockSupport.parkUntil(Long.MIN_VALUE),
                () -> LockSupport.parkUntil(blocker, Long.MIN_VALUE)
        };
        for (Runnable park : parks) {
            var returned = new boolean[1];
            var thread = new Thread(() -> {
                park.run();
                returned[0] = true;
            });
            installTimers(1_000_000_000);
            try {
                thread.start();
                Thread.sleep(1);
                Thread.sleep(1);
                assertTrue(returned[0]);
            } finally {
                thread.interrupt();
                restoreTimers();
            }
        }
    }

    @Test
    public void longTimeoutSurvivesHostDelayCap() throws InterruptedException {
        installTimers(1_000_000_000);
        try {
            long delay = Integer.MAX_VALUE + 100L;
            Thread.sleep(delay);
            assertTrue(elapsed() >= delay);
            assertEquals(Integer.MAX_VALUE, maximumDelay());
            assertTrue(firings() >= 3);
        } finally {
            restoreTimers();
        }
    }

    @Test
    public void interruptCancelsRearmedTimer() throws InterruptedException {
        var interrupted = new boolean[1];
        var returned = new boolean[1];
        var thread = new Thread(() -> {
            try {
                Thread.sleep(Long.MAX_VALUE);
                returned[0] = true;
            } catch (InterruptedException e) {
                interrupted[0] = true;
                assertFalse(Thread.currentThread().isInterrupted());
            }
        });
        installTimers(1_000_000_000);
        try {
            thread.start();
            Thread.sleep(1);
            Thread.sleep(1);
            assertTrue(thread.isAlive());
            assertFalse(returned[0]);
            thread.interrupt();
            thread.join();
            assertTrue(interrupted[0]);
            assertTrue(cancellations() > 0);
            Thread.sleep(10);
            assertFalse(returned[0]);
        } finally {
            restoreTimers();
        }
    }

    @Test
    public void unparkCancelsRearmedTimer() throws InterruptedException {
        var returned = new boolean[1];
        var thread = new Thread(() -> {
            LockSupport.parkNanos(Long.MAX_VALUE);
            returned[0] = true;
        });
        installTimers(1_000_000_000);
        try {
            thread.start();
            Thread.sleep(1);
            Thread.sleep(1);
            assertFalse(returned[0]);
            LockSupport.unpark(thread);
            thread.join();
            assertTrue(returned[0]);
            assertTrue(cancellations() > 0);
        } finally {
            restoreTimers();
        }
    }

    @Test
    public void notifyCancelsRearmedTimer() throws InterruptedException {
        Object monitor = new Object();
        var returned = new boolean[1];
        var thread = new Thread(() -> {
            synchronized (monitor) {
                try {
                    monitor.wait(Long.MAX_VALUE);
                    returned[0] = true;
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        });
        installTimers(1_000_000_000);
        try {
            thread.start();
            Thread.sleep(1);
            Thread.sleep(1);
            assertFalse(returned[0]);
            synchronized (monitor) {
                monitor.notify();
            }
            thread.join();
            assertTrue(returned[0]);
            assertTrue(cancellations() > 0);
        } finally {
            restoreTimers();
        }
    }

    @JSBody(params = "step", script = """
            const s = {
                timeout: globalThis.setTimeout, clear: globalThis.clearTimeout,
                now: Object.getOwnPropertyDescriptor(performance, 'now'), date: Date.now,
                getTime: Date.prototype.getTime,
                elapsed: 0, firings: 0, reads: 0, wallReads: 0, max: 0, cancelled: 0,
                pending: new Set()
            };
            globalThis.teavmTimerTest = s;
            Object.defineProperty(performance, 'now', { configurable: true, value: () => {
                ++s.reads;
                return 1000 + s.elapsed;
            }});
            Date.now = () => { ++s.wallReads; return 7000000; };
            Date.prototype.getTime = Date.now;
            globalThis.setTimeout = function(fn, delay) {
                delay = delay || 0;
                const args = Array.prototype.slice.call(arguments, 2);
                if (delay < 0 || delay > 0x7FFFFFFF) throw new Error('invalid host delay ' + delay);
                s.max = Math.max(s.max, delay);
                const id = s.timeout.call(globalThis, () => {
                    s.pending.delete(id);
                    if (delay > 0) {
                        s.elapsed += Math.min(delay, step);
                        ++s.firings;
                    }
                    fn.apply(null, args);
                }, 0);
                s.pending.add(id);
                return id;
            };
            globalThis.clearTimeout = id => {
                if (s.pending.delete(id)) ++s.cancelled;
                s.clear.call(globalThis, id);
            };
            """)
    private static native void installTimers(int step);

    @JSBody(script = """
            const s = globalThis.teavmTimerTest;
            s.pending.forEach(id => s.clear.call(globalThis, id));
            globalThis.setTimeout = s.timeout;
            globalThis.clearTimeout = s.clear;
            if (s.now) Object.defineProperty(performance, 'now', s.now);
            else delete performance.now;
            Date.now = s.date;
            Date.prototype.getTime = s.getTime;
            delete globalThis.teavmTimerTest;
            """)
    private static native void restoreTimers();

    @JSBody(script = "return globalThis.teavmTimerTest.elapsed;")
    private static native double elapsed();

    @JSBody(script = "return globalThis.teavmTimerTest.firings;")
    private static native int firings();

    @JSBody(script = "return globalThis.teavmTimerTest.reads;")
    private static native int clockReads();

    @JSBody(script = "return globalThis.teavmTimerTest.wallReads;")
    private static native int wallClockReads();

    @JSBody(script = "return globalThis.teavmTimerTest.max;")
    private static native int maximumDelay();

    @JSBody(script = "return globalThis.teavmTimerTest.cancelled;")
    private static native int cancellations();

    private interface Wait {
        void run() throws InterruptedException;
    }
}
