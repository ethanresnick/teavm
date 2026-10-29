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

import java.lang.ref.Cleaner;
import java.util.concurrent.ExecutorService;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Browser cleanup for the otherwise unreachable wrapper returned by newSingleThreadExecutor. */
final class ExecutorCleanup {
    private static final Cleaner cleaner = Cleaner.create();
    private ExecutorService executor;
    private Cleaner.Cleanable cleanable;
    private int calls;
    private boolean collected;

    ExecutorCleanup(JSObject token, ExecutorService executor) {
        this.executor = executor;
        cleanable = cleaner.register(token, this::collected);
    }

    // Keep the token field visible to the compiler and live through the last guarded call.
    @JSBody(params = "token", script = "")
    static native void keepAlive(JSObject token);

    // A live call may outlast its wrapper's last compiler-visible use. Defer cleanup until it returns.
    void beginCall() {
        ++calls;
    }

    void endCall() {
        if (--calls == 0 && collected && executor != null) {
            scheduleShutdown();
        }
    }

    void cancel() {
        var registration = cleanable;
        takeExecutor();
        if (registration != null) {
            // clean() invokes the action synchronously. Detach the executor first so this only unregisters it.
            registration.clean();
        }
    }

    private ExecutorService takeExecutor() {
        var result = executor;
        executor = null;
        cleanable = null;
        return result;
    }

    void collected() {
        collected = true;
        if (calls == 0 && executor != null) {
            scheduleShutdown();
        }
    }

    private void scheduleShutdown() {
        var service = takeExecutor();
        // A Cleaner callback has no green-thread context. Pool shutdown may lock, so only start a thread here.
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
}
