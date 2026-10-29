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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.teavm.classlib.java.util.concurrent;

/**
 * Exception thrown when an error or other exception is encountered
 * in the course of completing a result or task.
 *
 * @since 1.8
 * @author Doug Lea
 */
public class TCompletionException extends RuntimeException {
    private static final long serialVersionUID = 7830266012832686185L;

    /**
     * Constructs a {@code TCompletionException} with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    protected TCompletionException() { }

    /**
     * Constructs a {@code TCompletionException} with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param message the detail message
     */
    protected TCompletionException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code TCompletionException} with the specified detail
     * message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public TCompletionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a {@code TCompletionException} with the specified cause.
     * The detail message is set to {@code (cause == null ? null :
     * cause.toString())} (which typically contains the class and
     * detail message of {@code cause}).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public TCompletionException(Throwable cause) {
        super(cause);
    }
}
