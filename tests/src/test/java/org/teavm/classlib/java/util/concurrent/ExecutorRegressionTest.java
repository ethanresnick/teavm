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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.teavm.jso.browser.Window;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
public class ExecutorRegressionTest {
    // Future Java SE 21 specifies immediate result/exception queries and the four terminal/running states.
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html
    @Test
    public void immediateFutureQueries() {
        FutureTask<Integer> success = new FutureTask<>(() -> 42);
        assertEquals(Future.State.RUNNING, success.state());
        assertThrows(IllegalStateException.class, success::resultNow);
        assertThrows(IllegalStateException.class, success::exceptionNow);
        success.run();
        assertEquals(Future.State.SUCCESS, success.state());
        assertEquals(42, success.resultNow());
        assertThrows(IllegalStateException.class, success::exceptionNow);
        IllegalArgumentException cause = new IllegalArgumentException();
        FutureTask<Integer> failure = new FutureTask<>(() -> {
            throw cause;
        });
        failure.run();
        assertEquals(Future.State.FAILED, failure.state());
        assertSame(cause, failure.exceptionNow());
        assertThrows(IllegalStateException.class, failure::resultNow);
        for (boolean interrupt : new boolean[] {false, true}) {
            FutureTask<Integer> cancelled = new FutureTask<>(() -> 42);
            assertTrue(cancelled.cancel(interrupt));
            assertEquals(Future.State.CANCELLED, cancelled.state());
            assertThrows(IllegalStateException.class, cancelled::resultNow);
            assertThrows(IllegalStateException.class, cancelled::exceptionNow);
            assertFalse(cancelled.cancel(interrupt));
        }
    }

    @Test
    @SkipJVM
    @OnlyPlatform({TestPlatform.JAVASCRIPT, TestPlatform.WEBASSEMBLY_GC})
    public void callbackSubmissionAndCompletion() throws InterruptedException {
        var pool = Executors.newSingleThreadExecutor();
        var scheduled = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch completed = new CountDownLatch(3);
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Throwable[] failure = new Throwable[1];
        future.thenAccept(value -> {
            assertEquals(42, value);
            completed.countDown();
        });
        Window.setTimeout(() -> {
            try {
                FutureTask<Integer> task = new FutureTask<>(() -> 17);
                task.run();
                assertEquals(Future.State.SUCCESS, task.state());
                assertEquals(17, task.resultNow());
                pool.execute(completed::countDown);
                scheduled.schedule(completed::countDown, 1, TimeUnit.MILLISECONDS);
                future.complete(42);
            } catch (Throwable e) {
                failure[0] = e;
                while (completed.getCount() > 0) {
                    completed.countDown();
                }
            }
        }, 1);
        try {
            assertTrue(completed.await(10, TimeUnit.SECONDS));
            assertEquals(null, failure[0]);
        } finally {
            pool.shutdownNow();
            scheduled.shutdownNow();
        }
    }
    @Test
    @SkipJVM
    public void completionCancelsUnusedTimers() throws ReflectiveOperationException {
        for (boolean fallback : new boolean[] {false, true}) {
            for (int outcome = 0; outcome < 3; outcome++) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                if (fallback) {
                    future.completeOnTimeout(new Object(), 1, TimeUnit.DAYS);
                } else {
                    future.orTimeout(1, TimeUnit.DAYS);
                }
                // Inspect the reachable substitute's actual timer, not a separately linked T-prefixed class.
                var stackField = CompletableFuture.class.getDeclaredField("stack");
                stackField.setAccessible(true);
                Object completion = stackField.get(future);
                var actionField = completion.getClass().getDeclaredField("fn");
                actionField.setAccessible(true);
                Object canceller = actionField.get(completion);
                var timerField = canceller.getClass().getDeclaredField("f");
                timerField.setAccessible(true);
                Future<?> timer = (Future<?>) timerField.get(canceller);
                assertFalse(timer.isDone());
                if (outcome == 0) {
                    future.complete(new Object());
                } else if (outcome == 1) {
                    future.completeExceptionally(new IllegalArgumentException());
                } else {
                    future.cancel(false);
                }
                assertTrue(timer.isCancelled());
            }
        }
    }

    // Executor.execute (Java SE 21) requires NullPointerException for a null command.
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executor.html
    @Test
    @SkipJVM // JDK 25 accepts null here; exercise the Java SE 21 Executor contract on TeaVM.
    public void defaultDelayedExecutorRejectsNullTask() {
        for (long delay : new long[] {-1, 0, 1, Long.MAX_VALUE}) {
            var executor = CompletableFuture.delayedExecutor(delay, TimeUnit.NANOSECONDS);
            assertThrows(NullPointerException.class, () -> executor.execute(null));
        }
    }

    @Test
    @SkipJVM // Same reference-JDK deviation as the default delayed executor.
    public void explicitDelayedExecutorRejectsNullTask() {
        for (long delay : new long[] {-1, 0, 1, Long.MAX_VALUE}) {
            var executor = CompletableFuture.delayedExecutor(delay, TimeUnit.NANOSECONDS, Runnable::run);
            assertThrows(NullPointerException.class, () -> executor.execute(null));
        }
    }

}
