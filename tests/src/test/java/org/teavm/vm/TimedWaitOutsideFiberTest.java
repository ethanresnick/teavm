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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class TimedWaitOutsideFiberTest {
    // The program calls an Iterator.next() that can suspend through the Iterator interface. So TeaVM treats every
    // Iterator.next() call as a suspension point, including the one in Object's notify-listener bookkeeping
    // (removeNotifyListener -> AbstractCollection.remove). A timed wait whose timeout fires from the event queue,
    // outside any fiber, then runs that bookkeeping.
    @Test
    public void timedWaitAfterSuspendingInterfaceIterator() throws InterruptedException {
        var sum = 0;
        Iterable<Integer> source = () -> new SleepingIterator(2);
        for (int value : source) {
            sum += value;
        }
        assertEquals(1, sum);
        var lock = new Object();
        var thread = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait(20);
                } catch (InterruptedException e) {
                    // not expected
                }
            }
        });
        thread.start();
        thread.join();
    }

    static final class SleepingIterator implements Iterator<Integer> {
        private int next;
        private final int end;

        SleepingIterator(int end) {
            this.end = end;
        }

        @Override
        public boolean hasNext() {
            return next < end;
        }

        @Override
        public Integer next() {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return next++;
        }
    }
}
