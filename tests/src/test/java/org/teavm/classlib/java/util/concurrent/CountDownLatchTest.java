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
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

/*
 * TeaVM port of jsr166's CountDownLatchTest for TeaVM's test runner (JUnit 5).
 * Source: Android libcore, jsr166-tests/src/test/java/jsr166/CountDownLatchTest.java,
 * revision de876a01b29230b877c9f408348c38a90ee724a5.
 * JUnit 3 test methods have @Test; the base class JSR166TestCase is the cut-down TeaVM port.
 */

// The jsr166 code keeps its own style, so these checks are off for this file.
// CHECKSTYLE.OFF: EmptyBlock|ImportOrder|WhitespaceAround
package org.teavm.classlib.java.util.concurrent;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;


@TeaVMTest
public class CountDownLatchTest extends JSR166TestCase {
    // android-note: Removed because the CTS runner does a bad job of
    // retrying tests that have suite() declarations.
    //
    // public static void main(String[] args) {
    //     main(suite(), args);
    // }
    // public static Test suite() {
    //     return new TestSuite(CountDownLatchTest.class);
    // }

    /**
     * negative constructor argument throws IAE
     */
    @Test
    public void testConstructor() {
        try {
            new CountDownLatch(-1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getCount returns initial count and decreases after countDown
     */
    @Test
    public void testGetCount() {
        final CountDownLatch l = new CountDownLatch(2);
        assertEquals(2, l.getCount());
        l.countDown();
        assertEquals(1, l.getCount());
    }

    /**
     * countDown decrements count when positive and has no effect when zero
     */
    @Test
    public void testCountDown() {
        final CountDownLatch l = new CountDownLatch(1);
        assertEquals(1, l.getCount());
        l.countDown();
        assertEquals(0, l.getCount());
        l.countDown();
        assertEquals(0, l.getCount());
    }

    /**
     * await returns after countDown to zero, but not before
     */
    @Test
    public void testAwait() {
        final CountDownLatch l = new CountDownLatch(2);
        final CountDownLatch pleaseCountDown = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertEquals(2, l.getCount());
                pleaseCountDown.countDown();
                l.await();
                assertEquals(0, l.getCount());
            }});

        await(pleaseCountDown);
        assertEquals(2, l.getCount());
        l.countDown();
        assertEquals(1, l.getCount());
        assertThreadStaysAlive(t);
        l.countDown();
        assertEquals(0, l.getCount());
        awaitTermination(t);
    }

    /**
     * timed await returns after countDown to zero
     */
    @Test
    public void testTimedAwait() {
        final CountDownLatch l = new CountDownLatch(2);
        final CountDownLatch pleaseCountDown = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertEquals(2, l.getCount());
                pleaseCountDown.countDown();
                assertTrue(l.await(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(0, l.getCount());
            }});

        await(pleaseCountDown);
        assertEquals(2, l.getCount());
        l.countDown();
        assertEquals(1, l.getCount());
        assertThreadStaysAlive(t);
        l.countDown();
        assertEquals(0, l.getCount());
        awaitTermination(t);
    }

    /**
     * await throws IE if interrupted before counted down
     */
    @Test
    public void testAwait_Interruptible() {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.currentThread().interrupt();
                try {
                    l.await();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    l.await();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                assertEquals(1, l.getCount());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * timed await throws IE if interrupted before counted down
     */
    @Test
    public void testTimedAwait_Interruptible() {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.currentThread().interrupt();
                try {
                    l.await(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    l.await(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                assertEquals(1, l.getCount());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * timed await times out if not counted down before timeout
     */
    @Test
    public void testAwaitTimeout() throws InterruptedException {
        final CountDownLatch l = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertEquals(1, l.getCount());
                assertFalse(l.await(timeoutMillis(), MILLISECONDS));
                assertEquals(1, l.getCount());
            }});

        awaitTermination(t);
        assertEquals(1, l.getCount());
    }

    /**
     * toString indicates current count
     */
    @Test
    public void testToString() {
        CountDownLatch s = new CountDownLatch(2);
        assertTrue(s.toString().contains("Count = 2"));
        s.countDown();
        assertTrue(s.toString().contains("Count = 1"));
        s.countDown();
        assertTrue(s.toString().contains("Count = 0"));
    }


    // TeaVM: green threads need a preemption point in each blocking acquire, also when it succeeds at once.
    @Test
    public void testAwaitOfOpenLatchLetsOtherThreadsRun() throws Throwable {
        CountDownLatch latch = new CountDownLatch(0);
        assertSpinningLetsOtherThreadsRun(() -> latch.await());
    }

    @Test
    public void testTimedAwaitOfOpenLatchLetsOtherThreadsRun() throws Throwable {
        CountDownLatch latch = new CountDownLatch(0);
        assertSpinningLetsOtherThreadsRun(() -> assertTrue(latch.await(LONG_DELAY_MS, MILLISECONDS)));
    }
}
