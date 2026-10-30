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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class FutureDefaultsTest {
    @Test
    public void terminalStatesUseInterfaceDefaults() {
        for (var state : Future.State.values()) {
            var future = new SampleFuture(state);
            assertEquals(state, future.state());
            if (state == Future.State.SUCCESS) {
                assertEquals(42, future.resultNow());
            } else {
                assertThrows(IllegalStateException.class, future::resultNow);
            }
            if (state == Future.State.FAILED) {
                assertSame(future.failure, future.exceptionNow());
            } else {
                assertThrows(IllegalStateException.class, future::exceptionNow);
            }
        }
    }

    @Test
    public void defaultsRestoreInterruptAfterRetryingGet() {
        try {
            var success = new SampleFuture(Future.State.SUCCESS);
            Thread.currentThread().interrupt();
            assertEquals(42, success.resultNow());
            assertTrue(Thread.interrupted());
            Thread.currentThread().interrupt();
            assertEquals(Future.State.SUCCESS, success.state());
            assertTrue(Thread.interrupted());
            var failure = new SampleFuture(Future.State.FAILED);
            Thread.currentThread().interrupt();
            assertSame(failure.failure, failure.exceptionNow());
            assertTrue(Thread.interrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static class SampleFuture implements Future<Integer> {
        private final State outcome;
        private final RuntimeException failure = new IllegalArgumentException();

        SampleFuture(State outcome) {
            this.outcome = outcome;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return outcome == State.CANCELLED;
        }

        @Override
        public boolean isDone() {
            return outcome != State.RUNNING;
        }

        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (outcome == State.CANCELLED) {
                throw new CancellationException();
            }
            if (outcome == State.FAILED) {
                throw new ExecutionException(failure);
            }
            if (outcome == State.RUNNING) {
                throw new AssertionError();
            }
            return 42;
        }

        @Override
        public Integer get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
            return get();
        }
    }
}
