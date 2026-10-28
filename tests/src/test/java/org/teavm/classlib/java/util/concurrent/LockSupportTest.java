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
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

/*
 * TeaVM port of jsr166's LockSupportTest for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/LockSupportTest.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * JUnit 3 test methods have @Test; the base class JSR166TestCase is the cut-down TeaVM port.
 * Changed: spin loops call spinWait() (a 1 ms sleep) instead of Thread.yield(), because TeaVM's
 * Thread.yield() switches threads only after a 100 ms time slice.
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ImportOrder|LeftCurly|MethodName|NeedBraces|NoWhitespaceAfter|WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;


@TeaVMTest
public class LockSupportTest extends JSR166TestCase {
    // android-note: Removed because the CTS runner does a bad job of
    // retrying tests that have suite() declarations.
    //
    // public static void main(String[] args) {
    //     main(suite(), args);
    // }
    // public static Test suite() {
    //     return new TestSuite(LockSupportTest.class);
    // }

    static {
        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;
    }

    /**
     * Returns the blocker object used by tests in this file.
     * Any old object will do; we'll return a convenient one.
     */
    static Object theBlocker() {
        return LockSupportTest.class;
    }

    enum ParkMethod {
        park() {
            void park() {
                LockSupport.park();
            }
            void park(long millis) {
                throw new UnsupportedOperationException();
            }
        },
        parkUntil() {
            void park(long millis) {
                LockSupport.parkUntil(deadline(millis));
            }
        },
        parkNanos() {
            void park(long millis) {
                LockSupport.parkNanos(MILLISECONDS.toNanos(millis));
            }
        },
        parkBlocker() {
            void park() {
                LockSupport.park(theBlocker());
            }
            void park(long millis) {
                throw new UnsupportedOperationException();
            }
        },
        parkUntilBlocker() {
            void park(long millis) {
                LockSupport.parkUntil(theBlocker(), deadline(millis));
            }
        },
        parkNanosBlocker() {
            void park(long millis) {
                LockSupport.parkNanos(theBlocker(),
                                      MILLISECONDS.toNanos(millis));
            }
        };

        void park() { park(2 * LONG_DELAY_MS); }
        abstract void park(long millis);

        /** Returns a deadline to use with parkUntil. */
        long deadline(long millis) {
            // beware of rounding
            return System.currentTimeMillis() + millis + 1;
        }
    }

    /**
     * park is released by subsequent unpark
     */
    @Test
    public void testParkBeforeUnpark_park() {
        testParkBeforeUnpark(ParkMethod.park);
    }
    @Test
    public void testParkBeforeUnpark_parkNanos() {
        testParkBeforeUnpark(ParkMethod.parkNanos);
    }
    @Test
    public void testParkBeforeUnpark_parkUntil() {
        testParkBeforeUnpark(ParkMethod.parkUntil);
    }
    @Test
    public void testParkBeforeUnpark_parkBlocker() {
        testParkBeforeUnpark(ParkMethod.parkBlocker);
    }
    @Test
    public void testParkBeforeUnpark_parkNanosBlocker() {
        testParkBeforeUnpark(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testParkBeforeUnpark_parkUntilBlocker() {
        testParkBeforeUnpark(ParkMethod.parkUntilBlocker);
    }
    public void testParkBeforeUnpark(final ParkMethod parkMethod) {
        final CountDownLatch pleaseUnpark = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                pleaseUnpark.countDown();
                parkMethod.park();
            }});

        await(pleaseUnpark);
        LockSupport.unpark(t);
        awaitTermination(t);
    }

    /**
     * park is released by preceding unpark
     */
    @Test
    public void testParkAfterUnpark_park() {
        testParkAfterUnpark(ParkMethod.park);
    }
    @Test
    public void testParkAfterUnpark_parkNanos() {
        testParkAfterUnpark(ParkMethod.parkNanos);
    }
    @Test
    public void testParkAfterUnpark_parkUntil() {
        testParkAfterUnpark(ParkMethod.parkUntil);
    }
    @Test
    public void testParkAfterUnpark_parkBlocker() {
        testParkAfterUnpark(ParkMethod.parkBlocker);
    }
    @Test
    public void testParkAfterUnpark_parkNanosBlocker() {
        testParkAfterUnpark(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testParkAfterUnpark_parkUntilBlocker() {
        testParkAfterUnpark(ParkMethod.parkUntilBlocker);
    }
    public void testParkAfterUnpark(final ParkMethod parkMethod) {
        final CountDownLatch pleaseUnpark = new CountDownLatch(1);
        final AtomicBoolean pleasePark = new AtomicBoolean(false);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                pleaseUnpark.countDown();
                while (!pleasePark.get())
                    spinWait();
                parkMethod.park();
            }});

        await(pleaseUnpark);
        LockSupport.unpark(t);
        pleasePark.set(true);
        awaitTermination(t);
    }

    /**
     * park is released by subsequent interrupt
     */
    @Test
    public void testParkBeforeInterrupt_park() {
        testParkBeforeInterrupt(ParkMethod.park);
    }
    @Test
    public void testParkBeforeInterrupt_parkNanos() {
        testParkBeforeInterrupt(ParkMethod.parkNanos);
    }
    @Test
    public void testParkBeforeInterrupt_parkUntil() {
        testParkBeforeInterrupt(ParkMethod.parkUntil);
    }
    @Test
    public void testParkBeforeInterrupt_parkBlocker() {
        testParkBeforeInterrupt(ParkMethod.parkBlocker);
    }
    @Test
    public void testParkBeforeInterrupt_parkNanosBlocker() {
        testParkBeforeInterrupt(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testParkBeforeInterrupt_parkUntilBlocker() {
        testParkBeforeInterrupt(ParkMethod.parkUntilBlocker);
    }
    public void testParkBeforeInterrupt(final ParkMethod parkMethod) {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                pleaseInterrupt.countDown();
                do {
                    parkMethod.park();
                    // park may return spuriously
                } while (! Thread.currentThread().isInterrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * park is released by preceding interrupt
     */
    @Test
    public void testParkAfterInterrupt_park() {
        testParkAfterInterrupt(ParkMethod.park);
    }
    @Test
    public void testParkAfterInterrupt_parkNanos() {
        testParkAfterInterrupt(ParkMethod.parkNanos);
    }
    @Test
    public void testParkAfterInterrupt_parkUntil() {
        testParkAfterInterrupt(ParkMethod.parkUntil);
    }
    @Test
    public void testParkAfterInterrupt_parkBlocker() {
        testParkAfterInterrupt(ParkMethod.parkBlocker);
    }
    @Test
    public void testParkAfterInterrupt_parkNanosBlocker() {
        testParkAfterInterrupt(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testParkAfterInterrupt_parkUntilBlocker() {
        testParkAfterInterrupt(ParkMethod.parkUntilBlocker);
    }
    public void testParkAfterInterrupt(final ParkMethod parkMethod) {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        final AtomicBoolean pleasePark = new AtomicBoolean(false);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                pleaseInterrupt.countDown();
                while (!pleasePark.get())
                    spinWait();
                assertTrue(Thread.currentThread().isInterrupted());
                parkMethod.park();
                assertTrue(Thread.currentThread().isInterrupted());
            }});

        await(pleaseInterrupt);
        t.interrupt();
        pleasePark.set(true);
        awaitTermination(t);
    }

    /**
     * timed park times out if not unparked
     */
    @Test
    public void testParkTimesOut_parkNanos() {
        testParkTimesOut(ParkMethod.parkNanos);
    }
    @Test
    public void testParkTimesOut_parkUntil() {
        testParkTimesOut(ParkMethod.parkUntil);
    }
    @Test
    public void testParkTimesOut_parkNanosBlocker() {
        testParkTimesOut(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testParkTimesOut_parkUntilBlocker() {
        testParkTimesOut(ParkMethod.parkUntilBlocker);
    }
    public void testParkTimesOut(final ParkMethod parkMethod) {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                for (;;) {
                    long startTime = System.nanoTime();
                    parkMethod.park(timeoutMillis());
                    // park may return spuriously
                    if (millisElapsedSince(startTime) >= timeoutMillis())
                        return;
                }
            }});

        awaitTermination(t);
    }

    /**
     * getBlocker(null) throws NullPointerException
     */
    @Test
    public void testGetBlockerNull() {
        try {
            LockSupport.getBlocker(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getBlocker returns the blocker object passed to park
     */
    @Test
    public void testGetBlocker_parkBlocker() {
        testGetBlocker(ParkMethod.parkBlocker);
    }
    @Test
    public void testGetBlocker_parkNanosBlocker() {
        testGetBlocker(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testGetBlocker_parkUntilBlocker() {
        testGetBlocker(ParkMethod.parkUntilBlocker);
    }
    public void testGetBlocker(final ParkMethod parkMethod) {
        final CountDownLatch started = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                Thread t = Thread.currentThread();
                started.countDown();
                do {
                    assertNull(LockSupport.getBlocker(t));
                    parkMethod.park();
                    assertNull(LockSupport.getBlocker(t));
                    // park may return spuriously
                } while (! Thread.currentThread().isInterrupted());
            }});

        long startTime = System.nanoTime();
        await(started);
        for (;;) {
            Object x = LockSupport.getBlocker(t);
            if (x == theBlocker()) { // success
                t.interrupt();
                awaitTermination(t);
                assertNull(LockSupport.getBlocker(t));
                return;
            } else {
                assertNull(x);  // ok
                if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                    fail("timed out");
                spinWait();
            }
        }
    }

    /**
     * timed park(0) returns immediately.
     *
     * Requires hotspot fix for:
     * 6763959 java.util.concurrent.locks.LockSupport.parkUntil(0) blocks forever
     * which is in jdk7-b118 and 6u25.
     */
    @Test
    public void testPark0_parkNanos() {
        testPark0(ParkMethod.parkNanos);
    }
    @Test
    public void testPark0_parkUntil() {
        testPark0(ParkMethod.parkUntil);
    }
    @Test
    public void testPark0_parkNanosBlocker() {
        testPark0(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testPark0_parkUntilBlocker() {
        testPark0(ParkMethod.parkUntilBlocker);
    }
    public void testPark0(final ParkMethod parkMethod) {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                parkMethod.park(0L);
            }});

        awaitTermination(t);
    }

    /**
     * timed park(Long.MIN_VALUE) returns immediately.
     */
    @Test
    public void testParkNeg_parkNanos() {
        testParkNeg(ParkMethod.parkNanos);
    }
    @Test
    public void testParkNeg_parkUntil() {
        testParkNeg(ParkMethod.parkUntil);
    }
    @Test
    public void testParkNeg_parkNanosBlocker() {
        testParkNeg(ParkMethod.parkNanosBlocker);
    }
    @Test
    public void testParkNeg_parkUntilBlocker() {
        testParkNeg(ParkMethod.parkUntilBlocker);
    }
    public void testParkNeg(final ParkMethod parkMethod) {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                parkMethod.park(Long.MIN_VALUE);
            }});

        awaitTermination(t);
    }

    // TeaVM: a park that returns at once must still let other threads run, because green threads switch only when
    // a thread blocks or yields.
    @Test
    public void testParkWithPermitLetsOtherThreadsRun() throws Throwable {
        Thread current = Thread.currentThread();
        assertSpinningLetsOtherThreadsRun(() -> {
            LockSupport.unpark(current);
            LockSupport.park();
        });
    }

    @Test
    public void testParkOfInterruptedThreadLetsOtherThreadsRun() throws Throwable {
        final boolean[] ran = new boolean[1];
        Thread other = new Thread(() -> ran[0] = true);
        other.start();
        Thread.currentThread().interrupt();
        long startTime = System.nanoTime();
        while (!ran[0] && millisElapsedSince(startTime) < LONG_DELAY_MS) {
            LockSupport.park();
        }
        // park leaves the interrupt status set; clear it before join().
        assertTrue(Thread.interrupted());
        assertTrue("the other thread did not run", ran[0]);
        other.join();
    }
}
