/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;
import org.teavm.runtime.Fiber;

public class TThread extends TObject implements TRunnable {
    // Intrusive links keep the main-thread initializer free of collection code and suspension points.
    private static TThread liveHead;
    private static TThread liveTail;
    private static TThread mainThread = new TThread("main", true);
    private static TThread currentThread = mainThread;
    private static int nextId = 1;
    private static UncaughtExceptionHandler defaultUncaughtExceptionHandler = new TDefaultUncaughtExceptionHandler();
    private UncaughtExceptionHandler uncaughtExceptionHandler;
    private long id;
    private int priority = Thread.NORM_PRIORITY;
    private boolean daemon;
    private long timeSliceStart;
    private int yieldCount;
    private final Object finishedLock = new Object();
    private boolean interruptedFlag;
    public TThreadInterruptHandler interruptHandler;
    public Object key;

    private String name;
    // As in the JDK, isAlive() is false before start().
    private boolean alive;
    private boolean hasStarted;
    private TThreadGroup group;
    private TThread livePrev;
    private TThread liveNext;
    /**
     * The InheritableThreadLocal values this thread takes from its creator, as pairs (instance, value), or null.
     * They are read when the thread is created, as in the JDK, and set when it starts: a ThreadLocal keys its
     * values by {@link #key}, which exists only then.
     */
    private Object[] inherited;
    // The context class loader once set (null is a valid value); until then, the system loader. A new thread
    // copies both from the thread that creates it, as in the JDK.
    private TClassLoader contextClassLoader;
    private boolean contextClassLoaderSet;
    TRunnable target;

    public TThread() {
        this(null, null, true);
    }

    /**
     * The main thread's constructor. The main thread runs from the start, and it has no creator, so it copies
     * nothing. It must not reach
     * the public constructor's inheritance snapshot: Thread's class initializer creates the main thread, and
     * currentThread() runs that initializer, so code reachable from here would make currentThread() suspendable.
     */
    private TThread(String name, @SuppressWarnings("unused") boolean mainThread) {
        this.name = name;
        id = nextId++;
        alive = true;
        hasStarted = true;
        addLive();
    }

    public TThread(String name) {
        this(null, name, false);
    }

    public TThread(TRunnable target) {
        this(target, null, true);
    }

    public TThread(TRunnable target, String name) {
        this(target, name, false);
    }

    private TThread(TRunnable target, String name, boolean defaultName) {
        this(target, name, defaultName, true);
    }

    private TThread(TRunnable target, String name, boolean defaultName, boolean inheritThreadLocals) {
        this.name = defaultName ? null : Objects.requireNonNull(name);
        this.target = target;
        id = nextId++;
        // As in the JDK, a new thread takes its creator's context class loader and inheritable thread locals.
        TThread creator = currentThread;
        if (creator != null) {
            group = creator.group;
            priority = creator.priority;
            daemon = creator.daemon;
            contextClassLoader = creator.contextClassLoader;
            contextClassLoaderSet = creator.contextClassLoaderSet;
            if (inheritThreadLocals) {
                inherited = TInheritableThreadLocal.snapshot();
            }
        }
        // Only public construction reaches the lazy group holder, never the main-thread initializer.
        priority = Math.min(priority, getThreadGroup().getMaxPriority());
    }

    public TThread(TThreadGroup group, TRunnable target) {
        this(target, null, true);
        if (group != null) {
            this.group = group;
            priority = Math.min(currentThread.priority, group.getMaxPriority());
        }
    }

    public TThread(TThreadGroup group, String name) {
        this(group, null, name);
    }

    public TThread(TThreadGroup group, TRunnable target, String name) {
        this(target, name, false);
        if (group != null) {
            this.group = group;
            priority = Math.min(currentThread.priority, group.getMaxPriority());
        }
    }

    public TThread(TThreadGroup group, TRunnable target, String name, long stackSize) {
        this(group, target, name);
    }

    public TThread(TThreadGroup group, TRunnable target, String name, long stackSize, boolean inheritThreadLocals) {
        this(target, name, false, inheritThreadLocals);
        if (group != null) {
            this.group = group;
            priority = Math.min(currentThread.priority, group.getMaxPriority());
        }
    }

    public final TThreadGroup getThreadGroup() {
        if (!alive && hasStarted) {
            return null;
        }
        return group != null ? group : ThreadGroupHolder.MAIN;
    }

    private static class ThreadGroupHolder {
        static final TThreadGroup MAIN = TThreadGroup.createMainGroup();
    }

    public void setContextClassLoader(TClassLoader loader) {
        contextClassLoader = loader;
        contextClassLoaderSet = true;
    }

    public static void onSpinWait() {
        // A spin loop waits for another thread; with green threads that thread runs only if this one yields.
        TThread current = currentThread();
        current.yieldCount = 0;
        switchContext(current);
    }

    public void start() {
        if (hasStarted) {
            throw new IllegalThreadStateException();
        }
        hasStarted = true;
        alive = true;
        addLive();
        if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
            boolean daemon = this.daemon;
            if (!daemon) {
                Fiber.userThreadCount++;
            }
            EventQueue.offer(() -> Fiber.start(this::runThread, daemon));
        } else {
            Platform.startThread(this::runThread);
        }
    }

    private void runThread() {
        try {
            setCurrentThread(TThread.this);
            key = new Object();
            applyInherited();
            TThread.this.run();
        } catch (Throwable t) {
            try {
                getUncaughtExceptionHandler().uncaughtException(this, t);
            } catch (Throwable ignored) {
                // An exception thrown by an uncaught-exception handler is ignored by the VM.
            }
        } finally {
            // Clear alive under finishedLock, before the notify, so join() sees both together.
            // Constructor-free instances, such as mocks, have no finishedLock.
            var lock = finishedLock;
            if (lock != null) {
                synchronized (lock) {
                    alive = false;
                    lock.notifyAll();
                }
            } else {
                alive = false;
            }
            removeLive();
            group = null;
            key = null;
            setCurrentThread(mainThread);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyInherited() {
        Object[] pairs = inherited;
        inherited = null;
        if (pairs != null) {
            for (int i = 0; i < pairs.length && pairs[i] != null; i += 2) {
                ((TThreadLocal<Object>) pairs[i]).set(pairs[i + 1]);
            }
        }
    }

    static void setCurrentThread(TThread thread) {
        if (currentThread != thread) {
            currentThread = thread;
        }
        currentThread.timeSliceStart = EventQueue.now();
    }

    static TThread getMainThread() {
        return mainThread;
    }

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public static TThread currentThread() {
        return currentThread;
    }

    public String getName() {
        // Building this in the constructor would pull string code into the main-thread initializer.
        if (name == null) {
            name = "Thread-" + id;
        }
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        if (alive) {
            throw new IllegalThreadStateException();
        }
        this.daemon = daemon;
    }

    public final void join(long millis, int nanos) throws InterruptedException {
        if (millis < 0 || nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException();
        }
        // A thread that was never started, or has ended, has nothing to wait for. alive goes back to false only when
        // the thread ends, so this early return cannot race with it.
        if (currentThread() == this || !alive) {
            return;
        }
        // Check and wait under the lock, in a loop. monitorEnter can switch threads, so a check outside the lock can
        // miss the thread's end, and a wake with time left can come while the thread is still alive.
        synchronized (finishedLock) {
            // A timeout over about 292 years (Long.MAX_VALUE ns) waits without a deadline, because
            // millis * 1_000_000 overflows for it.
            if (millis == 0 && nanos == 0 || millis >= (Long.MAX_VALUE - nanos) / 1_000_000L) {
                while (alive) {
                    finishedLock.wait();
                }
            } else {
                long deadline = System.nanoTime() + millis * 1_000_000L + nanos;
                while (alive) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        break;
                    }
                    finishedLock.wait(left / 1_000_000L, (int) (left % 1_000_000L));
                }
            }
        }
    }

    public final void join(long millis) throws InterruptedException {
        join(millis, 0);
    }

    public final void join() throws InterruptedException {
        join(0);
    }

    public static void yield() {
        TThread currentThread = currentThread();
        if (++currentThread.yieldCount < 30) {
            return;
        }
        currentThread().yieldCount = 0;
        if (currentThread.timeSliceStart + 100 < EventQueue.now()) {
            switchContext(currentThread);
        }
    }

    @Async
    static native void switchContext(TThread thread);

    private static void switchContext(final TThread thread, final AsyncCallback<Void> callback) {
        if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
            EventQueue.offer(() -> {
                setCurrentThread(thread);
                callback.complete(null);
            });
        } else {
            Platform.postpone(() -> {
                setCurrentThread(thread);
                callback.complete(null);
            });
        }
    }

    public void interrupt() {
        interruptedFlag = true;
        var handler = interruptHandler;
        if (handler != null) {
            // The callback can resume the thread, which can install a new handler before this call returns.
            interruptHandler = null;
            handler.interrupted();
        }
    }

    public static boolean interrupted() {
        TThread thread = currentThread();
        boolean result = thread.interruptedFlag;
        thread.interruptedFlag = false;
        return result;
    }

    public boolean isInterrupted() {
        return interruptedFlag;
    }

    // For the interrupt handlers of blocking calls in other packages. As in the JDK, a blocked call that an
    // interrupt makes throw InterruptedException clears the thread's interrupt status.
    public void clearInterruptStatus() {
        interruptedFlag = false;
    }

    public boolean isAlive() {
        return alive;
    }

    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }

    public static int enumerate(TThread[] threads) {
        return currentThread().getThreadGroup().enumerate(threads);
    }

    public static Map<TThread, TStackTraceElement[]> getAllStackTraces() {
        int count = 0;
        for (var thread = liveHead; thread != null; thread = thread.liveNext) {
            if (thread.alive) {
                ++count;
            }
        }
        var threads = new TThread[count];
        int index = 0;
        for (var thread = liveHead; thread != null; thread = thread.liveNext) {
            if (thread.alive) {
                threads[index++] = thread;
            }
        }
        // Overridden getStackTrace or hashCode methods can terminate threads and change the live list.
        Map<TThread, TStackTraceElement[]> result = new HashMap<>();
        for (var thread : threads) {
            result.put(thread, thread.getStackTrace());
        }
        return result;
    }

    static int enumerate(TThreadGroup group, TThread[] threads, boolean recurse) {
        int count = 0;
        for (var thread = liveHead; thread != null; thread = thread.liveNext) {
            var threadGroup = thread.getThreadGroup();
            if (threadGroup == group || recurse && group.parentOf(threadGroup)) {
                if (threads != null) {
                    if (count == threads.length) {
                        break;
                    }
                    threads[count] = thread;
                }
                ++count;
            }
        }
        return count;
    }

    private void addLive() {
        livePrev = liveTail;
        if (liveTail == null) {
            liveHead = this;
        } else {
            liveTail.liveNext = this;
        }
        liveTail = this;
    }

    private void removeLive() {
        if (livePrev == null) {
            liveHead = liveNext;
        } else {
            livePrev.liveNext = liveNext;
        }
        if (liveNext == null) {
            liveTail = livePrev;
        } else {
            liveNext.livePrev = livePrev;
        }
        livePrev = null;
        liveNext = null;
    }

    public long getId() {
        return id;
    }

    public static boolean holdsLock(TObject obj) {
        return TObject.holdsLock(obj);
    }

    @Async
    public static native void sleep(long millis) throws TInterruptedException;

    // The JDK's sleep(millis, nanos) and sleep(Duration). The clock has millisecond resolution, so a part of a
    // millisecond rounds up, as in the JDK before 21.
    public static void sleep(long millis, int nanos) throws TInterruptedException {
        if (millis < 0 || nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException();
        }
        sleep(nanos > 0 && millis < Long.MAX_VALUE ? millis + 1 : millis);
    }

    public static void sleep(java.time.Duration duration) throws TInterruptedException {
        // As in the JDK, a negative duration returns at once, and one too long for a long of milliseconds
        // saturates instead of throwing.
        long seconds = duration.getSeconds();
        if (seconds < 0) {
            return;
        }
        int nanos = duration.getNano();
        long millis = seconds >= Long.MAX_VALUE / 1000 - 1 ? Long.MAX_VALUE : seconds * 1000 + nanos / 1_000_000;
        sleep(millis, nanos % 1_000_000);
    }

    private static void sleep(long millis, AsyncCallback<Void> callback) {
        TThread current = currentThread();
        SleepHandler handler = new SleepHandler(current, callback);
        // A thread interrupted before it sleeps throws at once, on every backend.
        if (current.interruptedFlag) {
            handler.interrupted();
            return;
        }
        if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
            handler.timer = EventQueue.scheduleAfter(handler, millis);
        } else {
            handler.timer = Platform.scheduleAfter(handler, millis);
        }
        current.interruptHandler = handler;
    }

    static class SleepHandler implements PlatformRunnable, EventQueue.Event, TThreadInterruptHandler {
        private TThread thread;
        private AsyncCallback<Void> callback;
        private boolean isInterrupted;
        EventQueue.Timer timer;

        SleepHandler(TThread thread, AsyncCallback<Void> callback) {
            this.thread = thread;
            this.callback = callback;
        }

        @Override
        public void interrupted() {
            thread.interruptedFlag = false;
            isInterrupted = true;
            if (timer != null) {
                timer.kill();
                timer = null;
            }
            if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                EventQueue.offer(() -> callback.error(new TInterruptedException()));
            } else {
                Platform.postpone(() -> callback.error(new TInterruptedException()));
            }
        }

        @Override
        public void run() {
            if (!isInterrupted) {
                thread.interruptHandler = null;
                setCurrentThread(thread);
                callback.complete(null);
            }
        }
    }

    public final void setPriority(int newPriority) {
        if (newPriority < Thread.MIN_PRIORITY || newPriority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException();
        }
        var threadGroup = getThreadGroup();
        if (threadGroup != null) {
            priority = Math.min(newPriority, threadGroup.getMaxPriority());
        }
    }

    public final int getPriority() {
        return this.priority;
    }

    public TStackTraceElement[] getStackTrace() {
        return new TStackTraceElement[0];
    }

    public TClassLoader getContextClassLoader() {
        return contextClassLoaderSet ? contextClassLoader : TClassLoader.getSystemClassLoader();
    }
    
    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        if (this.uncaughtExceptionHandler != null) {
            return this.uncaughtExceptionHandler;
        }
        return getThreadGroup();
    }
    
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }
    
    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }
    
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
        defaultUncaughtExceptionHandler = handler;
    }
    
    public interface UncaughtExceptionHandler {
        void uncaughtException(TThread t, Throwable e);
    }

    // The park state of java.util.concurrent.locks.LockSupport. Only that class uses it, so a program without it
    // does not have the field.
    public Object parkState;
}
