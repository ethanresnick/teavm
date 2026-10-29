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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ExecutorSupportContractsTest {
    @Test
    public void completionServiceUsesExecutorFactoriesAndSuppliedQueue() throws Exception {
        // Java SE 21 AbstractExecutorService: subclasses may customize tasks through newTaskFor.
        var executor = new ControlledExecutor();
        var queue = new LinkedBlockingQueue<Future<Integer>>();
        CompletionService<Integer> service = new ExecutorCompletionService<>(executor, queue);
        Future<Integer> callable = service.submit(() -> 42);
        assertSame(executor.lastTask, callable);
        Future<Integer> runnable = service.submit(() -> { }, 7);
        assertSame(executor.lastTask, runnable);
        assertEquals(1, executor.callableTasks);
        assertEquals(1, executor.runnableTasks);
        executor.tasks.get(1).run();
        executor.tasks.get(0).run();
        assertSame(runnable, service.take());
        assertEquals(7, runnable.get());
        assertSame(callable, queue.take());
        assertEquals(42, callable.get());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void completionQueueReceivesFailedAndCancelledTasks() throws Exception {
        // Java SE 21: completed tasks become available for retrieval regardless of their outcome.
        var executor = new ControlledExecutor();
        CompletionService<Integer> service = new ExecutorCompletionService<>(executor);
        var failure = new IllegalArgumentException();
        Future<Integer> failed = service.submit(() -> {
            throw failure;
        });
        Future<Integer> cancelled = service.submit(() -> 42);
        assertTrue(cancelled.cancel(false));
        executor.tasks.get(0).run();
        executor.tasks.get(1).run();
        assertSame(failed, service.poll());
        assertSame(failure, assertThrows(ExecutionException.class, failed::get).getCause());
        assertSame(cancelled, service.poll(1, TimeUnit.SECONDS));
        assertTrue(cancelled.isCancelled());
        assertEquals(null, service.poll());
    }

    @Test
    public void closeWaitsAndRestoresInterruptWithoutRepeatingShutdownNow() {
        // Java SE 21 ExecutorService.close: interrupt once initiates shutdownNow; await and restore status.
        var executor = new ControlledExecutor();
        executor.interrupts = 2;
        try {
            executor.close();
            assertTrue(Thread.interrupted());
            assertEquals(1, executor.shutdownCalls);
            assertEquals(1, executor.shutdownNowCalls);
            assertEquals(3, executor.waitCalls);
            assertTrue(executor.isTerminated());
            executor.close();
            assertEquals(1, executor.shutdownCalls);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void queueBulkOperationsPreserveOrderAcrossBatchBoundaries() {
        for (int size : new int[] { 0, 1, 63, 64, 65, 129 }) {
            var queue = new LinkedBlockingQueue<Integer>();
            var expected = new ArrayList<Integer>();
            for (int i = 0; i < size; i++) {
                queue.add(i);
                if (i % 3 != 0) {
                    expected.add(i);
                }
            }
            queue.removeIf(i -> i % 3 == 0);
            var actual = new ArrayList<Integer>();
            queue.forEach(actual::add);
            assertEquals(expected, actual);
            actual.clear();
            queue.spliterator().forEachRemaining(actual::add);
            assertEquals(expected, actual);
            queue.removeIf(i -> {
                queue.clear();
                return true;
            });
            assertTrue(queue.isEmpty());
            assertFalse(queue.removeIf(i -> true));
            assertTrue(queue.offer(42));
            assertEquals(42, queue.poll());
        }
    }

    private static class ControlledExecutor extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        RunnableFuture<?> lastTask;
        int callableTasks;
        int runnableTasks;
        int shutdownCalls;
        int shutdownNowCalls;
        int waitCalls;
        int interrupts;
        boolean terminated;

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> task) {
            callableTasks++;
            var result = super.newTaskFor(task);
            lastTask = result;
            return result;
        }

        @Override
        protected <T> RunnableFuture<T> newTaskFor(Runnable task, T value) {
            runnableTasks++;
            var result = super.newTaskFor(task, value);
            lastTask = result;
            return result;
        }

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNowCalls++;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdownCalls > 0;
        }

        @Override
        public boolean isTerminated() {
            return terminated;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            waitCalls++;
            if (interrupts-- > 0) {
                throw new InterruptedException();
            }
            terminated = true;
            return true;
        }
    }
}
