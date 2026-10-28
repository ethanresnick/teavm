/*
 *  Copyright 2018 Alexey Andreev.
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
package org.teavm.runtime;

import java.util.Arrays;
import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;
import org.teavm.interop.StaticInit;

@StaticInit
public final class EventQueue {
    private static Node[] data = new Node[16];
    private static int size;
    private static boolean finished;
    private static int idGenerator;

    private EventQueue() {
    }

    /**
     * The queue's clock, in milliseconds. Every {@code time} that this class takes or stores is on this clock. It is
     * {@code System.nanoTime()}, which measures elapsed time, so a change of the wall clock does not move a deadline.
     * {@code System.currentTimeMillis()} stays the wall clock.
     */
    public static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Returns {@code now() + millis}, saturated, so that a very long timeout does not wrap to the past. */
    public static long deadlineAfter(long millis) {
        long now = now();
        long delay = Math.max(0, millis);
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }

    /** Milliseconds until a deadline on this queue's clock, without wrapping for distant or expired deadlines. */
    public static long remainingMillis(long deadline) {
        long now = now();
        if (deadline <= now) {
            return 0;
        }
        long remaining = deadline - now;
        return remaining < 0 ? Long.MAX_VALUE : remaining;
    }

    /** Runs {@code action} after {@code millis} milliseconds of elapsed time. */
    public static int offerAfter(Event action, long millis) {
        return offer(action, deadlineAfter(millis));
    }

    /** Schedules a cancellable timeout, re-arming host timers that fire before the elapsed-time deadline. */
    public static Timer scheduleAfter(Event action, long millis) {
        long delay = Math.max(0, millis);
        Timer timer = new Timer(action, deadlineAfter(delay));
        timer.schedule(delay);
        return timer;
    }

    /** Like {@link #scheduleAfter}, with an absolute deadline on the {@link #now()} clock. */
    public static Timer scheduleAt(Event action, long deadline) {
        Timer timer = new Timer(action, deadline);
        timer.schedule(remainingMillis(deadline));
        return timer;
    }

    public static void offer(Event action) {
        offer(action, now());
    }

    /** Runs {@code action} at {@code time} on the {@link #now()} clock. */
    public static int offer(Event action, long time) {
        if (PlatformDetector.isWebAssemblyGC()) {
            return offerWasmGC(action, Math.min(remainingMillis(time), Integer.MAX_VALUE));
        }
        ensureCapacity(size + 1);
        int current = size;
        while (current > 0) {
            int parent = (current - 1) / 2;
            if (time < data[parent].time) {
                data[current] = data[parent];
                current = parent;
            } else {
                break;
            }
        }
        int id = idGenerator++;
        data[current] = new Node(id, action, time);
        ++size;
        if (current == 0) {
            interrupt();
        }
        return id;
    }

    @Intrinsified
    private static native int offerWasmGC(Event action, double delay);

    private static void run(Event action) {
        action.run();
    }

    public static void kill(int id) {
        if (PlatformDetector.isWebAssemblyGC()) {
            // offer() schedules a host timer on Wasm GC, so the timer must be cancelled there.
            killWasmGC(id);
            return;
        }
        for (int i = 0; i < size; ++i) {
            if (data[i].id == id) {
                remove(i);
                break;
            }
        }
    }

    @Import(name = "kill", module = "teavmAsync")
    private static native void killWasmGC(int id);

    public static void process() {
        while (size > 0 && !finished) {
            next();
        }
    }

    @Export(name = "teavm_processQueue")
    public static long processSingle() {
        if (size == 0) {
            return -1;
        }

        Node node = data[0];
        long currentTime = now();
        if (node.time <= currentTime) {
            remove(0);
            node.event.run();
            if (size == 0) {
                return -1;
            }
            return Math.max(0, node.time - currentTime);
        } else {
            return node.time - currentTime;
        }
    }

    @Export(name = "teavm_stopped")
    public static boolean isStopped() {
        return finished;
    }

    public static void stop() {
        finished = true;
    }

    private static void next() {
        while (data.length == 0) {
            waitUntil(now() + 1000);
        }
        Node node = data[0];
        waitUntil(node.time);
        if (node.time <= now()) {
            remove(0);
            node.event.run();
        }
    }

    private static void remove(int index) {
        --size;
        if (index < size) {
            data[index] = data[size];
            update(index);
        }
        data[size] = null;
    }

    private static void update(int index) {
        Node item = data[index];
        while (true) {
            int left = index * 2 + 1;
            int right = left + 1;
            int next;
            if (left >= size) {
                break;
            } else if (right >= size || data[left].time < data[right].time) {
                next = left;
            } else {
                next = right;
            }
            if (item.time <= data[next].time) {
                break;
            }
            data[index] = data[next];
            index = next;
        }
        data[index] = item;
    }

    private static void waitUntil(long time) {
        long diff = time - now();
        if (diff <= 0) {
            return;
        }
        // The native waits take a bounded timeout: macOS select() fails at once with EINVAL for one over 10^8 s, so
        // a far deadline at the head of the queue made process() spin. next() waits again after a shorter wait.
        waitFor(Math.min(diff, Integer.MAX_VALUE));
    }

    @Import(name = "teavm_waitFor")
    @RuntimeInclude("fiber.h")
    private static native void waitFor(long time);

    @Import(name = "teavm_interrupt", module = "teavm")
    @RuntimeInclude("fiber.h")
    private static native void interrupt();

    private static void ensureCapacity(int capacity) {
        if (data.length >= capacity) {
            return;
        }
        capacity = Math.max(capacity, data.length * 3 / 2);
        data = Arrays.copyOf(data, capacity);
    }

    static class Node {
        final int id;
        final Event event;
        final long time;

        Node(int id, Event event, long time) {
            this.id = id;
            this.event = event;
            this.time = time;
        }
    }

    public interface Event {
        void run();
    }

    /** A stable cancellation handle, even when an early host callback replaces the underlying timer. */
    public static class Timer implements Event {
        private Event action;
        private final long deadline;
        protected int id;

        protected Timer(Event action, long deadline) {
            this.action = action;
            this.deadline = deadline;
        }

        protected void schedule(long delay) {
            id = PlatformDetector.isWebAssemblyGC()
                    ? offerWasmGC(this, Math.min(delay, Integer.MAX_VALUE))
                    : offer(this, deadline);
        }

        public void kill() {
            if (action != null) {
                action = null;
                cancel(id);
            }
        }

        protected void cancel(int id) {
            EventQueue.kill(id);
        }

        @Override
        public final void run() {
            if (action == null) {
                return;
            }
            // The C event queue checks the deadline before dispatching. Host timers can fire early, including
            // after the host's maximum delay (2^31 - 1 ms). Read the clock once and reuse the remaining delay.
            if (!PlatformDetector.isLowLevel()) {
                long remaining = remainingMillis(deadline);
                if (remaining > 0) {
                    schedule(remaining);
                    return;
                }
            }
            Event action = this.action;
            this.action = null;
            action.run();
        }
    }
}
