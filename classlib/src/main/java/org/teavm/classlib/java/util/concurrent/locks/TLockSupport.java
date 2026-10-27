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
 * Written for TeaVM's green threads; not a port. The jsr166 version parks through Unsafe, which TeaVM lacks. The
 * public API and its contract match java.util.concurrent.locks.LockSupport.
 */
package org.teavm.classlib.java.util.concurrent.locks;

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.TThread;
import org.teavm.classlib.java.lang.TThreadInterruptHandler;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;

/**
 * Park and unpark for green threads. Each thread has one permit. {@code park} consumes the permit, or suspends the
 * thread until {@code unpark}, an interrupt or the timeout.
 *
 * <p>Only {@code park} can suspend. {@code unpark} and {@code setCurrentBlocker} take no monitor and never
 * suspend, so releasing a lock or counting down a latch does not make its callers async, and works in a JS callback
 * that runs outside any green thread.
 */
public final class TLockSupport {
    private TLockSupport() {
    }

    /**
     * A thread's permit and blocker, and, while it is parked, the callback that resumes it. It is also the timer
     * event and the interrupt handler of the park, so a park allocates nothing once the thread has this object.
     */
    private static final class ParkState implements EventQueue.Event, PlatformRunnable, TThreadInterruptHandler {
        final TThread thread;
        boolean permit;
        Object blocker;
        // Non-null while the thread is parked.
        AsyncCallback<Void> callback;
        // A wake that unpark or an interrupt posted, to run from the event loop.
        AsyncCallback<Void> posted;
        // -1 while no timer is scheduled. On C, event ids start at 0.
        int timerId = -1;

        ParkState(TThread thread) {
            this.thread = thread;
        }

        // A posted wake, or the timeout. Both run from the event loop, so they can resume the thread directly.
        @Override
        public void run() {
            AsyncCallback<Void> posted = this.posted;
            if (posted != null) {
                this.posted = null;
                posted.complete(null);
            } else {
                AsyncCallback<Void> callback = take();
                if (callback != null) {
                    callback.complete(null);
                }
            }
        }

        // An interrupt. park leaves the interrupt status set.
        @Override
        public void interrupted() {
            post();
        }

        /**
         * Wakes the parked thread from a running thread (unpark, interrupt), which must not resume another thread
         * inside its own stack.
         */
        void post() {
            AsyncCallback<Void> callback = take();
            if (callback != null) {
                posted = callback;
                if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                    EventQueue.offer(this);
                } else {
                    Platform.postpone(this);
                }
            }
        }

        /** Ends the park: returns its callback, or null if it already ended. */
        private AsyncCallback<Void> take() {
            AsyncCallback<Void> callback = this.callback;
            if (callback == null) {
                return null;
            }
            this.callback = null;
            if (timerId >= 0) {
                if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                    EventQueue.kill(timerId);
                } else {
                    Platform.killSchedule(timerId);
                }
                timerId = -1;
            }
            if (thread.interruptHandler == this) {
                thread.interruptHandler = null;
            }
            // The @Async machinery restores the current thread when the callback completes.
            return callback;
        }
    }

    // The state lives in a field of the thread, so it goes away with the thread.
    private static ParkState state(Thread t) {
        TThread thread = (TThread) (Object) t;
        ParkState s = (ParkState) thread.parkState;
        if (s == null) {
            s = new ParkState(thread);
            thread.parkState = s;
        }
        return s;
    }

    /** Waits for the permit. {@code timeoutMillis} of 0 means no timeout. */
    private static void park0(ParkState s, long timeoutMillis) {
        if (s.permit) {
            s.permit = false;
            return;
        }
        // park returns at once for an interrupted thread, and leaves the interrupt status set.
        if (s.thread.isInterrupted()) {
            return;
        }
        parkImpl(s, timeoutMillis);
    }

    @Async
    private static native void parkImpl(ParkState s, long timeoutMillis);

    private static void parkImpl(ParkState s, long timeoutMillis, AsyncCallback<Void> callback) {
        s.callback = callback;
        if (timeoutMillis > 0) {
            // Host timers take an int delay, and the clamp keeps the deadline from overflowing. A longer park wakes
            // early, which park allows.
            int delay = timeoutMillis < Integer.MAX_VALUE ? (int) timeoutMillis : Integer.MAX_VALUE;
            s.timerId = PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()
                    ? EventQueue.offerAfter(s, delay)
                    : Platform.schedule(s, delay);
        }
        s.thread.interruptHandler = s;
    }

    /** Parks with {@code blocker} recorded, as the blocker overloads do. */
    private static void parkWith(Object blocker, long timeoutMillis) {
        ParkState s = state(Thread.currentThread());
        s.blocker = blocker;
        try {
            park0(s, timeoutMillis);
        } finally {
            s.blocker = null;
        }
    }

    public static void setCurrentBlocker(Object blocker) {
        state(Thread.currentThread()).blocker = blocker;
    }

    public static void unpark(Thread thread) {
        if (thread == null) {
            return;
        }
        ParkState s = state(thread);
        if (s.callback != null) {
            // The permit goes straight to the parked thread.
            s.post();
        } else {
            s.permit = true;
        }
    }

    public static void park(Object blocker) {
        parkWith(blocker, 0);
    }

    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            parkWith(blocker, nanosToMillis(nanos));
        }
    }

    public static void parkUntil(Object blocker, long deadline) {
        long millis = deadline - System.currentTimeMillis();
        if (millis > 0) {
            parkWith(blocker, millis);
        }
    }

    public static Object getBlocker(Thread t) {
        if (t == null) {
            throw new NullPointerException();
        }
        ParkState s = (ParkState) ((TThread) (Object) t).parkState;
        return s != null ? s.blocker : null;
    }

    public static void park() {
        park0(state(Thread.currentThread()), 0);
    }

    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            park0(state(Thread.currentThread()), nanosToMillis(nanos));
        }
    }

    public static void parkUntil(long deadline) {
        long millis = deadline - System.currentTimeMillis();
        if (millis > 0) {
            park0(state(Thread.currentThread()), millis);
        }
    }

    static long getThreadId(Thread thread) {
        return thread.getId();
    }

    /** Rounds up, so a positive timeout never becomes 0 (which would mean "wait forever"). */
    private static long nanosToMillis(long nanos) {
        long millis = nanos / 1_000_000L;
        return (nanos % 1_000_000L) == 0 ? millis : millis + 1;
    }
}
