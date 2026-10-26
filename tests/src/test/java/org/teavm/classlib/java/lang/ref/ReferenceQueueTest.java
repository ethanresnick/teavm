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
package org.teavm.classlib.java.lang.ref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
public class ReferenceQueueTest {
    // The C backend generates an empty body for ReferenceQueue.remove(), so a waiter there never blocks.
    @Test
    @SkipPlatform(TestPlatform.C)
    public void removeCanBeInterruptedTwice() throws InterruptedException {
        var queue = new ReferenceQueue<Object>();
        // An AtomicInteger, not an int[] local: see ArrayBlockingQueueTest.takeCanBeInterruptedAgain.
        var interrupts = new AtomicInteger();
        var thread = new Thread(() -> {
            for (var i = 0; i < 2; ++i) {
                try {
                    queue.remove();
                } catch (InterruptedException e) {
                    interrupts.incrementAndGet();
                }
            }
        });
        thread.start();
        Thread.sleep(50);
        thread.interrupt();
        Thread.sleep(50);
        thread.interrupt();
        thread.join(2000);
        assertEquals(2, interrupts.get());
        assertFalse(thread.isAlive());
    }
}
