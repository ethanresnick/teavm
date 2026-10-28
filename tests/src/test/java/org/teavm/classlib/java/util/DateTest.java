/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.classlib.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.Date;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class DateTest {
    @SuppressWarnings("deprecation")
    @Test
    public void setsDateAndMonth() {
        Date date = new Date();
        date.setMonth(0);
        date.setDate(4);
        date.setYear(115);
        assertEquals(0, date.getMonth());
        assertEquals(4, date.getDate());
        assertEquals(115, date.getYear());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void setsUTC() {
        long epochTime = Date.UTC(2023, 1, 20, 10, 0, 0);
        Date date = new Date(epochTime);
        assertEquals(2023, date.getYear());
        assertEquals(1, date.getMonth());
        assertEquals(20, date.getDate());
    }

    // Date.toInstant(): "Converts this Date object to an Instant. The conversion creates an Instant that
    // represents the same point on the time-line as this Date."
    @Test
    public void toInstant() {
        for (var millis : new long[] { 0, 1, -1, 1600000000123L, -1600000000123L, Long.MAX_VALUE }) {
            assertEquals(Instant.ofEpochMilli(millis), new Date(millis).toInstant(), String.valueOf(millis));
            assertEquals(millis, new Date(millis).toInstant().toEpochMilli());
        }
    }

    // Date.from(Instant): "Instant uses a precision of nanoseconds, whereas Date uses a precision of
    // milliseconds. The conversion will truncate any excess precision information as though the amount in
    // nanoseconds was subject to integer division by one million."
    // "Throws: NullPointerException - if instant is null. IllegalArgumentException - if the instant is too
    // large to represent as a Date"
    @Test
    public void from() {
        assertEquals(5, Date.from(Instant.ofEpochMilli(5)).getTime());
        assertEquals(-5, Date.from(Instant.ofEpochMilli(-5)).getTime());
        assertEquals(1999, Date.from(Instant.ofEpochSecond(1, 999_999_999)).getTime());
        // Instant.toEpochMilli() rounds towards negative infinity: -1 s + 1 ns is -999.999999 ms.
        assertEquals(-1000, Date.from(Instant.ofEpochSecond(-1, 1)).getTime());
        assertEquals(Long.MAX_VALUE, Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)).getTime());
        for (var millis : new long[] { 0, 7, -7, 1600000000123L, -1600000000123L }) {
            var date = new Date(millis);
            assertEquals(date, Date.from(date.toInstant()));
        }
        assertThrows(NullPointerException.class, () -> Date.from(null));
        assertThrows(IllegalArgumentException.class, () -> Date.from(Instant.MAX));
        assertThrows(IllegalArgumentException.class, () -> Date.from(Instant.MIN));
        assertThrows(IllegalArgumentException.class, () -> Date.from(Instant.ofEpochMilli(Long.MAX_VALUE)
                .plusMillis(1)));
    }

    @Test
    public void instantConversions() {
        assertEquals(Instant.ofEpochMilli(1600000000123L), new Date(1600000000123L).toInstant());
        assertEquals(5, Date.from(Instant.ofEpochMilli(5)).getTime());
    }
}
