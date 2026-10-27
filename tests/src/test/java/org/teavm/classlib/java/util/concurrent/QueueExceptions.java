/*
 *  Copyright 2026 konsoletyper.
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

import java.util.NoSuchElementException;

final class QueueExceptions {
    private QueueExceptions() {
    }

    // Names the exception with instanceof checks, because getClass().getName() can be null on JS and C for an
    // exception that the runtime throws, such as a failed array bounds check.
    static String describe(RuntimeException e) {
        if (e instanceof ArrayIndexOutOfBoundsException) {
            return "ArrayIndexOutOfBoundsException";
        } else if (e instanceof NullPointerException) {
            return "NullPointerException";
        } else if (e instanceof IllegalStateException) {
            return "IllegalStateException";
        } else if (e instanceof NoSuchElementException) {
            return "NoSuchElementException";
        } else {
            return "other RuntimeException";
        }
    }
}
