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
        return millis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + Math.max(0, millis);
    }

    /** Runs {@code action} after {@code millis} milliseconds of elapsed time. */
    public static int offerAfter(Event action, long millis) {
        // On Wasm GC this must go through offer(Event, long): WasmGCDependencies links run() for the event types that
        // reach that method's parameter.
        return offer(action, deadlineAfter(millis));
    }

    public static void offer(Event action) {
        offer(action, now());
    }

    /** Runs {@code action} at {@code time} on the {@link #now()} clock. */
    public static int offer(Event action, long time) {
        if (PlatformDetector.isWebAssemblyGC()) {
            // The host takes a wall-clock time and subtracts the wall clock at once to get a timer delay. So pass the
            // remaining time added to a wall-clock time read now. A host timer delay is at most 2^31 - 1 ms: Node
            // runs a longer setTimeout after 1 ms.
            double delay = Math.min((double) (time - now()), Integer.MAX_VALUE);
            return offerWasmGC(action, (double) System.currentTimeMillis() + delay);
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
    private static native int offerWasmGC(Event action, double time);

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
}
