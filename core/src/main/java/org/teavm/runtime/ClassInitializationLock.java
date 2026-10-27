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
package org.teavm.runtime;

// While an initializer that can suspend runs, other threads that use its class must wait (JLS 12.4.2, steps 2
// and 3). The generated initializer calls enter first and exit when it ends. This class has no static
// initializer, because it runs inside class initializers.
public final class ClassInitializationLock {
    public static final int RUN = 0;
    public static final int RECURSIVE = 1;
    public static final int WAITED = 2;

    private static String[] classes;
    private static Thread[] threads;
    private static int count;
    private static int waiting;
    private static Object lock;

    private ClassInitializationLock() {
    }

    // RUN: the caller runs the initializer. RECURSIVE: the current thread already runs it.
    // WAITED: another thread ran it, and the caller must use the class's initializer again.
    public static int enter(String className) {
        var current = Thread.currentThread();
        var index = indexOf(className);
        if (index < 0) {
            if (classes == null || count == classes.length) {
                var newClasses = new String[count == 0 ? 4 : count * 2];
                var newThreads = new Thread[newClasses.length];
                for (var i = 0; i < count; ++i) {
                    newClasses[i] = classes[i];
                    newThreads[i] = threads[i];
                }
                classes = newClasses;
                threads = newThreads;
            }
            classes[count] = className;
            threads[count++] = current;
            return RUN;
        }
        if (threads[index] == current) {
            return RECURSIVE;
        }
        if (lock == null) {
            lock = new Object();
        }
        // As in the JVM, the wait can't be interrupted
        var interrupted = false;
        waiting++;
        synchronized (lock) {
            while (indexOf(className) >= 0) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        waiting--;
        if (interrupted) {
            current.interrupt();
        }
        return WAITED;
    }

    public static void exit(String className) {
        var index = indexOf(className);
        --count;
        classes[index] = classes[count];
        threads[index] = threads[count];
        classes[count] = null;
        threads[count] = null;
        if (waiting > 0) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    private static int indexOf(String className) {
        for (var i = 0; i < count; ++i) {
            if (classes[i].equals(className)) {
                return i;
            }
        }
        return -1;
    }
}
