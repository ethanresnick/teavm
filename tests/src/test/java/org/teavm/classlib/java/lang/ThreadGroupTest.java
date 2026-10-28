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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.GCSupport;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ThreadGroupTest {
    private static volatile ThreadGroup[] retainedRegistryGroups;

    // ThreadGroup: "A thread group is weakly reachable from its parent group".
    @Test
    public void unusedGroupsAndSubclassStateAreCollectable() throws InterruptedException {
        var parent = new ThreadGroup("parent");
        var retained = new ThreadGroup(parent, "retained");
        var references = new WeakReference<?>[2];
        var creator = new Thread(() -> createUnusedGroups(parent, references));
        creator.start();
        awaitCreator(creator);
        GCSupport.tryToTriggerGC(references[0]);
        // FinalizationRegistry delivers queue notifications in a later host job than the collection.
        Thread.sleep(20);
        assertNull(references[0].get());
        assertNull(references[1].get());
        assertEquals(1, parent.activeGroupCount());
        var groups = new ThreadGroup[3];
        assertEquals(1, parent.enumerate(groups));
        assertSame(retained, groups[0]);
        parent.setMaxPriority(3);
        assertEquals(3, retained.getMaxPriority());
        System.out.println("Unused thread groups collected");
    }

    private static void createUnusedGroups(ThreadGroup parent, WeakReference<?>[] references) {
        var payload = new Object();
        var child = new ThreadGroup(parent, "unused") {
            @Override
            public String toString() {
                return payload.toString();
            }
        };
        assertEquals(payload.toString(), child.toString());
        new ThreadGroup(child, "grandchild");
        references[0] = new WeakReference<>(child);
        references[1] = new WeakReference<>(payload);
    }

    // ThreadGroup is eligible for collection once it has no live threads and is otherwise unreachable.
    @Test
    public void liveThreadKeepsItsGroupUntilTermination() throws InterruptedException {
        checkGroupLifetime(false);
        System.out.println("Live thread group lifetime checked");
    }

    @Test
    @SkipJVM // TeaVM releases its own group metadata even if an application retains the terminated Thread.
    public void terminatedThreadDoesNotRetainGroupMetadata() throws InterruptedException {
        checkGroupLifetime(true);
        System.out.println("Terminated thread group metadata released");
    }

    private static void checkGroupLifetime(boolean retainThread) throws InterruptedException {
        var parent = new ThreadGroup("parent");
        var references = new WeakReference<?>[1];
        var members = new Thread[1];
        var failure = new Throwable[1];
        // No referent read or construction temporary survives in this test's frame at the final GC.
        var creator = new Thread(() -> {
            var started = new AtomicBoolean();
            var worker = createGroupMember(parent, references, started);
            members[0] = worker;
            worker.start();
            try {
                while (!started.get()) {
                    Thread.sleep(1);
                }
                GCSupport.tryToTriggerGC();
                assertSame(references[0].get(), worker.getThreadGroup());
                assertEquals(1, parent.activeGroupCount());
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            } finally {
                worker.interrupt();
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        });
        creator.setUncaughtExceptionHandler((thread, error) -> failure[0] = error);
        creator.start();
        awaitCreator(creator);
        assertNull(failure[0]);
        assertNull(members[0].getThreadGroup());
        if (!retainThread) {
            members[0] = null;
        }
        GCSupport.tryToTriggerGC(references[0]);
        Thread.sleep(20);
        assertNull(references[0].get());
        assertEquals(0, parent.activeGroupCount());
        if (retainThread) {
            assertFalse(members[0].isAlive());
        }
    }

    private static void awaitCreator(Thread creator) throws InterruptedException {
        // C's collector does not trace packed monitor references. Keep collection in the creator independent
        // of that existing runtime limitation by waiting without an active join monitor.
        while (creator.isAlive()) {
            Thread.sleep(1);
        }
        creator.join();
    }

    private static Thread createGroupMember(ThreadGroup parent, WeakReference<?>[] references, AtomicBoolean started) {
        var group = new ThreadGroup(parent, "member");
        references[0] = new WeakReference<>(group);
        return new Thread(group, () -> {
            started.set(true);
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                // Terminate when the test releases the live member.
            }
        });
    }

    @Test
    @SkipJVM // Observe TeaVM's registry node without keeping the node or group alive in the test frame.
    public void collectedGroupRegistryNodesAreReleased() throws InterruptedException {
        var parent = new ThreadGroup("parent");
        var groups = new WeakReference<?>[6];
        var nodes = new WeakReference<?>[6];
        var retained = new ThreadGroup[2];
        var failure = new Throwable[1];
        var creator = new Thread(() -> {
            try {
                var field = ThreadGroup.class.getDeclaredField("firstChild");
                field.setAccessible(true);
                for (int i = 0; i < groups.length; ++i) {
                    var child = new ThreadGroup(parent, Integer.toString(i));
                    groups[i] = new WeakReference<>(child);
                    nodes[i] = new WeakReference<>(field.get(parent));
                    if (i == 1 || i == 4) {
                        retained[i == 1 ? 0 : 1] = child;
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        creator.setUncaughtExceptionHandler((thread, error) -> failure[0] = error);
        creator.start();
        awaitCreator(creator);
        assertNull(failure[0]);
        // Keep the control parent and surviving children GC-live through the final node collection.
        retainedRegistryGroups = new ThreadGroup[] { parent, retained[0], retained[1] };
        try {
            GCSupport.tryToTriggerGC(groups[0]);
            Thread.sleep(20);
            // Collect the head, tail and adjacent middle nodes, leaving two live siblings in the registry.
            for (int index : new int[] { 0, 2, 3, 5 }) {
                assertNull(groups[index].get());
            }
            assertEquals(2, parent.activeGroupCount());
            var enumerated = new ThreadGroup[3];
            assertEquals(2, parent.enumerate(enumerated));
            assertTrue(Arrays.asList(enumerated).contains(retained[0]));
            assertTrue(Arrays.asList(enumerated).contains(retained[1]));
            parent.setMaxPriority(4);
            assertEquals(4, retained[0].getMaxPriority());
            assertEquals(4, retained[1].getMaxPriority());
            GCSupport.tryToTriggerGC(nodes[0]);
            assertSame(parent, retainedRegistryGroups[0]);
            assertSame(retained[0], retainedRegistryGroups[1]);
            assertSame(retained[1], retainedRegistryGroups[2]);
            for (int index : new int[] { 0, 2, 3, 5 }) {
                assertNull(nodes[index].get());
            }
            System.out.println("Thread group registry cleanup checked");
        } finally {
            retainedRegistryGroups = null;
        }
    }

    // ThreadGroup.interrupt interrupts every live member, independently of diagnostic method overrides.
    @Test
    public void interruptDoesNotUseOverridableEstimates() throws InterruptedException {
        var group = new ThreadGroup("overrides") {
            @Override
            public int activeCount() {
                return 0;
            }

            @Override
            public int enumerate(Thread[] threads) {
                return 0;
            }
        };
        var interrupted = new boolean[1];
        var worker = new Thread(group, () -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                interrupted[0] = true;
            }
        });
        worker.start();
        Thread.sleep(20);
        group.interrupt();
        worker.join(2000);
        try {
            assertTrue(interrupted[0]);
            assertFalse(worker.isAlive());
        } finally {
            worker.interrupt();
            worker.join();
        }
    }

    // ThreadGroup.getMaxPriority: "maximum priority for new threads created in the thread group".
    // Thread.setPriority sets the smaller of the requested priority and the group's maximum.
    @Test
    public void priorityCapsApplyToConstructionAndSetters() {
        var current = Thread.currentThread();
        int originalPriority = current.getPriority();
        try {
            current.setPriority(Thread.MAX_PRIORITY);
            for (int cap = Thread.MIN_PRIORITY; cap <= Thread.MAX_PRIORITY; ++cap) {
                var group = new ThreadGroup("cap");
                group.setMaxPriority(cap);
                var thread = new Thread(group, "worker");
                assertEquals(Math.min(current.getPriority(), cap), thread.getPriority());
                for (int requested = Thread.MIN_PRIORITY; requested <= Thread.MAX_PRIORITY; ++requested) {
                    thread.setPriority(requested);
                    assertEquals(Math.min(requested, cap), thread.getPriority());
                }
                for (int invalid : new int[] { Integer.MIN_VALUE, 0, 11, Integer.MAX_VALUE }) {
                    assertThrows(IllegalArgumentException.class, () -> thread.setPriority(invalid));
                }
                thread.setPriority(Thread.MAX_PRIORITY);
                int before = thread.getPriority();
                group.setMaxPriority(Thread.MIN_PRIORITY);
                // setMaxPriority does not change priorities of existing threads.
                assertEquals(before, thread.getPriority());
                thread.setPriority(Thread.MAX_PRIORITY);
                assertEquals(Thread.MIN_PRIORITY, thread.getPriority());
            }
        } finally {
            current.setPriority(originalPriority);
        }
    }

    // Thread: "Platform threads inherit the daemon status, thread priority".
    @Test
    public void threadConstructorsInheritCreatorMetadata() throws InterruptedException {
        var creator = new Thread(() -> {
            Thread[] children = {
                    new Thread(), new Thread("named"), new Thread(() -> { }),
                    new Thread(() -> { }, "target"), new Thread((ThreadGroup) null, () -> { }),
                    new Thread((ThreadGroup) null, "group"),
                    new Thread(null, () -> { }, "both"), new Thread(null, () -> { }, "stack", 0)
            };
            for (var child : children) {
                assertEquals(Thread.currentThread().getPriority(), child.getPriority());
                assertTrue(child.isDaemon());
                assertSame(Thread.currentThread().getThreadGroup(), child.getThreadGroup());
            }
        });
        var failure = new Throwable[1];
        creator.setUncaughtExceptionHandler((thread, error) -> failure[0] = error);
        creator.setDaemon(true);
        creator.setPriority(3);
        creator.start();
        awaitCreator(creator);
        assertNull(failure[0]);
    }

    // UncaughtExceptionHandler: "Any exception thrown by this method will be ignored".
    @Test
    public void throwingHandlersStillCompleteThreadLifecycle() throws InterruptedException {
        var calls = new int[1];
        var group = new ThreadGroup("throwing") {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                ++calls[0];
                throw new IllegalArgumentException();
            }
        };
        for (int explicit = 0; explicit < 2; ++explicit) {
            var worker = new Thread(group, () -> {
                throw new IllegalStateException();
            });
            if (explicit != 0) {
                worker.setUncaughtExceptionHandler((thread, error) -> {
                    ++calls[0];
                    throw new IllegalArgumentException();
                });
            }
            worker.start();
            worker.join(2000);
            assertFalse(worker.isAlive());
            assertNull(worker.getThreadGroup());
            assertFalse(Thread.getAllStackTraces().containsKey(worker));
        }
        assertEquals(2, calls[0]);
        assertEquals(0, group.activeCount());
    }

    // ThreadGroup.uncaughtException delegates to parents, then the default handler, then stderr.
    @Test
    public void uncaughtExceptionDelegationAndFallback() {
        var oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        var oldError = System.err;
        var output = new ByteArrayOutputStream();
        var group = new ThreadGroup("child");
        var thread = new Thread("fallback-worker");
        var error = new IllegalStateException();
        var seen = new Throwable[1];
        try {
            Thread.setDefaultUncaughtExceptionHandler((source, thrown) -> {
                assertSame(thread, source);
                seen[0] = thrown;
            });
            group.uncaughtException(thread, error);
            assertSame(error, seen[0]);
            Thread.setDefaultUncaughtExceptionHandler(null);
            System.setErr(new PrintStream(output));
            group.uncaughtException(thread, error);
            assertTrue(output.toString().contains("fallback-worker"));
            assertTrue(output.toString().contains("IllegalStateException"));
        } finally {
            System.setErr(oldError);
            Thread.setDefaultUncaughtExceptionHandler(oldHandler);
        }
    }

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
        awaitCreator(creator);
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
