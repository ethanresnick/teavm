/*
 *  Copyright 2015 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ThreadTest {
    @Test
    public void sleeps() throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(100);
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration >= 100, "Thread.sleep did not wait enough: " + duration);
    }

    @Test
    public void sleepInterrupted() {
        long start = System.currentTimeMillis();
        var mainThread = Thread.currentThread();
        new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ok
            }
            mainThread.interrupt();
        }).start();
        try {
            Thread.sleep(5000);
            fail("Exception expected");
        } catch (InterruptedException e) {
            long end = System.currentTimeMillis();
            assertEquals(Thread.currentThread(), mainThread);
            assertFalse(mainThread.isInterrupted());
            assertTrue(end - start < 5000, "Wait time " + (end - start));
        }
    }

    @Test
    public void sleepAfterInterruptedSleep() throws InterruptedException {
        var mainThread = Thread.currentThread();
        new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ok
            }
            mainThread.interrupt();
        }).start();
        try {
            Thread.sleep(5000);
            fail("Exception expected");
        } catch (InterruptedException e) {
            // expected
        }
        // A later suspension on the same thread must not throw the earlier exception again.
        long start = System.currentTimeMillis();
        Thread.sleep(20);
        assertTrue(System.currentTimeMillis() - start >= 20);
    }

    @Test
    public void catchesAsyncException() {
        try {
            throwException();
            fail("Exception should have been thrown");
        } catch (IllegalStateException e) {
            // all is ok
        }
    }


    private void throwException() {
        Thread.yield();
        throw new IllegalStateException();
    }

    @Test
    public void asyncVirtualCallsSupported() {
        List<A> alist = new ArrayList<>();
        alist.add(new A() {
            @Override int foo() {
                return 3;
            }
        });
        alist.add(new A() {
            @Override int foo() {
                Thread.yield();
                return 5;
            }
        });
        int sum = 0;
        for (A a : alist) {
            sum += a.foo();
        }
        assertEquals(8, sum);
    }
    
    @Test
    public void join() throws InterruptedException {
        var runnable = new Runnable() {
            boolean finished;
            
            @Override
            public void run() {
                try {
                    Thread.sleep(750);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                finished = true;
            }
        };
        var other = new Thread(runnable);
        other.start();
        Thread.sleep(150);
        assertFalse(runnable.finished);
        other.join();
        assertTrue(runnable.finished);
    }

    @Test
    public void joinAlreadyFinished() throws InterruptedException {
        var runnable = new Runnable() {
            boolean finished;

            @Override
            public void run() {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                finished = true;
            }
        };
        var other = new Thread(runnable);
        other.start();
        Thread.sleep(750);
        assertTrue(runnable.finished);
        other.join();
        assertTrue(runnable.finished);
    }

    abstract class A {
        abstract int foo();
    }

    // isAlive(): "A thread is alive if it has been started and has not yet died." So the started thread must not be
    // able to end before isAlive() is read: on the JVM it may run, and finish, before start() returns.
    @Test
    public void isAliveFollowsLifecycle() throws InterruptedException {
        var insideAlive = new boolean[1];
        var running = new AtomicBoolean();
        var release = new AtomicBoolean();
        var thread = new Thread(() -> {
            insideAlive[0] = Thread.currentThread().isAlive();
            running.set(true);
            // onSpinWait() lets the main thread run on TeaVM.
            while (!release.get()) {
                Thread.onSpinWait();
            }
        });
        assertFalse(thread.isAlive());
        thread.start();
        // Started, but on TeaVM it has not run yet: it runs only when this thread suspends. On the JVM it may have
        // run already, but it cannot end before release is set.
        assertTrue(thread.isAlive());
        while (!running.get()) {
            Thread.onSpinWait();
        }
        // Now it has run on every platform, and it is still running.
        assertTrue(thread.isAlive());
        release.set(true);
        thread.join();
        assertTrue(insideAlive[0]);
        assertFalse(thread.isAlive());
        assertTrue(Thread.currentThread().isAlive());

        // A thread that ends with an exception has died too.
        var failing = new Thread(() -> {
            throw new IllegalStateException();
        });
        failing.setUncaughtExceptionHandler((t, e) -> { });
        failing.start();
        failing.join();
        assertFalse(failing.isAlive());
    }

    @Test
    public void startTwiceThrows() throws InterruptedException {
        var thread = new Thread(() -> { });
        thread.start();
        thread.join();
        try {
            thread.start();
            fail("IllegalThreadStateException expected");
        } catch (IllegalThreadStateException e) {
            // ok
        }
    }

    // sleep(long, int): "IllegalArgumentException - if the value of millis is negative, or the value of nanos is
    // not in the range 0-999999".
    @Test
    public void sleepOverloadsAndSpinWait() throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(20, 500);
        Thread.sleep(Duration.ofMillis(20));
        Thread.onSpinWait();
        assertTrue(System.currentTimeMillis() - start >= 40);

        assertThrows(IllegalArgumentException.class, () -> Thread.sleep(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> Thread.sleep(0, -1));
        assertThrows(IllegalArgumentException.class, () -> Thread.sleep(0, 1_000_000));
        Thread.sleep(0, 999_999);
        Thread.sleep(Duration.ZERO);
        Thread.sleep(Duration.ofNanos(-1));
        Thread.sleep(Duration.ofSeconds(Long.MIN_VALUE));

        // onSpinWait() lets another thread run, so a spin loop on a flag that thread sets ends.
        var flag = new boolean[1];
        new Thread(() -> flag[0] = true).start();
        while (!flag[0]) {
            Thread.onSpinWait();
        }
    }

    // A sleep too long for a host timer, or for a deadline in a long, must still wait until interrupted.
    @Test
    public void longSleepsWaitUntilInterrupted() throws InterruptedException {
        var sleeps = new ArrayList<Runnable>();
        sleeps.add(() -> sleepUnchecked(Long.MAX_VALUE));
        sleeps.add(() -> sleepUnchecked(Integer.MAX_VALUE + 1L));
        sleeps.add(() -> sleepUnchecked(Duration.ofSeconds(Long.MAX_VALUE, 999_999_999)));
        sleeps.add(() -> sleepUnchecked(Duration.ofDays(30)));
        var outcomes = new String[sleeps.size()];
        var threads = new ArrayList<Thread>();
        for (var i = 0; i < sleeps.size(); ++i) {
            var index = i;
            var thread = new Thread(() -> {
                try {
                    sleeps.get(index).run();
                    outcomes[index] = "returned";
                } catch (RuntimeException e) {
                    outcomes[index] = e.getCause() instanceof InterruptedException ? "interrupted" : e.toString();
                }
            });
            threads.add(thread);
            thread.start();
        }
        Thread.sleep(200);
        for (var i = 0; i < threads.size(); ++i) {
            assertTrue(threads.get(i).isAlive(), "sleep #" + i + " ended early: " + outcomes[i]);
            threads.get(i).interrupt();
        }
        for (var thread : threads) {
            thread.join();
        }
        for (var i = 0; i < outcomes.length; ++i) {
            assertEquals("interrupted", outcomes[i], "sleep #" + i);
        }
    }

    // sleep: "InterruptedException - if any thread has interrupted the current thread. The interrupted status of
    // the current thread is cleared when this exception is thrown."
    @Test
    public void sleepThrowsForEarlierInterrupt() {
        var current = Thread.currentThread();
        var sleeps = new ArrayList<Runnable>();
        sleeps.add(() -> sleepUnchecked(5000));
        sleeps.add(() -> sleepUnchecked(Duration.ofSeconds(5)));
        for (var i = 0; i < sleeps.size(); ++i) {
            current.interrupt();
            long start = System.currentTimeMillis();
            try {
                sleeps.get(i).run();
                fail("sleep #" + i + " did not throw");
            } catch (RuntimeException e) {
                assertTrue(e.getCause() instanceof InterruptedException, "sleep #" + i + ": " + e);
            }
            assertTrue(System.currentTimeMillis() - start < 2500, "sleep #" + i + " waited");
            assertFalse(current.isInterrupted(), "sleep #" + i + " left the interrupt status set");
        }
    }

    // join: "InterruptedException - if any thread has interrupted the current thread. The interrupted status of
    // the current thread is cleared when this exception is thrown."
    @Test
    public void joinThrowsForEarlierInterrupt() throws InterruptedException {
        var current = Thread.currentThread();
        var other = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                // ends the thread
            }
        });
        other.start();
        var names = List.of("join()", "join(5000)", "join(5000, 1)");
        var joins = List.<Action>of(other::join, () -> other.join(5000), () -> other.join(5000, 1));
        for (var i = 0; i < joins.size(); ++i) {
            current.interrupt();
            long start = System.currentTimeMillis();
            var watchdog = interruptLater(current);
            var threw = false;
            try {
                joins.get(i).run();
            } catch (InterruptedException e) {
                threw = true;
            } catch (Exception e) {
                fail(names.get(i) + ": " + e);
            }
            watchdog.interrupt();
            assertTrue(threw, names.get(i) + " did not throw");
            assertTrue(System.currentTimeMillis() - start < 2500, names.get(i) + " waited");
            assertFalse(current.isInterrupted(), names.get(i) + " left the interrupt status set");
        }
        other.interrupt();
        other.join();
    }

    private static void sleepUnchecked(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sleepUnchecked(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // join(long millis, int nanos): "IllegalArgumentException - if the value of millis is negative, or the value
    // of nanos is not in the range 0-999999". join(long millis): "If the thread has not been started then this
    // method returns immediately, without waiting."
    @Test
    public void joinEdgeCases() throws InterruptedException {
        var notStarted = new Thread(() -> { });
        long start = System.currentTimeMillis();
        notStarted.join(1000);
        assertTrue(System.currentTimeMillis() - start < 500, "join() waited for a thread that was not started");
        notStarted.join();
        notStarted.join(0, 1);

        for (var thread : List.of(notStarted, Thread.currentThread())) {
            assertThrows(IllegalArgumentException.class, () -> thread.join(-1));
            assertThrows(IllegalArgumentException.class, () -> thread.join(0, -1));
            assertThrows(IllegalArgumentException.class, () -> thread.join(0, 1_000_000));
            assertThrows(IllegalArgumentException.class, () -> thread.join(Long.MIN_VALUE, 0));
        }

        // A timed join on a thread that outlives the timeout waits for the timeout, then returns.
        var longRunning = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                // ok
            }
        });
        longRunning.start();
        start = System.currentTimeMillis();
        longRunning.join(100);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 100 && elapsed < 5000, "join(100) took " + elapsed);
        assertTrue(longRunning.isAlive());
        start = System.currentTimeMillis();
        longRunning.join(50, 500_000);
        elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 50 && elapsed < 5000, "join(50, 500000) took " + elapsed);
        assertTrue(longRunning.isAlive());
        longRunning.interrupt();
        longRunning.join();
        assertFalse(longRunning.isAlive());
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void assertThrows(Class<? extends Throwable> type, Action action) {
        try {
            action.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return;
            }
            fail("Expected " + type.getName() + ", got " + e);
        }
        fail("Expected " + type.getName());
    }

    @Test
    public void joinWithLongMaxValueWaits() throws InterruptedException {
        var done = new boolean[1];
        var thread = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ignore
            }
            done[0] = true;
        });
        thread.start();
        thread.join(Long.MAX_VALUE);
        assertTrue(done[0]);
        assertFalse(thread.isAlive());
    }

    // getContextClassLoader(): "The context ClassLoader is provided by the creator of the thread". It may be null.
    @Test
    public void contextClassLoaderIsKeptAndInherited() throws InterruptedException {
        var current = Thread.currentThread();
        var original = current.getContextClassLoader();
        var loader = new ClassLoader() { };
        try {
            var createdBefore = new Thread(() -> { });
            current.setContextClassLoader(loader);
            assertSame(loader, current.getContextClassLoader());
            assertSame(original, createdBefore.getContextClassLoader());
            var seen = new ClassLoader[2];
            var childLoader = new ClassLoader() { };
            var thread = new Thread(() -> {
                seen[0] = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(childLoader);
                seen[1] = Thread.currentThread().getContextClassLoader();
            });
            thread.start();
            thread.join();
            assertSame(loader, seen[0]);
            assertSame(childLoader, seen[1]);
            assertSame(loader, current.getContextClassLoader());

            current.setContextClassLoader(null);
            assertNull(current.getContextClassLoader());
            var inheritsNull = new ClassLoader[] { loader };
            thread = new Thread(() -> inheritsNull[0] = Thread.currentThread().getContextClassLoader());
            thread.start();
            thread.join();
            assertNull(inheritsNull[0]);
        } finally {
            current.setContextClassLoader(original);
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

    @Test
    public void unnamedThreadsHaveDistinctStableNames() {
        var first = new Thread();
        var second = new Thread();
        assertTrue(first.getName().startsWith("Thread-"));
        assertTrue(second.getName().startsWith("Thread-"));
        assertFalse(first.getName().equals(second.getName()));
        assertEquals(first.getName(), first.getName());
        first.setName("renamed");
        assertEquals("renamed", first.getName());
        assertThrows(NullPointerException.class, () -> first.setName(null));
        assertThrows(NullPointerException.class, () -> new Thread((String) null));
    }

    @Test
    public void liveThreadsAppearAtStartAndDisappearAtExit() throws InterruptedException {
        var release = new boolean[1];
        var worker = new Thread(() -> {
            try {
                while (!release[0]) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        assertFalse(Thread.getAllStackTraces().containsKey(worker));
        worker.start();
        try {
            assertTrue(Thread.getAllStackTraces().containsKey(worker));
            assertTrue(Thread.getAllStackTraces().containsKey(Thread.currentThread()));
            var threads = new Thread[Thread.activeCount() + 10];
            int count = Thread.enumerate(threads);
            boolean found = false;
            for (int i = 0; i < count; ++i) {
                found |= threads[i] == worker;
            }
            assertTrue(found);
            assertEquals(0, Thread.enumerate(new Thread[0]));
            assertThrows(NullPointerException.class, () -> Thread.enumerate(null));
            assertThrows(IllegalThreadStateException.class, worker::start);
        } finally {
            release[0] = true;
            worker.join();
        }
        assertFalse(Thread.getAllStackTraces().containsKey(worker));
        assertNull(worker.getThreadGroup());
    }

    @Test
    public void secondInterruptReachesANewWait() throws InterruptedException {
        var interrupted = new int[1];
        var waiting = new int[1];
        var worker = new Thread(() -> {
            for (int i = 0; i < 2; ++i) {
                waiting[0] = i + 1;
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    ++interrupted[0];
                }
            }
        });
        worker.start();
        for (int i = 1; i <= 2; ++i) {
            while (waiting[0] < i) {
                Thread.sleep(1);
            }
            Thread.sleep(1);
            worker.interrupt();
        }
        worker.join(2000);
        assertEquals(2, interrupted[0]);
        assertFalse(worker.isAlive());
    }

    @Test
    @SkipJVM // finishedLock is TeaVM's internal join monitor.
    public void missingFinishedLockDoesNotPreventThreadCleanup() throws Exception {
        var ran = new boolean[1];
        var worker = new Thread(() -> ran[0] = true);
        var field = Thread.class.getDeclaredField("finishedLock");
        field.setAccessible(true);
        field.set(worker, null);
        worker.start();
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (worker.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(ran[0]);
        assertFalse(worker.isAlive());
        assertFalse(Thread.getAllStackTraces().containsKey(worker));
    }

    // Thread.setDaemon: "IllegalThreadStateException - if this thread is alive".
    @Test
    public void liveThreadDaemonStatusCannotChange() throws InterruptedException {
        for (boolean daemon : new boolean[] { false, true }) {
            var worker = new Thread(() -> {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // Release the live thread after both invalid setters have been checked.
                }
            });
            worker.setDaemon(daemon);
            worker.start();
            try {
                assertThrows(IllegalThreadStateException.class, () -> worker.setDaemon(false));
                assertThrows(IllegalThreadStateException.class, () -> worker.setDaemon(true));
                assertEquals(daemon, worker.isDaemon());
            } finally {
                worker.interrupt();
                worker.join();
            }
        }
    }

}
