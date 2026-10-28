/*
 *  Copyright 2022 ulugbek.abdullaev.
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@SkipJVM
@SuppressWarnings("removal")
public class SecurityManagerTest {
    @Test
    public void threadGroupTracksCallingThread() throws InterruptedException {
        var manager = System.getSecurityManager();
        assertNotNull(manager);
        assertSame(Thread.currentThread().getThreadGroup(), manager.getThreadGroup());
        var group = new ThreadGroup("security-manager-group");
        var observed = new ThreadGroup[1];
        var thread = new Thread(group, () -> observed[0] = manager.getThreadGroup(), "security-manager-reader");
        thread.start();
        thread.join();
        assertSame(group, observed[0]);
        assertSame(Thread.currentThread().getThreadGroup(), manager.getThreadGroup());
    }
}
