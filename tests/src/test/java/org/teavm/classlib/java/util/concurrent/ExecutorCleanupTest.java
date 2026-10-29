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
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
@SkipJVM
public class ExecutorCleanupTest {
    @Test
    public void abandonedDefaultExecutorStops() throws Exception {
        checkAbandoned(false);
    }

    @Test
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
    public void collectionWaitsForAllActiveCalls() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var wrapper = new Object();
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
    public void cancellationSuppressesPendingCleanup() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var wrapper = new Object();
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

    private static class State {
        WeakReference<ExecutorService> wrapper;
        Thread worker;
    }
}
