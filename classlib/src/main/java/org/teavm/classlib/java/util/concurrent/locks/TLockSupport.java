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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Park and unpark for green threads. Each thread has one permit. {@code park} consumes the permit or waits on
 * the thread's state object until {@code unpark}, an interrupt, or the timeout.
 *
 * <p>{@code unpark} may run from a JS callback, outside any green thread. It only takes the state object's
 * monitor, and no thread ever suspends while it holds that monitor (it suspends only inside {@code wait}, which
 * releases it), so the monitor is never contended there.
 */
public final class TLockSupport {
    private TLockSupport() {
    }

    private static final class ParkState {
        boolean permit;
        volatile Object blocker;
    }

    private static final Map<Thread, ParkState> STATES = new WeakHashMap<>();

    private static ParkState state(Thread t) {
        synchronized (STATES) {
            ParkState s = STATES.get(t);
            if (s == null) {
                s = new ParkState();
                STATES.put(t, s);
            }
            return s;
        }
    }

    /** Waits for the permit. {@code timeoutMillis} of 0 means no timeout. */
    private static void park0(long timeoutMillis) {
        Thread t = Thread.currentThread();
        ParkState s = state(t);
        synchronized (s) {
            if (s.permit) {
                s.permit = false;
                return;
            }
            if (t.isInterrupted()) {
                return;
            }
            try {
                if (timeoutMillis > 0) {
                    s.wait(timeoutMillis);
                } else {
                    s.wait();
                }
            } catch (InterruptedException e) {
                // park reports an interrupt by returning with the flag still set.
                t.interrupt();
            }
            s.permit = false;
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
        synchronized (s) {
            s.permit = true;
            s.notifyAll();
        }
    }

    public static void park(Object blocker) {
        setCurrentBlocker(blocker);
        try {
            park0(0);
        } finally {
            setCurrentBlocker(null);
        }
    }

    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            setCurrentBlocker(blocker);
            try {
                park0(nanosToMillis(nanos));
            } finally {
                setCurrentBlocker(null);
            }
        }
    }

    public static void parkUntil(Object blocker, long deadline) {
        long millis = deadline - System.currentTimeMillis();
        if (millis > 0) {
            setCurrentBlocker(blocker);
            try {
                park0(millis);
            } finally {
                setCurrentBlocker(null);
            }
        }
    }

    public static Object getBlocker(Thread t) {
        if (t == null) {
            throw new NullPointerException();
        }
        return state(t).blocker;
    }

    public static void park() {
        park0(0);
    }

    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            park0(nanosToMillis(nanos));
        }
    }

    public static void parkUntil(long deadline) {
        long millis = deadline - System.currentTimeMillis();
        if (millis > 0) {
            park0(millis);
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
