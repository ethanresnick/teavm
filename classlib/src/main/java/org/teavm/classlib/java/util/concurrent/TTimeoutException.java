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
/*
 * Ported from the public-domain jsr166 sources
 * (https://gee.cs.oswego.edu/dl/jsr166/src/main/java/util/concurrent/TimeoutException.java).
 * The port is mechanical: the package and the "T" prefix, plain-field helpers in place of VarHandles, no memory
 * fences, and EmulSupport in place of Thread.onSpinWait and ForkJoinPool.managedBlock. Hand edits are marked
 * "TeaVM:". The jsr166 formatting is kept. See classlib/PORTED-CLASSES.md.
 */
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// CHECKSTYLE.OFF: WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Exception thrown when a blocking operation times out.  Blocking
 * operations for which a timeout is specified need a means to
 * indicate that the timeout has occurred. For many such operations it
 * is possible to return a value that indicates timeout; when that is
 * not possible or desirable then {@code TTimeoutException} should be
 * declared and thrown.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class TTimeoutException extends Exception {
    private static final long serialVersionUID = 1900926677490660714L;

    /**
     * Constructs a {@code TTimeoutException} with no specified detail
     * message.
     */
    public TTimeoutException() {}

    /**
     * Constructs a {@code TTimeoutException} with the specified detail
     * message.
     *
     * @param message the detail message
     */
    public TTimeoutException(String message) {
        super(message);
    }
}
