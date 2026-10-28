/*
 *  Copyright 2026 kafka-wasm contributors.
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

/**
 * Thread groups for cooperative threads. A group records its name, parent, daemon flag,
 * and maximum priority. activeCount, enumerate, and interrupt find the group's threads in Thread.getAllStackTraces
 * (Thread keeps the list of live threads). Groups do not record their subgroups, so activeGroupCount is 0.
 */
public class TThreadGroup implements Thread.UncaughtExceptionHandler {
    private final String name;
    private final TThreadGroup parent;
    private int maxPriority = Thread.MAX_PRIORITY;
    private boolean daemon;

    /** As in the JDK, the parent is the current thread's group. */
    public TThreadGroup(String name) {
        this((TThreadGroup) (Object) Thread.currentThread().getThreadGroup(), name);
    }

    public TThreadGroup(TThreadGroup parent, String name) {
        this.parent = parent;
        this.name = name;
        if (parent != null) {
            this.maxPriority = parent.maxPriority;
            this.daemon = parent.daemon;
        }
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

    public final void setMaxPriority(int pri) {
        if (pri >= Thread.MIN_PRIORITY && pri <= Thread.MAX_PRIORITY) {
            maxPriority = parent != null ? Math.min(pri, parent.maxPriority) : pri;
        }
    }

    public final boolean isDaemon() {
        return daemon;
    }

    public final void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public final boolean parentOf(TThreadGroup g) {
        for (TThreadGroup x = g; x != null; x = x.parent) {
            if (x == this) {
                return true;
            }
        }
        return false;
    }

    /** The live threads in this group and its subgroups, in start order. */
    private java.util.List<Thread> liveThreads() {
        java.util.List<Thread> out = new java.util.ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && parentOf((TThreadGroup) (Object) t.getThreadGroup())) {
                out.add(t);
            }
        }
        return out;
    }

    public int activeCount() {
        return liveThreads().size();
    }

    public int enumerate(Thread[] list) {
        return enumerate(list, true);
    }

    public int enumerate(Thread[] list, boolean recurse) {
        int n = 0;
        for (Thread t : liveThreads()) {
            if (n >= list.length) {
                break;
            }
            if (recurse || (Object) t.getThreadGroup() == this) {
                list[n++] = t;
            }
        }
        return n;
    }

    public int activeGroupCount() {
        return 0;
    }

    public final void checkAccess() {
    }

    public final void interrupt() {
        for (Thread t : liveThreads()) {
            t.interrupt();
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (parent != null) {
            parent.uncaughtException(t, e);
            return;
        }
        Thread.UncaughtExceptionHandler ueh = Thread.getDefaultUncaughtExceptionHandler();
        if (ueh != null) {
            ueh.uncaughtException(t, e);
        } else {
            System.err.print("Exception in thread \"" + t.getName() + "\" ");
            e.printStackTrace(System.err);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[name=" + name + ",maxpri=" + maxPriority + "]";
    }
}
