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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.function.LongSupplier;
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

    // Compound assignment and narrower operand types compile to the same division instructions.
    @Test
    public void compoundAssignmentAndNarrowTypesThrow() {
        var i = intSeven;
        try {
            i /= intZero;
            fail("no exception, result " + i);
        } catch (ArithmeticException e) {
            // expected
        }
        var l = longSeven;
        try {
            l %= longZero;
            fail("no exception, result " + l);
        } catch (ArithmeticException e) {
            // expected
        }
        var b = (byte) intSeven;
        try {
            b /= (byte) intZero;
            fail("no exception, result " + b);
        } catch (ArithmeticException e) {
            // expected
        }
        var c = (char) intSeven;
        try {
            c %= (char) intZero;
            fail("no exception, result " + (int) c);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    // Integer.divideUnsigned and the like divide with the same instructions in every backend.
    @Test
    public void unsignedDivisionByZeroThrows() {
        try {
            var result = Integer.divideUnsigned(intSeven, intZero);
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
        try {
            var result = Integer.remainderUnsigned(intSeven, intZero);
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
        try {
            var result = Long.divideUnsigned(longSeven, longZero);
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
        try {
            var result = Long.remainderUnsigned(longSeven, longZero);
            fail("no exception, result " + result);
        } catch (RuntimeException e) {
            assertEquals("java.lang.ArithmeticException", e.getClass().getName());
        }
        try {
            var result = Integer.divideUnsigned(intSeven, 0);
            fail("no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }

    @Test
    public void unsignedDivisionByNonZero() {
        assertEquals(0x7FFFFFFF, Integer.divideUnsigned(intMinusOne, 2));
        assertEquals(1, Integer.remainderUnsigned(intMinusOne, 2));
        assertEquals(0, Integer.divideUnsigned(intSeven, intMinusOne));
        assertEquals(0x7FFFFFFFFFFFFFFFL, Long.divideUnsigned(longMinusOne, 2L));
        assertEquals(1L, Long.remainderUnsigned(longMinusOne, 2L));
        assertEquals(1L, Long.divideUnsigned(longMin, longMin));
    }

    private static final int[] INT_VALUES = { 0, 1, -1, 2, -2, 3, -3, 7, -7, 100, -100,
            Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1 };
    private static final long[] LONG_VALUES = { 0, 1, -1, 2, -2, 3, -3, 7, -7, 100, -100,
            Integer.MAX_VALUE, Integer.MIN_VALUE, (long) Integer.MAX_VALUE + 1, (long) Integer.MIN_VALUE - 1,
            Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MIN_VALUE, Long.MIN_VALUE + 1 };

    // For every pair of edge values, a zero divisor throws, and otherwise the results satisfy JLS 15.17.2 and
    // 15.17.3: (a / b) * b + (a % b) == a, the remainder is smaller than the divisor in magnitude and is zero or
    // has the sign of the dividend, and the quotient rounds toward zero.
    @Test
    public void intDivisionInvariants() {
        for (var a : INT_VALUES) {
            for (var b : INT_VALUES) {
                var pair = a + " / " + b;
                if (b == 0) {
                    assertThrowsArithmetic(pair, () -> a / b);
                    assertThrowsArithmetic(pair, () -> a % b);
                    continue;
                }
                int q = a / b;
                int r = a % b;
                assertEquals(a, q * b + r, pair);
                assertTrue(r == 0 || (r < 0) == (a < 0), pair);
                assertTrue(Math.abs((long) r) < Math.abs((long) b), pair);
                if (a == Integer.MIN_VALUE && b == -1) {
                    assertEquals(Integer.MIN_VALUE, q, pair);
                    assertEquals(0, r, pair);
                } else {
                    assertEquals((long) a / b, q, pair);
                }
            }
        }
    }

    @Test
    public void longDivisionInvariants() {
        for (var a : LONG_VALUES) {
            for (var b : LONG_VALUES) {
                var pair = a + " / " + b;
                if (b == 0) {
                    assertThrowsArithmetic(pair, () -> a / b);
                    assertThrowsArithmetic(pair, () -> a % b);
                    continue;
                }
                long q = a / b;
                long r = a % b;
                assertEquals(a, q * b + r, pair);
                assertTrue(r == 0 || (r < 0) == (a < 0), pair);
                assertTrue(b == Long.MIN_VALUE ? r != Long.MIN_VALUE : Math.abs(r) < Math.abs(b), pair);
                if (a == Long.MIN_VALUE && b == -1) {
                    assertEquals(Long.MIN_VALUE, q, pair);
                    assertEquals(0L, r, pair);
                }
            }
        }
    }

    private static void assertThrowsArithmetic(String message, LongSupplier operation) {
        try {
            var result = operation.getAsLong();
            fail(message + ": no exception, result " + result);
        } catch (ArithmeticException e) {
            // expected
        }
    }
}
