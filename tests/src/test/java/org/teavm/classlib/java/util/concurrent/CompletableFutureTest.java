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
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * TeaVM port of jsr166's CompletableFutureTest for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/CompletableFutureTest.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * JUnit 3 test methods have @Test; the base class JSR166TestCase is the cut-down TeaVM port.
 * Removed:
 * - testDefaultExecutor: compares with ForkJoinPool.commonPool(); TeaVM has no ForkJoinPool
 * - resultOf: reads a private field by reflection (implementation detail, off by default)
 * - testExceptionPropagationReusesResultObject: an implementation-detail test that reads a private field by reflection
 * - testMinimalCompletionStage_minimality: an implementation-detail test that calls every method by reflection
 * Changed: ThreadExecutor marks its threads with a ThreadLocal instead of a ThreadGroup, and the
 * execution-mode checks no longer compare with ForkJoinPool (TeaVM has neither).
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ExplicitInitialization|ImportOrder|InnerAssignment|LeftCurly|MethodParamPad
// CHECKSTYLE.OFF: MultipleVariableDeclarations|NeedBraces|OperatorWrap|ParenPad|RightCurly
// CHECKSTYLE.OFF: UnnecessaryParentheses|UnusedImports|WhitespaceAfter|WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;


@TeaVMTest
public class CompletableFutureTest extends JSR166TestCase {

    // android-note: Removed because the CTS runner does a bad job of
    // retrying tests that have suite() declarations.
    //
    // public static void main(String[] args) {
    //     main(suite(), args);
    // }
    // public static Test suite() {
    //     return new TestSuite(CompletableFutureTest.class);
    // }

    static class CFException extends RuntimeException {}

    void checkIncomplete(CompletableFuture<?> f) {
        assertFalse(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("Not completed"));
        try {
            assertNull(f.getNow(null));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            f.get(0L, SECONDS);
            shouldThrow();
        }
        catch (TimeoutException success) {}
        catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    <T> void checkCompletedNormally(CompletableFuture<T> f, T value) {
        checkTimedGet(f, value);

        try {
            assertEquals(value, f.join());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.getNow(null));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertFalse(f.isCompletedExceptionally());
        assertTrue(f.toString().contains("[Completed normally]"));
    }

    /**
     * Returns the "raw" internal exceptional completion of f,
     * without any additional wrapping with CompletionException.
     */
    <U> Throwable exceptionalCompletion(CompletableFuture<U> f) {
        // handle (and whenComplete) can distinguish between "direct"
        // and "wrapped" exceptional completion
        return f.handle((U u, Throwable t) -> t).join();
    }

    void checkCompletedExceptionally(CompletableFuture<?> f,
                                     boolean wrapped,
                                     Consumer<Throwable> checker) {
        Throwable cause = exceptionalCompletion(f);
        if (wrapped) {
            assertTrue(cause instanceof CompletionException);
            cause = cause.getCause();
        }
        checker.accept(cause);

        long startTime = System.nanoTime();
        try {
            f.get(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(cause, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS / 2);

        try {
            f.join();
            shouldThrow();
        } catch (CompletionException success) {
            assertSame(cause, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            f.getNow(null);
            shouldThrow();
        } catch (CompletionException success) {
            assertSame(cause, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            f.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(cause, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        assertFalse(f.isCancelled());
        assertTrue(f.isDone());
        assertTrue(f.isCompletedExceptionally());
        // Android-changed: compatible toString() check (b/233721784)
        // assertTrue(f.toString().contains("[Completed exceptionally]"));
        assertTrue(f.toString().matches(".*\\[.*Completed exceptionally.*\\]"));
    }

    void checkCompletedWithWrappedCFException(CompletableFuture<?> f) {
        checkCompletedExceptionally(f, true,
            (t) -> assertTrue(t instanceof CFException));
    }

    void checkCompletedWithWrappedCancellationException(CompletableFuture<?> f) {
        checkCompletedExceptionally(f, true,
            (t) -> assertTrue(t instanceof CancellationException));
    }

    void checkCompletedWithTimeoutException(CompletableFuture<?> f) {
        checkCompletedExceptionally(f, false,
            (t) -> assertTrue(t instanceof TimeoutException));
    }

    void checkCompletedWithWrappedException(CompletableFuture<?> f,
                                            Throwable ex) {
        checkCompletedExceptionally(f, true, (t) -> assertSame(t, ex));
    }

    void checkCompletedExceptionally(CompletableFuture<?> f, Throwable ex) {
        checkCompletedExceptionally(f, false, (t) -> assertSame(t, ex));
    }

    void checkCancelled(CompletableFuture<?> f) {
        long startTime = System.nanoTime();
        try {
            f.get(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS / 2);

        try {
            f.join();
            shouldThrow();
        } catch (CancellationException success) {}
        try {
            f.getNow(null);
            shouldThrow();
        } catch (CancellationException success) {}
        try {
            f.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        assertTrue(exceptionalCompletion(f) instanceof CancellationException);

        assertTrue(f.isDone());
        assertTrue(f.isCompletedExceptionally());
        assertTrue(f.isCancelled());
        // Android-changed: compatible toString() check (b/233721784)
        // assertTrue(f.toString().contains("[Completed exceptionally]"));
        assertTrue(f.toString().matches(".*\\[.*Completed exceptionally.*\\]"));
    }

    /**
     * A newly constructed CompletableFuture is incomplete, as indicated
     * by methods isDone, isCancelled, and getNow
     */
    @Test
    public void testConstructor() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
    }

    /**
     * complete completes normally, as indicated by methods isDone,
     * isCancelled, join, get, and getNow
     */
    @Test
    public void testComplete() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.complete(v1));
        assertFalse(f.complete(v1));
        checkCompletedNormally(f, v1);
    }}

    /**
     * completeExceptionally completes exceptionally, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    @Test
    public void testCompleteExceptionally() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CFException ex = new CFException();
        checkIncomplete(f);
        f.completeExceptionally(ex);
        checkCompletedExceptionally(f, ex);
    }

    /**
     * cancel completes exceptionally and reports cancelled, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    @Test
    public void testCancel() {
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.cancel(mayInterruptIfRunning));
        assertTrue(f.cancel(mayInterruptIfRunning));
        assertTrue(f.cancel(!mayInterruptIfRunning));
        checkCancelled(f);
    }}

    /**
     * obtrudeValue forces completion with given value
     */
    @Test
    public void testObtrudeValue() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.complete(one));
        checkCompletedNormally(f, one);
        f.obtrudeValue(three);
        checkCompletedNormally(f, three);
        f.obtrudeValue(two);
        checkCompletedNormally(f, two);
        f = new CompletableFuture<>();
        f.obtrudeValue(three);
        checkCompletedNormally(f, three);
        f.obtrudeValue(null);
        checkCompletedNormally(f, null);
        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        f.obtrudeValue(four);
        checkCompletedNormally(f, four);
    }

    /**
     * obtrudeException forces completion with given exception
     */
    @Test
    public void testObtrudeException() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CFException ex;
        CompletableFuture<Integer> f;

        f = new CompletableFuture<>();
        assertTrue(f.complete(v1));
        for (int i = 0; i < 2; i++) {
            f.obtrudeException(ex = new CFException());
            checkCompletedExceptionally(f, ex);
        }

        f = new CompletableFuture<>();
        for (int i = 0; i < 2; i++) {
            f.obtrudeException(ex = new CFException());
            checkCompletedExceptionally(f, ex);
        }

        f = new CompletableFuture<>();
        f.completeExceptionally(ex = new CFException());
        f.obtrudeValue(v1);
        checkCompletedNormally(f, v1);
        f.obtrudeException(ex = new CFException());
        checkCompletedExceptionally(f, ex);
        f.completeExceptionally(new CFException());
        checkCompletedExceptionally(f, ex);
        assertFalse(f.complete(v1));
        checkCompletedExceptionally(f, ex);
    }}

    /**
     * getNumberOfDependents returns number of dependent tasks
     */
    @Test
    public void testGetNumberOfDependents() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        assertEquals(0, f.getNumberOfDependents());
        final CompletableFuture<Void> g = m.thenRun(f, new Noop(m));
        assertEquals(1, f.getNumberOfDependents());
        assertEquals(0, g.getNumberOfDependents());
        final CompletableFuture<Void> h = m.thenRun(f, new Noop(m));
        assertEquals(2, f.getNumberOfDependents());
        assertEquals(0, h.getNumberOfDependents());
        assertTrue(f.complete(v1));
        checkCompletedNormally(g, null);
        checkCompletedNormally(h, null);
        assertEquals(0, f.getNumberOfDependents());
        assertEquals(0, g.getNumberOfDependents());
        assertEquals(0, h.getNumberOfDependents());
    }}

    /**
     * toString indicates current completion state
     */
    @Test
    public void testToString() {
        CompletableFuture<String> f;

        f = new CompletableFuture<String>();
        assertTrue(f.toString().contains("[Not completed]"));

        assertTrue(f.complete("foo"));
        assertTrue(f.toString().contains("[Completed normally]"));

        f = new CompletableFuture<String>();
        assertTrue(f.completeExceptionally(new IndexOutOfBoundsException()));
        // Android-changed: compatible toString() check (b/233721784)
        // assertTrue(f.toString().contains("[Completed exceptionally]"));
        assertTrue(f.toString().matches(".*\\[.*Completed exceptionally.*\\]"));

        for (boolean mayInterruptIfRunning : new boolean[] { true, false }) {
            f = new CompletableFuture<String>();
            assertTrue(f.cancel(mayInterruptIfRunning));
            // Android-changed: compatible toString() check (b/233721784)
            // assertTrue(f.toString().contains("[Completed exceptionally]"));
            assertTrue(f.toString().matches(".*\\[.*Completed exceptionally.*\\]"));
        }
    }

    /**
     * completedFuture returns a completed CompletableFuture with given value
     */
    @Test
    public void testCompletedFuture() {
        CompletableFuture<String> f = CompletableFuture.completedFuture("test");
        checkCompletedNormally(f, "test");
    }

    abstract class CheckedAction {
        int invocationCount = 0;
        final ExecutionMode m;
        CheckedAction(ExecutionMode m) { this.m = m; }
        void invoked() {
            m.checkExecutionMode();
            assertEquals(0, invocationCount++);
        }
        void assertNotInvoked() { assertEquals(0, invocationCount); }
        void assertInvoked() { assertEquals(1, invocationCount); }
    }

    abstract class CheckedIntegerAction extends CheckedAction {
        Integer value;
        CheckedIntegerAction(ExecutionMode m) { super(m); }
        void assertValue(Integer expected) {
            assertInvoked();
            assertEquals(expected, value);
        }
    }

    class IntegerSupplier extends CheckedAction
        implements Supplier<Integer>
    {
        final Integer value;
        IntegerSupplier(ExecutionMode m, Integer value) {
            super(m);
            this.value = value;
        }
        public Integer get() {
            invoked();
            return value;
        }
    }

    // A function that handles and produces null values as well.
    static Integer inc(Integer x) {
        return (x == null) ? null : x + 1;
    }

    class NoopConsumer extends CheckedIntegerAction
        implements Consumer<Integer>
    {
        NoopConsumer(ExecutionMode m) { super(m); }
        public void accept(Integer x) {
            invoked();
            value = x;
        }
    }

    class IncFunction extends CheckedIntegerAction
        implements Function<Integer,Integer>
    {
        IncFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x) {
            invoked();
            return value = inc(x);
        }
    }

    // Choose non-commutative actions for better coverage
    // A non-commutative function that handles and produces null values as well.
    static Integer subtract(Integer x, Integer y) {
        return (x == null && y == null) ? null :
            ((x == null) ? 42 : x.intValue())
            - ((y == null) ? 99 : y.intValue());
    }

    class SubtractAction extends CheckedIntegerAction
        implements BiConsumer<Integer, Integer>
    {
        SubtractAction(ExecutionMode m) { super(m); }
        public void accept(Integer x, Integer y) {
            invoked();
            value = subtract(x, y);
        }
    }

    class SubtractFunction extends CheckedIntegerAction
        implements BiFunction<Integer, Integer, Integer>
    {
        SubtractFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x, Integer y) {
            invoked();
            return value = subtract(x, y);
        }
    }

    class Noop extends CheckedAction implements Runnable {
        Noop(ExecutionMode m) { super(m); }
        public void run() {
            invoked();
        }
    }

    class FailingSupplier extends CheckedAction
        implements Supplier<Integer>
    {
        FailingSupplier(ExecutionMode m) { super(m); }
        public Integer get() {
            invoked();
            throw new CFException();
        }
    }

    class FailingConsumer extends CheckedIntegerAction
        implements Consumer<Integer>
    {
        FailingConsumer(ExecutionMode m) { super(m); }
        public void accept(Integer x) {
            invoked();
            value = x;
            throw new CFException();
        }
    }

    class FailingBiConsumer extends CheckedIntegerAction
        implements BiConsumer<Integer, Integer>
    {
        FailingBiConsumer(ExecutionMode m) { super(m); }
        public void accept(Integer x, Integer y) {
            invoked();
            value = subtract(x, y);
            throw new CFException();
        }
    }

    class FailingFunction extends CheckedIntegerAction
        implements Function<Integer, Integer>
    {
        FailingFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x) {
            invoked();
            value = x;
            throw new CFException();
        }
    }

    class FailingBiFunction extends CheckedIntegerAction
        implements BiFunction<Integer, Integer, Integer>
    {
        FailingBiFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x, Integer y) {
            invoked();
            value = subtract(x, y);
            throw new CFException();
        }
    }

    class FailingRunnable extends CheckedAction implements Runnable {
        FailingRunnable(ExecutionMode m) { super(m); }
        public void run() {
            invoked();
            throw new CFException();
        }
    }

    class CompletableFutureInc extends CheckedIntegerAction
        implements Function<Integer, CompletableFuture<Integer>>
    {
        CompletableFutureInc(ExecutionMode m) { super(m); }
        public CompletableFuture<Integer> apply(Integer x) {
            invoked();
            value = x;
            CompletableFuture<Integer> f = new CompletableFuture<>();
            assertTrue(f.complete(inc(x)));
            return f;
        }
    }

    class FailingCompletableFutureFunction extends CheckedIntegerAction
        implements Function<Integer, CompletableFuture<Integer>>
    {
        FailingCompletableFutureFunction(ExecutionMode m) { super(m); }
        public CompletableFuture<Integer> apply(Integer x) {
            invoked();
            value = x;
            throw new CFException();
        }
    }

    // Used for explicit executor tests
    static final class ThreadExecutor implements Executor {
        final AtomicInteger count = new AtomicInteger(0);
        // TeaVM port: TeaVM has no ThreadGroup, so a thread-local flag marks the threads this executor starts.
        static final ThreadLocal<Boolean> started = new ThreadLocal<>();
        static boolean startedCurrentThread() {
            return started.get() != null;
        }

        public void execute(Runnable r) {
            count.getAndIncrement();
            new Thread(() -> {
                started.set(Boolean.TRUE);
                r.run();
            }).start();
        }
    }

    /**
     * Permits the testing of parallel code for the 3 different
     * execution modes without copy/pasting all the test methods.
     */
    enum ExecutionMode {
        SYNC {
            public void checkExecutionMode() {
                assertFalse(ThreadExecutor.startedCurrentThread());
            }
            public CompletableFuture<Void> runAsync(Runnable a) {
                throw new UnsupportedOperationException();
            }
            public <U> CompletableFuture<U> supplyAsync(Supplier<U> a) {
                throw new UnsupportedOperationException();
            }
            public <T> CompletableFuture<Void> thenRun
                (CompletableFuture<T> f, Runnable a) {
                return f.thenRun(a);
            }
            public <T> CompletableFuture<Void> thenAccept
                (CompletableFuture<T> f, Consumer<? super T> a) {
                return f.thenAccept(a);
            }
            public <T,U> CompletableFuture<U> thenApply
                (CompletableFuture<T> f, Function<? super T,U> a) {
                return f.thenApply(a);
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenCompose(a);
            }
            public <T,U> CompletableFuture<U> handle
                (CompletableFuture<T> f,
                 BiFunction<? super T,Throwable,? extends U> a) {
                return f.handle(a);
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenComplete(a);
            }
            public <T,U> CompletableFuture<Void> runAfterBoth
                (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
                return f.runAfterBoth(g, a);
            }
            public <T,U> CompletableFuture<Void> thenAcceptBoth
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiConsumer<? super T,? super U> a) {
                return f.thenAcceptBoth(g, a);
            }
            public <T,U,V> CompletableFuture<V> thenCombine
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiFunction<? super T,? super U,? extends V> a) {
                return f.thenCombine(g, a);
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEither(g, a);
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEither(g, a);
            }
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEither(g, a);
            }
        },

        ASYNC {
            public void checkExecutionMode() {
                // TeaVM port: no ForkJoinPool; the default executor is not ThreadExecutor.
                assertFalse(ThreadExecutor.startedCurrentThread());
            }
            public CompletableFuture<Void> runAsync(Runnable a) {
                return CompletableFuture.runAsync(a);
            }
            public <U> CompletableFuture<U> supplyAsync(Supplier<U> a) {
                return CompletableFuture.supplyAsync(a);
            }
            public <T> CompletableFuture<Void> thenRun
                (CompletableFuture<T> f, Runnable a) {
                return f.thenRunAsync(a);
            }
            public <T> CompletableFuture<Void> thenAccept
                (CompletableFuture<T> f, Consumer<? super T> a) {
                return f.thenAcceptAsync(a);
            }
            public <T,U> CompletableFuture<U> thenApply
                (CompletableFuture<T> f, Function<? super T,U> a) {
                return f.thenApplyAsync(a);
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenComposeAsync(a);
            }
            public <T,U> CompletableFuture<U> handle
                (CompletableFuture<T> f,
                 BiFunction<? super T,Throwable,? extends U> a) {
                return f.handleAsync(a);
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenCompleteAsync(a);
            }
            public <T,U> CompletableFuture<Void> runAfterBoth
                (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
                return f.runAfterBothAsync(g, a);
            }
            public <T,U> CompletableFuture<Void> thenAcceptBoth
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiConsumer<? super T,? super U> a) {
                return f.thenAcceptBothAsync(g, a);
            }
            public <T,U,V> CompletableFuture<V> thenCombine
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiFunction<? super T,? super U,? extends V> a) {
                return f.thenCombineAsync(g, a);
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEitherAsync(g, a);
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEitherAsync(g, a);
            }
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEitherAsync(g, a);
            }
        },

        EXECUTOR {
            public void checkExecutionMode() {
                assertTrue(ThreadExecutor.startedCurrentThread());
            }
            public CompletableFuture<Void> runAsync(Runnable a) {
                return CompletableFuture.runAsync(a, new ThreadExecutor());
            }
            public <U> CompletableFuture<U> supplyAsync(Supplier<U> a) {
                return CompletableFuture.supplyAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> thenRun
                (CompletableFuture<T> f, Runnable a) {
                return f.thenRunAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> thenAccept
                (CompletableFuture<T> f, Consumer<? super T> a) {
                return f.thenAcceptAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> thenApply
                (CompletableFuture<T> f, Function<? super T,U> a) {
                return f.thenApplyAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenComposeAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> handle
                (CompletableFuture<T> f,
                 BiFunction<? super T,Throwable,? extends U> a) {
                return f.handleAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenCompleteAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<Void> runAfterBoth
                (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
                return f.runAfterBothAsync(g, a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<Void> thenAcceptBoth
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiConsumer<? super T,? super U> a) {
                return f.thenAcceptBothAsync(g, a, new ThreadExecutor());
            }
            public <T,U,V> CompletableFuture<V> thenCombine
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiFunction<? super T,? super U,? extends V> a) {
                return f.thenCombineAsync(g, a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEitherAsync(g, a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEitherAsync(g, a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEitherAsync(g, a, new ThreadExecutor());
            }
        };

        public abstract void checkExecutionMode();
        public abstract CompletableFuture<Void> runAsync(Runnable a);
        public abstract <U> CompletableFuture<U> supplyAsync(Supplier<U> a);
        public abstract <T> CompletableFuture<Void> thenRun
            (CompletableFuture<T> f, Runnable a);
        public abstract <T> CompletableFuture<Void> thenAccept
            (CompletableFuture<T> f, Consumer<? super T> a);
        public abstract <T,U> CompletableFuture<U> thenApply
            (CompletableFuture<T> f, Function<? super T,U> a);
        public abstract <T,U> CompletableFuture<U> thenCompose
            (CompletableFuture<T> f,
             Function<? super T,? extends CompletionStage<U>> a);
        public abstract <T,U> CompletableFuture<U> handle
            (CompletableFuture<T> f,
             BiFunction<? super T,Throwable,? extends U> a);
        public abstract <T> CompletableFuture<T> whenComplete
            (CompletableFuture<T> f,
             BiConsumer<? super T,? super Throwable> a);
        public abstract <T,U> CompletableFuture<Void> runAfterBoth
            (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a);
        public abstract <T,U> CompletableFuture<Void> thenAcceptBoth
            (CompletableFuture<T> f,
             CompletionStage<? extends U> g,
             BiConsumer<? super T,? super U> a);
        public abstract <T,U,V> CompletableFuture<V> thenCombine
            (CompletableFuture<T> f,
             CompletionStage<? extends U> g,
             BiFunction<? super T,? super U,? extends V> a);
        public abstract <T> CompletableFuture<Void> runAfterEither
            (CompletableFuture<T> f,
             CompletionStage<?> g,
             java.lang.Runnable a);
        public abstract <T> CompletableFuture<Void> acceptEither
            (CompletableFuture<T> f,
             CompletionStage<? extends T> g,
             Consumer<? super T> a);
        public abstract <T,U> CompletableFuture<U> applyToEither
            (CompletableFuture<T> f,
             CompletionStage<? extends T> g,
             Function<? super T,U> a);
    }

    /**
     * exceptionally action is not invoked when source completes
     * normally, and source result is propagated
     */
    @Test
    public void testExceptionally_normalCompletion() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                a.getAndIncrement();
                threadFail("should not be called");
                return null;            // unreached
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, v1);
        checkCompletedNormally(f, v1);
        assertEquals(0, a.get());
    }}

    /**
     * exceptionally action completes with function value on source
     * exception
     */
    @Test
    public void testExceptionally_exceptionalCompletion() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                ExecutionMode.SYNC.checkExecutionMode();
                threadAssertSame(t, ex);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedNormally(g, v1);
        assertEquals(1, a.get());
    }}

    /**
     * If an "exceptionally action" throws an exception, it completes
     * exceptionally with that exception
     */
    @Test
    public void testExceptionally_exceptionalCompletionActionFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex1);
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                ExecutionMode.SYNC.checkExecutionMode();
                threadAssertSame(t, ex1);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedException(g, ex2);
        checkCompletedExceptionally(f, ex1);
        assertEquals(1, a.get());
    }}

    /**
     * whenComplete action executes on normal completion, propagating
     * source result.
     */
    @Test
    public void testWhenComplete_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(result, v1);
                threadAssertNull(t);
                a.getAndIncrement();
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, v1);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * whenComplete action executes on exceptional completion, propagating
     * source result.
     */
    @Test
    public void testWhenComplete_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(result);
                threadAssertSame(t, ex);
                a.getAndIncrement();
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        assertEquals(1, a.get());
    }}

    /**
     * whenComplete action executes on cancelled source, propagating
     * CancellationException.
     */
    @Test
    public void testWhenComplete_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(result);
                threadAssertTrue(t instanceof CancellationException);
                a.getAndIncrement();
            });
        if (createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        assertEquals(1, a.get());
    }}

    /**
     * If a whenComplete action throws an exception when triggered by
     * a normal completion, it completes exceptionally
     */
    @Test
    public void testWhenComplete_sourceCompletedNormallyActionFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(result, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                throw ex;
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedWithWrappedException(g, ex);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * If a whenComplete action throws an exception when triggered by
     * a source completion that also throws an exception, the source
     * exception takes precedence (unlike handle)
     */
    @Test
    public void testWhenComplete_sourceFailedActionFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (ExecutionMode m : ExecutionMode.values())
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();

        if (!createIncomplete) f.completeExceptionally(ex1);
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(t, ex1);
                threadAssertNull(result);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedException(g, ex1);
        checkCompletedExceptionally(f, ex1);
        if (testImplementationDetails) {
            assertEquals(1, ex1.getSuppressed().length);
            assertSame(ex2, ex1.getSuppressed()[0]);
        }
        assertEquals(1, a.get());
    }}

    /**
     * handle action completes normally with function value on normal
     * completion of source
     */
    @Test
    public void testHandle_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(result, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                return inc(v1);
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * handle action completes normally with function value on
     * exceptional completion of source
     */
    @Test
    public void testHandle_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(result);
                threadAssertSame(t, ex);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedNormally(g, v1);
        checkCompletedExceptionally(f, ex);
        assertEquals(1, a.get());
    }}

    /**
     * handle action completes normally with function value on
     * cancelled source
     */
    @Test
    public void testHandle_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(result);
                threadAssertTrue(t instanceof CancellationException);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));

        checkCompletedNormally(g, v1);
        checkCancelled(f);
        assertEquals(1, a.get());
    }}

    /**
     * If a "handle action" throws an exception when triggered by
     * a normal completion, it completes exceptionally
     */
    @Test
    public void testHandle_sourceCompletedNormallyActionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(result, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                throw ex;
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedWithWrappedException(g, ex);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * If a "handle action" throws an exception when triggered by
     * a source completion that also throws an exception, the action
     * exception takes precedence (unlike whenComplete)
     */
    @Test
    public void testHandle_sourceFailedActionFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (ExecutionMode m : ExecutionMode.values())
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();

        if (!createIncomplete) f.completeExceptionally(ex1);
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer result, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(result);
                threadAssertSame(ex1, t);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedException(g, ex2);
        checkCompletedExceptionally(f, ex1);
        assertEquals(1, a.get());
    }}

    /**
     * runAsync completes after running Runnable
     */
    @Test
    public void testRunAsync_normalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
    {
        final Noop r = new Noop(m);
        final CompletableFuture<Void> f = m.runAsync(r);
        assertNull(f.join());
        checkCompletedNormally(f, null);
        r.assertInvoked();
    }}

    /**
     * failing runAsync completes exceptionally after running Runnable
     */
    @Test
    public void testRunAsync_exceptionalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
    {
        final FailingRunnable r = new FailingRunnable(m);
        final CompletableFuture<Void> f = m.runAsync(r);
        checkCompletedWithWrappedCFException(f);
        r.assertInvoked();
    }}

    /**
     * supplyAsync completes with result of supplier
     */
    @Test
    public void testSupplyAsync_normalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
        for (Integer v1 : new Integer[] { 1, null })
    {
        final IntegerSupplier r = new IntegerSupplier(m, v1);
        final CompletableFuture<Integer> f = m.supplyAsync(r);
        assertSame(v1, f.join());
        checkCompletedNormally(f, v1);
        r.assertInvoked();
    }}

    /**
     * Failing supplyAsync completes exceptionally
     */
    @Test
    public void testSupplyAsync_exceptionalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
    {
        FailingSupplier r = new FailingSupplier(m);
        CompletableFuture<Integer> f = m.supplyAsync(r);
        checkCompletedWithWrappedCFException(f);
        r.assertInvoked();
    }}

    // seq completion methods

    /**
     * thenRun result completes normally after normal completion of source
     */
    @Test
    public void testThenRun_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.thenRun(f, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterBoth(f, f, rs[1]);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, f, rs[2]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(f.complete(v1));
        final CompletableFuture<Void> h3 = m.thenRun(f, rs[3]);
        final CompletableFuture<Void> h4 = m.runAfterBoth(f, f, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(f, f, rs[5]);

        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        checkCompletedNormally(h4, null);
        checkCompletedNormally(h5, null);
        checkCompletedNormally(f, v1);
        for (Noop r : rs) r.assertInvoked();
    }}

    /**
     * thenRun result completes exceptionally after exceptional
     * completion of source
     */
    @Test
    public void testThenRun_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.thenRun(f, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterBoth(f, f, rs[1]);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, f, rs[2]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(f.completeExceptionally(ex));
        final CompletableFuture<Void> h3 = m.thenRun(f, rs[3]);
        final CompletableFuture<Void> h4 = m.runAfterBoth(f, f, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(f, f, rs[5]);

        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        checkCompletedWithWrappedException(h5, ex);
        checkCompletedExceptionally(f, ex);
        for (Noop r : rs) r.assertNotInvoked();
    }}

    /**
     * thenRun result completes exceptionally if source cancelled
     */
    @Test
    public void testThenRun_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.thenRun(f, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterBoth(f, f, rs[1]);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, f, rs[2]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Void> h3 = m.thenRun(f, rs[3]);
        final CompletableFuture<Void> h4 = m.runAfterBoth(f, f, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(f, f, rs[5]);

        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        checkCompletedWithWrappedCancellationException(h4);
        checkCompletedWithWrappedCancellationException(h5);
        checkCancelled(f);
        for (Noop r : rs) r.assertNotInvoked();
    }}

    /**
     * thenRun result completes exceptionally if action does
     */
    @Test
    public void testThenRun_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingRunnable[] rs = new FailingRunnable[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingRunnable(m);

        final CompletableFuture<Void> h0 = m.thenRun(f, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterBoth(f, f, rs[1]);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, f, rs[2]);
        assertTrue(f.complete(v1));
        final CompletableFuture<Void> h3 = m.thenRun(f, rs[3]);
        final CompletableFuture<Void> h4 = m.runAfterBoth(f, f, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(f, f, rs[5]);

        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        checkCompletedWithWrappedCFException(h4);
        checkCompletedWithWrappedCFException(h5);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenApply result completes normally after normal completion of source
     */
    @Test
    public void testThenApply_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.thenApply(f, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(f, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        assertTrue(f.complete(v1));
        final CompletableFuture<Integer> h2 = m.thenApply(f, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(f, f, rs[3]);

        checkCompletedNormally(h0, inc(v1));
        checkCompletedNormally(h1, inc(v1));
        checkCompletedNormally(h2, inc(v1));
        checkCompletedNormally(h3, inc(v1));
        checkCompletedNormally(f, v1);
        for (IncFunction r : rs) r.assertValue(inc(v1));
    }}

    /**
     * thenApply result completes exceptionally after exceptional
     * completion of source
     */
    @Test
    public void testThenApply_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.thenApply(f, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(f, f, rs[1]);
        assertTrue(f.completeExceptionally(ex));
        final CompletableFuture<Integer> h2 = m.thenApply(f, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(f, f, rs[3]);

        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedExceptionally(f, ex);
        for (IncFunction r : rs) r.assertNotInvoked();
    }}

    /**
     * thenApply result completes exceptionally if source cancelled
     */
    @Test
    public void testThenApply_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.thenApply(f, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(f, f, rs[1]);
        assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> h2 = m.thenApply(f, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(f, f, rs[3]);

        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        checkCancelled(f);
        for (IncFunction r : rs) r.assertNotInvoked();
    }}

    /**
     * thenApply result completes exceptionally if action does
     */
    @Test
    public void testThenApply_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingFunction[] rs = new FailingFunction[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingFunction(m);

        final CompletableFuture<Integer> h0 = m.thenApply(f, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(f, f, rs[1]);
        assertTrue(f.complete(v1));
        final CompletableFuture<Integer> h2 = m.thenApply(f, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(f, f, rs[3]);

        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenAccept result completes normally after normal completion of source
     */
    @Test
    public void testThenAccept_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.thenAccept(f, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(f, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        assertTrue(f.complete(v1));
        final CompletableFuture<Void> h2 = m.thenAccept(f, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(f, f, rs[3]);

        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        checkCompletedNormally(f, v1);
        for (NoopConsumer r : rs) r.assertValue(v1);
    }}

    /**
     * thenAccept result completes exceptionally after exceptional
     * completion of source
     */
    @Test
    public void testThenAccept_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.thenAccept(f, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(f, f, rs[1]);
        assertTrue(f.completeExceptionally(ex));
        final CompletableFuture<Void> h2 = m.thenAccept(f, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(f, f, rs[3]);

        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedExceptionally(f, ex);
        for (NoopConsumer r : rs) r.assertNotInvoked();
    }}

    /**
     * thenAccept result completes exceptionally if source cancelled
     */
    @Test
    public void testThenAccept_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.thenAccept(f, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(f, f, rs[1]);
        assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Void> h2 = m.thenAccept(f, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(f, f, rs[3]);

        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        checkCancelled(f);
        for (NoopConsumer r : rs) r.assertNotInvoked();
    }}

    /**
     * thenAccept result completes exceptionally if action does
     */
    @Test
    public void testThenAccept_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingConsumer[] rs = new FailingConsumer[4];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingConsumer(m);

        final CompletableFuture<Void> h0 = m.thenAccept(f, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(f, f, rs[1]);
        assertTrue(f.complete(v1));
        final CompletableFuture<Void> h2 = m.thenAccept(f, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(f, f, rs[3]);

        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenCombine result completes normally after normal completion
     * of sources
     */
    @Test
    public void testThenCombine_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction[] rs = new SubtractFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new SubtractFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Integer> h0 = m.thenCombine(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.thenCombine(fst, fst, rs[1]);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.thenCombine(fst, fst, rs[3]);
        checkIncomplete(h0); rs[0].assertNotInvoked();
        checkIncomplete(h2); rs[2].assertNotInvoked();
        checkCompletedNormally(h1, subtract(w1, w1));
        checkCompletedNormally(h3, subtract(w1, w1));
        rs[1].assertValue(subtract(w1, w1));
        rs[3].assertValue(subtract(w1, w1));
        assertTrue(snd.complete(w2));
        final CompletableFuture<Integer> h4 = m.thenCombine(f, g, rs[4]);

        checkCompletedNormally(h0, subtract(v1, v2));
        checkCompletedNormally(h2, subtract(v1, v2));
        checkCompletedNormally(h4, subtract(v1, v2));
        rs[0].assertValue(subtract(v1, v2));
        rs[2].assertValue(subtract(v1, v2));
        rs[4].assertValue(subtract(v1, v2));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenCombine result completes exceptionally after exceptional
     * completion of either source
     */
    @Test
    public void testThenCombine_exceptionalCompletion() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final SubtractFunction r1 = new SubtractFunction(m);
        final SubtractFunction r2 = new SubtractFunction(m);
        final SubtractFunction r3 = new SubtractFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.completeExceptionally(ex) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.completeExceptionally(ex);

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCompletedExceptionally(failFirst ? fst : snd, ex);
    }}

    /**
     * thenCombine result completes exceptionally if either source cancelled
     */
    @Test
    public void testThenCombine_sourceCancelled() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r1 = new SubtractFunction(m);
        final SubtractFunction r2 = new SubtractFunction(m);
        final SubtractFunction r3 = new SubtractFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.cancel(mayInterruptIfRunning) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.cancel(mayInterruptIfRunning);

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCancelled(failFirst ? fst : snd);
    }}

    /**
     * thenCombine result completes exceptionally if action does
     */
    @Test
    public void testThenCombine_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiFunction r1 = new FailingBiFunction(m);
        final FailingBiFunction r2 = new FailingBiFunction(m);
        final FailingBiFunction r3 = new FailingBiFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        assertTrue(snd.complete(w2));
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenAcceptBoth result completes normally after normal
     * completion of sources
     */
    @Test
    public void testThenAcceptBoth_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r1 = new SubtractAction(m);
        final SubtractAction r2 = new SubtractAction(m);
        final SubtractAction r3 = new SubtractAction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        r1.assertValue(subtract(v1, v2));
        r2.assertValue(subtract(v1, v2));
        r3.assertValue(subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenAcceptBoth result completes exceptionally after exceptional
     * completion of either source
     */
    @Test
    public void testThenAcceptBoth_exceptionalCompletion() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final SubtractAction r1 = new SubtractAction(m);
        final SubtractAction r2 = new SubtractAction(m);
        final SubtractAction r3 = new SubtractAction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.completeExceptionally(ex) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.completeExceptionally(ex);

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCompletedExceptionally(failFirst ? fst : snd, ex);
    }}

    /**
     * thenAcceptBoth result completes exceptionally if either source cancelled
     */
    @Test
    public void testThenAcceptBoth_sourceCancelled() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r1 = new SubtractAction(m);
        final SubtractAction r2 = new SubtractAction(m);
        final SubtractAction r3 = new SubtractAction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.cancel(mayInterruptIfRunning) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.cancel(mayInterruptIfRunning);

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCancelled(failFirst ? fst : snd);
    }}

    /**
     * thenAcceptBoth result completes exceptionally if action does
     */
    @Test
    public void testThenAcceptBoth_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiConsumer r1 = new FailingBiConsumer(m);
        final FailingBiConsumer r2 = new FailingBiConsumer(m);
        final FailingBiConsumer r3 = new FailingBiConsumer(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterBoth result completes normally after normal
     * completion of sources
     */
    @Test
    public void testRunAfterBoth_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r1 = new Noop(m);
        final Noop r2 = new Noop(m);
        final Noop r3 = new Noop(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterBoth result completes exceptionally after exceptional
     * completion of either source
     */
    @Test
    public void testRunAfterBoth_exceptionalCompletion() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final Noop r1 = new Noop(m);
        final Noop r2 = new Noop(m);
        final Noop r3 = new Noop(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.completeExceptionally(ex) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.completeExceptionally(ex);

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCompletedExceptionally(failFirst ? fst : snd, ex);
    }}

    /**
     * runAfterBoth result completes exceptionally if either source cancelled
     */
    @Test
    public void testRunAfterBoth_sourceCancelled() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r1 = new Noop(m);
        final Noop r2 = new Noop(m);
        final Noop r3 = new Noop(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.cancel(mayInterruptIfRunning) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.cancel(mayInterruptIfRunning);

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCancelled(failFirst ? fst : snd);
    }}

    /**
     * runAfterBoth result completes exceptionally if action does
     */
    @Test
    public void testRunAfterBoth_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable r1 = new FailingRunnable(m);
        final FailingRunnable r2 = new FailingRunnable(m);
        final FailingRunnable r3 = new FailingRunnable(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * applyToEither result completes normally after normal completion
     * of either source
     */
    @Test
    public void testApplyToEither_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.complete(v1);
        checkCompletedNormally(h0, inc(v1));
        checkCompletedNormally(h1, inc(v1));
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedNormally(h2, inc(v1));
        checkCompletedNormally(h3, inc(v1));
        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);
        rs[4].assertValue(h4.join());
        rs[5].assertValue(h5.join());
        assertTrue(Objects.equals(inc(v1), h4.join()) ||
                   Objects.equals(inc(v2), h4.join()));
        assertTrue(Objects.equals(inc(v1), h5.join()) ||
                   Objects.equals(inc(v2), h5.join()));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h0, inc(v1));
        checkCompletedNormally(h1, inc(v1));
        checkCompletedNormally(h2, inc(v1));
        checkCompletedNormally(h3, inc(v1));
        for (int i = 0; i < 4; i++) rs[i].assertValue(inc(v1));
    }}

    /**
     * applyToEither result completes exceptionally after exceptional
     * completion of either source
     */
    @Test
    public void testApplyToEither_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.completeExceptionally(ex);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);
        try {
            assertEquals(inc(v1), h4.join());
            rs[4].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h4, ex);
            rs[4].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h5.join());
            rs[5].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h5, ex);
            rs[5].assertNotInvoked();
        }

        checkCompletedExceptionally(f, ex);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    @Test
    public void testApplyToEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        assertTrue(fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        assertTrue(!fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(inc(v1), h0.join());
            rs[0].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h0, ex);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h1.join());
            rs[1].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h1, ex);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h2.join());
            rs[2].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h2, ex);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h3.join());
            rs[3].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h3, ex);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCompletedExceptionally(g, ex);
    }}

    /**
     * applyToEither result completes exceptionally if either source cancelled
     */
    @Test
    public void testApplyToEither_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.cancel(mayInterruptIfRunning);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);
        try {
            assertEquals(inc(v1), h4.join());
            rs[4].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h4);
            rs[4].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h5.join());
            rs[5].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h5);
            rs[5].assertNotInvoked();
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    @Test
    public void testApplyToEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        assertTrue(fFirst ? f.complete(v1) : g.cancel(mayInterruptIfRunning));
        assertTrue(!fFirst ? f.complete(v1) : g.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(inc(v1), h0.join());
            rs[0].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h0);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h1.join());
            rs[1].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h1);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h2.join());
            rs[2].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h2);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h3.join());
            rs[3].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h3);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCancelled(g);
    }}

    /**
     * applyToEither result completes exceptionally if action does
     */
    @Test
    public void testApplyToEither_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingFunction[] rs = new FailingFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        f.complete(v1);
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertValue(v1);

        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);

        checkCompletedWithWrappedCFException(h4);
        assertTrue(Objects.equals(v1, rs[4].value) ||
                   Objects.equals(v2, rs[4].value));
        checkCompletedWithWrappedCFException(h5);
        assertTrue(Objects.equals(v1, rs[5].value) ||
                   Objects.equals(v2, rs[5].value));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * acceptEither result completes normally after normal completion
     * of either source
     */
    @Test
    public void testAcceptEither_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.complete(v1);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        rs[0].assertValue(v1);
        rs[1].assertValue(v1);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        rs[2].assertValue(v1);
        rs[3].assertValue(v1);
        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);
        checkCompletedNormally(h4, null);
        checkCompletedNormally(h5, null);
        assertTrue(Objects.equals(v1, rs[4].value) ||
                   Objects.equals(v2, rs[4].value));
        assertTrue(Objects.equals(v1, rs[5].value) ||
                   Objects.equals(v2, rs[5].value));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        for (int i = 0; i < 4; i++) rs[i].assertValue(v1);
    }}

    /**
     * acceptEither result completes exceptionally after exceptional
     * completion of either source
     */
    @Test
    public void testAcceptEither_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.completeExceptionally(ex);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);

        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h4, ex);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h5, ex);
            rs[5].assertNotInvoked();
        }

        checkCompletedExceptionally(f, ex);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    @Test
    public void testAcceptEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        assertTrue(fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        assertTrue(!fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(null, h0.join());
            rs[0].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h0, ex);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(null, h1.join());
            rs[1].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h1, ex);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(null, h2.join());
            rs[2].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h2, ex);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(null, h3.join());
            rs[3].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h3, ex);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCompletedExceptionally(g, ex);
    }}

    /**
     * acceptEither result completes exceptionally if either source cancelled
     */
    @Test
    public void testAcceptEither_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.cancel(mayInterruptIfRunning);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);

        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h4);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h5);
            rs[5].assertNotInvoked();
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    /**
     * acceptEither result completes exceptionally if action does
     */
    @Test
    public void testAcceptEither_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingConsumer[] rs = new FailingConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        f.complete(v1);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertValue(v1);

        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);

        checkCompletedWithWrappedCFException(h4);
        assertTrue(Objects.equals(v1, rs[4].value) ||
                   Objects.equals(v2, rs[4].value));
        checkCompletedWithWrappedCFException(h5);
        assertTrue(Objects.equals(v1, rs[5].value) ||
                   Objects.equals(v2, rs[5].value));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterEither result completes normally after normal completion
     * of either source
     */
    @Test
    public void testRunAfterEither_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.complete(v1);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        rs[0].assertInvoked();
        rs[1].assertInvoked();
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        rs[2].assertInvoked();
        rs[3].assertInvoked();

        g.complete(v2);

        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        checkCompletedNormally(h4, null);
        checkCompletedNormally(h5, null);
        for (int i = 0; i < 6; i++) rs[i].assertInvoked();
    }}

    /**
     * runAfterEither result completes exceptionally after exceptional
     * completion of either source
     */
    @Test
    public void testRunAfterEither_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        assertTrue(f.completeExceptionally(ex));
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);

        assertTrue(g.complete(v1));

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h4, ex);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h5, ex);
            rs[5].assertNotInvoked();
        }

        checkCompletedExceptionally(f, ex);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    @Test
    public void testRunAfterEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        assertTrue( fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        assertTrue(!fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(null, h0.join());
            rs[0].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h0, ex);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(null, h1.join());
            rs[1].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h1, ex);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(null, h2.join());
            rs[2].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h2, ex);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(null, h3.join());
            rs[3].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h3, ex);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCompletedExceptionally(g, ex);
    }}

    /**
     * runAfterEither result completes exceptionally if either source cancelled
     */
    @Test
    public void testRunAfterEither_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.cancel(mayInterruptIfRunning);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);

        assertTrue(g.complete(v1));

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h4);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h5);
            rs[5].assertNotInvoked();
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    /**
     * runAfterEither result completes exceptionally if action does
     */
    @Test
    public void testRunAfterEither_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable[] rs = new FailingRunnable[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingRunnable(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        assertTrue(f.complete(v1));
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertInvoked();
        assertTrue(g.complete(v2));
        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);
        checkCompletedWithWrappedCFException(h4);
        checkCompletedWithWrappedCFException(h5);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        for (int i = 0; i < 6; i++) rs[i].assertInvoked();
    }}

    /**
     * thenCompose result completes normally after normal completion of source
     */
    @Test
    public void testThenCompose_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFutureInc r = new CompletableFutureInc(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        r.assertValue(v1);
    }}

    /**
     * thenCompose result completes exceptionally after exceptional
     * completion of source
     */
    @Test
    public void testThenCompose_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final CFException ex = new CFException();
        final CompletableFutureInc r = new CompletableFutureInc(m);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        r.assertNotInvoked();
    }}

    /**
     * thenCompose result completes exceptionally if action does
     */
    @Test
    public void testThenCompose_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingCompletableFutureFunction r
            = new FailingCompletableFutureFunction(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedWithWrappedCFException(g);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenCompose result completes exceptionally if source cancelled
     */
    @Test
    public void testThenCompose_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFutureInc r = new CompletableFutureInc(m);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
    }}

    /**
     * thenCompose result completes exceptionally if the result of the action does
     */
    @Test
    public void testThenCompose_actionReturnsFailingFuture() {
        for (ExecutionMode m : ExecutionMode.values())
        for (int order = 0; order < 6; order++)
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CompletableFuture<Integer> h;
        // Test all permutations of orders
        switch (order) {
        case 0:
            assertTrue(f.complete(v1));
            assertTrue(g.completeExceptionally(ex));
            h = m.thenCompose(f, (x -> g));
            break;
        case 1:
            assertTrue(f.complete(v1));
            h = m.thenCompose(f, (x -> g));
            assertTrue(g.completeExceptionally(ex));
            break;
        case 2:
            assertTrue(g.completeExceptionally(ex));
            assertTrue(f.complete(v1));
            h = m.thenCompose(f, (x -> g));
            break;
        case 3:
            assertTrue(g.completeExceptionally(ex));
            h = m.thenCompose(f, (x -> g));
            assertTrue(f.complete(v1));
            break;
        case 4:
            h = m.thenCompose(f, (x -> g));
            assertTrue(f.complete(v1));
            assertTrue(g.completeExceptionally(ex));
            break;
        case 5:
            h = m.thenCompose(f, (x -> g));
            assertTrue(f.complete(v1));
            assertTrue(g.completeExceptionally(ex));
            break;
        default: throw new AssertionError();
        }

        checkCompletedExceptionally(g, ex);
        checkCompletedWithWrappedException(h, ex);
        checkCompletedNormally(f, v1);
    }}

    // other static methods

    /**
     * allOf(no component futures) returns a future completed normally
     * with the value null
     */
    @Test
    public void testAllOf_empty() throws Exception {
        CompletableFuture<Void> f = CompletableFuture.allOf();
        checkCompletedNormally(f, null);
    }

    /**
     * allOf returns a future completed normally with the value null
     * when all components complete normally
     */
    @Test
    public void testAllOf_normal() throws Exception {
        for (int k = 1; k < 10; k++) {
            CompletableFuture<Integer>[] fs
                = (CompletableFuture<Integer>[]) new CompletableFuture[k];
            for (int i = 0; i < k; i++)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Void> f = CompletableFuture.allOf(fs);
            for (int i = 0; i < k; i++) {
                checkIncomplete(f);
                checkIncomplete(CompletableFuture.allOf(fs));
                fs[i].complete(one);
            }
            checkCompletedNormally(f, null);
            checkCompletedNormally(CompletableFuture.allOf(fs), null);
        }
    }

    @Test
    public void testAllOf_backwards() throws Exception {
        for (int k = 1; k < 10; k++) {
            CompletableFuture<Integer>[] fs
                = (CompletableFuture<Integer>[]) new CompletableFuture[k];
            for (int i = 0; i < k; i++)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Void> f = CompletableFuture.allOf(fs);
            for (int i = k - 1; i >= 0; i--) {
                checkIncomplete(f);
                checkIncomplete(CompletableFuture.allOf(fs));
                fs[i].complete(one);
            }
            checkCompletedNormally(f, null);
            checkCompletedNormally(CompletableFuture.allOf(fs), null);
        }
    }

    @Test
    public void testAllOf_exceptional() throws Exception {
        for (int k = 1; k < 10; k++) {
            CompletableFuture<Integer>[] fs
                = (CompletableFuture<Integer>[]) new CompletableFuture[k];
            CFException ex = new CFException();
            for (int i = 0; i < k; i++)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Void> f = CompletableFuture.allOf(fs);
            for (int i = 0; i < k; i++) {
                checkIncomplete(f);
                checkIncomplete(CompletableFuture.allOf(fs));
                if (i != k / 2) {
                    fs[i].complete(i);
                    checkCompletedNormally(fs[i], i);
                } else {
                    fs[i].completeExceptionally(ex);
                    checkCompletedExceptionally(fs[i], ex);
                }
            }
            checkCompletedWithWrappedException(f, ex);
            checkCompletedWithWrappedException(CompletableFuture.allOf(fs), ex);
        }
    }

    /**
     * anyOf(no component futures) returns an incomplete future
     */
    @Test
    public void testAnyOf_empty() throws Exception {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Object> f = CompletableFuture.anyOf();
        checkIncomplete(f);

        f.complete(v1);
        checkCompletedNormally(f, v1);
    }}

    /**
     * anyOf returns a future completed normally with a value when
     * a component future does
     */
    @Test
    public void testAnyOf_normal() throws Exception {
        for (int k = 0; k < 10; k++) {
            CompletableFuture[] fs = new CompletableFuture[k];
            for (int i = 0; i < k; i++)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = 0; i < k; i++) {
                fs[i].complete(i);
                checkCompletedNormally(f, 0);
                int x = (int) CompletableFuture.anyOf(fs).join();
                assertTrue(0 <= x && x <= i);
            }
        }
    }
    @Test
    public void testAnyOf_normal_backwards() throws Exception {
        for (int k = 0; k < 10; k++) {
            CompletableFuture[] fs = new CompletableFuture[k];
            for (int i = 0; i < k; i++)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = k - 1; i >= 0; i--) {
                fs[i].complete(i);
                checkCompletedNormally(f, k - 1);
                int x = (int) CompletableFuture.anyOf(fs).join();
                assertTrue(i <= x && x <= k - 1);
            }
        }
    }

    /**
     * anyOf result completes exceptionally when any component does.
     */
    @Test
    public void testAnyOf_exceptional() throws Exception {
        for (int k = 0; k < 10; k++) {
            CompletableFuture[] fs = new CompletableFuture[k];
            CFException[] exs = new CFException[k];
            for (int i = 0; i < k; i++) {
                fs[i] = new CompletableFuture<>();
                exs[i] = new CFException();
            }
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = 0; i < k; i++) {
                fs[i].completeExceptionally(exs[i]);
                checkCompletedWithWrappedException(f, exs[0]);
                checkCompletedWithWrappedCFException(CompletableFuture.anyOf(fs));
            }
        }
    }

    @Test
    public void testAnyOf_exceptional_backwards() throws Exception {
        for (int k = 0; k < 10; k++) {
            CompletableFuture[] fs = new CompletableFuture[k];
            CFException[] exs = new CFException[k];
            for (int i = 0; i < k; i++) {
                fs[i] = new CompletableFuture<>();
                exs[i] = new CFException();
            }
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = k - 1; i >= 0; i--) {
                fs[i].completeExceptionally(exs[i]);
                checkCompletedWithWrappedException(f, exs[k - 1]);
                checkCompletedWithWrappedCFException(CompletableFuture.anyOf(fs));
            }
        }
    }

    /**
     * Completion methods throw NullPointerException with null arguments
     */
    @Test
    public void testNPE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = new CompletableFuture<>();
        CompletableFuture<Integer> nullFuture = (CompletableFuture<Integer>)null;
        ThreadExecutor exec = new ThreadExecutor();

        Runnable[] throwingActions = {
            () -> CompletableFuture.supplyAsync(null),
            () -> CompletableFuture.supplyAsync(null, exec),
            () -> CompletableFuture.supplyAsync(new IntegerSupplier(ExecutionMode.SYNC, 42), null),

            () -> CompletableFuture.runAsync(null),
            () -> CompletableFuture.runAsync(null, exec),
            () -> CompletableFuture.runAsync(() -> {}, null),

            () -> f.completeExceptionally(null),

            () -> f.thenApply(null),
            () -> f.thenApplyAsync(null),
            () -> f.thenApplyAsync((x) -> x, null),
            () -> f.thenApplyAsync(null, exec),

            () -> f.thenAccept(null),
            () -> f.thenAcceptAsync(null),
            () -> f.thenAcceptAsync((x) -> {} , null),
            () -> f.thenAcceptAsync(null, exec),

            () -> f.thenRun(null),
            () -> f.thenRunAsync(null),
            () -> f.thenRunAsync(() -> {} , null),
            () -> f.thenRunAsync(null, exec),

            () -> f.thenCombine(g, null),
            () -> f.thenCombineAsync(g, null),
            () -> f.thenCombineAsync(g, null, exec),
            () -> f.thenCombine(nullFuture, (x, y) -> x),
            () -> f.thenCombineAsync(nullFuture, (x, y) -> x),
            () -> f.thenCombineAsync(nullFuture, (x, y) -> x, exec),
            () -> f.thenCombineAsync(g, (x, y) -> x, null),

            () -> f.thenAcceptBoth(g, null),
            () -> f.thenAcceptBothAsync(g, null),
            () -> f.thenAcceptBothAsync(g, null, exec),
            () -> f.thenAcceptBoth(nullFuture, (x, y) -> {}),
            () -> f.thenAcceptBothAsync(nullFuture, (x, y) -> {}),
            () -> f.thenAcceptBothAsync(nullFuture, (x, y) -> {}, exec),
            () -> f.thenAcceptBothAsync(g, (x, y) -> {}, null),

            () -> f.runAfterBoth(g, null),
            () -> f.runAfterBothAsync(g, null),
            () -> f.runAfterBothAsync(g, null, exec),
            () -> f.runAfterBoth(nullFuture, () -> {}),
            () -> f.runAfterBothAsync(nullFuture, () -> {}),
            () -> f.runAfterBothAsync(nullFuture, () -> {}, exec),
            () -> f.runAfterBothAsync(g, () -> {}, null),

            () -> f.applyToEither(g, null),
            () -> f.applyToEitherAsync(g, null),
            () -> f.applyToEitherAsync(g, null, exec),
            () -> f.applyToEither(nullFuture, (x) -> x),
            () -> f.applyToEitherAsync(nullFuture, (x) -> x),
            () -> f.applyToEitherAsync(nullFuture, (x) -> x, exec),
            () -> f.applyToEitherAsync(g, (x) -> x, null),

            () -> f.acceptEither(g, null),
            () -> f.acceptEitherAsync(g, null),
            () -> f.acceptEitherAsync(g, null, exec),
            () -> f.acceptEither(nullFuture, (x) -> {}),
            () -> f.acceptEitherAsync(nullFuture, (x) -> {}),
            () -> f.acceptEitherAsync(nullFuture, (x) -> {}, exec),
            () -> f.acceptEitherAsync(g, (x) -> {}, null),

            () -> f.runAfterEither(g, null),
            () -> f.runAfterEitherAsync(g, null),
            () -> f.runAfterEitherAsync(g, null, exec),
            () -> f.runAfterEither(nullFuture, () -> {}),
            () -> f.runAfterEitherAsync(nullFuture, () -> {}),
            () -> f.runAfterEitherAsync(nullFuture, () -> {}, exec),
            () -> f.runAfterEitherAsync(g, () -> {}, null),

            () -> f.thenCompose(null),
            () -> f.thenComposeAsync(null),
            () -> f.thenComposeAsync(new CompletableFutureInc(ExecutionMode.EXECUTOR), null),
            () -> f.thenComposeAsync(null, exec),

            () -> f.exceptionally(null),

            () -> f.handle(null),

            () -> CompletableFuture.allOf((CompletableFuture<?>)null),
            () -> CompletableFuture.allOf((CompletableFuture<?>[])null),
            () -> CompletableFuture.allOf(f, null),
            () -> CompletableFuture.allOf(null, f),

            () -> CompletableFuture.anyOf((CompletableFuture<?>)null),
            () -> CompletableFuture.anyOf((CompletableFuture<?>[])null),
            () -> CompletableFuture.anyOf(f, null),
            () -> CompletableFuture.anyOf(null, f),

            () -> f.obtrudeException(null),

            () -> CompletableFuture.delayedExecutor(1L, SECONDS, null),
            () -> CompletableFuture.delayedExecutor(1L, null, new ThreadExecutor()),
            () -> CompletableFuture.delayedExecutor(1L, null),

            () -> f.orTimeout(1L, null),
            () -> f.completeOnTimeout(42, 1L, null),

            () -> CompletableFuture.failedFuture(null),
            () -> CompletableFuture.failedStage(null),
        };

        assertThrows(NullPointerException.class, throwingActions);
        assertEquals(0, exec.count.get());
    }

    /**
     * toCompletableFuture returns this CompletableFuture.
     */
    @Test
    public void testToCompletableFuture() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        assertSame(f, f.toCompletableFuture());
    }

    // jdk9

    /**
     * newIncompleteFuture returns an incomplete CompletableFuture
     */
    @Test
    public void testNewIncompleteFuture() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.newIncompleteFuture();
        checkIncomplete(f);
        checkIncomplete(g);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkIncomplete(g);
        g.complete(v1);
        checkCompletedNormally(g, v1);
        assertSame(g.getClass(), CompletableFuture.class);
    }}

    /**
     * completedStage returns a completed CompletionStage
     */
    @Test
    public void testCompletedStage() {
        AtomicInteger x = new AtomicInteger(0);
        AtomicReference<Throwable> r = new AtomicReference<Throwable>();
        CompletionStage<Integer> f = CompletableFuture.completedStage(1);
        f.whenComplete((v, e) -> {if (e != null) r.set(e); else x.set(v);});
        assertEquals(x.get(), 1);
        assertNull(r.get());
    }

    /**
     * failedFuture returns a CompletableFuture completed
     * exceptionally with the given Exception
     */
    @Test
    public void testFailedFuture() {
        CFException ex = new CFException();
        CompletableFuture<Integer> f = CompletableFuture.failedFuture(ex);
        checkCompletedExceptionally(f, ex);
    }

    /**
     * failedFuture(null) throws NPE
     */
    @Test
    public void testFailedFuture_null() {
        try {
            CompletableFuture<Integer> f = CompletableFuture.failedFuture(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * copy returns a CompletableFuture that is completed normally,
     * with the same value, when source is.
     */
    @Test
    public void testCopy() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.copy();
        checkIncomplete(f);
        checkIncomplete(g);
        f.complete(1);
        checkCompletedNormally(f, 1);
        checkCompletedNormally(g, 1);
    }

    /**
     * copy returns a CompletableFuture that is completed exceptionally
     * when source is.
     */
    @Test
    public void testCopy2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.copy();
        checkIncomplete(f);
        checkIncomplete(g);
        CFException ex = new CFException();
        f.completeExceptionally(ex);
        checkCompletedExceptionally(f, ex);
        checkCompletedWithWrappedException(g, ex);
    }

    /**
     * minimalCompletionStage returns a CompletableFuture that is
     * completed normally, with the same value, when source is.
     */
    @Test
    public void testMinimalCompletionStage() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletionStage<Integer> g = f.minimalCompletionStage();
        AtomicInteger x = new AtomicInteger(0);
        AtomicReference<Throwable> r = new AtomicReference<Throwable>();
        checkIncomplete(f);
        g.whenComplete((v, e) -> {if (e != null) r.set(e); else x.set(v);});
        f.complete(1);
        checkCompletedNormally(f, 1);
        assertEquals(x.get(), 1);
        assertNull(r.get());
    }

    /**
     * minimalCompletionStage returns a CompletableFuture that is
     * completed exceptionally when source is.
     */
    @Test
    public void testMinimalCompletionStage2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletionStage<Integer> g = f.minimalCompletionStage();
        AtomicInteger x = new AtomicInteger(0);
        AtomicReference<Throwable> r = new AtomicReference<Throwable>();
        g.whenComplete((v, e) -> {if (e != null) r.set(e); else x.set(v);});
        checkIncomplete(f);
        CFException ex = new CFException();
        f.completeExceptionally(ex);
        checkCompletedExceptionally(f, ex);
        assertEquals(x.get(), 0);
        assertEquals(r.get().getCause(), ex);
    }

    /**
     * failedStage returns a CompletionStage completed
     * exceptionally with the given Exception
     */
    @Test
    public void testFailedStage() {
        CFException ex = new CFException();
        CompletionStage<Integer> f = CompletableFuture.failedStage(ex);
        AtomicInteger x = new AtomicInteger(0);
        AtomicReference<Throwable> r = new AtomicReference<Throwable>();
        f.whenComplete((v, e) -> {if (e != null) r.set(e); else x.set(v);});
        assertEquals(x.get(), 0);
        assertEquals(r.get(), ex);
    }

    /**
     * completeAsync completes with value of given supplier
     */
    @Test
    public void testCompleteAsync() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        f.completeAsync(() -> v1);
        f.join();
        checkCompletedNormally(f, v1);
    }}

    /**
     * completeAsync completes exceptionally if given supplier throws
     */
    @Test
    public void testCompleteAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CFException ex = new CFException();
        f.completeAsync(() -> {if (true) throw ex; return 1;});
        try {
            f.join();
            shouldThrow();
        } catch (CompletionException success) {}
        checkCompletedWithWrappedException(f, ex);
    }

    /**
     * completeAsync with given executor completes with value of given supplier
     */
    @Test
    public void testCompleteAsync3() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        ThreadExecutor executor = new ThreadExecutor();
        f.completeAsync(() -> v1, executor);
        assertSame(v1, f.join());
        checkCompletedNormally(f, v1);
        assertEquals(1, executor.count.get());
    }}

    /**
     * completeAsync with given executor completes exceptionally if
     * given supplier throws
     */
    @Test
    public void testCompleteAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CFException ex = new CFException();
        ThreadExecutor executor = new ThreadExecutor();
        f.completeAsync(() -> {if (true) throw ex; return 1;}, executor);
        try {
            f.join();
            shouldThrow();
        } catch (CompletionException success) {}
        checkCompletedWithWrappedException(f, ex);
        assertEquals(1, executor.count.get());
    }

    /**
     * orTimeout completes with TimeoutException if not complete
     */
    @Test
    public void testOrTimeout_timesOut() {
        long timeoutMillis = timeoutMillis();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        long startTime = System.nanoTime();
        f.orTimeout(timeoutMillis, MILLISECONDS);
        checkCompletedWithTimeoutException(f);
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
    }

    /**
     * orTimeout completes normally if completed before timeout
     */
    @Test
    public void testOrTimeout_completed() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = new CompletableFuture<>();
        long startTime = System.nanoTime();
        f.complete(v1);
        f.orTimeout(LONG_DELAY_MS, MILLISECONDS);
        g.orTimeout(LONG_DELAY_MS, MILLISECONDS);
        g.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v1);
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS / 2);
    }}

    /**
     * completeOnTimeout completes with given value if not complete
     */
    @Test
    public void testCompleteOnTimeout_timesOut() {
        testInParallel(() -> testCompleteOnTimeout_timesOut(42),
                       () -> testCompleteOnTimeout_timesOut(null));
    }

    public void testCompleteOnTimeout_timesOut(Integer v) {
        long timeoutMillis = timeoutMillis();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        long startTime = System.nanoTime();
        f.completeOnTimeout(v, timeoutMillis, MILLISECONDS);
        assertSame(v, f.join());
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        f.complete(99);         // should have no effect
        checkCompletedNormally(f, v);
    }

    /**
     * completeOnTimeout has no effect if completed within timeout
     */
    @Test
    public void testCompleteOnTimeout_completed() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = new CompletableFuture<>();
        long startTime = System.nanoTime();
        f.complete(v1);
        f.completeOnTimeout(-1, LONG_DELAY_MS, MILLISECONDS);
        g.completeOnTimeout(-1, LONG_DELAY_MS, MILLISECONDS);
        g.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v1);
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS / 2);
    }}

    /**
     * delayedExecutor returns an executor that delays submission
     */
    @Test
    public void testDelayedExecutor() {
        testInParallel(() -> checkDelayedExecutor(null, null),
                       () -> checkDelayedExecutor(null, 1),
                       () -> checkDelayedExecutor(new ThreadExecutor(), 1),
                       () -> checkDelayedExecutor(new ThreadExecutor(), 1));
    }

    public void checkDelayedExecutor(Executor executor, Integer v) throws Exception {
        long timeoutMillis = timeoutMillis();
        // Use an "unreasonably long" long timeout to catch lingering threads
        long longTimeoutMillis = 1000 * 60 * 60 * 24;
        final Executor delayer, longDelayer;
        if (executor == null) {
            delayer = CompletableFuture.delayedExecutor(timeoutMillis, MILLISECONDS);
            longDelayer = CompletableFuture.delayedExecutor(longTimeoutMillis, MILLISECONDS);
        } else {
            delayer = CompletableFuture.delayedExecutor(timeoutMillis, MILLISECONDS, executor);
            longDelayer = CompletableFuture.delayedExecutor(longTimeoutMillis, MILLISECONDS, executor);
        }
        long startTime = System.nanoTime();
        CompletableFuture<Integer> f =
            CompletableFuture.supplyAsync(() -> v, delayer);
        CompletableFuture<Integer> g =
            CompletableFuture.supplyAsync(() -> v, longDelayer);

        assertNull(g.getNow(null));

        assertSame(v, f.get(LONG_DELAY_MS, MILLISECONDS));
        long millisElapsed = millisElapsedSince(startTime);
        assertTrue(millisElapsed >= timeoutMillis);
        assertTrue(millisElapsed < LONG_DELAY_MS / 2);

        checkCompletedNormally(f, v);

        checkIncomplete(g);
        assertTrue(g.cancel(true));
    }

    //--- tests of implementation details; not part of official tck ---

    static class Monad {
        static class ZeroException extends RuntimeException {
            public ZeroException() { super("monadic zero"); }
        }
        // "return", "unit"
        static <T> CompletableFuture<T> unit(T value) {
            return completedFuture(value);
        }
        // monadic zero ?
        static <T> CompletableFuture<T> zero() {
            return failedFuture(new ZeroException());
        }
        // >=>
        static <T,U,V> Function<T, CompletableFuture<V>> compose
            (Function<T, CompletableFuture<U>> f,
             Function<U, CompletableFuture<V>> g) {
            return (x) -> f.apply(x).thenCompose(g);
        }

        static void assertZero(CompletableFuture<?> f) {
            try {
                f.getNow(null);
                throw new AssertionFailedError("should throw");
            } catch (CompletionException success) {
                assertTrue(success.getCause() instanceof ZeroException);
            }
        }

        static <T> void assertFutureEquals(CompletableFuture<T> f,
                                           CompletableFuture<T> g) {
            T fval = null, gval = null;
            Throwable fex = null, gex = null;

            try { fval = f.get(); }
            catch (ExecutionException ex) { fex = ex.getCause(); }
            catch (Throwable ex) { fex = ex; }

            try { gval = g.get(); }
            catch (ExecutionException ex) { gex = ex.getCause(); }
            catch (Throwable ex) { gex = ex; }

            if (fex != null || gex != null)
                assertSame(fex.getClass(), gex.getClass());
            else
                assertEquals(fval, gval);
        }

        static class PlusFuture<T> extends CompletableFuture<T> {
            AtomicReference<Throwable> firstFailure = new AtomicReference<>(null);
        }

        /** Implements "monadic plus". */
        static <T> CompletableFuture<T> plus(CompletableFuture<? extends T> f,
                                             CompletableFuture<? extends T> g) {
            PlusFuture<T> plus = new PlusFuture<T>();
            BiConsumer<T, Throwable> action = (T result, Throwable ex) -> {
                try {
                    if (ex == null) {
                        if (plus.complete(result))
                            if (plus.firstFailure.get() != null)
                                plus.firstFailure.set(null);
                    }
                    else if (plus.firstFailure.compareAndSet(null, ex)) {
                        if (plus.isDone())
                            plus.firstFailure.set(null);
                    }
                    else {
                        // first failure has precedence
                        Throwable first = plus.firstFailure.getAndSet(null);

                        // may fail with "Self-suppression not permitted"
                        try { first.addSuppressed(ex); }
                        catch (Exception ignored) {}

                        plus.completeExceptionally(first);
                    }
                } catch (Throwable unexpected) {
                    plus.completeExceptionally(unexpected);
                }
            };
            f.whenComplete(action);
            g.whenComplete(action);
            return plus;
        }
    }

    /**
     * CompletableFuture is an additive monad - sort of.
     * https://en.wikipedia.org/wiki/Monad_(functional_programming)#Additive_monads
     */
    @Test
    public void testAdditiveMonad() throws Throwable {
        Function<Long, CompletableFuture<Long>> unit = Monad::unit;
        CompletableFuture<Long> zero = Monad.zero();

        // Some mutually non-commutative functions
        Function<Long, CompletableFuture<Long>> triple
            = (x) -> Monad.unit(3 * x);
        Function<Long, CompletableFuture<Long>> inc
            = (x) -> Monad.unit(x + 1);

        // unit is a right identity: m >>= unit === m
        Monad.assertFutureEquals(inc.apply(5L).thenCompose(unit),
                                 inc.apply(5L));
        // unit is a left identity: (unit x) >>= f === f x
        Monad.assertFutureEquals(unit.apply(5L).thenCompose(inc),
                                 inc.apply(5L));

        // associativity: (m >>= f) >>= g === m >>= ( \x -> (f x >>= g) )
        Monad.assertFutureEquals(
            unit.apply(5L).thenCompose(inc).thenCompose(triple),
            unit.apply(5L).thenCompose((x) -> inc.apply(x).thenCompose(triple)));

        // The case for CompletableFuture as an additive monad is weaker...

        // zero is a monadic zero
        Monad.assertZero(zero);

        // left zero: zero >>= f === zero
        Monad.assertZero(zero.thenCompose(inc));
        // right zero: f >>= (\x -> zero) === zero
        Monad.assertZero(inc.apply(5L).thenCompose((x) -> zero));

        // f plus zero === f
        Monad.assertFutureEquals(Monad.unit(5L),
                                 Monad.plus(Monad.unit(5L), zero));
        // zero plus f === f
        Monad.assertFutureEquals(Monad.unit(5L),
                                 Monad.plus(zero, Monad.unit(5L)));
        // zero plus zero === zero
        Monad.assertZero(Monad.plus(zero, zero));
        {
            CompletableFuture<Long> f = Monad.plus(Monad.unit(5L),
                                                   Monad.unit(8L));
            // non-determinism
            assertTrue(f.get() == 5L || f.get() == 8L);
        }

        CompletableFuture<Long> godot = new CompletableFuture<>();
        // f plus godot === f (doesn't wait for godot)
        Monad.assertFutureEquals(Monad.unit(5L),
                                 Monad.plus(Monad.unit(5L), godot));
        // godot plus f === f (doesn't wait for godot)
        Monad.assertFutureEquals(Monad.unit(5L),
                                 Monad.plus(godot, Monad.unit(5L)));
    }

//     static <U> U join(CompletionStage<U> stage) {
//         CompletableFuture<U> f = new CompletableFuture<>();
//         stage.whenComplete((v, ex) -> {
//             if (ex != null) f.completeExceptionally(ex); else f.complete(v);
//         });
//         return f.join();
//     }

//     static <U> boolean isDone(CompletionStage<U> stage) {
//         CompletableFuture<U> f = new CompletableFuture<>();
//         stage.whenComplete((v, ex) -> {
//             if (ex != null) f.completeExceptionally(ex); else f.complete(v);
//         });
//         return f.isDone();
//     }

//     static <U> U join2(CompletionStage<U> stage) {
//         return stage.toCompletableFuture().copy().join();
//     }

//     static <U> boolean isDone2(CompletionStage<U> stage) {
//         return stage.toCompletableFuture().copy().isDone();
//     }

}
