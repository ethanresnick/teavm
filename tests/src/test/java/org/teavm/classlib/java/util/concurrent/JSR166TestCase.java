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
 * TeaVM port of jsr166's JSR166TestCase, cut down for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/JSR166TestCase.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * Removed, because TeaVM has no support for them or they do not apply:
 * - the JUnit 3 TestCase base, TestSuite construction, method filters and profiling (TeaVM runs @Test methods);
 * - system-property knobs for delays and expensive tests (delays use the defaults);
 * - security policies and runWithPermissions (no SecurityManager);
 * - serialization helpers (no Java serialization);
 * - the ForkJoinPool leak check and thread dumps (no ForkJoinPool, no ThreadMXBean, Thread.enumerate needs
 *   ThreadGroup), CheckedRecursiveAction/Task, CheckedBarrier and await(Semaphore) (classes outside the port).
 * Changed: waitForThreadToEnterWaitState sleeps instead of reading Thread.getState(), and spin loops call
 * spinWait() (a 1 ms sleep) instead of Thread.yield(); see those methods. JUnit 3 assertions are kept as
 * methods of this class, with JUnit 3's argument order.
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ExplicitInitialization|LeftCurly|MethodParamPad|NeedBraces|NoWhitespaceAfter
// CHECKSTYLE.OFF: OperatorWrap|RedundantModifier|RightCurly|StaticVariableName|UnusedImports|WhitespaceAfter
// CHECKSTYLE.OFF: WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class JSR166TestCase {

    // TeaVM port: the defaults of the jsr166.expensiveTests and jsr166.testImplementationDetails properties.
    protected static final boolean expensiveTests = false;
    protected static final boolean testImplementationDetails = false;

    // TeaVM port: JUnit 3's assertions, with its argument order (message first).

    static class AssertionFailedError extends AssertionError {
        AssertionFailedError() {
        }

        AssertionFailedError(String message) {
            super(message);
        }
    }

    public static void fail() {
        throw new AssertionFailedError();
    }

    public static void fail(String message) {
        throw new AssertionFailedError(message);
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            fail();
        }
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    public static void assertFalse(boolean condition) {
        assertTrue(!condition);
    }

    public static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    public static void assertNull(Object o) {
        assertTrue("expected null: " + o, o == null);
    }

    public static void assertNull(String message, Object o) {
        assertTrue(message, o == null);
    }

    public static void assertNotNull(Object o) {
        assertTrue("expected not null", o != null);
    }

    public static void assertNotNull(String message, Object o) {
        assertTrue(message, o != null);
    }

    public static void assertSame(Object expected, Object actual) {
        assertTrue("expected same: " + expected + " but was: " + actual, expected == actual);
    }

    public static void assertSame(String message, Object expected, Object actual) {
        assertTrue(message, expected == actual);
    }

    public static void assertNotSame(Object expected, Object actual) {
        assertTrue("expected not same: " + actual, expected != actual);
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail((message != null ? message + " " : "") + "expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    public static void assertEquals(long expected, long actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, long expected, long actual) {
        if (expected != actual) {
            fail((message != null ? message + " " : "") + "expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    public static void assertEquals(int expected, int actual) {
        assertEquals(null, (long) expected, (long) actual);
    }

    public static void assertEquals(boolean expected, boolean actual) {
        assertEquals(null, (Object) expected, (Object) actual);
    }

    public static void assertEquals(double expected, double actual, double delta) {
        assertTrue("expected: <" + expected + "> but was: <" + actual + ">", Math.abs(expected - actual) <= delta);
    }

    // Delays for timing-dependent tests, in milliseconds.

    public static long SHORT_DELAY_MS;
    public static long SMALL_DELAY_MS;
    public static long MEDIUM_DELAY_MS;
    public static long LONG_DELAY_MS;

    /**
     * Returns the shortest timed delay. This can be scaled up for
     * slow machines using the jsr166.delay.factor system property,
     * or via jtreg's -timeoutFactor: flag.
     * http://openjdk.java.net/jtreg/command-help.html
     */
    protected long getShortDelay() {
        return 50;
    }

    /**
     * Sets delays as multiples of SHORT_DELAY.
     */
    protected void setDelays() {
        SHORT_DELAY_MS = getShortDelay();
        SMALL_DELAY_MS  = SHORT_DELAY_MS * 5;
        MEDIUM_DELAY_MS = SHORT_DELAY_MS * 10;
        LONG_DELAY_MS   = SHORT_DELAY_MS * 200;
    }

    /**
     * Returns a timeout in milliseconds to be used in tests that
     * verify that operations block or time out.
     */
    long timeoutMillis() {
        return SHORT_DELAY_MS / 4;
    }

    /**
     * Returns a new Date instance representing a time at least
     * delayMillis milliseconds in the future.
     */
    Date delayedDate(long delayMillis) {
        // Add 1 because currentTimeMillis is known to round into the past.
        return new Date(System.currentTimeMillis() + delayMillis + 1);
    }

    /**
     * The first exception encountered if any threadAssertXXX method fails.
     */
    private final AtomicReference<Throwable> threadFailure
        = new AtomicReference<Throwable>(null);

    /**
     * Records an exception so that it can be rethrown later in the test
     * harness thread, triggering a test case failure.  Only the first
     * failure is recorded; subsequent calls to this method from within
     * the same test have no effect.
     */
    public void threadRecordFailure(Throwable t) {
        System.err.println(t);
        threadFailure.compareAndSet(null, t);
    }

    @BeforeEach
    public void setUp() {
        setDelays();
    }

    void tearDownFail(String format, Object... args) {
        String msg = toString() + ": " + String.format(format, args);
        System.err.println(msg);
        throw new AssertionFailedError(msg);
    }

    /**
     * Extra checks that get done for all test cases.
     *
     * Triggers test case failure if any thread assertions have failed,
     * by rethrowing, in the test harness thread, any exception recorded
     * earlier by threadRecordFailure.
     *
     * Triggers test case failure if interrupt status is set in the main thread.
     */
    @AfterEach
    public void tearDown() throws Exception {
        Throwable t = threadFailure.getAndSet(null);
        if (t != null) {
            if (t instanceof Error)
                throw (Error) t;
            else if (t instanceof RuntimeException)
                throw (RuntimeException) t;
            else if (t instanceof Exception)
                throw (Exception) t;
            else {
                AssertionFailedError afe =
                    new AssertionFailedError(t.toString());
                afe.initCause(t);
                throw afe;
            }
        }

        if (Thread.interrupted())
            tearDownFail("interrupt status set in main thread");
    }

    /**
     * Just like fail(reason), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadFail(String reason) {
        try {
            fail(reason);
        } catch (AssertionFailedError t) {
            threadRecordFailure(t);
            throw t;
        }
    }

    /**
     * Just like assertTrue(b), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadAssertTrue(boolean b) {
        try {
            assertTrue(b);
        } catch (AssertionFailedError t) {
            threadRecordFailure(t);
            throw t;
        }
    }

    /**
     * Just like assertFalse(b), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadAssertFalse(boolean b) {
        try {
            assertFalse(b);
        } catch (AssertionFailedError t) {
            threadRecordFailure(t);
            throw t;
        }
    }

    /**
     * Just like assertNull(x), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadAssertNull(Object x) {
        try {
            assertNull(x);
        } catch (AssertionFailedError t) {
            threadRecordFailure(t);
            throw t;
        }
    }

    /**
     * Just like assertEquals(x, y), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadAssertEquals(long x, long y) {
        try {
            assertEquals(x, y);
        } catch (AssertionFailedError t) {
            threadRecordFailure(t);
            throw t;
        }
    }

    /**
     * Just like assertEquals(x, y), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadAssertEquals(Object x, Object y) {
        try {
            assertEquals(x, y);
        } catch (AssertionFailedError fail) {
            threadRecordFailure(fail);
            throw fail;
        } catch (Throwable fail) {
            threadUnexpectedException(fail);
        }
    }

    /**
     * Just like assertSame(x, y), but additionally recording (using
     * threadRecordFailure) any AssertionFailedError thrown, so that
     * the current testcase will fail.
     */
    public void threadAssertSame(Object x, Object y) {
        try {
            assertSame(x, y);
        } catch (AssertionFailedError fail) {
            threadRecordFailure(fail);
            throw fail;
        }
    }

    /**
     * Calls threadFail with message "should throw exception".
     */
    public void threadShouldThrow() {
        threadFail("should throw exception");
    }

    /**
     * Calls threadFail with message "should throw" + exceptionName.
     */
    public void threadShouldThrow(String exceptionName) {
        threadFail("should throw " + exceptionName);
    }

    /**
     * Records the given exception using {@link #threadRecordFailure},
     * then rethrows the exception, wrapping it in an
     * AssertionFailedError if necessary.
     */
    public void threadUnexpectedException(Throwable t) {
        threadRecordFailure(t);
        t.printStackTrace();
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else {
            AssertionFailedError afe =
                new AssertionFailedError("unexpected exception: " + t);
            afe.initCause(t);
            throw afe;
        }
    }

    /**
     * Delays, via Thread.sleep, for the given millisecond delay, but
     * if the sleep is shorter than specified, may re-sleep or yield
     * until time elapses.  Ensures that the given time, as measured
     * by System.nanoTime(), has elapsed.
     */
    static void delay(long millis) throws InterruptedException {
        long nanos = millis * (1000 * 1000);
        final long wakeupTime = System.nanoTime() + nanos;
        do {
            if (millis > 0L)
                Thread.sleep(millis);
            else // too short to sleep
                spinWait();
            nanos = wakeupTime - System.nanoTime();
            millis = nanos / (1000 * 1000);
        } while (nanos >= 0L);
    }

    /**
     * Allows use of try-with-resources with per-test thread pools.
     */
    class PoolCleaner implements AutoCloseable {
        private final ExecutorService pool;
        public PoolCleaner(ExecutorService pool) { this.pool = pool; }
        public void close() { joinPool(pool); }
    }

    /**
     * An extension of PoolCleaner that has an action to release the pool.
     */
    class PoolCleanerWithReleaser extends PoolCleaner {
        private final Runnable releaser;
        public PoolCleanerWithReleaser(ExecutorService pool, Runnable releaser) {
            super(pool);
            this.releaser = releaser;
        }
        public void close() {
            try {
                releaser.run();
            } finally {
                super.close();
            }
        }
    }

    PoolCleaner cleaner(ExecutorService pool) {
        return new PoolCleaner(pool);
    }

    PoolCleaner cleaner(ExecutorService pool, Runnable releaser) {
        return new PoolCleanerWithReleaser(pool, releaser);
    }

    PoolCleaner cleaner(ExecutorService pool, CountDownLatch latch) {
        return new PoolCleanerWithReleaser(pool, releaser(latch));
    }

    Runnable releaser(final CountDownLatch latch) {
        return new Runnable() { public void run() {
            do { latch.countDown(); }
            while (latch.getCount() > 0);
        }};
    }

    PoolCleaner cleaner(ExecutorService pool, AtomicBoolean flag) {
        return new PoolCleanerWithReleaser(pool, releaser(flag));
    }

    Runnable releaser(final AtomicBoolean flag) {
        return new Runnable() { public void run() { flag.set(true); }};
    }

    /**
     * Waits out termination of a thread pool or fails doing so.
     */
    void joinPool(ExecutorService pool) {
        try {
            pool.shutdown();
            if (!pool.awaitTermination(2 * LONG_DELAY_MS, MILLISECONDS)) {
                try {
                    threadFail("ExecutorService " + pool +
                               " did not terminate in a timely manner");
                } finally {
                    // last resort, for the benefit of subsequent tests
                    pool.shutdownNow();
                    pool.awaitTermination(MEDIUM_DELAY_MS, MILLISECONDS);
                }
            }
        } catch (InterruptedException fail) {
            threadFail("Unexpected InterruptedException");
        }
    }

    /** Like Runnable, but with the freedom to throw anything */
    interface Action { public void run() throws Throwable; }

    /**
     * Runs all the given actions in parallel, failing if any fail.
     * Useful for running multiple variants of tests that are
     * necessarily individually slow because they must block.
     */
    void testInParallel(Action ... actions) {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (PoolCleaner cleaner = cleaner(pool)) {
            ArrayList<Future<?>> futures = new ArrayList<>(actions.length);
            for (final Action action : actions)
                futures.add(pool.submit(new CheckedRunnable() {
                    public void realRun() throws Throwable { action.run();}}));
            for (Future<?> future : futures)
                try {
                    assertNull(future.get(LONG_DELAY_MS, MILLISECONDS));
                } catch (ExecutionException ex) {
                    threadUnexpectedException(ex.getCause());
                } catch (Exception ex) {
                    threadUnexpectedException(ex);
                }
        }
    }

    /**
     * Checks that thread does not terminate within the default
     * millisecond delay of {@code timeoutMillis()}.
     */
    void assertThreadStaysAlive(Thread thread) {
        assertThreadStaysAlive(thread, timeoutMillis());
    }

    /**
     * Checks that thread does not terminate within the given millisecond delay.
     */
    void assertThreadStaysAlive(Thread thread, long millis) {
        try {
            // No need to optimize the failing case via Thread.join.
            delay(millis);
            assertTrue(thread.isAlive());
        } catch (InterruptedException fail) {
            threadFail("Unexpected InterruptedException");
        }
    }

    /**
     * Checks that the threads do not terminate within the default
     * millisecond delay of {@code timeoutMillis()}.
     */
    void assertThreadsStayAlive(Thread... threads) {
        assertThreadsStayAlive(timeoutMillis(), threads);
    }

    /**
     * Checks that the threads do not terminate within the given millisecond delay.
     */
    void assertThreadsStayAlive(long millis, Thread... threads) {
        try {
            // No need to optimize the failing case via Thread.join.
            delay(millis);
            for (Thread thread : threads)
                assertTrue(thread.isAlive());
        } catch (InterruptedException fail) {
            threadFail("Unexpected InterruptedException");
        }
    }

    /**
     * Checks that future.get times out, with the default timeout of
     * {@code timeoutMillis()}.
     */
    void assertFutureTimesOut(Future future) {
        assertFutureTimesOut(future, timeoutMillis());
    }

    /**
     * Checks that future.get times out, with the given millisecond timeout.
     */
    void assertFutureTimesOut(Future future, long timeoutMillis) {
        long startTime = System.nanoTime();
        try {
            future.get(timeoutMillis, MILLISECONDS);
            shouldThrow();
        } catch (TimeoutException success) {
        } catch (Exception fail) {
            threadUnexpectedException(fail);
        } finally { future.cancel(true); }
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
    }

    /**
     * Fails with message "should throw exception".
     */
    public void shouldThrow() {
        fail("Should throw exception");
    }

    /**
     * Fails with message "should throw " + exceptionName.
     */
    public void shouldThrow(String exceptionName) {
        fail("Should throw " + exceptionName);
    }

    /**
     * The number of elements to place in collections, arrays, etc.
     */
    public static final int SIZE = 20;

    // Some convenient Integer constants

    public static final Integer zero  = Integer.valueOf(0);
    public static final Integer one   = Integer.valueOf(1);
    public static final Integer two   = Integer.valueOf(2);
    public static final Integer three = Integer.valueOf(3);
    public static final Integer four  = Integer.valueOf(4);
    public static final Integer five  = Integer.valueOf(5);
    public static final Integer six   = Integer.valueOf(6);
    public static final Integer seven = Integer.valueOf(7);
    public static final Integer eight = Integer.valueOf(8);
    public static final Integer nine  = Integer.valueOf(9);
    public static final Integer m1  = Integer.valueOf(-1);
    public static final Integer m2  = Integer.valueOf(-2);
    public static final Integer m3  = Integer.valueOf(-3);
    public static final Integer m4  = Integer.valueOf(-4);
    public static final Integer m5  = Integer.valueOf(-5);
    public static final Integer m6  = Integer.valueOf(-6);
    public static final Integer m10 = Integer.valueOf(-10);

    /**
     * Sleeps until the given time has elapsed.
     * Throws AssertionFailedError if interrupted.
     */
    void sleep(long millis) {
        try {
            delay(millis);
        } catch (InterruptedException fail) {
            AssertionFailedError afe =
                new AssertionFailedError("Unexpected InterruptedException");
            afe.initCause(fail);
            throw afe;
        }
    }

    /**
     * TeaVM port: TeaVM's Thread.getState() reports only NEW, RUNNABLE and TERMINATED, so a thread that blocks
     * is never seen in a wait state. These waits give the thread time to block, then check that it is alive.
     */
    void waitForThreadToEnterWaitState(Thread thread, long timeoutMillis) {
        sleep(Math.min(timeoutMillis, SHORT_DELAY_MS));
        if (!thread.isAlive())
            fail("Unexpected thread termination");
    }

    void waitForThreadToEnterWaitState(
        Thread thread, long timeoutMillis, Callable<Boolean> waitingForGodot) {
        long startTime = System.nanoTime();
        for (;;) {
            if (!thread.isAlive())
                fail("Unexpected thread termination");
            try {
                if (waitingForGodot.call())
                    return;
            } catch (Throwable fail) { threadUnexpectedException(fail); }
            if (millisElapsedSince(startTime) > timeoutMillis)
                fail("timed out waiting for thread to enter wait state");
            spinWait();
        }
    }

    void waitForThreadToEnterWaitState(Thread thread) {
        waitForThreadToEnterWaitState(thread, LONG_DELAY_MS);
    }

    void waitForThreadToEnterWaitState(
        Thread thread, Callable<Boolean> waitingForGodot) {
        waitForThreadToEnterWaitState(thread, LONG_DELAY_MS, waitingForGodot);
    }

    /**
     * TeaVM port: replaces Thread.yield() in spin loops that wait for another thread. TeaVM's Thread.yield()
     * switches threads only after the current one has run for 100 ms; a short sleep lets the other thread run
     * at once.
     */
    static void spinWait() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the number of milliseconds since time given by
     * startNanoTime, which must have been previously returned from a
     * call to {@link System#nanoTime()}.
     */
    static long millisElapsedSince(long startNanoTime) {
        return NANOSECONDS.toMillis(System.nanoTime() - startNanoTime);
    }

//     void assertTerminatesPromptly(long timeoutMillis, Runnable r) {
//         long startTime = System.nanoTime();
//         try {
//             r.run();
//         } catch (Throwable fail) { threadUnexpectedException(fail); }
//         if (millisElapsedSince(startTime) > timeoutMillis/2)
//             throw new AssertionFailedError("did not return promptly");
//     }

//     void assertTerminatesPromptly(Runnable r) {
//         assertTerminatesPromptly(LONG_DELAY_MS/2, r);
//     }

    /**
     * Checks that timed f.get() returns the expected value, and does not
     * wait for the timeout to elapse before returning.
     */
    <T> void checkTimedGet(Future<T> f, T expectedValue, long timeoutMillis) {
        long startTime = System.nanoTime();
        try {
            assertEquals(expectedValue, f.get(timeoutMillis, MILLISECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        if (millisElapsedSince(startTime) > timeoutMillis/2)
            throw new AssertionFailedError("timed get did not return promptly");
    }

    <T> void checkTimedGet(Future<T> f, T expectedValue) {
        checkTimedGet(f, expectedValue, LONG_DELAY_MS);
    }

    /**
     * Returns a new started daemon Thread running the given runnable.
     */
    Thread newStartedThread(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Waits for the specified time (in milliseconds) for the thread
     * to terminate (using {@link Thread#join(long)}), else interrupts
     * the thread (in the hope that it may terminate later) and fails.
     */
    void awaitTermination(Thread t, long timeoutMillis) {
        try {
            t.join(timeoutMillis);
        } catch (InterruptedException fail) {
            threadUnexpectedException(fail);
        } finally {
            if (t.isAlive()) {
                t.interrupt();
                threadFail("timed out waiting for thread to terminate");
            }
        }
    }

    /**
     * Waits for LONG_DELAY_MS milliseconds for the thread to
     * terminate (using {@link Thread#join(long)}), else interrupts
     * the thread (in the hope that it may terminate later) and fails.
     */
    void awaitTermination(Thread t) {
        awaitTermination(t, LONG_DELAY_MS);
    }

    // Some convenient Runnable classes

    public abstract class CheckedRunnable implements Runnable {
        protected abstract void realRun() throws Throwable;

        public final void run() {
            try {
                realRun();
            } catch (Throwable fail) {
                threadUnexpectedException(fail);
            }
        }
    }

    public abstract class RunnableShouldThrow implements Runnable {
        protected abstract void realRun() throws Throwable;

        final Class<?> exceptionClass;

        <T extends Throwable> RunnableShouldThrow(Class<T> exceptionClass) {
            this.exceptionClass = exceptionClass;
        }

        public final void run() {
            try {
                realRun();
                threadShouldThrow(exceptionClass.getSimpleName());
            } catch (Throwable t) {
                if (! exceptionClass.isInstance(t))
                    threadUnexpectedException(t);
            }
        }
    }

    public abstract class ThreadShouldThrow extends Thread {
        protected abstract void realRun() throws Throwable;

        final Class<?> exceptionClass;

        <T extends Throwable> ThreadShouldThrow(Class<T> exceptionClass) {
            this.exceptionClass = exceptionClass;
        }

        public final void run() {
            try {
                realRun();
                threadShouldThrow(exceptionClass.getSimpleName());
            } catch (Throwable t) {
                if (! exceptionClass.isInstance(t))
                    threadUnexpectedException(t);
            }
        }
    }

    public abstract class CheckedInterruptedRunnable implements Runnable {
        protected abstract void realRun() throws Throwable;

        public final void run() {
            try {
                realRun();
                threadShouldThrow("InterruptedException");
            } catch (InterruptedException success) {
                threadAssertFalse(Thread.interrupted());
            } catch (Throwable fail) {
                threadUnexpectedException(fail);
            }
        }
    }

    public abstract class CheckedCallable<T> implements Callable<T> {
        protected abstract T realCall() throws Throwable;

        public final T call() {
            try {
                return realCall();
            } catch (Throwable fail) {
                threadUnexpectedException(fail);
                return null;
            }
        }
    }

    public abstract class CheckedInterruptedCallable<T>
        implements Callable<T> {
        protected abstract T realCall() throws Throwable;

        public final T call() {
            try {
                T result = realCall();
                threadShouldThrow("InterruptedException");
                return result;
            } catch (InterruptedException success) {
                threadAssertFalse(Thread.interrupted());
            } catch (Throwable fail) {
                threadUnexpectedException(fail);
            }
            return null;
        }
    }

    public static class NoOpRunnable implements Runnable {
        public void run() {}
    }

    public static class NoOpCallable implements Callable {
        public Object call() { return Boolean.TRUE; }
    }

    public static final String TEST_STRING = "a test string";

    public static class StringTask implements Callable<String> {
        final String value;
        public StringTask() { this(TEST_STRING); }
        public StringTask(String value) { this.value = value; }
        public String call() { return value; }
    }

    public Callable<String> latchAwaitingStringTask(final CountDownLatch latch) {
        return new CheckedCallable<String>() {
            protected String realCall() {
                try {
                    latch.await();
                } catch (InterruptedException quittingTime) {}
                return TEST_STRING;
            }};
    }

    public Runnable countDowner(final CountDownLatch latch) {
        return new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                latch.countDown();
            }};
    }

    class LatchAwaiter extends CheckedRunnable {
        static final int NEW = 0;
        static final int RUNNING = 1;
        static final int DONE = 2;
        final CountDownLatch latch;
        int state = NEW;
        LatchAwaiter(CountDownLatch latch) { this.latch = latch; }
        public void realRun() throws InterruptedException {
            state = 1;
            await(latch);
            state = 2;
        }
    }

    public LatchAwaiter awaiter(CountDownLatch latch) {
        return new LatchAwaiter(latch);
    }

    public void await(CountDownLatch latch, long timeoutMillis) {
        try {
            if (!latch.await(timeoutMillis, MILLISECONDS))
                fail("timed out waiting for CountDownLatch for "
                     + (timeoutMillis/1000) + " sec");
        } catch (Throwable fail) {
            threadUnexpectedException(fail);
        }
    }

    public void await(CountDownLatch latch) {
        await(latch, LONG_DELAY_MS);
    }

//     /**
//      * Spin-waits up to LONG_DELAY_MS until flag becomes true.
//      */
//     public void await(AtomicBoolean flag) {
//         await(flag, LONG_DELAY_MS);
//     }

//     /**
//      * Spin-waits up to the specified timeout until flag becomes true.
//      */
//     public void await(AtomicBoolean flag, long timeoutMillis) {
//         long startTime = System.nanoTime();
//         while (!flag.get()) {
//             if (millisElapsedSince(startTime) > timeoutMillis)
//                 throw new AssertionFailedError("timed out");
//             Thread.yield();
//         }
//     }

    public static class NPETask implements Callable<String> {
        public String call() { throw new NullPointerException(); }
    }

    public static class CallableOne implements Callable<Integer> {
        public Integer call() { return one; }
    }

    public class ShortRunnable extends CheckedRunnable {
        protected void realRun() throws Throwable {
            delay(SHORT_DELAY_MS);
        }
    }

    public class ShortInterruptedRunnable extends CheckedInterruptedRunnable {
        protected void realRun() throws InterruptedException {
            delay(SHORT_DELAY_MS);
        }
    }

    public class SmallRunnable extends CheckedRunnable {
        protected void realRun() throws Throwable {
            delay(SMALL_DELAY_MS);
        }
    }

    public class SmallPossiblyInterruptedRunnable extends CheckedRunnable {
        protected void realRun() {
            try {
                delay(SMALL_DELAY_MS);
            } catch (InterruptedException ok) {}
        }
    }

    public class SmallCallable extends CheckedCallable {
        protected Object realCall() throws InterruptedException {
            delay(SMALL_DELAY_MS);
            return Boolean.TRUE;
        }
    }

    public class MediumRunnable extends CheckedRunnable {
        protected void realRun() throws Throwable {
            delay(MEDIUM_DELAY_MS);
        }
    }

    public class MediumInterruptedRunnable extends CheckedInterruptedRunnable {
        protected void realRun() throws InterruptedException {
            delay(MEDIUM_DELAY_MS);
        }
    }

    public Runnable possiblyInterruptedRunnable(final long timeoutMillis) {
        return new CheckedRunnable() {
            protected void realRun() {
                try {
                    delay(timeoutMillis);
                } catch (InterruptedException ok) {}
            }};
    }

    public class MediumPossiblyInterruptedRunnable extends CheckedRunnable {
        protected void realRun() {
            try {
                delay(MEDIUM_DELAY_MS);
            } catch (InterruptedException ok) {}
        }
    }

    public class LongPossiblyInterruptedRunnable extends CheckedRunnable {
        protected void realRun() {
            try {
                delay(LONG_DELAY_MS);
            } catch (InterruptedException ok) {}
        }
    }

    /**
     * For use as ThreadFactory in constructors
     */
    public static class SimpleThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            return new Thread(r);
        }
    }

    public interface TrackedRunnable extends Runnable {
        boolean isDone();
    }

    public static TrackedRunnable trackedRunnable(final long timeoutMillis) {
        return new TrackedRunnable() {
                private volatile boolean done = false;
                public boolean isDone() { return done; }
                public void run() {
                    try {
                        delay(timeoutMillis);
                        done = true;
                    } catch (InterruptedException ok) {}
                }
            };
    }

    public static class TrackedShortRunnable implements Runnable {
        public volatile boolean done = false;
        public void run() {
            try {
                delay(SHORT_DELAY_MS);
                done = true;
            } catch (InterruptedException ok) {}
        }
    }

    public static class TrackedSmallRunnable implements Runnable {
        public volatile boolean done = false;
        public void run() {
            try {
                delay(SMALL_DELAY_MS);
                done = true;
            } catch (InterruptedException ok) {}
        }
    }

    public static class TrackedMediumRunnable implements Runnable {
        public volatile boolean done = false;
        public void run() {
            try {
                delay(MEDIUM_DELAY_MS);
                done = true;
            } catch (InterruptedException ok) {}
        }
    }

    public static class TrackedLongRunnable implements Runnable {
        public volatile boolean done = false;
        public void run() {
            try {
                delay(LONG_DELAY_MS);
                done = true;
            } catch (InterruptedException ok) {}
        }
    }

    public static class TrackedNoOpRunnable implements Runnable {
        public volatile boolean done = false;
        public void run() {
            done = true;
        }
    }

    public static class TrackedCallable implements Callable {
        public volatile boolean done = false;
        public Object call() {
            try {
                delay(SMALL_DELAY_MS);
                done = true;
            } catch (InterruptedException ok) {}
            return Boolean.TRUE;
        }
    }

    /**
     * For use as RejectedExecutionHandler in constructors
     */
    public static class NoOpREHandler implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r,
                                      ThreadPoolExecutor executor) {}
    }

    void checkEmpty(BlockingQueue q) {
        try {
            assertTrue(q.isEmpty());
            assertEquals(0, q.size());
            assertNull(q.peek());
            assertNull(q.poll());
            assertNull(q.poll(0, MILLISECONDS));
            assertEquals(q.toString(), "[]");
            assertTrue(Arrays.equals(q.toArray(), new Object[0]));
            assertFalse(q.iterator().hasNext());
            try {
                q.element();
                shouldThrow();
            } catch (NoSuchElementException success) {}
            try {
                q.iterator().next();
                shouldThrow();
            } catch (NoSuchElementException success) {}
            try {
                q.remove();
                shouldThrow();
            } catch (NoSuchElementException success) {}
        } catch (InterruptedException fail) { threadUnexpectedException(fail); }
    }

    @SuppressWarnings("unchecked")
    public void assertThrows(Class<? extends Throwable> expectedExceptionClass,
                             Runnable... throwingActions) {
        for (Runnable throwingAction : throwingActions) {
            boolean threw = false;
            try { throwingAction.run(); }
            catch (Throwable t) {
                threw = true;
                if (!expectedExceptionClass.isInstance(t)) {
                    AssertionFailedError afe =
                        new AssertionFailedError
                        ("Expected " + expectedExceptionClass.getName() +
                         ", got " + t.getClass().getName());
                    afe.initCause(t);
                    threadUnexpectedException(afe);
                }
            }
            if (!threw)
                shouldThrow(expectedExceptionClass.getName());
        }
    }

    public void assertIteratorExhausted(Iterator<?> it) {
        try {
            it.next();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        assertFalse(it.hasNext());
    }
}
