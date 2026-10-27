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

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.Test;
import org.teavm.jso.browser.Window;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

/**
 * Uncontended locks used from a JS callback, which runs outside any green thread and so cannot suspend. The JDK
 * specifies no suspension for an uncontended {@code lock()}, {@code unlock()} or {@code countDown()}, so they must
 * work there, however long ago the current thread's time slice started.
 */
@TeaVMTest
@SkipJVM
@OnlyPlatform({ TestPlatform.JAVASCRIPT, TestPlatform.WEBASSEMBLY_GC })
public class LockCallbackTest {
    private static volatile String result;

    @Test
    public void uncontendedLocksWorkInCallback() throws InterruptedException {
        result = null;
        ReentrantLock lock = new ReentrantLock();
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        CountDownLatch latch = new CountDownLatch(1000);
        // The callback runs about 300 ms after this thread's time slice started, so a time-slice preemption point
        // in any of these calls would try to suspend.
        Window.setTimeout(() -> {
            try {
                int n = 0;
                for (int i = 0; i < 1000; ++i) {
                    lock.lock();
                    try {
                        lock.lockInterruptibly();
                        n++;
                        lock.unlock();
                    } finally {
                        lock.unlock();
                    }
                    if (lock.tryLock()) {
                        lock.unlock();
                    }
                    rw.writeLock().lock();
                    rw.readLock().lock();
                    rw.readLock().unlock();
                    rw.writeLock().unlock();
                    latch.countDown();
                }
                result = "ok " + n + " " + latch.getCount();
            } catch (Throwable e) {
                result = "failed: " + e;
            }
        }, 300);
        // One long sleep, so this thread's time slice, which a resume restarts, began well before the callback.
        Thread.sleep(1000);
        for (int i = 0; i < 100 && result == null; ++i) {
            Thread.sleep(50);
        }
        assertEquals("ok 1000 0", result);
    }
}
