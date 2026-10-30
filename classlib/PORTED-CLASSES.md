# Classes ported from jsr166

Most of TeaVM's class library is written for TeaVM: short, and shaped for its cooperative threads (see
konsoletyper/teavm#710 on "async pollution"). The classes below are ports of the public-domain jsr166 sources
(https://gee.cs.oswego.edu/dl/jsr166/), the reference implementation of `java.util.concurrent`. They keep jsr166's
code and formatting. So each file starts with a `// CHECKSTYLE.OFF:` comment for the checks that jsr166's style
does not meet, and checkstyle passes with no violations.

The port is mechanical:

- the package moves to `org.teavm.classlib.java.util.concurrent[.locks]`, and each ported type gets the `T` prefix;
- each `Unsafe` or `VarHandle` compare-and-set or ordered access becomes a plain-field helper method, and the
  `MethodHandles.lookup()` code that found the `VarHandle`s is removed. TeaVM runs one green thread
  at a time and switches threads only at blocking calls, so a plain read-compare-write is atomic;
- `@ReservedStackAccess` annotations are removed;
- `ForkJoinPool.managedBlock` becomes a call to `org.teavm.classlib.EmulSupport`.

Additional TeaVM adaptations are documented below and in the source.

Each file has the Apache-2.0 header of this port, then jsr166's public-domain notice
(https://creativecommons.org/publicdomain/zero/1.0/).

| TeaVM class | JDK class | Origin |
| --- | --- | --- |
| `TAbstractOwnableSynchronizer` | `java.util.concurrent.locks.AbstractOwnableSynchronizer` | jsr166 |
| `TAbstractQueuedSynchronizer` | `java.util.concurrent.locks.AbstractQueuedSynchronizer` | jsr166 |
| `TCondition` | `java.util.concurrent.locks.Condition` | jsr166 |
| `TLock` | `java.util.concurrent.locks.Lock` | jsr166 |
| `TReadWriteLock` | `java.util.concurrent.locks.ReadWriteLock` | jsr166 |
| `TReentrantLock` | `java.util.concurrent.locks.ReentrantLock` | jsr166 |
| `TReentrantReadWriteLock` | `java.util.concurrent.locks.ReentrantReadWriteLock` | jsr166 |
| `TRejectedExecutionException` | `java.util.concurrent.RejectedExecutionException` | jsr166 |
| `TCountDownLatch` | `java.util.concurrent.CountDownLatch` | jsr166 |
| `TTimeoutException` | `java.util.concurrent.TimeoutException` | jsr166 |
| `TConcurrentLinkedQueue` | `java.util.concurrent.ConcurrentLinkedQueue` | jsr166 |
| `TConcurrentLinkedDeque` | `java.util.concurrent.ConcurrentLinkedDeque` | jsr166 |
| `TSynchronousQueue` | `java.util.concurrent.SynchronousQueue` | jsr166 |
| `THelpers` | `java.util.concurrent.Helpers` (package-private) | jsr166 |
| `TLockSupport` | `java.util.concurrent.locks.LockSupport` | Not a port: written for TeaVM's green threads. jsr166's version parks through `Unsafe`. Only `park` suspends, so `unpark`, and with it `unlock`, `signal` and `countDown`, never makes its caller async. |

`org.teavm.classlib.EmulSupport` holds the helpers that the ports call in place of JDK internals. It is written for
TeaVM, not ported.

## Executor and future ports

| `TCompletableFuture` | `java.util.concurrent.CompletableFuture` | jsr166 |
| `TExecutors` | `java.util.concurrent.Executors` | jsr166 |
| `TFutureTask` | `java.util.concurrent.FutureTask` | jsr166 |
| `TScheduledThreadPoolExecutor` | `java.util.concurrent.ScheduledThreadPoolExecutor` | jsr166 |
| `TThreadPoolExecutor` | `java.util.concurrent.ThreadPoolExecutor` | jsr166 |

Executor support types are also ported from the public-domain jsr166 sources; no host-JDK bytecode is needed.

Executor ports also remove ForkJoinPool-only paths, use one green thread per default asynchronous
CompletableFuture task. On JavaScript and Wasm GC, single-thread executor wrappers use Cleaner
instead of finalizers. Each wrapper retains a JavaScript lifetime token because a Java object's
interop wrapper can be collected independently on Wasm GC. Guarded calls keep that token live.
Collection starts an isolated daemon thread that calls orderly shutdown;
explicit shutdown unregisters cleanup. Active delegated calls delay cleanup until they finish,
including calls that suspend. Pure state queries do not need this protection. Other executor
factories do not gain automatic shutdown. Automatic shutdown after collection is not implemented
on C; callers must shut down their executors explicitly. Disabled C-only regressions document this gap.
Hand edits are marked "TeaVM:" in the source. The default factory captures its
creating thread's group. The Java SE 21 privilegedThreadFactory, privilegedCallable and
privilegedCallableUsingCurrentClassLoader methods are omitted because TeaVM cannot capture and enforce
the required access-control context. No new security infrastructure is introduced.
Exception messages and empty VarHandle initialization handlers are omitted. FutureTask.state does not
spin on COMPLETING: both transitions to a terminal state are atomic between cooperative suspension points.
CompletableFuture removes unused timeout tasks after exceptional completion and cancellation as well as success.

Additional executor APIs ported from jsr166: `AbstractExecutorService`, `CompletionException`, `CompletionStage`, `Delayed`, `ExecutorService`, `Future`, `RejectedExecutionHandler`, `RunnableFuture`, `RunnableScheduledFuture`, `ScheduledExecutorService`, `ScheduledFuture`, `ThreadFactory`, `CompletionService`, `ExecutorCompletionService`, and `LinkedBlockingQueue`.

The key interfaces, `SecretKeySpec`, destruction interfaces, and missing security/thread exception types
are independently implemented against their public APIs. They do not copy OpenJDK implementation code.
