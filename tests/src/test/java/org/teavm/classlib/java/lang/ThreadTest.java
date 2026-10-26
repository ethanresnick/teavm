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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ThreadTest {
    @Test
    public void sleeps() throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(100);
        long duration = System.currentTimeMillis() - start;
        assertTrue(duration >= 100, "Thread.sleep did not wait enough: " + duration);
    }

    @Test
    public void sleepInterrupted() {
        long start = System.currentTimeMillis();
        var mainThread = Thread.currentThread();
        new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ok
            }
            mainThread.interrupt();
        }).start();
        try {
            Thread.sleep(5000);
            fail("Exception expected");
        } catch (InterruptedException e) {
            long end = System.currentTimeMillis();
            assertEquals(Thread.currentThread(), mainThread);
            assertFalse(mainThread.isInterrupted());
            assertTrue(end - start < 5000, "Wait time " + (end - start));
        }
    }

    @Test
    public void sleepAfterInterruptedSleep() throws InterruptedException {
        var mainThread = Thread.currentThread();
        new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ok
            }
            mainThread.interrupt();
        }).start();
        try {
            Thread.sleep(5000);
            fail("Exception expected");
        } catch (InterruptedException e) {
            // expected
        }
        // A later suspension on the same thread must not throw the earlier exception again.
        long start = System.currentTimeMillis();
        Thread.sleep(20);
        assertTrue(System.currentTimeMillis() - start >= 20);
    }

    @Test
    public void catchesAsyncException() {
        try {
            throwException();
            fail("Exception should have been thrown");
        } catch (IllegalStateException e) {
            // all is ok
        }
    }


    private void throwException() {
        Thread.yield();
        throw new IllegalStateException();
    }

    @Test
    public void asyncVirtualCallsSupported() {
        List<A> alist = new ArrayList<>();
        alist.add(new A() {
            @Override int foo() {
                return 3;
            }
        });
        alist.add(new A() {
            @Override int foo() {
                Thread.yield();
                return 5;
            }
        });
        int sum = 0;
        for (A a : alist) {
            sum += a.foo();
        }
        assertEquals(8, sum);
    }
    
    @Test
    public void join() throws InterruptedException {
        var runnable = new Runnable() {
            boolean finished;
            
            @Override
            public void run() {
                try {
                    Thread.sleep(750);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                finished = true;
            }
        };
        var other = new Thread(runnable);
        other.start();
        Thread.sleep(150);
        assertFalse(runnable.finished);
        other.join();
        assertTrue(runnable.finished);
    }

    @Test
    public void joinAlreadyFinished() throws InterruptedException {
        var runnable = new Runnable() {
            boolean finished;

            @Override
            public void run() {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                finished = true;
            }
        };
        var other = new Thread(runnable);
        other.start();
        Thread.sleep(750);
        assertTrue(runnable.finished);
        other.join();
        assertTrue(runnable.finished);
    }

    abstract class A {
        abstract int foo();
    }

    @Test
    public void threadStates() throws InterruptedException {
        var thread = new Thread(() -> { });
        assertEquals(Thread.State.NEW, thread.getState());
        thread.start();
        thread.join();
        assertEquals(Thread.State.TERMINATED, thread.getState());
        assertEquals(Thread.State.RUNNABLE, Thread.currentThread().getState());
    }

    @Test
    public void sleepOverloadsAndSpinWait() throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(20, 500);
        Thread.sleep(Duration.ofMillis(20));
        Thread.onSpinWait();
        assertTrue(System.currentTimeMillis() - start >= 40);
    }

    @Test
    public void joinWithLongMaxValueWaits() throws InterruptedException {
        var done = new boolean[1];
        var thread = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // ignore
            }
            done[0] = true;
        });
        thread.start();
        thread.join(Long.MAX_VALUE);
        assertTrue(done[0]);
        assertFalse(thread.isAlive());
    }

    @Test
    public void contextClassLoaderIsKeptAndInherited() throws InterruptedException {
        var current = Thread.currentThread();
        var original = current.getContextClassLoader();
        var loader = new ClassLoader() { };
        try {
            current.setContextClassLoader(loader);
            assertSame(loader, current.getContextClassLoader());
            var seen = new ClassLoader[1];
            var thread = new Thread(() -> seen[0] = Thread.currentThread().getContextClassLoader());
            thread.start();
            thread.join();
            assertSame(loader, seen[0]);
        } finally {
            current.setContextClassLoader(original);
        }
    }
}
