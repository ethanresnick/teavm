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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.teavm.classlib.PlatformDetector;

/** Cleanup for the otherwise unreachable wrapper returned by newSingleThreadExecutor. */
final class ExecutorCleanup extends WeakReference<Object> {
    private static final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private static final Set<ExecutorCleanup> pending = new HashSet<>();
    private static Thread worker;
    private ExecutorService executor;
    private int calls;
    private boolean collected;

    ExecutorCleanup(Object wrapper, ExecutorService executor) {
        super(wrapper, queue);
        this.executor = executor;
        pending.add(this);
        if (worker == null) {
            worker = cleanupThread(ExecutorCleanup::processQueue);
            worker.start();
        }
    }

    // These short operations cannot suspend: TeaVM executes one cooperative thread at a time.
    // A live call may outlast its wrapper's last compiler-visible use. Defer cleanup until it returns,
    // rather than relying on the no-op reachabilityFence used by the original port.
    void beginCall() {
        ++calls;
    }

    void endCall() {
        if (--calls == 0 && collected && executor != null) {
            scheduleShutdown();
        }
    }

    void cancel() {
        takeExecutor();
        if (pending.isEmpty() && worker != null) {
            worker.interrupt();
        }
    }

    private ExecutorService takeExecutor() {
        var result = executor;
        executor = null;
        pending.remove(this);
        clear();
        return result;
    }

    void collected() {
        collected = true;
        pending.remove(this);
        if (calls == 0 && executor != null) {
            scheduleShutdown();
        }
    }

    private void scheduleShutdown() {
        var service = takeExecutor();
        // Shutdown can acquire a lock. Neither delegated calls nor the queue worker should wait for it.
        cleanupThread(service::shutdown).start();
    }

    private static Thread cleanupThread(Runnable action) {
        var group = Thread.currentThread().getThreadGroup();
        while (group.getParent() != null) {
            group = group.getParent();
        }
        var thread = new Thread(group, action, "executor-cleanup", 0, false);
        thread.setContextClassLoader(null);
        thread.setDaemon(true);
        return thread;
    }

    private static void processQueue() {
        while (!pending.isEmpty()) {
            try {
                processNext();
            } catch (InterruptedException e) {
                // Explicit shutdown of the last registration wakes the worker so it can exit.
            }
        }
        worker = null;
    }

    private static void processNext() throws InterruptedException {
        ExecutorCleanup ref;
        if (PlatformDetector.isC()) {
            // C supports poll(), but not blocking ReferenceQueue.remove().
            ref = (ExecutorCleanup) queue.poll();
            if (ref == null) {
                Thread.sleep(100);
                return;
            }
        } else {
            ref = (ExecutorCleanup) queue.remove();
        }
        ref.collected();
    }
}
