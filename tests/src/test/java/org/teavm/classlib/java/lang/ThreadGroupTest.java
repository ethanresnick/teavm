/*
 *  Copyright 2015 Alexey Andreev.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ThreadGroupTest {
    @Test
    public void hierarchyAndPriorityMetadata() {
        var parent = new ThreadGroup("parent");
        var child = new ThreadGroup(parent, "child");
        var grandchild = new ThreadGroup(child, "grandchild");
        assertSame(Thread.currentThread().getThreadGroup(), parent.getParent());
        assertEquals("child", child.getName());
        assertSame(parent, child.getParent());
        assertTrue(parent.parentOf(parent));
        assertTrue(parent.parentOf(grandchild));
        assertFalse(child.parentOf(parent));
        assertFalse(parent.parentOf(null));
        assertThrows(NullPointerException.class, () -> new ThreadGroup(null, "bad"));
        assertEquals(2, parent.activeGroupCount());
        var groups = new ThreadGroup[3];
        assertEquals(2, parent.enumerate(groups));
        assertTrue(Arrays.asList(groups).contains(child));
        assertTrue(Arrays.asList(groups).contains(grandchild));
        Arrays.fill(groups, null);
        assertEquals(1, parent.enumerate(groups, false));
        assertSame(child, groups[0]);
        assertEquals(0, parent.enumerate(new ThreadGroup[0]));
        parent.setMaxPriority(4);
        assertEquals(4, child.getMaxPriority());
        assertEquals(4, grandchild.getMaxPriority());
        child.setMaxPriority(8);
        assertEquals(4, child.getMaxPriority());
        child.setMaxPriority(0);
        assertEquals(4, child.getMaxPriority());
    }

    @Test
    public void membershipEnumerationAndInterrupt() throws InterruptedException {
        var parent = new ThreadGroup("parent");
        var child = new ThreadGroup(parent, "child");
        var interrupted = new int[1];
        Runnable wait = () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                ++interrupted[0];
            }
        };
        var first = new Thread(parent, wait, "first");
        var second = new Thread(child, wait, "second", 0);
        assertSame(parent, first.getThreadGroup());
        assertSame(child, second.getThreadGroup());
        assertEquals(0, parent.activeCount());
        first.start();
        second.start();
        Thread.sleep(20);
        assertEquals(2, parent.activeCount());
        assertEquals(1, child.activeCount());
        var threads = new Thread[3];
        assertEquals(2, parent.enumerate(threads));
        assertTrue(Arrays.asList(threads).contains(first));
        assertTrue(Arrays.asList(threads).contains(second));
        Arrays.fill(threads, null);
        assertEquals(1, parent.enumerate(threads, false));
        assertSame(first, threads[0]);
        assertEquals(1, parent.enumerate(new Thread[1]));
        parent.interrupt();
        first.join();
        second.join();
        assertEquals(2, interrupted[0]);
        assertEquals(0, parent.activeCount());
        assertNull(first.getThreadGroup());
        assertNull(second.getThreadGroup());
    }

    @Test
    public void childrenInheritTheCreatorsGroup() throws InterruptedException {
        var group = new ThreadGroup("creator");
        var seen = new ThreadGroup[3];
        var creator = new Thread(group, () -> {
            seen[0] = new Thread().getThreadGroup();
            seen[1] = new Thread((ThreadGroup) null, () -> { }).getThreadGroup();
            seen[2] = new ThreadGroup("child").getParent();
        });
        creator.start();
        creator.join();
        assertEquals(Arrays.asList(group, group, group), Arrays.asList(seen));
    }

    @Test
    public void uncaughtExceptionsReachTheGroup() throws InterruptedException {
        var errors = new Throwable[1];
        var group = new ThreadGroup("handler") {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                errors[0] = error;
            }
        };
        var expected = new IllegalStateException();
        var worker = new Thread(group, () -> {
            throw expected;
        });
        assertSame(group, worker.getUncaughtExceptionHandler());
        worker.start();
        worker.join();
        assertSame(expected, errors[0]);
    }
}
