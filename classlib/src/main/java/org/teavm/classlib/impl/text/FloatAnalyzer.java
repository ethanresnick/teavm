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
package org.teavm.classlib.impl.text;

import java.util.Arrays;

public final class FloatAnalyzer {
    public static final int PRECISION = 9;
    public static final int MAX_POS = 100000000;
    static final int MAX_ABS_DEC_EXP = 50;
    private static final int MAX_MANTISSA = Integer.divideUnsigned(-1, 10);

    private FloatAnalyzer() {
    }

    public static void analyze(float d, Result result) {
        int bits = Float.floatToIntBits(d);
        result.sign = (bits & (1 << 31)) != 0;
        int mantissa = bits & ((1 << 23) - 1);
        int exponent = (bits >> 23) & ((1 << 8) - 1);
        if (mantissa == 0 && exponent == 0) {
            result.mantissa = 0;
            result.exponent = 0;
            return;
        }

        // A subnormal mantissa is shifted left until it has the implicit bit. Its rounding interval stays half an
        // ulp of the subnormal, which is 2^shift times wider than half an ulp of the shifted mantissa.
        int shift = 0;
        if (exponent == 0) {
            mantissa <<= 1;
            shift++;
            while ((mantissa & (1L << 23)) == 0) {
                mantissa <<= 1;
                exponent--;
                shift++;
            }
        } else {
            mantissa |= 1 << 23;
        }

        int decExponent = Arrays.binarySearch(exp10Table, exponent);
        if (decExponent < 0) {
            decExponent = -decExponent;
        }
        int binExponentCorrection = exponent - exp10Table[decExponent + 1];
        int mantissaShift = 9 + binExponentCorrection;

        int decMantissa = mulAndShiftRight(mantissa, mantissa10Table[decExponent + 1], mantissaShift);
        if (decMantissa < MAX_MANTISSA) {
            while (Integer.compareUnsigned(decMantissa, MAX_MANTISSA) <= 0) {
                --decExponent;
                decMantissa = decMantissa * 10 + 9;
            }
            binExponentCorrection = exponent - exp10Table[decExponent + 1];
            mantissaShift = 9 + binExponentCorrection;
            decMantissa = mulAndShiftRight(mantissa, mantissa10Table[decExponent + 1], mantissaShift);
        }
        if (shift > 0) {
            // A subnormal interval can be wide enough to leave a one-digit decimal (see below), and its upper bound
            // can overflow. A subnormal has fewer significant digits than a normal number, so use one digit less.
            decExponent++;
            binExponentCorrection = exponent - exp10Table[decExponent + 1];
            mantissaShift = 9 + binExponentCorrection;
            decMantissa = mulAndShiftRight(mantissa, mantissa10Table[decExponent + 1], mantissaShift);
        }
        var decMantissaHi = mulAndShiftRight((mantissa << 1) + (1 << shift), mantissa10Table[decExponent + 1],
                mantissaShift - 1);
        // Below a power of two the next float is closer, so the lower bound is a quarter of an ulp away
        var powerOfTwo = shift == 0 && mantissa == 1 << 23 && exponent > 1;
        var decMantissaLow = powerOfTwo
                ? mulAndShiftRight((mantissa << 2) - 1, mantissa10Table[decExponent + 1], mantissaShift - 2)
                : mulAndShiftRight((mantissa << 1) - (1 << shift), mantissa10Table[decExponent + 1],
                        mantissaShift - 1);

        // The value is mantissa * 2^(binExp + 1), and decMantissa has units of 10^decExp. A computed bound is a few
        // units below the exact one. When the exact bound is a whole number of units, a decimal can sit exactly
        // on it. The JDK includes both bounds when the mantissa is even, and excludes them when it is odd.
        // binExp >= decExp is needed for such a bound to exist. The bound mantissa is below 2^26 < 5^12, so for
        // decExp >= 12 it cannot be a whole number of units.
        var decExp = decExponent - MAX_ABS_DEC_EXP - 8;
        var binExp = exponent - 151;
        if (binExp >= decExp && decExp < 12) {
            var even = ((mantissa >>> shift) & 1) == 0;
            decMantissaHi = exactBound(decMantissaHi, decExp, (mantissa << 1) + (1 << shift), binExp, even, true);
            decMantissaLow = powerOfTwo
                    ? exactBound(decMantissaLow, decExp, (mantissa << 2) - 1, binExp - 1, even, false)
                    : exactBound(decMantissaLow, decExp, (mantissa << 1) - (1 << shift), binExp, even, false);
        }

        var exactMantissa = decMantissa;
        var lowerPos = findLowerDistance(decMantissa, decMantissaLow);
        var upperPos = findUpperDistance(decMantissa, decMantissaHi);
        var posCmp = Integer.compareUnsigned(lowerPos, upperPos);
        if (posCmp > 0) {
            decMantissa = Integer.divideUnsigned(decMantissa, lowerPos) * lowerPos;
        } else if (posCmp < 0) {
            decMantissa = Integer.divideUnsigned(decMantissa, upperPos) * upperPos + upperPos;
        } else {
            var half = upperPos >>> 1;
            // The value is mantissa * 2^(binExp + 1) and a halfway decimal is a multiple of 10^decExp, so the value
            // can be exactly halfway only if it is a multiple of 2^decExp
            if (half > 0 && binExp + 1 + Integer.numberOfTrailingZeros(mantissa) >= decExp) {
                var lower = Integer.divideUnsigned(decMantissa, upperPos);
                var lowerMantissa = lower * upperPos;
                var remainder = decMantissa - lowerMantissa;
                // decMantissa is within 3 units of the exact value, so only a remainder near half can be exact
                // (the window has one more unit of slack)
                if (remainder >= half - 4 && remainder <= half + 4 && isExact(lowerMantissa + half, decExp,
                        mantissa, binExp + 1)) {
                    // The value is exactly halfway between two decimals. The JDK takes the one with the even digit.
                    decMantissa = (lower & 1) == 0 ? lowerMantissa : lowerMantissa + upperPos;
                } else {
                    decMantissa = remainder >= upperPos - half ? lowerMantissa + upperPos : lowerMantissa;
                }
            } else {
                decMantissa = Integer.divideUnsigned(decMantissa + half, upperPos) * upperPos;
            }
        }
        if (shift > 0 && decMantissa / 10 <= (posCmp > 0 ? lowerPos : upperPos)) {
            // The shortest decimal has one digit. Take the closest two-digit decimal instead (JDK 19 and later).
            // Only a subnormal interval is wide enough for the two to differ: Float.MIN_VALUE is 1.4E-45.
            // All values here are below 2^31.
            var pos = exactMantissa < 100000000 ? 1000000 : 10000000;
            var twoDigits = (exactMantissa + pos / 2) / pos * pos;
            if (twoDigits > decMantissaLow && twoDigits <= decMantissaHi) {
                decMantissa = twoDigits;
            }
        }

        if (Integer.compareUnsigned(decMantissa, 1000000000) >= 0) {
            do {
                decExponent++;
                decMantissa = Integer.divideUnsigned(decMantissa, 10);
            } while (Integer.compareUnsigned(decMantissa, 1000000000) >= 0);
        } else if (Integer.compareUnsigned(decMantissa, 100000000) < 0) {
            decExponent--;
            decMantissa *= 10;
        }

        result.mantissa = decMantissa;
        result.exponent = decExponent - MAX_ABS_DEC_EXP;
    }

    // Returns the bound for the comparisons in findLowerDistance (decimal > bound) and findUpperDistance
    // (decimal <= bound). If the exact bound is binMantissa * 2^binExp, a whole number of units of 10^decExp,
    // the result includes or excludes it exactly. The exact bound, binMantissa * 2^binExp / 10^decExp, is computed
    // directly, and it is used only if it is a whole number 0 to 2 units above the computed bound. Only int
    // arithmetic is used, since long arithmetic is slow on JS.
    private static int exactBound(int bound, int decExp, int binMantissa, int binExp, boolean inclusive,
            boolean upper) {
        var shift = binExp - decExp;
        // A negative shift happens only for the lower bound below a power of two, and then binMantissa is odd
        if (shift < 0 || shift >= 32 || decExp >= POW5.length || decExp <= -POW5.length) {
            return bound;
        }
        int exact;
        if (decExp >= 0) {
            // binMantissa is divisible by 5^decExp exactly when this product is at most (2^32 - 1) / 5^decExp,
            // and then the product is the quotient
            var quotient = binMantissa * INV_POW5[decExp];
            if (Integer.compareUnsigned(quotient, INV_POW5_LIMIT[decExp]) > 0) {
                return bound;
            }
            exact = quotient << shift;
        } else {
            exact = (binMantissa << shift) * POW5[-decExp];
        }
        // The exact value is at most bound + 3, so it fits in 32 bits unless bound is within 4 of 2^32. In that
        // case a wrapped value is below bound and is rejected.
        if (Integer.compareUnsigned(exact, bound) < 0 || Integer.compareUnsigned(exact - bound, 2) > 0) {
            return bound;
        }
        return upper == inclusive ? exact : exact - 1;
    }

    // Checks that decMantissa * 10^decExp == binMantissa * 2^binExp exactly. decMantissa is unsigned.
    private static boolean isExact(int decMantissa, int decExp, int binMantissa, int binExp) {
        if (decExp >= POW5.length || decExp <= -POW5.length) {
            return false;
        }
        var decZeros = Integer.numberOfTrailingZeros(decMantissa);
        var binZeros = Integer.numberOfTrailingZeros(binMantissa);
        // Powers of two must match, then the odd parts must differ by exactly 5^|decExp|
        if (decZeros + decExp != binZeros + binExp) {
            return false;
        }
        var decOdd = decMantissa >>> decZeros;
        var binOdd = binMantissa >>> binZeros;
        if (decExp >= 0) {
            var pow5 = POW5[decExp];
            return binOdd % pow5 == 0 && binOdd / pow5 == decOdd;
        } else {
            var pow5 = POW5[-decExp];
            return Integer.remainderUnsigned(decOdd, pow5) == 0 && Integer.divideUnsigned(decOdd, pow5) == binOdd;
        }
    }

    // Powers of 5 that fit in an int. A larger power cannot divide an odd factor of an unsigned int.
    private static final int[] POW5 = {
            1, 5, 25, 125, 625, 3125, 15625, 78125, 390625, 1953125, 9765625, 48828125, 244140625, 1220703125
    };

    // Inverses of POW5 modulo 2^32
    private static final int[] INV_POW5 = {
            1, -858993459, -1030792151, 652835029, 989560465, 197912093, 1757569337, -1366473051, 585698849,
            976133229, 1054220105, 210844021, -816824655, -163364931
    };

    // (2^32 - 1) / POW5, unsigned
    private static final int[] INV_POW5_LIMIT = {
            -1, 858993459, 171798691, 34359738, 6871947, 1374389, 274877, 54975, 10995, 2199, 439, 87, 17, 3
    };

    private static int findLowerDistance(int mantissa, int lower) {
        int pos = 1;
        while (Integer.compareUnsigned(
                Integer.divideUnsigned(mantissa, pos * 10),
                Integer.divideUnsigned(lower, pos * 10)) > 0) {
            pos *= 10;
        }
        return pos;
    }

    private static int findUpperDistance(int mantissa, int upper) {
        int pos = 1;
        while (Integer.compareUnsigned(
                Integer.divideUnsigned(mantissa, pos * 10),
                Integer.divideUnsigned(upper, pos * 10)) < 0) {
            pos *= 10;
        }
        return pos;
    }

    static int mulAndShiftRight(int a, int b, int shift) {
        var result = (a & 0xFFFFFFFFL) * (b & 0xFFFFFFFFL);
        return (int) (result >>> (32 - shift));
    }

    public static class Result {
        public int mantissa;
        public int exponent;
        public boolean sign;
    }

    // The code below was generated by FloatAnalyzerGenerator

    private static final int[] mantissa10Table = {
            -18543760,
            -873828468,
            -1558056233,
            -2105438446,
            -791721136,
            -1492370368,
            -2052889754,
            -707643228,
            -1425108042,
            -1999079893,
            -621547450,
            -1356231419,
            -1943978595,
            -533385374,
            -1285701758,
            -1887554866,
            -443107408,
            -1213479385,
            -1829776968,
            -350662770,
            -1139523676,
            -1770612400,
            -255999462,
            -1063793029,
            -1710027882,
            -159064234,
            -986244846,
            -1647989336,
            -59802560,
            -906835507,
            -1584461865,
            -2126562952,
            -825520345,
            -1519409735,
            -2074521247,
            -742253618,
            -1452796353,
            -2021230542,
            -656988489,
            -1384584251,
            -1966660860,
            -569676998,
            -1314735058,
            -1910781505,
            -480270031,
            -1243209484,
            -1853561046,
            -388717296,
            -1169967296,
            -1794967296,
            -294967296,
            -1094967296,
            -1734967296,
            -198967296,
            -1018167296,
            -1673527296,
            -100663296,
            -939524096,
            -1610612736,
            -2147483648,
            -858993460,
            -1546188227,
            -2095944041,
            -776530088,
            -1480217529,
            -2043167483,
            -692087595,
            -1412663535,
            -1989124287,
            -605618482,
            -1343488245,
            -1933784055,
            -517074110,
            -1272652747,
            -1877115657,
            -426404674,
            -1200117198,
            -1819087218,
            -333559171,
            -1125840796,
            -1759666096,
            -238485376,
            -1049781760,
            -1698818867,
            -141129810,
            -971897307,
            -1636511305,
            -41437710,
            -892143627,
            -1572708361,
            -2117160148,
            -810475859,
            -1507374147,
            -2064892777,
            -726848065,
            -1440471911,
            -2011370988,
            -641213203,
            -1371964022,
            -1956564688,
    };

    private static final int[] exp10Table = {
            -37,
            -34,
            -31,
            -28,
            -24,
            -21,
            -18,
            -14,
            -11,
            -8,
            -4,
            -1,
            2,
            6,
            9,
            12,
            16,
            19,
            22,
            26,
            29,
            32,
            36,
            39,
            42,
            46,
            49,
            52,
            56,
            59,
            62,
            65,
            69,
            72,
            75,
            79,
            82,
            85,
            89,
            92,
            95,
            99,
            102,
            105,
            109,
            112,
            115,
            119,
            122,
            125,
            129,
            132,
            135,
            139,
            142,
            145,
            149,
            152,
            155,
            158,
            162,
            165,
            168,
            172,
            175,
            178,
            182,
            185,
            188,
            192,
            195,
            198,
            202,
            205,
            208,
            212,
            215,
            218,
            222,
            225,
            228,
            232,
            235,
            238,
            242,
            245,
            248,
            252,
            255,
            258,
            261,
            265,
            268,
            271,
            275,
            278,
            281,
            285,
            288,
            291,
    };
}
