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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class RuntimeTest {
    @Test
    public void maxMemoryIsPositive() {
        assertTrue(Runtime.getRuntime().maxMemory() > 0);
    }

    @Test
    public void shutdownHooks() {
        var runtime = Runtime.getRuntime();
        var hook = new Thread(() -> { });
        runtime.addShutdownHook(hook);
        try {
            runtime.addShutdownHook(hook);
            fail();
        } catch (IllegalArgumentException e) {
            // ok
        }
        assertTrue(runtime.removeShutdownHook(hook));
        assertFalse(runtime.removeShutdownHook(hook));
    }
}
