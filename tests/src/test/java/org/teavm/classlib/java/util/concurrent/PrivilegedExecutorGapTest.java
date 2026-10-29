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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@SuppressWarnings("removal")
public class PrivilegedExecutorGapTest {
    // Java SE 21 requires access-control context capture and enforcement, which TeaVM does not implement.
    // These APIs are intentionally omitted rather than returning wrappers with partial security behavior.
    @Test
    @Disabled("Omitted until Java SE 21 access-control context semantics are supported")
    public void privilegedThreadFactory() {
        assertNotNull(Executors.privilegedThreadFactory());
    }

    @Test
    @Disabled("Omitted until Java SE 21 access-control context semantics are supported")
    public void privilegedCallable() {
        assertNotNull(Executors.privilegedCallable(() -> 42));
    }

    @Test
    @Disabled("Omitted until Java SE 21 access-control context semantics are supported")
    public void privilegedCallableUsingCurrentClassLoader() {
        assertNotNull(Executors.privilegedCallableUsingCurrentClassLoader(() -> 42));
    }
}
