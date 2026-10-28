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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSBody;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

/**
 * The StrictMath Javadoc: "the Java math library is defined with respect to fdlibm version 5.3", and its methods
 * must give fdlibm's results bit for bit. The expected bits and checksums below come from the JVM's StrictMath,
 * and the JVM leg of each test checks them again. Each checksum covers a few thousand to a few hundred
 * thousand inputs, generated from a fixed seed.
 *
 * <p>The bit-exact tests are skipped on C. The C backend's generated code lets clang contract {@code a * b + c} into
 * a fused multiply-add (clang's default -ffp-contract=on, even at -O0), which Java forbids. fdlibm's steps then give
 * a result 1 ulp off: for the input 0x3FF50D3B414EDA2E, C gave 0x3FD18FA30D0C6D53 where fdlibm gives
 * 0x3FD18FA30D0C6D54. With -ffp-contract=off (or {@code #pragma STDC FP_CONTRACT OFF}) they pass on C.
 */
@TeaVMTest
public class StrictMathTest {
    @Test
    @SkipPlatform(TestPlatform.C)
    public void logGivesFdlibmBits() {
        // Inputs for which Node 24's Math.log gives another last bit than fdlibm.
        assertLogBits(0x3FF50D3B414EDA2EL, 0x3FD18FA30D0C6D54L);
        assertLogBits(0x3FD9CD0FFC328D70L, 0xBFED122D47E139FFL);
        assertLogBits(0x400B0433CAEEBD13L, 0x3FF378D841B54204L);
        assertLogBits(0x400599AEB56D9852L, 0x3FEFC8D736D54E56L);
    }

    // Javadoc of StrictMath.log: "If the argument is NaN or less than zero, then the result is NaN. If the argument
    // is positive infinity, then the result is positive infinity. If the argument is positive zero or negative zero,
    // then the result is negative infinity. If the argument is 1.0, then the result is +0.0."
    @Test
    public void logSpecialValues() {
        assertLogBits(0L, 0xFFF0000000000000L);
        assertLogBits(0x8000000000000000L, 0xFFF0000000000000L);
        assertLogBits(0x3FF0000000000000L, 0L);
        assertLogBits(0x7FF0000000000000L, 0x7FF0000000000000L);
        assertTrue(Double.isNaN(StrictMath.log(Double.NaN)));
        assertTrue(Double.isNaN(StrictMath.log(Double.NEGATIVE_INFINITY)));
        assertTrue(Double.isNaN(StrictMath.log(-1.0)));
        assertTrue(Double.isNaN(StrictMath.log(-Double.MIN_VALUE)));
        assertTrue(Double.isNaN(StrictMath.log(-Double.MIN_NORMAL)));
        assertTrue(Double.isNaN(StrictMath.log(-Double.MAX_VALUE)));
        // Smallest and largest subnormals, smallest normal, largest finite value, e.
        assertLogBits(0x0000000000000001L, 0xC0874385446D71C3L);
        assertLogBits(0x000FFFFFFFFFFFFFL, 0xC086232BDD7ABCD2L);
        assertLogBits(0x0010000000000000L, 0xC086232BDD7ABCD2L);
        assertLogBits(0x7FEFFFFFFFFFFFFFL, 0x40862E42FEFA39EFL);
        assertLogBits(0x4005BF0A8B145769L, 0x3FF0000000000000L);
    }

    // Javadoc of StrictMath.log10: "If the argument is NaN or less than zero, then the result is NaN. If the argument
    // is positive infinity, then the result is positive infinity. If the argument is positive zero or negative zero,
    // then the result is negative infinity. If the argument is equal to 10^n for integer n, then the result is n. In
    // particular, if the argument is 1.0 (10^0), then the result is +0.0."
    @Test
    public void log10SpecialValues() {
        assertEquals(Double.NEGATIVE_INFINITY, StrictMath.log10(0.0), 0);
        assertEquals(Double.NEGATIVE_INFINITY, StrictMath.log10(-0.0), 0);
        assertEquals(0L, Double.doubleToRawLongBits(StrictMath.log10(1.0)));
        assertEquals(Double.POSITIVE_INFINITY, StrictMath.log10(Double.POSITIVE_INFINITY), 0);
        assertTrue(Double.isNaN(StrictMath.log10(Double.NaN)));
        assertTrue(Double.isNaN(StrictMath.log10(Double.NEGATIVE_INFINITY)));
        assertTrue(Double.isNaN(StrictMath.log10(-1.0)));
        assertTrue(Double.isNaN(StrictMath.log10(-Double.MIN_VALUE)));
        assertEquals(-323.3062153431158, StrictMath.log10(Double.MIN_VALUE), 0);
        assertEquals(308.25471555991675, StrictMath.log10(Double.MAX_VALUE), 0);
        // Every power of ten up to 10^22 is exact as a double.
        double p = 1;
        for (int n = 0; n <= 22; ++n) {
            assertEquals(n, StrictMath.log10(p), 0, "log10(1e" + n + ")");
            p *= 10;
        }
    }

    @Test
    public void logOfNegativeIsNaN() {
        long state = 1;
        for (int n = 0; n < 4096; ++n) {
            state += 0x9E3779B97F4A7C15L;
            double x = Double.longBitsToDouble(mix(state) | 0x8000000000000000L);
            if (x != 0) {
                assertTrue(Double.isNaN(StrictMath.log(x)), "log(" + x + ")");
                assertTrue(Double.isNaN(StrictMath.log10(x)), "log10(" + x + ")");
            }
        }
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void logOfPowersOfTwo() {
        assertChecksum(0xF66640C133F0DCEFL, powersOfTwo(StrictMath::log));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log10OfPowersOfTwo() {
        assertChecksum(0x5F3D3CC33690EF6CL, powersOfTwo(StrictMath::log10));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void logNearOne() {
        assertChecksum(0x55113B9AA4159DAFL, nearOne(StrictMath::log));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log10NearOne() {
        assertChecksum(0xC72BC23BAD96C364L, nearOne(StrictMath::log10));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void logOfSubnormals() {
        assertChecksum(0xC02EA725644C35DAL, subnormals(StrictMath::log));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log10OfSubnormals() {
        assertChecksum(0xADCE6677EFBFDCCFL, subnormals(StrictMath::log10));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void logAtBranchThresholds() {
        assertChecksum(0x98B52647D60A4A13L, branchThresholds(StrictMath::log));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log10AtBranchThresholds() {
        assertChecksum(0x6E778649C0DAF841L, branchThresholds(StrictMath::log10));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void logOfRandomValues() {
        assertChecksum(0x76266F79B722C90AL, randomValues(StrictMath::log));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log10OfRandomValues() {
        assertChecksum(0xB5FE974954FC2434L, randomValues(StrictMath::log10));
    }

    @Test
    @SkipJVM
    @OnlyPlatform(TestPlatform.JAVASCRIPT)
    public void logDoesNotUseHostMathLog() {
        // A host Math.log need not be fdlibm's: Node 24's differs in the last bit for the inputs above, Chromium
        // 138's does not. Replace it with a wrong function, so that a StrictMath.log that calls it fails.
        breakHostFunctions();
        try {
            assertLogBits(0x3FF50D3B414EDA2EL, 0x3FD18FA30D0C6D54L);
            assertEquals(3, StrictMath.log10(1000), 0);
        } finally {
            restoreHostFunctions();
        }
    }

    // Javadoc of StrictMath.exp: "If the argument is NaN, the result is NaN. If the argument is positive infinity,
    // then the result is positive infinity. If the argument is negative infinity, then the result is positive zero."
    // expm1: "If the argument is NaN, the result is NaN. If the argument is positive infinity, then the result is
    // positive infinity. If the argument is negative infinity, then the result is -1.0. If the argument is zero, then
    // the result is a zero with the same sign as the argument."
    @Test
    public void expSpecialValues() {
        assertTrue(Double.isNaN(StrictMath.exp(Double.NaN)));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.exp(Double.POSITIVE_INFINITY));
        assertBits(0.0, StrictMath.exp(Double.NEGATIVE_INFINITY));
        assertBits(1.0, StrictMath.exp(0.0));
        assertBits(1.0, StrictMath.exp(-0.0));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.exp(710));
        assertBits(0.0, StrictMath.exp(-746));
        assertBits(Double.MIN_VALUE, StrictMath.exp(-745));

        assertTrue(Double.isNaN(StrictMath.expm1(Double.NaN)));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.expm1(Double.POSITIVE_INFINITY));
        assertBits(-1.0, StrictMath.expm1(Double.NEGATIVE_INFINITY));
        assertBits(0.0, StrictMath.expm1(0.0));
        assertBits(-0.0, StrictMath.expm1(-0.0));
        assertBits(Double.MIN_VALUE, StrictMath.expm1(Double.MIN_VALUE));
        assertBits(-Double.MIN_VALUE, StrictMath.expm1(-Double.MIN_VALUE));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.expm1(710));
        assertBits(-1.0, StrictMath.expm1(-40));
    }

    // Javadoc of StrictMath.log1p: "If the argument is NaN or less than -1, then the result is NaN. If the argument is
    // positive infinity, then the result is positive infinity. If the argument is negative one, then the result is
    // negative infinity. If the argument is zero, then the result is a zero with the same sign as the argument."
    @Test
    public void log1pSpecialValues() {
        assertTrue(Double.isNaN(StrictMath.log1p(Double.NaN)));
        assertTrue(Double.isNaN(StrictMath.log1p(Double.NEGATIVE_INFINITY)));
        assertTrue(Double.isNaN(StrictMath.log1p(-1.0000000000000002)));
        assertTrue(Double.isNaN(StrictMath.log1p(-2)));
        assertTrue(Double.isNaN(StrictMath.log1p(-Double.MAX_VALUE)));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.log1p(Double.POSITIVE_INFINITY));
        assertBits(Double.NEGATIVE_INFINITY, StrictMath.log1p(-1));
        assertBits(0.0, StrictMath.log1p(0.0));
        assertBits(-0.0, StrictMath.log1p(-0.0));
        assertBits(Double.MIN_VALUE, StrictMath.log1p(Double.MIN_VALUE));
        assertBits(-Double.MIN_VALUE, StrictMath.log1p(-Double.MIN_VALUE));
        assertBits(709.782712893384, StrictMath.log1p(Double.MAX_VALUE));
    }

    // Javadoc of StrictMath.cbrt: "If the argument is NaN, then the result is NaN. If the argument is infinite, then
    // the result is an infinity with the same sign as the argument. If the argument is zero, then the result is a zero
    // with the same sign as the argument." sinh: the same three rules.
    @Test
    public void cbrtAndSinhSpecialValues() {
        for (DoubleUnaryOperator f : new DoubleUnaryOperator[] { StrictMath::cbrt, StrictMath::sinh }) {
            assertTrue(Double.isNaN(f.applyAsDouble(Double.NaN)));
            assertBits(Double.POSITIVE_INFINITY, f.applyAsDouble(Double.POSITIVE_INFINITY));
            assertBits(Double.NEGATIVE_INFINITY, f.applyAsDouble(Double.NEGATIVE_INFINITY));
            assertBits(0.0, f.applyAsDouble(0.0));
            assertBits(-0.0, f.applyAsDouble(-0.0));
        }
        assertBits(3.0, StrictMath.cbrt(27));
        assertBits(-3.0, StrictMath.cbrt(-27));
        assertBits(1e100, StrictMath.cbrt(1e300));
        assertBits(4.9406564584124654E-324, StrictMath.sinh(Double.MIN_VALUE));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.sinh(711));
        assertBits(Double.NEGATIVE_INFINITY, StrictMath.sinh(-711));
    }

    // Javadoc of StrictMath.cosh: "If the argument is NaN, then the result is NaN. If the argument is infinite, then
    // the result is positive infinity. If the argument is zero, then the result is 1.0." tanh: "If the argument is
    // NaN, then the result is NaN. If the argument is zero, then the result is a zero with the same sign as the
    // argument. If the argument is positive infinity, then the result is +1.0. If the argument is negative infinity,
    // then the result is -1.0."
    @Test
    public void coshAndTanhSpecialValues() {
        assertTrue(Double.isNaN(StrictMath.cosh(Double.NaN)));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.cosh(Double.POSITIVE_INFINITY));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.cosh(Double.NEGATIVE_INFINITY));
        assertBits(1.0, StrictMath.cosh(0.0));
        assertBits(1.0, StrictMath.cosh(-0.0));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.cosh(711));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.cosh(-711));

        assertTrue(Double.isNaN(StrictMath.tanh(Double.NaN)));
        assertBits(0.0, StrictMath.tanh(0.0));
        assertBits(-0.0, StrictMath.tanh(-0.0));
        assertBits(1.0, StrictMath.tanh(Double.POSITIVE_INFINITY));
        assertBits(-1.0, StrictMath.tanh(Double.NEGATIVE_INFINITY));
        assertBits(1.0, StrictMath.tanh(1000));
        assertBits(-1.0, StrictMath.tanh(-1000));
        assertBits(Double.MIN_VALUE, StrictMath.tanh(Double.MIN_VALUE));
    }

    // Javadoc of StrictMath.hypot: "If either argument is infinite, then the result is positive infinity. If either
    // argument is NaN and neither argument is infinite, then the result is NaN. If both arguments are zero, the result
    // is positive zero."
    @Test
    public void hypotSpecialValues() {
        double[] others = { 0.0, -0.0, 1, -1, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
        for (double y : others) {
            assertBits(Double.POSITIVE_INFINITY, StrictMath.hypot(Double.POSITIVE_INFINITY, y));
            assertBits(Double.POSITIVE_INFINITY, StrictMath.hypot(y, Double.NEGATIVE_INFINITY));
            if (!Double.isInfinite(y)) {
                assertTrue(Double.isNaN(StrictMath.hypot(Double.NaN, y)));
                assertTrue(Double.isNaN(StrictMath.hypot(y, Double.NaN)));
            }
        }
        assertBits(0.0, StrictMath.hypot(0.0, 0.0));
        assertBits(0.0, StrictMath.hypot(-0.0, -0.0));
        assertBits(5.0, StrictMath.hypot(-3, 4));
        assertBits(Double.MIN_VALUE, StrictMath.hypot(Double.MIN_VALUE, -0.0));
        assertBits(Double.POSITIVE_INFINITY, StrictMath.hypot(Double.MAX_VALUE, Double.MAX_VALUE));
    }

    // The checksums below cover inputs at every high word that fdlibm compares with (and its neighbours), with random
    // low words and both signs, random values from 2^-60 to 2^11, and random bit patterns.

    @Test
    @SkipPlatform(TestPlatform.C)
    public void expGivesFdlibmBits() {
        assertChecksum(0x4F494C060337CFABL, unaryInputs(StrictMath::exp, 0x3e300000, 0x3fd62e42, 0x3FF0A2B2,
                0x40862E42, 0x408633CE, 0x40874910, 0x40862000));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void expm1GivesFdlibmBits() {
        // Besides the thresholds, 0x402BB9D5..0x402BC000 is where the reduction's k reaches 20.
        assertChecksum(0x4955301DB6C28A72L, unaryInputs(StrictMath::expm1, 0x3c900000, 0x3fd62e42, 0x3FF0A2B2,
                0x4043687A, 0x40862E42, 0x402BB9D5, 0x402BC000, 0x3FD00000));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log1pGivesFdlibmBits() {
        assertChecksum(0xE40A98750182524CL, unaryInputs(StrictMath::log1p, 0x3FDA827A, 0x3ff00000, 0x3e200000,
                0x3c900000, 0x3FD2BEC3, 0x43400000, 0x3FF6A09E, 0x3FE6A09E));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void log1pAtNormalizationThreshold() {
        // log1p normalizes u = 1 + x to u or u/2 at a significand of 0x6a09e (about sqrt(2)), and takes another branch
        // when the significand's high bits are 0. Cover both, for u from 2^-1 to 2^60.
        long h = 0;
        long state = 6;
        for (int exponent = 1022; exponent <= 1083; ++exponent) {
            for (int significand : new int[] { 0x6a09c, 0x6a09d, 0x6a09e, 0x6a09f, 0, 1, 0xfffff }) {
                for (int n = 0; n < 16; ++n) {
                    state += 0x9E3779B97F4A7C15L;
                    long bits = ((long) ((exponent << 20) | significand) << 32) | (mix(state) & 0xFFFFFFFFL);
                    h = hash(h, StrictMath.log1p(Double.longBitsToDouble(bits) - 1));
                }
            }
        }
        assertChecksum(0x5170212BC831316FL, h);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void cbrtGivesFdlibmBits() {
        assertChecksum(0xE5F601F41D19DC81L, unaryInputs(StrictMath::cbrt, 0x00100000, 0x000FFFFF, 0x3FF00000,
                0x7FEFFFFF));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void cbrtOfSubnormals() {
        assertChecksum(0xF9444DEB0F298015L, subnormals(StrictMath::cbrt));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void sinhGivesFdlibmBits() {
        assertChecksum(0x67C9A48101CDEA0BL, unaryInputs(StrictMath::sinh, 0x3e300000, 0x3ff00000, 0x40360000,
                0x40862E42, 0x408633CE));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void coshGivesFdlibmBits() {
        assertChecksum(0x0C8E8CF9507C809EL, unaryInputs(StrictMath::cosh, 0x3c800000, 0x3fd62e43, 0x40360000,
                0x40862E42, 0x408633CE));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void tanhGivesFdlibmBits() {
        assertChecksum(0xD8775F8C750DFBE8L, unaryInputs(StrictMath::tanh, 0x3c800000, 0x3ff00000, 0x40360000));
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void sinhAndCoshAtOverflowThreshold() {
        // Both overflow above 0x408633CE_8FB9F87D. Steps of one ulp on both sides of it.
        long h = 0;
        for (int d = -2048; d <= 2048; ++d) {
            double x = Double.longBitsToDouble(0x408633CE8FB9F87DL + d);
            h = hash(hash(hash(h, StrictMath.sinh(x)), StrictMath.sinh(-x)), StrictMath.cosh(x));
        }
        assertChecksum(0xD3AC126690E30F22L, h);
    }

    @Test
    @SkipPlatform(TestPlatform.C)
    public void hypotGivesFdlibmBits() {
        long h = 0;
        long state = 7;
        // Random pairs over all exponents, subnormals included, whose exponents differ by up to 64.
        for (int n = 0; n < 1 << 15; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long m = mix(state);
            int ex = (int) ((m >>> 52) % 2047);
            int ey = Math.max(0, Math.min(2046, ex + (int) ((m >>> 40) & 127) - 64));
            state += 0x9E3779B97F4A7C15L;
            long significands = mix(state);
            double x = Double.longBitsToDouble(((long) ex << 52) | (significands & 0xFFFFFFFFFFFFFL));
            double y = Double.longBitsToDouble(((long) ey << 52) | ((significands >>> 12) ^ m) & 0xFFFFFFFFFFFFFL);
            h = hash(h, StrictMath.hypot((m & 1) == 0 ? x : -x, (m & 2) == 0 ? y : -y));
        }
        // fdlibm scales a above 2^500 and b below 2^-500, returns a + b when a / b > 2^60, and picks its formula by
        // whether a - b > b. Cover each of these thresholds.
        int[][] highWords = { { 0x5f300000, 0x5f300000 }, { 0x5f300001, 0x5b700001 }, { 0x20b00000, 0x20b00000 },
                { 0x24700000, 0x20afffff }, { 0x3ff00000, 0x3c300000 }, { 0x3ff00000, 0x3c2fffff },
                { 0x3ff00000, 0x3c300001 }, { 0x3ff00000, 0x3fe00000 }, { 0x3ff00000, 0x3fdfffff },
                { 0x00100000, 0x000fffff }, { 0x03c00000, 0x00000001 }, { 0x7fefffff, 0x7fefffff } };
        for (int[] pair : highWords) {
            for (int d = -2; d <= 2; ++d) {
                for (int n = 0; n < 64; ++n) {
                    state += 0x9E3779B97F4A7C15L;
                    long low = mix(state);
                    double x = Double.longBitsToDouble(((long) (pair[0] + d) << 32) | (low & 0xFFFFFFFFL));
                    double y = Double.longBitsToDouble(((long) pair[1] << 32) | (low >>> 32));
                    h = hash(hash(h, StrictMath.hypot(x, y)), StrictMath.hypot(y, x));
                }
            }
        }
        // Random bit patterns.
        for (int n = 0; n < 1 << 13; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long a = mix(state);
            state += 0x9E3779B97F4A7C15L;
            h = hash(h, StrictMath.hypot(Double.longBitsToDouble(a), Double.longBitsToDouble(mix(state))));
        }
        assertChecksum(0x55F55B6268CD6835L, h);
    }

    @Test
    @SkipJVM
    @OnlyPlatform(TestPlatform.JAVASCRIPT)
    public void doesNotUseHostMathFunctions() {
        // Replace the host's functions with wrong ones, so that a StrictMath method that calls one fails.
        breakHostFunctions();
        try {
            assertBits(2.7182818284590455, StrictMath.exp(1));
            assertBits(1.718281828459045, StrictMath.expm1(1));
            assertBits(0.6931471805599453, StrictMath.log1p(1));
            assertBits(3.0, StrictMath.cbrt(27));
            assertBits(1.1752011936438014, StrictMath.sinh(1));
            assertBits(1.543080634815244, StrictMath.cosh(1));
            assertBits(0.7615941559557649, StrictMath.tanh(1));
            assertBits(5.0, StrictMath.hypot(3, 4));
        } finally {
            restoreHostFunctions();
        }
    }

    private static long unaryInputs(DoubleUnaryOperator f, int... highWords) {
        long h = 0;
        long state = highWords[0];
        for (int high : highWords) {
            for (int d = -2; d <= 2; ++d) {
                for (int n = 0; n < 32; ++n) {
                    state += 0x9E3779B97F4A7C15L;
                    long bits = ((long) (high + d) << 32) | (mix(state) & 0xFFFFFFFFL);
                    h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
                    h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits ^ 0x8000000000000000L)));
                }
            }
        }
        for (int n = 0; n < 1 << 15; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long m = mix(state);
            long exponent = 963 + (m >>> 56) % 71;
            long bits = (m & 0x800FFFFFFFFFFFFFL) | (exponent << 52);
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
        }
        for (int n = 0; n < 1 << 14; ++n) {
            state += 0x9E3779B97F4A7C15L;
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(mix(state))));
        }
        return h;
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Long.toHexString(Double.doubleToRawLongBits(expected)),
                Long.toHexString(Double.doubleToRawLongBits(actual)));
    }

    private static long powersOfTwo(DoubleUnaryOperator f) {
        long h = 0;
        for (int e = -1074; e <= 1023; ++e) {
            long bits = e >= -1022 ? (long) (e + 1023) << 52 : 1L << (e + 1074);
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
        }
        return h;
    }

    private static long nearOne(DoubleUnaryOperator f) {
        long h = 0;
        long one = 0x3FF0000000000000L;
        // Steps of one ulp on both sides of 1, covering f == 0 and the |f| < 2**-20 branch.
        for (int n = 1; n <= 4096; ++n) {
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(one + n)));
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(one - n)));
        }
        // Random offsets of up to 2**-20, 2**-10 and 2**-2 on both sides of 1.
        long state = 2;
        for (double scale : new double[] { 0x1.0p-73, 0x1.0p-63, 0x1.0p-55 }) {
            for (int n = 0; n < 8192; ++n) {
                state += 0x9E3779B97F4A7C15L;
                double offset = (mix(state) >>> 11) * scale;
                h = hash(hash(h, f.applyAsDouble(1 + offset)), f.applyAsDouble(1 - offset));
            }
        }
        return h;
    }

    private static long subnormals(DoubleUnaryOperator f) {
        long h = 0;
        for (long bits = 1; bits <= 2048; ++bits) {
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
            h = hash(h, f.applyAsDouble(Double.longBitsToDouble(0x0010000000000000L - bits)));
        }
        long state = 3;
        for (int n = 0; n < 16384; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long bits = (mix(state) & 0x000FFFFFFFFFFFFFL) >>> (n % 52);
            if (bits != 0) {
                h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
            }
        }
        return h;
    }

    private static long branchThresholds(DoubleUnaryOperator f) {
        // fdlibm switches between x and x/2 at a significand of 0x6a09e (about sqrt(2)), and between two
        // formulas at 0x6147a and 0x6b851. Cover each threshold of the high word, with random low words, at
        // several exponents.
        long h = 0;
        long state = 4;
        for (int threshold : new int[] { 0x6a09e, 0x6a09f, 0x6147a, 0x6b851 }) {
            for (int exponent : new int[] { 1, 1022, 1023, 1024, 2046 }) {
                for (int d = -3; d <= 3; ++d) {
                    for (int n = 0; n < 64; ++n) {
                        state += 0x9E3779B97F4A7C15L;
                        long high = ((long) exponent << 20) | (threshold + d);
                        long bits = (high << 32) | (mix(state) & 0xFFFFFFFFL);
                        h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
                    }
                }
            }
        }
        return h;
    }

    private static long randomValues(DoubleUnaryOperator f) {
        long h = 0;
        long state = 5;
        // Random bit patterns over all positive finite doubles.
        for (int n = 0; n < 1 << 17; ++n) {
            state += 0x9E3779B97F4A7C15L;
            long bits = mix(state) & 0x7FFFFFFFFFFFFFFFL;
            if (bits < 0x7FF0000000000000L) {
                h = hash(h, f.applyAsDouble(Double.longBitsToDouble(bits)));
            }
        }
        // Random values in (0, 1), as Random.nextGaussian passes.
        for (int n = 0; n < 1 << 16; ++n) {
            state += 0x9E3779B97F4A7C15L;
            h = hash(h, f.applyAsDouble(((mix(state) >>> 11) + 1) * 0x1.0p-53));
        }
        return h;
    }

    private static void assertLogBits(long input, long expected) {
        double x = Double.longBitsToDouble(input);
        assertEquals(Long.toHexString(expected), Long.toHexString(Double.doubleToRawLongBits(StrictMath.log(x))),
                "log(" + Long.toHexString(input) + ")");
    }

    private static void assertChecksum(long expected, long actual) {
        assertEquals(Long.toHexString(expected), Long.toHexString(actual));
    }

    // The finalizer of SplitMix64.
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // NaN results hash alike: fdlibm's results are only pinned up to the NaN's bits, which differ between hosts.
    private static long hash(long h, double value) {
        return mix(h ^ Double.doubleToLongBits(value)) + 1;
    }

    @JSBody(script = "var names = ['log', 'exp', 'expm1', 'log1p', 'cbrt', 'sinh', 'cosh', 'tanh', 'hypot'];"
            + "globalThis.teavmTestSavedFunctions = names.map(function(name) { return Math[name]; });"
            + "names.forEach(function(name) { Math[name] = function() { return 0; }; });")
    private static native void breakHostFunctions();

    @JSBody(script = "var names = ['log', 'exp', 'expm1', 'log1p', 'cbrt', 'sinh', 'cosh', 'tanh', 'hypot'];"
            + "names.forEach(function(name, i) { Math[name] = globalThis.teavmTestSavedFunctions[i]; });")
    private static native void restoreHostFunctions();
}
