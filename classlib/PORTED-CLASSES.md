# Classes ported from jsr166

Most of TeaVM's class library is written for TeaVM: short, and shaped for its cooperative threads (see
konsoletyper/teavm#710 on "async pollution"). The classes below are ports of the public-domain jsr166 sources
(https://gee.cs.oswego.edu/dl/jsr166/), the reference implementation of `java.util.concurrent`. They keep jsr166's
code and formatting. So each file starts with a `// CHECKSTYLE.OFF:` comment for the checks that jsr166's style
does not meet, and checkstyle passes with no violations.

The port is mechanical:

- the package moves to `org.teavm.classlib.java.util.concurrent[.locks]`, and each ported type gets the `T` prefix;
- each `VarHandle` becomes plain-field helper methods. TeaVM runs one green thread at a time and switches threads
  only at blocking calls, so a plain read-compare-write is atomic;
- memory fences are removed;
- `Thread.onSpinWait()` and `ForkJoinPool.managedBlock` become calls to `org.teavm.classlib.EmulSupport`.

Hand edits beyond that are marked `TeaVM:` in the code.

Each file has the Apache-2.0 header of this port, then jsr166's public-domain notice
(https://creativecommons.org/publicdomain/zero/1.0/).

| TeaVM class | JDK class | Origin |
| --- | --- | --- |
| `TAbstractOwnableSynchronizer` | `java.util.concurrent.locks.AbstractOwnableSynchronizer` | jsr166 |
| `TAbstractQueuedSynchronizer` | `java.util.concurrent.locks.AbstractQueuedSynchronizer` | jsr166 |
| `TCondition` | `java.util.concurrent.locks.Condition` | jsr166 |
| `TLock` | `java.util.concurrent.locks.Lock` | jsr166 |
| `TReadWriteLock` | `java.util.concurrent.locks.ReadWriteLock` | jsr166 |
| `TReentrantLock` | `java.util.concurrent.locks.ReentrantLock` | jsr166, with a preemption point (`Thread.yield()`) in `lock` and `lockInterruptibly` (marked `TeaVM:`) |
| `TReentrantReadWriteLock` | `java.util.concurrent.locks.ReentrantReadWriteLock` | jsr166 |
| `TRejectedExecutionException` | `java.util.concurrent.RejectedExecutionException` | jsr166 |
| `TCountDownLatch` | `java.util.concurrent.CountDownLatch` | jsr166 |
| `TTimeoutException` | `java.util.concurrent.TimeoutException` | jsr166 |
| `TLockSupport` | `java.util.concurrent.locks.LockSupport` | Not a port: written for TeaVM's green threads. jsr166's version parks through `Unsafe`. |

`org.teavm.classlib.EmulSupport` holds the helpers that the ports call in place of JDK internals. It is written for
TeaVM, not ported.
