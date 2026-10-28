/*
 *  Copyright 2026 TeaVM contributors.
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigInteger;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * Accuracy of the Math methods whose Javadoc bounds their error. log10, cbrt, expm1, log1p and hypot: "The computed
 * result must be within 1 ulp of the exact result". sinh, cosh and tanh: "within 2.5 ulps of the exact result".
 * log10, expm1 and log1p: "Results must be semi-monotonic"; hypot: "If one parameter is held constant, the results
 * must be semi-monotonic in the other parameter".
 *
 * <p>The tables below hold inputs and the JVM's StrictMath results for them (fdlibm, which is itself within 1 ulp).
 * cbrt and hypot are checked against the exact result instead, with BigInteger arithmetic. Random log10 inputs are
 * compared with StrictMath.log10, an fdlibm port that StrictMathTest checks bit for bit against the JVM.
 */
@TeaVMTest
public class MathAccuracyTest {
    private static final double[] LOG10 = {
            Double.MIN_VALUE, -323.3062153431158, 1.0E-320, -320.000004834948, Double.MIN_NORMAL, -307.6526555685888,
            1.0E-300, -300.0, 1.0E-100, -100.0, 1.0E-20, -20.0, 1.0E-5, -5.0, 0.001, -3.0, 0.1, -1.0,
            0.2, -0.6989700043360187, 0.5, -0.3010299956639812, 0.9999999999999999, -4.8216373327664354E-17,
            1.0000000000000002, 9.64327466553287E-17, 2.0, 0.3010299956639812, 3.0, 0.47712125471966244,
            7.0, 0.8450980400142568, 9.999999999999998, 0.9999999999999999, 10.000000000000002, 1.0,
            99.0, 1.99563519459755, 101.0, 2.0043213737826426, 12345.678, 4.091514945509202, 1.0E23, 23.0,
            1.0E100, 100.0, 1.0E200, 200.0, 1.0E300, 300.0, Double.MAX_VALUE, 308.25471555991675
    };

    private static final double[] EXPM1 = {
            1.0E-300, 1.0E-300, -1.0E-300, -1.0E-300, 1.0E-20, 1.0E-20, -1.0E-20, -1.0E-20,
            1.0E-10, 1.00000000005E-10, -1.0E-10, -9.999999999500001E-11, 3.0E-6, 3.0000045000045E-6,
            -3.0E-6, -2.9999955000045E-6, 0.001, 0.0010005001667083417, -0.001, -9.995001666250085E-4,
            0.1, 0.10517091807564763, -0.1, -0.09516258196404043, 0.3, 0.3498588075760031, -0.3, -0.2591817793182821,
            0.5, 0.6487212707001282, -0.5, -0.3934693402873666, 0.75, 1.1170000166126748, -0.75, -0.5276334472589853,
            1.0, 1.718281828459045, -1.0, -0.6321205588285577, 1.5, 3.481689070338065, -1.5, -0.7768698398515702,
            2.0, 6.38905609893065, -2.0, -0.8646647167633873, 3.7, 39.4473043600674, -3.7, -0.9752764735296606,
            10.0, 22025.465794806718, -10.0, -0.9999546000702375, 22.5, 5.910522062023291E9,
            -22.5, -0.9999999998308102, 100.0, 2.6881171418161356E43, -100.0, -1.0, 700.0, 1.0142320547350045E304,
            -700.0, -1.0, 709.5, 1.3549863193146328E308, -709.5, -1.0, 710.0, Double.POSITIVE_INFINITY, -710.0, -1.0
    };

    private static final double[] LOG1P = {
            1.0E-300, 1.0E-300, -1.0E-300, -1.0E-300, 1.0E-20, 1.0E-20, -1.0E-20, -1.0E-20,
            1.0E-10, 9.999999999500001E-11, -1.0E-10, -1.00000000005E-10, 3.0E-6, 2.999995500009E-6,
            -3.0E-6, -3.000004500009E-6, 0.001, 9.995003330835331E-4, -0.001, -0.0010005003335835335,
            0.1, 0.09531017980432487, -0.1, -0.10536051565782631, 0.3, 0.26236426446749106,
            -0.3, -0.35667494393873234, 0.5, 0.4054651081081644, -0.5, -0.6931471805599453,
            -0.75, -1.3862943611198906, -0.999999, -13.815510557935518, 1.0, 0.6931471805599453,
            1.5, 0.9162907318741551, 2.0, 1.0986122886681096, 3.7, 1.547562508716013, 10.0, 2.3978952727983707,
            1.0E10, 23.025850930040455, 1.0E100, 230.25850929940458, 1.0E300, 690.7755278982137,
            Double.MAX_VALUE, 709.782712893384
    };

    private static final double[] SINH = {
            1.0E-300, 1.0E-300, -1.0E-300, -1.0E-300, 1.0E-20, 1.0E-20, -1.0E-20, -1.0E-20, 1.0E-10, 1.0E-10,
            -1.0E-10, -1.0E-10, 3.0E-6, 3.0000000000045004E-6, -3.0E-6, -3.0000000000045004E-6,
            0.001, 0.001000000166666675, -0.001, -0.001000000166666675, 0.1, 0.10016675001984403,
            -0.1, -0.10016675001984403, 0.3, 0.3045202934471426, -0.3, -0.3045202934471426, 0.5, 0.5210953054937474,
            -0.5, -0.5210953054937474, 0.75, 0.82231673193583, -0.75, -0.82231673193583, 1.0, 1.1752011936438014,
            -1.0, -1.1752011936438014, 1.5, 2.1292794550948173, -1.5, -2.1292794550948173, 2.0, 3.626860407847019,
            -2.0, -3.626860407847019, 3.7, 20.21129041679853, -3.7, -20.21129041679853, 10.0, 11013.232874703393,
            -10.0, -11013.232874703393, 22.5, 2.9552610315116453E9, -22.5, -2.9552610315116453E9,
            100.0, 1.3440585709080678E43, -100.0, -1.3440585709080678E43, 700.0, 5.0711602736750225E303,
            -700.0, -5.0711602736750225E303, 709.5, 6.774931596573164E307, -709.5, -6.774931596573164E307,
            710.0, 1.1169973830808557E308, -710.0, -1.1169973830808557E308
    };

    private static final double[] COSH = {
            1.0E-300, 1.0, -1.0E-300, 1.0, 1.0E-20, 1.0, -1.0E-20, 1.0, 1.0E-10, 1.0, -1.0E-10, 1.0,
            3.0E-6, 1.0000000000045, -3.0E-6, 1.0000000000045, 0.001, 1.0000005000000416, -0.001, 1.0000005000000416,
            0.1, 1.0050041680558035, -0.1, 1.0050041680558035, 0.3, 1.0453385141288605, -0.3, 1.0453385141288605,
            0.5, 1.1276259652063807, -0.5, 1.1276259652063807, 0.75, 1.2946832846768448, -0.75, 1.2946832846768448,
            1.0, 1.543080634815244, -1.0, 1.543080634815244, 1.5, 2.352409615243247, -1.5, 2.352409615243247,
            2.0, 3.7621956910836314, -2.0, 3.7621956910836314, 3.7, 20.23601394326887, -3.7, 20.23601394326887,
            10.0, 11013.232920103324, -10.0, 11013.232920103324, 22.5, 2.9552610315116453E9,
            -22.5, 2.9552610315116453E9, 100.0, 1.3440585709080678E43, -100.0, 1.3440585709080678E43,
            700.0, 5.0711602736750225E303, -700.0, 5.0711602736750225E303, 709.5, 6.774931596573164E307,
            -709.5, 6.774931596573164E307, 710.0, 1.1169973830808557E308, -710.0, 1.1169973830808557E308
    };

    private static final double[] TANH = {
            1.0E-300, 1.0E-300, -1.0E-300, -1.0E-300, 1.0E-20, 1.0E-20, -1.0E-20, -1.0E-20, 1.0E-10, 1.0E-10,
            -1.0E-10, -1.0E-10, 3.0E-6, 2.999999999991E-6, -3.0E-6, -2.999999999991E-6, 0.001, 9.999996666668E-4,
            -0.001, -9.999996666668E-4, 0.1, 0.09966799462495582, -0.1, -0.09966799462495582,
            0.3, 0.2913126124515909, -0.3, -0.2913126124515909, 0.5, 0.46211715726000974, -0.5, -0.46211715726000974,
            0.75, 0.6351489523872873, -0.75, -0.6351489523872873, 1.0, 0.7615941559557649, -1.0, -0.7615941559557649,
            1.5, 0.9051482536448664, -1.5, -0.9051482536448664, 2.0, 0.9640275800758169, -2.0, -0.9640275800758169,
            3.7, 0.9987782412811312, -3.7, -0.9987782412811312, 10.0, 0.9999999958776927, -10.0, -0.9999999958776927,
            22.5, 1.0, -22.5, -1.0, 100.0, 1.0, -100.0, -1.0, 700.0, 1.0, -700.0, -1.0, 709.5, 1.0, -709.5, -1.0,
            710.0, 1.0, -710.0, -1.0
    };

    // Javadoc of Math.log10: "If the argument is equal to 10^n for integer n, then the result is n. In particular,
    // if the argument is 1.0 (10^0), then the result is +0.0." 10^n is a double exactly for n from 0 to 22.
    @Test
    public void log10OfExactPowersOfTen() {
        double p = 1;
        for (int n = 0; n <= 22; ++n) {
            assertEquals(Double.valueOf(n), Double.valueOf(Math.log10(p)), "log10(1e" + n + ")");
            p *= 10;
        }
        assertEquals(0L, Double.doubleToRawLongBits(Math.log10(1)));
    }

    // Javadoc of Math.log10: "If the argument is NaN or less than zero, then the result is NaN. If the argument is
    // positive infinity, then the result is positive infinity. If the argument is positive zero or negative zero,
    // then the result is negative infinity."
    @Test
    public void log10SpecialValues() {
        assertTrue(Double.isNaN(Math.log10(Double.NaN)));
        assertTrue(Double.isNaN(Math.log10(-1)));
        assertTrue(Double.isNaN(Math.log10(-Double.MIN_VALUE)));
        assertTrue(Double.isNaN(Math.log10(Double.NEGATIVE_INFINITY)));
        assertEquals(Double.POSITIVE_INFINITY, Math.log10(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, Math.log10(0.0));
        assertEquals(Double.NEGATIVE_INFINITY, Math.log10(-0.0));
    }

    @Test
    public void log10OfReferenceValues() {
        checkTable("log10", Math::log10, LOG10, 1);
    }

    @Test
    public void log10OfSubnormals() {
        long state = 11;
        for (int n = 0; n < 4096; ++n) {
            state += 0x9E3779B97F4A7C15L;
            double x = Double.longBitsToDouble((mix(state) & 0x000FFFFFFFFFFFFFL) | 1);
            assertWithinUlps("log10", x, StrictMath.log10(x), Math.log10(x), 1);
        }
    }

    @Test
    public void log10OfRandomValues() {
        long state = 7;
        // Random bit patterns over all positive finite doubles.
        for (int n = 0; n < 1 << 15; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long bits = mix(state) & 0x7FFFFFFFFFFFFFFFL;
            if (bits < 0x7FF0000000000000L) {
                double x = Double.longBitsToDouble(bits);
                assertWithinUlps("log10", x, StrictMath.log10(x), Math.log10(x), 1);
            }
        }
        // Random values in (0, 1000).
        for (int n = 0; n < 1 << 14; ++n) {
            state += 0x9E3779B97F4A7C15L;
            double x = ((mix(state) >>> 11) + 1) * 0x1.0p-53 * 1000;
            assertWithinUlps("log10", x, StrictMath.log10(x), Math.log10(x), 1);
        }
    }

    @Test
    public void log10IsSemiMonotonic() {
        double p = 1;
        for (int n = 0; n <= 22; ++n) {
            checkMonotonic("log10", Math::log10, nextDown(p, 200), 400);
            p *= 10;
        }
        checkMonotonic("log10", Math::log10, Double.MIN_VALUE, 400);
        checkMonotonic("log10", Math::log10, nextDown(Double.MIN_NORMAL, 200), 400);
        checkMonotonic("log10", Math::log10, nextDown(Double.MAX_VALUE, 400), 400);
        checkRandomMonotonic("log10", Math::log10, false);
    }

    // Javadoc of Math.cbrt: "For positive finite x, cbrt(-x) == -cbrt(x) [...] If the argument is NaN, then the
    // result is NaN. If the argument is infinite, then the result is an infinity with the same sign as the argument.
    // If the argument is zero, then the result is a zero with the same sign as the argument."
    @Test
    public void cbrtSpecialValues() {
        assertTrue(Double.isNaN(Math.cbrt(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, Math.cbrt(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, Math.cbrt(Double.NEGATIVE_INFINITY));
        assertEquals(0L, Double.doubleToRawLongBits(Math.cbrt(0.0)));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(Math.cbrt(-0.0)));
    }

    @Test
    public void cbrtIsWithinOneUlp() {
        long state = 13;
        for (int n = 0; n < 3000; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long bits = mix(state) & 0x7FFFFFFFFFFFFFFFL;
            if (bits >= 0x7FF0000000000000L || bits == 0) {
                continue;
            }
            double x = Double.longBitsToDouble(bits);
            double c = Math.cbrt(x);
            // The exact cube root lies strictly between the neighbours of c iff their cubes enclose x.
            BigInteger exact = scaled(x, 3 * 1100);
            BigInteger lo = scaled(Math.nextDown(c), 1100);
            BigInteger hi = scaled(Math.nextUp(c), 1100);
            assertTrue(lo.pow(3).compareTo(exact) < 0 && hi.pow(3).compareTo(exact) > 0,
                    "cbrt(" + x + ") = " + c);
            assertEquals(Double.valueOf(-c), Double.valueOf(Math.cbrt(-x)), "cbrt(-" + x + ")");
        }
    }

    // Javadoc of Math.expm1: "If the argument is NaN, the result is NaN. If the argument is positive infinity, then
    // the result is positive infinity. If the argument is negative infinity, then the result is -1.0. If the argument
    // is zero, then the result is a zero with the same sign as the argument. [...] The result of expm1 for any finite
    // input must be greater than or equal to -1.0."
    @Test
    public void expm1() {
        assertTrue(Double.isNaN(Math.expm1(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, Math.expm1(Double.POSITIVE_INFINITY));
        assertEquals(-1.0, Math.expm1(Double.NEGATIVE_INFINITY));
        assertEquals(0L, Double.doubleToRawLongBits(Math.expm1(0.0)));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(Math.expm1(-0.0)));
        assertTrue(Math.expm1(-Double.MAX_VALUE) >= -1.0);
        checkTable("expm1", Math::expm1, EXPM1, 1);
        checkMonotonic("expm1", Math::expm1, -Double.MIN_NORMAL, 400);
        checkRandomMonotonic("expm1", Math::expm1, true);
    }

    // Javadoc of Math.log1p: "If the argument is NaN or less than -1, then the result is NaN. If the argument is
    // positive infinity, then the result is positive infinity. If the argument is negative one, then the result is
    // negative infinity. If the argument is zero, then the result is a zero with the same sign as the argument."
    @Test
    public void log1p() {
        assertTrue(Double.isNaN(Math.log1p(Double.NaN)));
        assertTrue(Double.isNaN(Math.log1p(-1.5)));
        assertTrue(Double.isNaN(Math.log1p(Double.NEGATIVE_INFINITY)));
        assertEquals(Double.POSITIVE_INFINITY, Math.log1p(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, Math.log1p(-1.0));
        assertEquals(0L, Double.doubleToRawLongBits(Math.log1p(0.0)));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(Math.log1p(-0.0)));
        checkTable("log1p", Math::log1p, LOG1P, 1);
        checkMonotonic("log1p", Math::log1p, -Double.MIN_NORMAL, 400);
        checkMonotonic("log1p", Math::log1p, -1.0, 400);
        checkRandomMonotonic("log1p", Math::log1p, false);
    }

    // Javadoc of Math.sinh: "If the argument is NaN, then the result is NaN. If the argument is infinite, then the
    // result is an infinity with the same sign as the argument. If the argument is zero, then the result is a zero
    // with the same sign as the argument."
    @Test
    public void sinh() {
        assertTrue(Double.isNaN(Math.sinh(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, Math.sinh(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, Math.sinh(Double.NEGATIVE_INFINITY));
        assertEquals(0L, Double.doubleToRawLongBits(Math.sinh(0.0)));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(Math.sinh(-0.0)));
        checkTable("sinh", Math::sinh, SINH, 2);
    }

    // Javadoc of Math.cosh: "If the argument is NaN, then the result is NaN. If the argument is infinite, then the
    // result is positive infinity. If the argument is zero, then the result is 1.0."
    @Test
    public void cosh() {
        assertTrue(Double.isNaN(Math.cosh(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, Math.cosh(Double.POSITIVE_INFINITY));
        assertEquals(Double.POSITIVE_INFINITY, Math.cosh(Double.NEGATIVE_INFINITY));
        assertEquals(1.0, Math.cosh(0.0));
        assertEquals(1.0, Math.cosh(-0.0));
        checkTable("cosh", Math::cosh, COSH, 2);
    }

    // Javadoc of Math.tanh: "If the argument is NaN, then the result is NaN. If the argument is zero, then the result
    // is a zero with the same sign as the argument. If the argument is positive infinity, then the result is +1.0. If
    // the argument is negative infinity, then the result is -1.0. [...] The absolute value of the result of tanh for
    // any finite input must be less than or equal to 1."
    @Test
    public void tanh() {
        assertTrue(Double.isNaN(Math.tanh(Double.NaN)));
        assertEquals(0L, Double.doubleToRawLongBits(Math.tanh(0.0)));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(Math.tanh(-0.0)));
        assertEquals(1.0, Math.tanh(Double.POSITIVE_INFINITY));
        assertEquals(-1.0, Math.tanh(Double.NEGATIVE_INFINITY));
        assertEquals(1.0, Math.tanh(Double.MAX_VALUE));
        assertEquals(-1.0, Math.tanh(-Double.MAX_VALUE));
        checkTable("tanh", Math::tanh, TANH, 2);
    }

    // Javadoc of Math.hypot: "If either argument is infinite, then the result is positive infinity. If either argument
    // is NaN and neither argument is infinite, then the result is NaN. If both arguments are zero, the result is
    // positive zero."
    @Test
    public void hypotSpecialValues() {
        assertEquals(Double.POSITIVE_INFINITY, Math.hypot(Double.POSITIVE_INFINITY, Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY, Math.hypot(Double.NaN, Double.NEGATIVE_INFINITY));
        assertEquals(Double.POSITIVE_INFINITY, Math.hypot(1, Double.NEGATIVE_INFINITY));
        assertTrue(Double.isNaN(Math.hypot(Double.NaN, 1)));
        assertTrue(Double.isNaN(Math.hypot(0, Double.NaN)));
        assertEquals(0L, Double.doubleToRawLongBits(Math.hypot(0.0, 0.0)));
        assertEquals(0L, Double.doubleToRawLongBits(Math.hypot(-0.0, -0.0)));
        assertEquals(5.0, Math.hypot(-3, 4));
        assertEquals(2.5, Math.hypot(0, -2.5));
        assertEquals(Double.MIN_VALUE, Math.hypot(Double.MIN_VALUE, 0));
        assertEquals(Double.POSITIVE_INFINITY, Math.hypot(Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(Double.MAX_VALUE, Math.hypot(Double.MAX_VALUE, 1));
    }

    @Test
    public void hypotIsWithinOneUlp() {
        long state = 17;
        for (int n = 0; n < 6000; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long r = mix(state);
            double x;
            double y;
            switch (n % 4) {
                case 0:
                    x = (r >>> 11) * 0x1.0p-53;
                    state += 0x9E3779B97F4A7C15L;
                    y = (mix(state) >>> 11) * 0x1.0p-53;
                    break;
                case 1:
                    // Nearly equal arguments.
                    x = (r >>> 11) * 0x1.0p-53 + 0.5;
                    y = x * (1 + (r & 0xFFFF) * 0x1.0p-30);
                    break;
                case 2:
                    // Arguments whose ratio is near 2^-27, where hypot switches to returning the larger one.
                    x = (r >>> 11) * 0x1.0p-53 + 0.5;
                    y = x * ((r & 0xFFFF) + 1) * 0x1.0p-43;
                    break;
                default: {
                    // Any finite magnitudes, including subnormals and those near overflow.
                    x = Double.longBitsToDouble(r & 0x7FEFFFFFFFFFFFFFL);
                    int shift = (int) (r >>> 59);
                    y = x * (((r >>> 20) & 0xFFFF) + 1) * 0x1.0p-16 / (1L << shift);
                    break;
                }
            }
            double h = Math.hypot(x, y);
            assertEquals(Double.valueOf(h), Double.valueOf(Math.hypot(-y, x)), "hypot(" + x + ", " + y + ")");
            if (Double.isInfinite(h)) {
                assertTrue(scaled(Double.MAX_VALUE, 1100).pow(2).compareTo(sumOfSquares(x, y)) < 0,
                        "hypot(" + x + ", " + y + ") = " + h);
                continue;
            }
            // The exact result lies strictly between the neighbours of h iff their squares enclose x^2 + y^2.
            BigInteger exact = sumOfSquares(x, y);
            BigInteger lo = scaled(Math.nextDown(h), 1100);
            double up = Math.nextUp(h);
            assertTrue(lo.pow(2).compareTo(exact) < 0
                    && (Double.isInfinite(up) || scaled(up, 1100).pow(2).compareTo(exact) > 0),
                    "hypot(" + x + ", " + y + ") = " + h);
        }
    }

    @Test
    public void hypotIsSemiMonotonic() {
        long state = 19;
        for (int n = 0; n < 500; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long r = mix(state);
            double x = (r >>> 11) * 0x1.0p-53 + 0.5;
            double y = x * (r & 0xFFFF) * 0x1.0p-16;
            double held = n % 2 == 0 ? x : y;
            double start = n % 2 == 0 ? y : x;
            checkMonotonic("hypot", t -> Math.hypot(held, t), start, 64);
        }
    }

    private static BigInteger sumOfSquares(double x, double y) {
        return scaled(x, 1100).pow(2).add(scaled(y, 1100).pow(2));
    }

    // The absolute value of a finite double times 2^scale, which must be an integer.
    private static BigInteger scaled(double d, int scale) {
        long bits = Double.doubleToRawLongBits(d);
        int exponent = (int) ((bits >>> 52) & 0x7FF);
        long significand = bits & 0xFFFFFFFFFFFFFL;
        if (exponent == 0) {
            exponent = 1;
        } else {
            significand |= 1L << 52;
        }
        return BigInteger.valueOf(significand).shiftLeft(exponent - 1075 + scale);
    }

    private static void checkTable(String name, DoubleUnaryOperator f, double[] table, int ulps) {
        for (int i = 0; i < table.length; i += 2) {
            assertWithinUlps(name, table[i], table[i + 1], f.applyAsDouble(table[i]), ulps);
        }
    }

    private static void assertWithinUlps(String name, double x, double expected, double actual, int ulps) {
        if (Double.isInfinite(expected)) {
            assertEquals(Double.valueOf(expected), Double.valueOf(actual), name + "(" + x + ")");
        } else {
            assertTrue(Math.abs(actual - expected) <= ulps * Math.ulp(expected),
                    name + "(" + x + ") = " + actual + ", expected " + expected);
        }
    }

    private static void checkRandomMonotonic(String name, DoubleUnaryOperator f, boolean negative) {
        long state = 23;
        for (int n = 0; n < 1000; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long bits = mix(state) & (negative ? -1L : 0x7FFFFFFFFFFFFFFFL);
            double x = Double.longBitsToDouble(bits);
            if (!Double.isNaN(x) && !Double.isInfinite(x)) {
                checkMonotonic(name, f, x, 32);
            }
        }
    }

    private static void checkMonotonic(String name, DoubleUnaryOperator f, double start, int steps) {
        double x = start;
        double previous = f.applyAsDouble(x);
        for (int i = 0; i < steps; ++i) {
            double next = Math.nextUp(x);
            if (Double.isInfinite(next)) {
                break;
            }
            double value = f.applyAsDouble(next);
            assertTrue(value >= previous,
                    name + " is not monotonic between " + x + " (" + previous + ") and " + next + " (" + value + ")");
            x = next;
            previous = value;
        }
    }

    private static double nextDown(double x, int steps) {
        for (int i = 0; i < steps; ++i) {
            x = Math.nextDown(x);
        }
        return x;
    }

    // The finalizer of SplitMix64.
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
