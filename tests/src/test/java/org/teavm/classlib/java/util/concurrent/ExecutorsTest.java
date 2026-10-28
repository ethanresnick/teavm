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
 * TeaVM port of jsr166's ExecutorsTest for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/ExecutorsTest.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * JUnit 3 test methods have @Test; the base class JSR166TestCase is the cut-down TeaVM port.
 * Removed:
 * - testPrivilegedThreadFactory: needs a SecurityManager and permissions (runWithPermissions), which TeaVM does not
 *    have
 * - haveCCLPermissions: needs a SecurityManager
 * - checkCCL: needs a SecurityManager (with the CheckCCL class)
 * - testCreatePrivilegedCallableUsingCCLWithNoPrivs: needs a SecurityManager and permissions (runWithPermissions),
 *    which TeaVM does not have
 * - testPrivilegedCallableUsingCCLWithPrivs: needs a SecurityManager and permissions (runWithPermissions), which TeaVM
 *    does not have
 * - testPrivilegedCallableWithNoPrivs: needs a SecurityManager and permissions (runWithPermissions), which TeaVM does
 *    not have
 * - testPrivilegedCallableWithPrivs: needs a SecurityManager and permissions (runWithPermissions), which TeaVM does
 *    not have
 * Changed: testDefaultThreadFactory no longer checks the thread group, because TeaVM has no ThreadGroup.
 * Removed: catch blocks for SecurityException, which only a SecurityManager throws.
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ImportOrder|LeftCurly|MethodParamPad|NeedBraces|UnusedImports|WhitespaceAfter
// CHECKSTYLE.OFF: WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;


@TeaVMTest
public class ExecutorsTest extends JSR166TestCase {
    // android-note: Removed because the CTS runner does a bad job of
    // retrying tests that have suite() declarations.
    //
    // public static void main(String[] args) {
    //     main(suite(), args);
    // }
    // public static Test suite() {
    //     return new TestSuite(ExecutorsTest.class);
    // }

    /**
     * A newCachedThreadPool can execute runnables
     */
    @Test
    public void testNewCachedThreadPool1() {
        final ExecutorService e = Executors.newCachedThreadPool();
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A newCachedThreadPool with given ThreadFactory can execute runnables
     */
    @Test
    public void testNewCachedThreadPool2() {
        final ExecutorService e = Executors.newCachedThreadPool(new SimpleThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A newCachedThreadPool with null ThreadFactory throws NPE
     */
    @Test
    public void testNewCachedThreadPool3() {
        try {
            ExecutorService e = Executors.newCachedThreadPool(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new SingleThreadExecutor can execute runnables
     */
    @Test
    public void testNewSingleThreadExecutor1() {
        final ExecutorService e = Executors.newSingleThreadExecutor();
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new SingleThreadExecutor with given ThreadFactory can execute runnables
     */
    @Test
    public void testNewSingleThreadExecutor2() {
        final ExecutorService e = Executors.newSingleThreadExecutor(new SimpleThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new SingleThreadExecutor with null ThreadFactory throws NPE
     */
    @Test
    public void testNewSingleThreadExecutor3() {
        try {
            ExecutorService e = Executors.newSingleThreadExecutor(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new SingleThreadExecutor cannot be casted to concrete implementation
     */
    @Test
    public void testCastNewSingleThreadExecutor() {
        final ExecutorService e = Executors.newSingleThreadExecutor();
        try (PoolCleaner cleaner = cleaner(e)) {
            try {
                // TeaVM port: TeaVM removes a cast whose result is unused, so the test uses the result.
                ThreadPoolExecutor tpe = (ThreadPoolExecutor)e;
                tpe.getCorePoolSize();
                shouldThrow();
            } catch (ClassCastException success) {}
        }
    }

    /**
     * A new newFixedThreadPool can execute runnables
     */
    @Test
    public void testNewFixedThreadPool1() {
        final ExecutorService e = Executors.newFixedThreadPool(2);
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new newFixedThreadPool with given ThreadFactory can execute runnables
     */
    @Test
    public void testNewFixedThreadPool2() {
        final ExecutorService e = Executors.newFixedThreadPool(2, new SimpleThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * A new newFixedThreadPool with null ThreadFactory throws NPE
     */
    @Test
    public void testNewFixedThreadPool3() {
        try {
            ExecutorService e = Executors.newFixedThreadPool(2, null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new newFixedThreadPool with 0 threads throws IAE
     */
    @Test
    public void testNewFixedThreadPool4() {
        try {
            ExecutorService e = Executors.newFixedThreadPool(0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * An unconfigurable newFixedThreadPool can execute runnables
     */
    @Test
    public void testUnconfigurableExecutorService() {
        final ExecutorService e = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(2));
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
            e.execute(new NoOpRunnable());
        }
    }

    /**
     * unconfigurableExecutorService(null) throws NPE
     */
    @Test
    public void testUnconfigurableExecutorServiceNPE() {
        try {
            ExecutorService e = Executors.unconfigurableExecutorService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * unconfigurableScheduledExecutorService(null) throws NPE
     */
    @Test
    public void testUnconfigurableScheduledExecutorServiceNPE() {
        try {
            ExecutorService e = Executors.unconfigurableScheduledExecutorService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * a newSingleThreadScheduledExecutor successfully runs delayed task
     */
    @Test
    public void testNewSingleThreadScheduledExecutor() throws Exception {
        final ScheduledExecutorService p = Executors.newSingleThreadScheduledExecutor();
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * a newScheduledThreadPool successfully runs delayed task
     */
    @Test
    public void testNewScheduledThreadPool() throws Exception {
        final ScheduledExecutorService p = Executors.newScheduledThreadPool(2);
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * an unconfigurable newScheduledThreadPool successfully runs delayed task
     */
    @Test
    public void testUnconfigurableScheduledExecutorService() throws Exception {
        final ScheduledExecutorService p =
            Executors.unconfigurableScheduledExecutorService
            (Executors.newScheduledThreadPool(2));
        try (PoolCleaner cleaner = cleaner(p)) {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        }
    }

    /**
     * Future.get on submitted tasks will time out if they compute too long.
     */
    @Test
    public void testTimedCallable() throws Exception {
        final ExecutorService[] executors = {
            Executors.newSingleThreadExecutor(),
            Executors.newCachedThreadPool(),
            Executors.newFixedThreadPool(2),
            Executors.newScheduledThreadPool(2),
        };

        final Runnable sleeper = new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                delay(LONG_DELAY_MS);
            }};

        List<Thread> threads = new ArrayList<Thread>();
        for (final ExecutorService executor : executors) {
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() {
                    Future future = executor.submit(sleeper);
                    assertFutureTimesOut(future);
                }}));
        }
        for (Thread thread : threads)
            awaitTermination(thread);
        for (ExecutorService executor : executors)
            joinPool(executor);
    }

    /**
     * ThreadPoolExecutor using defaultThreadFactory has
     * specified group, priority, daemon status, and name
     */
    @Test
    public void testDefaultThreadFactory() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        Runnable r = new CheckedRunnable() {
            public void realRun() {
                Thread current = Thread.currentThread();
                assertTrue(!current.isDaemon());
                assertTrue(current.getPriority() <= Thread.NORM_PRIORITY);
                String name = current.getName();
                assertTrue(name.endsWith("thread-1"));
                done.countDown();
            }};
        ExecutorService e = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
        try (PoolCleaner cleaner = cleaner(e)) {
            e.execute(r);
            await(done);
        }
    }

    /**
     * callable(Runnable) returns null when called
     */
    @Test
    public void testCallable1() throws Exception {
        Callable c = Executors.callable(new NoOpRunnable());
        assertNull(c.call());
    }

    /**
     * callable(Runnable, result) returns result when called
     */
    @Test
    public void testCallable2() throws Exception {
        Callable c = Executors.callable(new NoOpRunnable(), one);
        assertSame(one, c.call());
    }

    /**
     * callable(PrivilegedAction) returns its result when called
     */
    @Test
    public void testCallable3() throws Exception {
        Callable c = Executors.callable(new PrivilegedAction() {
                public Object run() { return one; }});
        assertSame(one, c.call());
    }

    /**
     * callable(PrivilegedExceptionAction) returns its result when called
     */
    @Test
    public void testCallable4() throws Exception {
        Callable c = Executors.callable(new PrivilegedExceptionAction() {
                public Object run() { return one; }});
        assertSame(one, c.call());
    }

    /**
     * callable(null Runnable) throws NPE
     */
    @Test
    public void testCallableNPE1() {
        try {
            Callable c = Executors.callable((Runnable) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null, result) throws NPE
     */
    @Test
    public void testCallableNPE2() {
        try {
            Callable c = Executors.callable((Runnable) null, one);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null PrivilegedAction) throws NPE
     */
    @Test
    public void testCallableNPE3() {
        try {
            Callable c = Executors.callable((PrivilegedAction) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null PrivilegedExceptionAction) throws NPE
     */
    @Test
    public void testCallableNPE4() {
        try {
            Callable c = Executors.callable((PrivilegedExceptionAction) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

}
