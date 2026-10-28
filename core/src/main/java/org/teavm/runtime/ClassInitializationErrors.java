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
package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

/** Failure records are allocated only when an initializer fails. */
@StaticInit
public final class ClassInitializationErrors {
    private static Entry first;

    private ClassInitializationErrors() {
    }

    public static Throwable wrap(Throwable original) {
        return new ExceptionInInitializerError(original);
    }

    public static void fail(Throwable saved) {
        var error = new NoClassDefFoundError();
        error.initCause(saved);
        throw error;
    }

    // C class identities are unmanaged addresses. This managed list roots their saved exceptions without
    // adding a reference field to every class descriptor. Failed classes live for the life of the program.
    public static void saveC(Address cls, Throwable original) {
        var entry = new Entry();
        entry.cls = cls.toLong();
        entry.error = wrap(original);
        entry.next = first;
        first = entry;
    }

    @Unmanaged
    public static void failC(Address cls) {
        var entry = first;
        while (entry != null && entry.cls != cls.toLong()) {
            entry = entry.next;
        }
        var error = new NoClassDefFoundError();
        if (entry != null) {
            error.initCause(entry.error);
        }
        ExceptionHandling.throwException(error);
    }

    private static class Entry {
        long cls;
        Throwable error;
        Entry next;
    }
}
