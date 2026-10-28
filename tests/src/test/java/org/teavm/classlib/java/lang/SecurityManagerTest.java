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
    // Java SE 21 SecurityManager.getThreadGroup: the default returns the current thread's group.
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/SecurityManager.html#getThreadGroup()
    @Test
    public void threadGroupTracksCallingThread() throws InterruptedException {
        var manager = System.getSecurityManager();
        assertNotNull(manager);
        assertSame(Thread.currentThread().getThreadGroup(), manager.getThreadGroup());
        var group = Thread.currentThread().getThreadGroup();
        for (int depth = 0; depth < 4; depth++) {
            group = new ThreadGroup(group, "security-manager-group-" + depth);
            var observed = new ThreadGroup[2];
            var thread = new Thread(group, () -> {
                observed[0] = manager.getThreadGroup();
                Thread.yield();
                observed[1] = manager.getThreadGroup();
            }, "security-manager-reader");
            thread.start();
            thread.join();
            assertSame(group, observed[0]);
            assertSame(group, observed[1]);
        }
        assertSame(Thread.currentThread().getThreadGroup(), manager.getThreadGroup());
    }

    @Test
    public void subclassCanChooseThreadGroup() {
        var group = new ThreadGroup("chosen-security-manager-group");
        SecurityManager manager = new SecurityManager() {
            @Override
            public ThreadGroup getThreadGroup() {
                return group;
            }
        };
        // The same Javadoc explicitly permits a specific security manager to override this method.
        assertSame(group, manager.getThreadGroup());
        assertSame(Thread.currentThread().getThreadGroup(), new SecurityManager().getThreadGroup());
    }
}
