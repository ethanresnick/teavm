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
 * (https://gee.cs.oswego.edu/dl/jsr166/src/main/java/util/concurrent/RejectedExecutionException.java).
 * The port is mechanical: the package and the "T" prefix, plain-field helpers in place of Unsafe, no
 * @ReservedStackAccess annotations, and EmulSupport in place of ForkJoinPool.managedBlock. The jsr166 formatting is
 * kept. See classlib/PORTED-CLASSES.md.
 */
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.teavm.classlib.java.util.concurrent;

/**
 * Exception thrown by an {@link Executor} when a task cannot be
 * accepted for execution.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class TRejectedExecutionException extends RuntimeException {
    private static final long serialVersionUID = -375805702767069545L;

    /**
     * Constructs a {@code TRejectedExecutionException} with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public TRejectedExecutionException() { }

    /**
     * Constructs a {@code TRejectedExecutionException} with the
     * specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link
     * #initCause(Throwable) initCause}.
     *
     * @param message the detail message
     */
    public TRejectedExecutionException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code TRejectedExecutionException} with the
     * specified detail message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public TRejectedExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a {@code TRejectedExecutionException} with the
     * specified cause.  The detail message is set to {@code (cause ==
     * null ? null : cause.toString())} (which typically contains
     * the class and detail message of {@code cause}).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public TRejectedExecutionException(Throwable cause) {
        super(cause);
    }
}
