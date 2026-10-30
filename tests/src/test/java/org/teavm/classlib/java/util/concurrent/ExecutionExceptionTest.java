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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class ExecutionExceptionTest {
    @Test
    public void protectedConstructorsLeaveCauseUninitialized() {
        var cause = new IllegalStateException();
        var empty = new Subclass();
        assertNull(empty.getCause());
        empty.initCause(cause);
        assertSame(cause, empty.getCause());
        var withMessage = new Subclass("provided by caller");
        withMessage.initCause(cause);
        assertSame(cause, withMessage.getCause());
    }

    private static class Subclass extends ExecutionException {
        Subclass() {
        }

        Subclass(String message) {
            super(message);
        }
    }
}
