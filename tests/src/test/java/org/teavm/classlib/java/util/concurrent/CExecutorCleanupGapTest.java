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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.C)
@SkipJVM
public class CExecutorCleanupGapTest {
    @Test
    @Disabled("Automatic executor shutdown after collection is not implemented on C")
    public void abandonedDefaultExecutorStops() throws Exception {
        checkAbandoned(false);
    }

    @Test
    @Disabled("Automatic executor shutdown after collection is not implemented on C")
    public void abandonedCustomFactoryExecutorStops() throws Exception {
        checkAbandoned(true);
    }

    private void checkAbandoned(boolean custom) throws Exception {
        var state = abandon(custom);
        GCSupport.tryToTriggerGC(state.wrapper);
        assertNull(state.wrapper.get());
        state.worker.join(5000);
        assertFalse(state.worker.isAlive());
    }

    private State abandon(boolean custom) throws Exception {
        var executor = custom ? Executors.newSingleThreadExecutor(Thread::new) : Executors.newSingleThreadExecutor();
        var state = new State();
        state.wrapper = new WeakReference<>(executor);
        state.worker = executor.submit(Thread::currentThread).get();
        return state;
    }

    private static class State {
        WeakReference<ExecutorService> wrapper;
        Thread worker;
    }
}
