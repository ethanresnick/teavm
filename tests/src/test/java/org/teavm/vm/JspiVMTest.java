/*
 *  Copyright 2016 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMProperties;
import org.teavm.junit.TeaVMProperty;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;
import org.teavm.runtime.Fiber;

@TeaVMTest
@SkipJVM
@EachTestCompiledSeparately
@OnlyPlatform(TestPlatform.WEBASSEMBLY_GC)
@TeaVMProperties(@TeaVMProperty(key = "teavm.wasm.jspi", value = "true"))
public class JspiVMTest extends org.teavm.vm.VMTest {
    @Test
    public void synchronousAsyncFailuresDoNotPoisonLaterCalls() {
        assertThrows(IllegalStateException.class, () -> asyncResult(0));
        assertEquals(42, asyncResult(2));
        assertThrows(IllegalArgumentException.class, () -> asyncResult(1));
        assertEquals(42, asyncResult(2));
    }

    @Test
    public void nestedFiberCompletesBeforeCallerParks() {
        Fiber caller = Fiber.current();
        assertEquals(42, nestedResult(false));
        assertSame(caller, Fiber.current());
        assertThrows(IllegalArgumentException.class, () -> nestedResult(true));
        assertSame(caller, Fiber.current());
        assertEquals(42, asyncResult(2));
    }

    @Test
    public void deferredCompletionRestoresContextAndFinally() {
        assertTrue(hostSupportsJspi());
        Fiber caller = Fiber.current();
        Thread thread = Thread.currentThread();
        int completed = 0;
        for (int i = 0; i < 4; ++i) {
            try {
                if ((i & 1) == 0) {
                    assertEquals(42, deferredResult(false));
                } else {
                    assertThrows(IllegalArgumentException.class, () -> deferredResult(true));
                }
            } finally {
                assertSame(caller, Fiber.current());
                assertSame(thread, Thread.currentThread());
                ++completed;
            }
        }
        assertEquals(4, completed);
    }

    @JSBody(script = "return typeof WebAssembly.Suspending === 'function'"
            + " && typeof WebAssembly.promising === 'function';")
    private static native boolean hostSupportsJspi();

    @Async
    private static native int nestedResult(boolean fail);

    private static void nestedResult(boolean fail, AsyncCallback<Integer> callback) {
        Fiber.start(() -> {
            if (fail) {
                callback.error(new IllegalArgumentException());
            } else {
                callback.complete(42);
            }
        }, true);
    }

    @Async
    private static native int deferredResult(boolean fail);

    private static void deferredResult(boolean fail, AsyncCallback<Integer> callback) {
        Window.setTimeout(() -> {
            if (fail) {
                callback.error(new IllegalArgumentException());
            } else {
                callback.complete(42);
            }
        }, 0);
    }

    @Async
    private static native int asyncResult(int mode);

    private static void asyncResult(int mode, AsyncCallback<Integer> callback) {
        if (mode == 0) {
            throw new IllegalStateException();
        } else if (mode == 1) {
            callback.error(new IllegalArgumentException());
        } else {
            callback.complete(42);
        }
    }
}
