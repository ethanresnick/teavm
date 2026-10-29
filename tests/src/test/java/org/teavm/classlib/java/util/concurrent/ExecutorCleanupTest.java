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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.jso.core.JSObjects;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
@EachTestCompiledSeparately
@SkipJVM
public class ExecutorCleanupTest {
    @Test
    @SkipPlatform(TestPlatform.C)
    public void abandonedDefaultExecutorStops() throws Exception {
        checkAbandoned(false);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void abandonedCustomFactoryExecutorStops() throws Exception {
        checkAbandoned(true);
    }

    private void checkAbandoned(boolean custom) throws Exception {
        var state = abandon(custom);
        GCSupport.tryToTriggerGC(state.wrapper);
        assertNull(state.wrapper.get(), "wrapper was not collected");
        state.worker.join(5000);
        assertFalse(state.worker.isAlive(), "abandoned executor kept its worker alive");
    }

    private State abandon(boolean custom) throws Exception {
        var executor = custom ? Executors.newSingleThreadExecutor(Thread::new) : Executors.newSingleThreadExecutor();
        var state = new State();
        state.wrapper = new WeakReference<>(executor);
        state.worker = executor.submit(Thread::currentThread).get();
        return state;
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void collectionDrainsQueuedTasksWithoutInterruptingRunningTask() throws Exception {
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var interrupted = new boolean[1];
        var state = abandonWithTasks(release, completed, interrupted);
        GCSupport.tryToTriggerGC(state.wrapper);
        // Allow the notification and shutdown thread to run while the first task is still blocked.
        Thread.sleep(300);
        assertFalse(interrupted[0]);
        release.countDown();
        assertTrue(completed.await(5, TimeUnit.SECONDS), "queued task was discarded");
        assertNull(state.wrapper.get(), "wrapper was not collected");
        state.worker.join(5000);
        assertFalse(state.worker.isAlive());
        assertFalse(interrupted[0]);
    }

    private State abandonWithTasks(CountDownLatch release, CountDownLatch completed, boolean[] interrupted)
            throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var state = new State();
        state.wrapper = new WeakReference<>(executor);
        state.worker = executor.submit(Thread::currentThread).get();
        executor.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted[0] = true;
            }
        });
        executor.execute(completed::countDown);
        return state;
    }

    @Test
    public void reachableWrapperRemainsUsable() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> { }).get();
            GCSupport.tryToTriggerGC();
            assertEquals(42, executor.submit(() -> 42).get().intValue());
            assertFalse(executor.isShutdown());
        } finally {
            executor.shutdownNow();
        }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void collectionWaitsForAllActiveCalls() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var wrapper = JSObjects.create();
        var cleanup = new ExecutorCleanup(wrapper, executor);
        cleanup.beginCall();
        cleanup.beginCall();
        cleanup.collected();
        Thread.sleep(20);
        assertFalse(executor.isShutdown());
        cleanup.endCall();
        Thread.sleep(20);
        assertFalse(executor.isShutdown());
        cleanup.endCall();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertNotNull(wrapper);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void cancellationSuppressesPendingCleanup() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var wrapper = JSObjects.create();
        var cleanup = new ExecutorCleanup(wrapper, executor);
        cleanup.beginCall();
        cleanup.collected();
        cleanup.cancel();
        cleanup.endCall();
        Thread.sleep(20);
        assertFalse(executor.isShutdown());
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertNotNull(wrapper);
    }

    @Test
    public void explicitShutdownAndShutdownNowRemainUsable() throws Exception {
        for (var immediate : new boolean[] { false, true }) {
            var executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> { }).get();
            if (immediate) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(executor.isShutdown());
            // Repeated shutdown must remain harmless after unregistration.
            executor.shutdown();
            executor.shutdownNow();
        }
    }

    @Test
    public void cleanupThreadDoesNotInheritApplicationLocals() throws Exception {
        var copies = new int[1];
        var local = new InheritableThreadLocal<Object>() {
            @Override
            protected Object childValue(Object parent) {
                ++copies[0];
                return parent;
            }
        };
        local.set(new Object());
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals(0, copies[0], "cleanup dispatcher inherited application locals");
            executor.submit(() -> { }).get();
            assertEquals(1, copies[0], "only the executor's worker should inherit locals");
        } finally {
            local.remove();
            executor.shutdownNow();
        }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void threadConstructorHonorsInheritanceFlag() throws Exception {
        var local = new InheritableThreadLocal<String>();
        local.set("parent");
        try {
            for (var inherit : new boolean[] { false, true }) {
                var value = new String[] { "not run" };
                var thread = new Thread(null, () -> value[0] = local.get(), "test", 0, inherit);
                thread.start();
                thread.join();
                assertEquals(inherit ? "parent" : null, value[0]);
            }
        } finally {
            local.remove();
        }
    }

    @Test
    public void threadConstructorControlsContextLoaderInheritance() throws Exception {
        // SE21 Thread: inheritable values include the context class loader; the five-argument
        // constructor can suppress inheritance. True behaves like the four-argument constructor.
        var current = Thread.currentThread();
        var original = current.getContextClassLoader();
        try {
            for (var loader : new ClassLoader[] { null, new ClassLoader() { } }) {
                current.setContextClassLoader(loader);
                assertSame(loader, new Thread(null, null, "default", 0).getContextClassLoader());
                for (var inherit : new boolean[] { false, true }) {
                    var seen = new ClassLoader[1];
                    var child = new Thread(null, () -> seen[0] = Thread.currentThread().getContextClassLoader(),
                            "child", 0, inherit);
                    var expected = inherit ? loader : ClassLoader.getSystemClassLoader();
                    assertSame(expected, child.getContextClassLoader());
                    child.start();
                    child.join();
                    assertSame(expected, seen[0]);
                }
            }
        } finally {
            current.setContextClassLoader(original);
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void cleanupCompletesAfterExplicitShutdownOfEarlierExecutor() throws Exception {
        for (var immediate : new boolean[] { false, true }) {
            var executor = Executors.newSingleThreadExecutor();
            if (immediate) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            awaitCleanupThreadsExit();
            checkAbandoned(immediate);
            awaitCleanupThreadsExit();
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void suspendedAndAbruptCallsReleaseCleanupProtection() throws Exception {
        for (int mode = 0; mode < 3; ++mode) {
            var state = abandonAfterBlockingCall(mode);
            GCSupport.tryToTriggerGC(state.wrapper);
            assertNull(state.wrapper.get());
            state.worker.join(5000);
            assertFalse(state.worker.isAlive());
        }
    }

    private State abandonAfterBlockingCall(int mode) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var state = new State();
        state.wrapper = new WeakReference<>(executor);
        state.worker = executor.submit(Thread::currentThread).get();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var caller = Thread.currentThread();
        var releaser = new Thread(() -> {
            try {
                entered.await();
                GCSupport.tryToTriggerGC();
                if (mode == 1) {
                    caller.interrupt();
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            } finally {
                release.countDown();
            }
        });
        releaser.start();
        java.util.concurrent.Callable<Integer> task = () -> {
            entered.countDown();
            release.await();
            if (mode == 2) {
                throw new IllegalStateException();
            }
            return 42;
        };
        try {
            if (mode == 2) {
                executor.invokeAny(List.of(task));
                throw new AssertionError();
            } else {
                var results = executor.invokeAll(List.of(task));
                assertEquals(0, mode);
                assertEquals(42, results.get(0).get().intValue());
            }
        } catch (InterruptedException e) {
            assertEquals(1, mode);
            assertFalse(Thread.interrupted());
        } catch (ExecutionException e) {
            assertEquals(2, mode);
            assertTrue(e.getCause() instanceof IllegalStateException);
        } finally {
            releaser.join();
        }
        return state;
    }

    private void awaitCleanupThreadsExit() throws Exception {
        for (int attempt = 0; attempt < 500; ++attempt) {
            boolean found = false;
            for (var thread : Thread.getAllStackTraces().keySet()) {
                found |= thread.getName().equals("executor-cleanup");
            }
            if (!found) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("cleanup thread remained live");
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void collectedShutdownRunsOnNewIsolatedThreadAndMayBlock() throws Exception {
        var gate = new ReentrantLock();
        var attempted = new CountDownLatch(1);
        var observed = new Thread[1];
        var inheritedValue = new Object[1];
        var copies = new int[1];
        var local = new InheritableThreadLocal<Object>() {
            @Override
            protected Object childValue(Object parent) {
                ++copies[0];
                return parent;
            }
        };
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public void shutdown() {
                observed[0] = Thread.currentThread();
                inheritedValue[0] = local.get();
                attempted.countDown();
                gate.lock();
                try {
                    super.shutdown();
                } finally {
                    gate.unlock();
                }
            }
        };
        var weak = new WeakReference<?>[1];
        local.set(new Object());
        try {
            var group = new ThreadGroup(Thread.currentThread().getThreadGroup(), "application");
            var creator = new Thread(group, () -> {
                var wrapper = JSObjects.create();
                weak[0] = new WeakReference<>(wrapper);
                new ExecutorCleanup(wrapper, executor);
            });
            creator.setContextClassLoader(new ClassLoader() { });
            creator.start();
            creator.join();
            assertEquals(1, copies[0]);
            var priorThreads = Thread.getAllStackTraces().keySet();
            gate.lock();
            try {
                GCSupport.tryToTriggerGC(weak[0]);
                assertNull(weak[0].get());
                assertTrue(attempted.await(5, TimeUnit.SECONDS));
                // A host callback cannot block on this lock; shutdown must run on its own thread.
                assertFalse(priorThreads.contains(observed[0]));
                assertTrue(observed[0].isDaemon());
                assertNull(observed[0].getContextClassLoader());
                assertNull(observed[0].getThreadGroup().getParent());
                assertNull(inheritedValue[0]);
                assertEquals(1, copies[0]);
            } finally {
                gate.unlock();
            }
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            awaitCleanupThreadsExit();
        } finally {
            local.remove();
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void cancellationBeforeCollectionOnlyUnregisters() throws Exception {
        var shutdowns = new int[1];
        var executor = countingExecutor(shutdowns);
        var references = new WeakReference<?>[1];
        var creator = new Thread(() -> references[0] = cancelRegistration(executor));
        creator.start();
        creator.join();
        var weak = references[0];
        GCSupport.tryToTriggerGC(weak);
        assertNull(weak.get());
        Thread.sleep(200);
        assertEquals(0, shutdowns[0]);
        assertFalse(executor.isShutdown());
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, shutdowns[0]);
        awaitCleanupThreadsExit();
    }

    private WeakReference<Object> cancelRegistration(ExecutorService executor) {
        var wrapper = JSObjects.create();
        var weak = new WeakReference<Object>(wrapper);
        var cleanup = new ExecutorCleanup(wrapper, executor);
        cleanup.cancel();
        cleanup.cancel();
        return weak;
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void duplicateCollectionAndCancellationShutDownOnce() throws Exception {
        var shutdowns = new int[1];
        var executor = countingExecutor(shutdowns);
        var wrapper = JSObjects.create();
        var cleanup = new ExecutorCleanup(wrapper, executor);
        cleanup.beginCall();
        cleanup.collected();
        cleanup.collected();
        assertEquals(0, shutdowns[0]);
        cleanup.endCall();
        cleanup.collected();
        cleanup.cancel();
        cleanup.cancel();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        Thread.sleep(20);
        assertEquals(1, shutdowns[0]);
        assertNotNull(wrapper);
        awaitCleanupThreadsExit();
    }

    private ThreadPoolExecutor countingExecutor(int[] shutdowns) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public void shutdown() {
                ++shutdowns[0];
                super.shutdown();
            }
        };
    }

    private static class State {
        WeakReference<ExecutorService> wrapper;
        Thread worker;
    }
}
