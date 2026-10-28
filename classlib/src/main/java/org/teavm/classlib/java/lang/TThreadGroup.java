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

import java.util.Objects;

/** Thread groups describe the hierarchy of TeaVM's cooperative threads. */
public class TThreadGroup implements TThread.UncaughtExceptionHandler {
    private final String name;
    private final TThreadGroup parent;
    private int maxPriority = Thread.MAX_PRIORITY;
    private boolean daemon;
    private TThreadGroup firstChild;
    private TThreadGroup nextSibling;

    static TThreadGroup createMainGroup() {
        return new TThreadGroup();
    }

    private TThreadGroup() {
        name = "main";
        parent = null;
    }

    public TThreadGroup(String name) {
        this(TThread.currentThread().getThreadGroup(), name);
    }

    public TThreadGroup(TThreadGroup parent, String name) {
        this.parent = Objects.requireNonNull(parent);
        this.name = name;
        maxPriority = parent.maxPriority;
        daemon = parent.daemon;
        nextSibling = parent.firstChild;
        parent.firstChild = this;
    }

    public final String getName() {
        return name;
    }

    public final TThreadGroup getParent() {
        return parent;
    }

    public final int getMaxPriority() {
        return maxPriority;
    }

    public final void setMaxPriority(int priority) {
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            return;
        }
        maxPriority = parent == null ? priority : Math.min(priority, parent.maxPriority);
        for (var child = firstChild; child != null; child = child.nextSibling) {
            child.setMaxPriority(priority);
        }
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public final boolean parentOf(TThreadGroup group) {
        for (var current = group; current != null; current = current.parent) {
            if (current == this) {
                return true;
            }
        }
        return false;
    }

    public int activeCount() {
        return TThread.enumerate(this, null, true);
    }

    public int enumerate(TThread[] threads) {
        return enumerate(threads, true);
    }

    public int enumerate(TThread[] threads, boolean recurse) {
        Objects.requireNonNull(threads);
        return TThread.enumerate(this, threads, recurse);
    }

    public int activeGroupCount() {
        int count = 0;
        for (var child = firstChild; child != null; child = child.nextSibling) {
            count += 1 + child.activeGroupCount();
        }
        return count;
    }

    public int enumerate(TThreadGroup[] groups) {
        return enumerate(groups, true);
    }

    public int enumerate(TThreadGroup[] groups, boolean recurse) {
        Objects.requireNonNull(groups);
        return enumerateGroups(groups, 0, recurse);
    }

    private int enumerateGroups(TThreadGroup[] groups, int count, boolean recurse) {
        for (var child = firstChild; child != null && count < groups.length; child = child.nextSibling) {
            groups[count++] = child;
            if (recurse) {
                count = child.enumerateGroups(groups, count, true);
            }
        }
        return count;
    }

    public final void checkAccess() {
        // TeaVM does not enforce a security policy.
    }

    public final void interrupt() {
        // A resumed thread can alter the live list, so take the snapshot before invoking any handlers.
        var threads = new TThread[activeCount()];
        int count = enumerate(threads);
        for (int i = 0; i < count; ++i) {
            threads[i].interrupt();
        }
    }

    @Override
    public void uncaughtException(TThread thread, Throwable error) {
        if (parent != null) {
            parent.uncaughtException(thread, error);
            return;
        }
        var handler = TThread.getDefaultUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(thread, error);
        } else if (!(error instanceof ThreadDeath)) {
            error.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[name=" + name + ",maxpri=" + maxPriority + "]";
    }
}
