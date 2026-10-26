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
package org.teavm.classlib.java.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@SuppressWarnings("removal")
public class AccessControllerTest {
    @Test
    public void checkPermissionDeniesEveryPermission() {
        try {
            AccessController.checkPermission(new TestPermission());
            fail();
        } catch (AccessControlException e) {
            // ok
        }
    }

    @Test
    public void doPrivilegedWrapsCheckedException() {
        var exception = new IOException();
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
                throw exception;
            });
            fail();
        } catch (PrivilegedActionException e) {
            assertSame(exception, e.getException());
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void doPrivilegedReturnsResult() throws PrivilegedActionException {
        assertEquals("ok", AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> "ok"));
        assertTrue(new PrivilegedActionException(new IOException()).getException() instanceof IOException);
    }

    static class TestPermission extends Permission {
        TestPermission() {
            super("test");
        }

        @Override
        public boolean implies(Permission permission) {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String getActions() {
            return "";
        }
    }
}
