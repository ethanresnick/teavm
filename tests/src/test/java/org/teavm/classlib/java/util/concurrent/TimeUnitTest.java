/*
 *  Copyright 2018 Alexey Andreev.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class TimeUnitTest {
    @Test
    public void convert() {
        assertEquals(60, TimeUnit.MINUTES.toSeconds(1));
        assertEquals(30000, TimeUnit.SECONDS.toMillis(30));
        assertEquals(2, TimeUnit.DAYS.convert(48, TimeUnit.HOURS));
        assertEquals(180, TimeUnit.MINUTES.convert(3, TimeUnit.HOURS));
    }

    @Test
    public void conversionSaturates() {
        assertEquals(Long.MAX_VALUE, TimeUnit.SECONDS.toMillis(Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, TimeUnit.SECONDS.toMillis(Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, TimeUnit.DAYS.toNanos(Long.MAX_VALUE / 1000));
        assertEquals(Long.MIN_VALUE, TimeUnit.HOURS.toMicros(-Long.MAX_VALUE / 1000));
        assertEquals(Long.MAX_VALUE, TimeUnit.NANOSECONDS.convert(Long.MAX_VALUE, TimeUnit.SECONDS));
        assertEquals(Long.MAX_VALUE / 1000 * 1000, TimeUnit.SECONDS.toMillis(Long.MAX_VALUE / 1000));
        assertEquals(Long.MAX_VALUE, TimeUnit.NANOSECONDS.toNanos(Long.MAX_VALUE));
    }

    @Test
    public void sleepWithNonPositiveTimeoutReturns() throws InterruptedException {
        TimeUnit.SECONDS.sleep(0);
        TimeUnit.SECONDS.sleep(-1);
    }

    @Test
    public void timedWaitWithZeroTimeoutReturns() throws InterruptedException {
        Object lock = new Object();
        // Wakes the wait below if it waits forever, so the test fails instead of hanging.
        Thread notifier = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (lock) {
                lock.notifyAll();
            }
        });
        notifier.start();
        long start = System.currentTimeMillis();
        synchronized (lock) {
            TimeUnit.SECONDS.timedWait(lock, 0);
        }
        long elapsed = System.currentTimeMillis() - start;
        notifier.interrupt();
        assertTrue(elapsed < 1000, "timedWait(0) waited " + elapsed + " ms");
    }

    @Test
    public void convertDuration() {
        assertEquals(3004005, TimeUnit.MICROSECONDS.convert(Duration.ofSeconds(3, 4_005_006)));
        assertEquals(2, TimeUnit.DAYS.convert(Duration.ofHours(49)));
        // A negative Duration rounds toward zero, as the other conversions do.
        assertEquals(-999, TimeUnit.MILLISECONDS.convert(Duration.ofSeconds(-1, 1)));
        assertEquals(-1, TimeUnit.SECONDS.convert(Duration.ofMillis(-1500)));
        assertEquals(Long.MAX_VALUE, TimeUnit.NANOSECONDS.convert(Duration.ofSeconds(Long.MAX_VALUE)));
        assertEquals(Long.MIN_VALUE, TimeUnit.NANOSECONDS.convert(Duration.ofSeconds(Long.MIN_VALUE)));
        // The seconds fit, and the overflow comes from adding the nanoseconds.
        assertEquals(Long.MAX_VALUE, TimeUnit.NANOSECONDS.convert(Duration.ofSeconds(9223372036L, 854775807)));
        assertEquals(Long.MAX_VALUE, TimeUnit.NANOSECONDS.convert(Duration.ofSeconds(9223372036L, 854775808)));
        assertEquals(Long.MIN_VALUE, TimeUnit.NANOSECONDS.convert(Duration.ofSeconds(-9223372037L, 145224191)));
    }

    @Test
    public void chronoUnitMapping() {
        var units = TimeUnit.values();
        var chronoUnits = new ChronoUnit[] { ChronoUnit.NANOS, ChronoUnit.MICROS, ChronoUnit.MILLIS,
                ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS };
        assertEquals(chronoUnits.length, units.length);
        for (var i = 0; i < units.length; ++i) {
            assertEquals(chronoUnits[i], units[i].toChronoUnit());
            assertEquals(units[i], TimeUnit.of(chronoUnits[i]));
        }
        assertThrows(IllegalArgumentException.class, () -> TimeUnit.of(ChronoUnit.WEEKS));
        assertThrows(NullPointerException.class, () -> TimeUnit.of(null));
    }
}
