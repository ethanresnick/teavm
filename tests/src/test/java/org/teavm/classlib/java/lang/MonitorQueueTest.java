/*
 *  Copyright 2015 Alexey Andreev.
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class MonitorQueueTest {
    // Tests cooperative scheduler progress, not JVM monitor fairness.
    @Test
    @SkipJVM
    public void queuedReaderProgressesWhileWriterReacquires() throws InterruptedException {
        Object monitor = new Object();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean holding = new AtomicBoolean();
        AtomicBoolean acquired = new AtomicBoolean();
        Thread writer = new Thread(() -> {
            while (running.get()) {
                synchronized (monitor) {
                    holding.set(true);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });
        Thread reader = new Thread(() -> {
            synchronized (monitor) {
                synchronized (monitor) {
                    acquired.set(true);
                }
            }
        });
        writer.start();
        while (!holding.get()) {
            Thread.sleep(1);
        }
        reader.start();
        Thread.sleep(500);
        boolean progressed = acquired.get();
        running.set(false);
        writer.join();
        reader.join();
        assertTrue(progressed);
    }
}
