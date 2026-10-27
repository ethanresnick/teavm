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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class DivisionByZeroTest {
    // Fields, so that the compiler cannot fold the divisions.
    private static int intZero;
    private static long longZero;
    private static int intSeven = 7;
    private static long longSeven = 7;
    private static int intMinusOne = -1;
    private static long longMinusOne = -1;
    private static int intMin = Integer.MIN_VALUE;
    private static long longMin = Long.MIN_VALUE;

    @Test
    public void intDivisionByZeroThrows() {
        try {
            var result = intSeven / intZero;
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void intRemainderByZeroThrows() {
        try {
            var result = intSeven % intZero;
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void longDivisionByZeroThrows() {
        try {
            var result = longSeven / longZero;
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void longRemainderByZeroThrows() {
        try {
            var result = longSeven % longZero;
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    // The exception leaves the method that divides and reaches the caller's handler.
    @Test
    public void divisionByZeroInCalleeThrows() {
        try {
            var result = divide(intSeven, intZero);
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            assertEquals("java.lang.ArithmeticException", e.getClass().getName());
        }
    }

    private static int divide(int a, int b) {
        return a / b;
    }

    // A handler for a supertype also receives the exception.
    @Test
    public void divisionByZeroCaughtAsRuntimeException() {
        try {
            var result = longSeven % longZero;
            fail("no exception, result " + result);
        } catch (RuntimeException e) {
            assertEquals("java.lang.ArithmeticException", e.getClass().getName());
        }
    }

    // A constant zero divisor still throws: the compiler keeps the division, as javac does.
    @SuppressWarnings("divzero")
    @Test
    public void constantZeroDivisorThrows() {
        try {
            var result = intSeven / 0;
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
        try {
            var result = longSeven % 0L;
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    // Java defines this overflow: the quotient is MIN_VALUE and the remainder is 0, with no exception.
    @Test
    public void minValueDividedByMinusOne() {
        assertEquals(Integer.MIN_VALUE, intMin / intMinusOne);
        assertEquals(0, intMin % intMinusOne);
        assertEquals(Long.MIN_VALUE, longMin / longMinusOne);
        assertEquals(0L, longMin % longMinusOne);
        assertEquals(Integer.MIN_VALUE, intMin / -1);
        assertEquals(Long.MIN_VALUE, longMin / -1L);
        assertEquals(0, intMin % -1);
        assertEquals(0L, longMin % -1L);
    }

    @Test
    public void nonZeroDivisorsStillDivide() {
        assertEquals(-3, -22 / intSeven);
        assertEquals(-1, -22 % intSeven);
        assertEquals(-3L, -22L / longSeven);
        assertEquals(-1L, -22L % longSeven);
        assertEquals(22, -22 / intMinusOne);
        assertEquals(0, intZero / intSeven);
    }
}
