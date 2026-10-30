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
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class CompletionStageDefaultsTest {
    @Test
    public void defaultsRecoverFailuresAndPreserveSuccess() {
        // Java SE 21: exceptional continuations recover failure; normal results pass through unchanged.
        for (boolean exceptional : new boolean[] { false, true }) {
            var source = new CompletableFuture<Integer>();
            if (exceptional) {
                source.completeExceptionally(new IllegalArgumentException());
            } else {
                source.complete(7);
            }
            CompletionStage<Integer> stage = new DelegatingStage<>(source);
            int expected = exceptional ? 42 : 7;
            assertEquals(expected, stage.exceptionallyAsync(e -> 42).toCompletableFuture().join());
            assertEquals(expected, stage.exceptionallyAsync(e -> 42, Runnable::run).toCompletableFuture().join());
            assertEquals(expected, stage.exceptionallyCompose(e -> CompletableFuture.completedFuture(42))
                    .toCompletableFuture().join());
            assertEquals(expected, stage.exceptionallyComposeAsync(e -> CompletableFuture.completedFuture(42))
                    .toCompletableFuture().join());
            assertEquals(expected, stage.exceptionallyComposeAsync(e -> CompletableFuture.completedFuture(42),
                    Runnable::run).toCompletableFuture().join());
        }
    }

    @Test
    public void explicitExecutorAndExceptionalCompletionArePreserved() {
        // Java SE 21: the supplied Executor executes async continuations, and thrown exceptions propagate.
        var tasks = new ArrayList<Runnable>();
        var source = new CompletableFuture<Integer>();
        CompletionStage<Integer> stage = new DelegatingStage<>(source);
        var failure = new IllegalStateException();
        var result = stage.exceptionallyComposeAsync(e -> {
            throw failure;
        }, tasks::add).toCompletableFuture();
        assertEquals(0, tasks.size());
        source.completeExceptionally(new IllegalArgumentException());
        assertEquals(1, tasks.size());
        tasks.remove(0).run();
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6 })
    @SkipJVM
    public void defaultsRejectNullArgumentsBeforeCompletion(int operation) {
        // Java SE 21 requires NPE for null non-result parameters; JDK 25 defaults omit these checks.
        // A delegating implementation avoids the checks supplied by CompletableFuture overrides.
        for (int state = 0; state < 3; state++) {
            var source = new CompletableFuture<Integer>();
            if (state == 1) {
                source.complete(7);
            } else if (state == 2) {
                source.completeExceptionally(new IllegalArgumentException());
            }
            CompletionStage<Integer> stage = new DelegatingStage<>(source);
            assertThrows(NullPointerException.class, () -> {
                switch (operation) {
                    case 0:
                        stage.exceptionallyAsync(null);
                        break;
                    case 1:
                        stage.exceptionallyAsync(null, Runnable::run);
                        break;
                    case 2:
                        stage.exceptionallyAsync(e -> 42, null);
                        break;
                    case 3:
                        stage.exceptionallyCompose(null);
                        break;
                    case 4:
                        stage.exceptionallyComposeAsync(null);
                        break;
                    case 5:
                        stage.exceptionallyComposeAsync(null, Runnable::run);
                        break;
                    case 6:
                        stage.exceptionallyComposeAsync(e -> CompletableFuture.completedFuture(42), null);
                        break;
                    default:
                        throw new AssertionError();
                }
            });
        }
    }

    private static class DelegatingStage<T> implements CompletionStage<T> {
        private final CompletableFuture<T> delegate;

        DelegatingStage(CompletableFuture<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn) {
            return delegate.thenApply(fn);
        }

        @Override
        public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
            return delegate.thenApplyAsync(fn);
        }

        @Override
        public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
            return delegate.thenApplyAsync(fn, executor);
        }

        @Override
        public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
            return delegate.thenAccept(action);
        }

        @Override
        public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
            return delegate.thenAcceptAsync(action);
        }

        @Override
        public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
            return delegate.thenAcceptAsync(action, executor);
        }

        @Override
        public CompletionStage<Void> thenRun(Runnable action) {
            return delegate.thenRun(action);
        }

        @Override
        public CompletionStage<Void> thenRunAsync(Runnable action) {
            return delegate.thenRunAsync(action);
        }

        @Override
        public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
            return delegate.thenRunAsync(action, executor);
        }

        @Override
        public <U, V> CompletionStage<V> thenCombine(
                CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
            return delegate.thenCombine(other, fn);
        }

        @Override
        public <U, V> CompletionStage<V> thenCombineAsync(
                CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
            return delegate.thenCombineAsync(other, fn);
        }

        @Override
        public <U, V> CompletionStage<V> thenCombineAsync(
                CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn,
            Executor executor) {
            return delegate.thenCombineAsync(other, fn, executor);
        }

        @Override
        public <U> CompletionStage<Void> thenAcceptBoth(
                CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
            return delegate.thenAcceptBoth(other, action);
        }

        @Override
        public <U> CompletionStage<Void> thenAcceptBothAsync(
                CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
            return delegate.thenAcceptBothAsync(other, action);
        }

        @Override
        public <U> CompletionStage<Void> thenAcceptBothAsync(
                CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
            return delegate.thenAcceptBothAsync(other, action, executor);
        }

        @Override
        public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
            return delegate.runAfterBoth(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
            return delegate.runAfterBothAsync(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
            return delegate.runAfterBothAsync(other, action, executor);
        }

        @Override
        public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
            return delegate.applyToEither(other, fn);
        }

        @Override
        public <U> CompletionStage<U> applyToEitherAsync(
                CompletionStage<? extends T> other, Function<? super T, U> fn) {
            return delegate.applyToEitherAsync(other, fn);
        }

        @Override
        public <U> CompletionStage<U> applyToEitherAsync(
                CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
            return delegate.applyToEitherAsync(other, fn, executor);
        }

        @Override
        public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
            return delegate.acceptEither(other, action);
        }

        @Override
        public CompletionStage<Void> acceptEitherAsync(
                CompletionStage<? extends T> other, Consumer<? super T> action) {
            return delegate.acceptEitherAsync(other, action);
        }

        @Override
        public CompletionStage<Void> acceptEitherAsync(
                CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
            return delegate.acceptEitherAsync(other, action, executor);
        }

        @Override
        public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
            return delegate.runAfterEither(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
            return delegate.runAfterEitherAsync(other, action);
        }

        @Override
        public CompletionStage<Void> runAfterEitherAsync(
                CompletionStage<?> other, Runnable action, Executor executor) {
            return delegate.runAfterEitherAsync(other, action, executor);
        }

        @Override
        public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            return delegate.thenCompose(fn);
        }

        @Override
        public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
            return delegate.thenComposeAsync(fn);
        }

        @Override
        public <U> CompletionStage<U> thenComposeAsync(
                Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
            return delegate.thenComposeAsync(fn, executor);
        }

        @Override
        public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
            return delegate.handle(fn);
        }

        @Override
        public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
            return delegate.handleAsync(fn);
        }

        @Override
        public <U> CompletionStage<U> handleAsync(
                BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
            return delegate.handleAsync(fn, executor);
        }

        @Override
        public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            return delegate.whenComplete(action);
        }

        @Override
        public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
            return delegate.whenCompleteAsync(action);
        }

        @Override
        public CompletionStage<T> whenCompleteAsync(
                BiConsumer<? super T, ? super Throwable> action, Executor executor) {
            return delegate.whenCompleteAsync(action, executor);
        }

        @Override
        public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
            return delegate.exceptionally(fn);
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return delegate.toCompletableFuture();
        }
    }
}
