/*
 *  Copyright 2026 Alexey Andreev.
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
package org.teavm.browserrunner;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProcessTree {
    private ProcessTree() {
    }

    /**
     * Forcibly kills the process with all its descendants (e.g. a browser's child processes, or a test
     * running under a wrapper such as gdb), and waits up to the given time for all of them to exit.
     */
    public static void kill(Process process, long timeout, TimeUnit unit) {
        // Collect the descendants first: they get re-parented, and become unreachable, once their
        // parent is dead.
        var processes = new ArrayList<ProcessHandle>();
        process.descendants().forEach(processes::add);
        processes.add(process.toHandle());
        var exits = new CompletableFuture<?>[processes.size()];
        for (var i = 0; i < processes.size(); ++i) {
            processes.get(i).destroyForcibly();
            exits[i] = processes.get(i).onExit();
        }
        try {
            CompletableFuture.allOf(exits).get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Some process is still alive; nothing more can be done about it
        }
    }
}
