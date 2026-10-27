/*
 *  Copyright 2014 Alexey Andreev.
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

public final class TStrictMath extends TObject {
    public static final double E = 2.71828182845904523536;
    public static final double PI = 3.14159265358979323846;
    public static final double TAU = 2 * PI;

    private TStrictMath() {
    }

    public static double sin(double a) {
        return TMath.sin(a);
    }

    public static double cos(double a) {
        return TMath.cos(a);
    }

    public static double tan(double a) {
        return TMath.tan(a);
    }

    public static double asin(double a) {
        return TMath.asin(a);
    }

    public static double acos(double a) {
        return TMath.acos(a);
    }

    public static double atan(double a) {
        return TMath.atan(a);
    }

    public static double toRadians(double angdeg) {
        return TMath.toRadians(angdeg);
    }

    public static double toDegrees(double angrad) {
        return TMath.toDegrees(angrad);
    }

    public static double exp(double a) {
        return Fdlibm.exp(a);
    }

    public static double log(double a) {
        return Fdlibm.log(a);
    }

    /**
     * StrictMath's methods must give fdlibm's results bit for bit, as the JDK's do. The host's functions that Math
     * calls can differ in the last bit (V8 in Node 24 does for log, for some inputs).
     * These are ports of fdlibm 5.3 (netlib.org/fdlibm), with the same constants and steps:
     * __ieee754_log from e_log.c ("@(#)e_log.c 1.3 95/01/18"), __ieee754_log10 from e_log10.c
     * ("@(#)e_log10.c 1.3 95/01/18"), __ieee754_exp from e_exp.c ("@(#)e_exp.c 1.6 04/04/22"), expm1 from
     * s_expm1.c ("@(#)s_expm1.c 1.5 04/04/22"), log1p from s_log1p.c ("@(#)s_log1p.c 1.3 95/01/18"), cbrt from
     * s_cbrt.c ("@(#)s_cbrt.c 1.3 95/01/18"), __ieee754_sinh from e_sinh.c ("@(#)e_sinh.c 1.3 95/01/18"),
     * __ieee754_cosh from e_cosh.c ("@(#)e_cosh.c 1.3 95/01/18"), tanh from s_tanh.c ("@(#)s_tanh.c 1.3 95/01/18")
     * and __ieee754_hypot from e_hypot.c ("@(#)e_hypot.c 1.3 95/01/18"). Those files carry this notice:
     *
     * <p>Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
     *
     * <p>Developed at SunSoft, a Sun Microsystems, Inc. business. Permission to use, copy, modify, and distribute
     * this software is freely granted, provided that this notice is preserved.
     *
     * <p>except e_exp.c and s_expm1.c, which carry this one:
     *
     * <p>Copyright (C) 2004 by Sun Microsystems, Inc. All rights reserved.
     *
     * <p>Permission to use, copy, modify, and distribute this software is freely granted, provided that this notice
     * is preserved.
     */
    private static final class Fdlibm {
        private static final double LN2_HI = 6.93147180369123816490e-01;
        private static final double LN2_LO = 1.90821492927058770002e-10;
        private static final double TWO54 = 1.80143985094819840000e+16;
        private static final double LG1 = 6.666666666666735130e-01;
        private static final double LG2 = 3.999999999940941908e-01;
        private static final double LG3 = 2.857142874366239149e-01;
        private static final double LG4 = 2.222219843214978396e-01;
        private static final double LG5 = 1.818357216161805012e-01;
        private static final double LG6 = 1.531383769920937332e-01;
        private static final double LG7 = 1.479819860511658591e-01;
        private static final double IVLN10 = 4.34294481903251816668e-01;
        private static final double LOG10_2HI = 3.01029995663611771306e-01;
        private static final double LOG10_2LO = 3.69423907715893078616e-13;
        private static final double HUGE = 1.0e+300;
        private static final double TINY = 1.0e-300;
        private static final double SHUGE = 1.0e307;
        private static final double TWOM1000 = 9.33263618503218878990e-302;
        private static final double O_THRESHOLD = 7.09782712893383973096e+02;
        private static final double U_THRESHOLD = -7.45133219101941108420e+02;
        private static final double INVLN2 = 1.44269504088896338700e+00;
        private static final double P1 = 1.66666666666666019037e-01;
        private static final double P2 = -2.77777777770155933842e-03;
        private static final double P3 = 6.61375632143793436117e-05;
        private static final double P4 = -1.65339022054652515390e-06;
        private static final double P5 = 4.13813679705723846039e-08;
        private static final double Q1 = -3.33333333333331316428e-02;
        private static final double Q2 = 1.58730158725481460165e-03;
        private static final double Q3 = -7.93650757867487942473e-05;
        private static final double Q4 = 4.00821782732936239552e-06;
        private static final double Q5 = -2.01099218183624371326e-07;
        private static final int CBRT_B1 = 715094163;
        private static final int CBRT_B2 = 696219795;
        private static final double CBRT_C = 5.42857142857142815906e-01;
        private static final double CBRT_D = -7.05306122448979611050e-01;
        private static final double CBRT_E = 1.41428571428571436819e+00;
        private static final double CBRT_F = 1.60714285714285720630e+00;
        private static final double CBRT_G = 3.57142857142857150787e-01;

        private Fdlibm() {
        }

        // Takes bits that the caller already read: on JS, longs are BigInts, so each conversion is costly.
        private static double withHigh(long bits, int high) {
            return Double.longBitsToDouble(((long) high << 32) | (bits & 0xFFFFFFFFL));
        }

        // A double with the given high word and a low word of 0.
        private static double fromHigh(int high) {
            return Double.longBitsToDouble((long) high << 32);
        }

        private static int high(double x) {
            return (int) (Double.doubleToRawLongBits(x) >>> 32);
        }

        // Adds k to the exponent of x, as fdlibm's __HI(x) += k << 20 does.
        private static double addToExponent(double x, int k) {
            long bits = Double.doubleToRawLongBits(x);
            return withHigh(bits, (int) (bits >>> 32) + (k << 20));
        }

        static double exp(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int hx = (int) (bits >>> 32);
            int xsb = hx >>> 31; // sign bit of x
            hx &= 0x7fffffff; // high word of |x|

            // filter out non-finite argument
            if (hx >= 0x40862E42) {
                // if |x| >= 709.78...
                if (hx >= 0x7ff00000) {
                    if (((hx & 0xfffff) | (int) bits) != 0) {
                        return x + x; // NaN
                    }
                    return xsb == 0 ? x : 0.0; // exp(+-inf) = {inf, 0}
                }
                if (x > O_THRESHOLD) {
                    return HUGE * HUGE; // overflow
                }
                if (x < U_THRESHOLD) {
                    return TWOM1000 * TWOM1000; // underflow
                }
            }

            // argument reduction
            double hi = 0;
            double lo = 0;
            int k;
            if (hx > 0x3fd62e42) {
                // if |x| > 0.5 ln2
                if (hx < 0x3FF0A2B2) {
                    // and |x| < 1.5 ln2
                    hi = xsb == 0 ? x - LN2_HI : x + LN2_HI;
                    lo = xsb == 0 ? LN2_LO : -LN2_LO;
                    k = 1 - xsb - xsb;
                } else {
                    k = (int) (INVLN2 * x + (xsb == 0 ? 0.5 : -0.5));
                    double t = k;
                    hi = x - t * LN2_HI; // t * ln2_hi is exact here
                    lo = t * LN2_LO;
                }
                x = hi - lo;
            } else if (hx < 0x3e300000) {
                // when |x| < 2**-28
                return 1.0 + x;
            } else {
                k = 0;
            }

            // x is now in primary range
            double t = x * x;
            double c = x - t * (P1 + t * (P2 + t * (P3 + t * (P4 + t * P5))));
            if (k == 0) {
                return 1.0 - ((x * c) / (c - 2.0) - x);
            }
            double y = 1.0 - ((lo - (x * c) / (2.0 - c)) - hi);
            if (k >= -1021) {
                return addToExponent(y, k);
            }
            return addToExponent(y, k + 1000) * TWOM1000;
        }

        static double expm1(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int hx = (int) (bits >>> 32);
            int xsb = hx & 0x80000000; // sign bit of x
            hx &= 0x7fffffff; // high word of |x|

            // filter out huge and non-finite argument
            if (hx >= 0x4043687A) {
                // if |x| >= 56 * ln2
                if (hx >= 0x40862E42) {
                    // if |x| >= 709.78...
                    if (hx >= 0x7ff00000) {
                        if (((hx & 0xfffff) | (int) bits) != 0) {
                            return x + x; // NaN
                        }
                        return xsb == 0 ? x : -1.0; // exp(+-inf) = {inf, -1}
                    }
                    if (x > O_THRESHOLD) {
                        return HUGE * HUGE; // overflow
                    }
                }
                if (xsb != 0) {
                    // x < -56 * ln2, return -1.0 (fdlibm also raises inexact here)
                    return TINY - 1.0;
                }
            }

            // argument reduction
            int k;
            double c = 0;
            if (hx > 0x3fd62e42) {
                // if |x| > 0.5 ln2
                double hi;
                double lo;
                if (hx < 0x3FF0A2B2) {
                    // and |x| < 1.5 ln2
                    if (xsb == 0) {
                        hi = x - LN2_HI;
                        lo = LN2_LO;
                        k = 1;
                    } else {
                        hi = x + LN2_HI;
                        lo = -LN2_LO;
                        k = -1;
                    }
                } else {
                    k = (int) (INVLN2 * x + (xsb == 0 ? 0.5 : -0.5));
                    double t = k;
                    hi = x - t * LN2_HI; // t * ln2_hi is exact here
                    lo = t * LN2_LO;
                }
                x = hi - lo;
                c = (hi - x) - lo;
            } else if (hx < 0x3c900000) {
                // when |x| < 2**-54, return x
                return x;
            } else {
                k = 0;
            }

            // x is now in primary range
            double hfx = 0.5 * x;
            double hxs = x * hfx;
            double r1 = 1.0 + hxs * (Q1 + hxs * (Q2 + hxs * (Q3 + hxs * (Q4 + hxs * Q5))));
            double t = 3.0 - r1 * hfx;
            double e = hxs * ((r1 - t) / (6.0 - x * t));
            if (k == 0) {
                return x - (x * e - hxs); // c is 0
            }
            e = x * (e - c) - c;
            e -= hxs;
            if (k == -1) {
                return 0.5 * (x - e) - 0.5;
            }
            if (k == 1) {
                if (x < -0.25) {
                    return -2.0 * (e - (x + 0.5));
                }
                return 1.0 + 2.0 * (x - e);
            }
            if (k <= -2 || k > 56) {
                // suffice to return exp(x) - 1
                return addToExponent(1.0 - (e - x), k) - 1.0;
            }
            double y;
            if (k < 20) {
                t = fromHigh(0x3ff00000 - (0x200000 >> k)); // t = 1 - 2^-k
                y = t - (e - x);
            } else {
                t = fromHigh((0x3ff - k) << 20); // 2^-k
                y = x - (e + t);
                y += 1.0;
            }
            return addToExponent(y, k);
        }

        static double log1p(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int hx = (int) (bits >>> 32);
            int ax = hx & 0x7fffffff;

            int k = 1;
            double f = 0;
            int hu = 0;
            if (hx < 0x3FDA827A) {
                // x < 0.41422
                if (ax >= 0x3ff00000) {
                    // x <= -1.0
                    if (x == -1.0) {
                        return -TWO54 / 0.0; // log1p(-1) = -inf
                    }
                    return (x - x) / (x - x); // log1p(x < -1) = NaN
                }
                if (ax < 0x3e200000) {
                    // |x| < 2**-29
                    if (ax < 0x3c900000) {
                        return x; // |x| < 2**-54
                    }
                    return x - x * x * 0.5;
                }
                if (hx > 0 || hx <= 0xbfd2bec3) {
                    // -0.2929 < x < 0.41422
                    k = 0;
                    f = x;
                    hu = 1;
                }
            }
            if (hx >= 0x7ff00000) {
                return x + x;
            }
            double c = 0;
            if (k != 0) {
                double u;
                long ubits;
                if (hx < 0x43400000) {
                    u = 1.0 + x;
                    ubits = Double.doubleToRawLongBits(u);
                    hu = (int) (ubits >>> 32);
                    k = (hu >> 20) - 1023;
                    c = k > 0 ? 1.0 - (u - x) : x - (u - 1.0); // correction term
                    c /= u;
                } else {
                    ubits = bits;
                    hu = hx;
                    k = (hu >> 20) - 1023;
                    c = 0;
                }
                hu &= 0x000fffff;
                if (hu < 0x6a09e) {
                    u = withHigh(ubits, hu | 0x3ff00000); // normalize u
                } else {
                    k += 1;
                    u = withHigh(ubits, hu | 0x3fe00000); // normalize u / 2
                    hu = (0x00100000 - hu) >> 2;
                }
                f = u - 1.0;
            }
            double hfsq = 0.5 * f * f;
            double r;
            if (hu == 0) {
                // |f| < 2**-20
                if (f == 0.0) {
                    if (k == 0) {
                        return 0.0;
                    }
                    c += k * LN2_LO;
                    return k * LN2_HI + c;
                }
                r = hfsq * (1.0 - 0.66666666666666666 * f);
                if (k == 0) {
                    return f - r;
                }
                return k * LN2_HI - ((r - (k * LN2_LO + c)) - f);
            }
            double s = f / (2.0 + f);
            double z = s * s;
            r = z * (LG1 + z * (LG2 + z * (LG3 + z * (LG4 + z * (LG5 + z * (LG6 + z * LG7))))));
            if (k == 0) {
                return f - (hfsq - s * (hfsq + r));
            }
            return k * LN2_HI - ((hfsq - (s * (hfsq + r) + (k * LN2_LO + c))) - f);
        }

        static double cbrt(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int hx = (int) (bits >>> 32);
            int sign = hx & 0x80000000; // sign = sign(x)
            hx ^= sign;
            if (hx >= 0x7ff00000) {
                return x + x; // cbrt(NaN, INF) is itself
            }
            if ((hx | (int) bits) == 0) {
                return x; // cbrt(0) is itself
            }
            x = Math.abs(x); // x <- |x|

            // rough cbrt to 5 bits
            double t;
            if (hx < 0x00100000) {
                // subnormal number
                t = TWO54 * x;
                long tbits = Double.doubleToRawLongBits(t);
                t = withHigh(tbits, (int) (tbits >>> 32) / 3 + CBRT_B2);
            } else {
                t = fromHigh(hx / 3 + CBRT_B1);
            }

            // new cbrt to 23 bits
            double r = t * t / x;
            double s = CBRT_C + r * t;
            t *= CBRT_G + CBRT_F / (s + CBRT_E + CBRT_D / s);

            // chopped to 20 bits and make it larger than cbrt(x)
            t = fromHigh(high(t) + 1);

            // one step newton iteration to 53 bits with error less than 0.667 ulps
            s = t * t; // t * t is exact
            r = x / s;
            double w = t + t;
            r = (r - t) / (w + r); // r - s is exact
            t = t + t * r;

            // restore the sign bit (t is positive and finite, so negating it only sets that bit)
            return sign != 0 ? -t : t;
        }

        static double sinh(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int jx = (int) (bits >>> 32);
            int ix = jx & 0x7fffffff;

            // x is INF or NaN
            if (ix >= 0x7ff00000) {
                return x + x;
            }

            double h = jx < 0 ? -0.5 : 0.5;
            // |x| in [0, 22], return sign(x) * 0.5 * (E + E / (E + 1)))
            if (ix < 0x40360000) {
                // |x| < 22
                if (ix < 0x3e300000) {
                    return x; // |x| < 2**-28: sinh(tiny) = tiny
                }
                double t = expm1(Math.abs(x));
                if (ix < 0x3ff00000) {
                    return h * (2.0 * t - t * t / (t + 1.0));
                }
                return h * (t + t / (t + 1.0));
            }

            // |x| in [22, log(maxdouble)] return 0.5 * exp(|x|)
            if (ix < 0x40862E42) {
                return h * exp(Math.abs(x));
            }

            // |x| in [log(maxdouble), overflowthresold]
            if (ix < 0x408633CE || ix == 0x408633ce && (bits & 0xFFFFFFFFL) <= 0x8fb9f87dL) {
                double w = exp(0.5 * Math.abs(x));
                double t = h * w;
                return t * w;
            }

            // |x| > overflowthresold, sinh(x) overflow
            return x * SHUGE;
        }

        static double cosh(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int ix = (int) (bits >>> 32) & 0x7fffffff;

            // x is INF or NaN
            if (ix >= 0x7ff00000) {
                return x * x;
            }

            // |x| in [0, 0.5 * ln2], return 1 + expm1(|x|)^2 / (2 * exp(|x|))
            if (ix < 0x3fd62e43) {
                double t = expm1(Math.abs(x));
                double w = 1.0 + t;
                if (ix < 0x3c800000) {
                    return w; // cosh(tiny) = 1
                }
                return 1.0 + (t * t) / (w + w);
            }

            // |x| in [0.5 * ln2, 22], return (exp(|x|) + 1 / exp(|x|) / 2
            if (ix < 0x40360000) {
                double t = exp(Math.abs(x));
                return 0.5 * t + 0.5 / t;
            }

            // |x| in [22, log(maxdouble)] return half * exp(|x|)
            if (ix < 0x40862E42) {
                return 0.5 * exp(Math.abs(x));
            }

            // |x| in [log(maxdouble), overflowthresold]
            if (ix < 0x408633CE || ix == 0x408633ce && (bits & 0xFFFFFFFFL) <= 0x8fb9f87dL) {
                double w = exp(0.5 * Math.abs(x));
                double t = 0.5 * w;
                return t * w;
            }

            // |x| > overflowthresold, cosh(x) overflow
            return HUGE * HUGE;
        }

        static double tanh(double x) {
            int jx = high(x);
            int ix = jx & 0x7fffffff;

            // x is INF or NaN
            if (ix >= 0x7ff00000) {
                if (jx >= 0) {
                    return 1.0 / x + 1.0; // tanh(+-inf) = +-1
                }
                return 1.0 / x - 1.0; // tanh(NaN) = NaN
            }

            double z;
            if (ix < 0x40360000) {
                // |x| < 22
                if (ix < 0x3c800000) {
                    return x * (1.0 + x); // |x| < 2**-55: tanh(small) = small
                }
                if (ix >= 0x3ff00000) {
                    // |x| >= 1
                    double t = expm1(2.0 * Math.abs(x));
                    z = 1.0 - 2.0 / (t + 2.0);
                } else {
                    double t = expm1(-2.0 * Math.abs(x));
                    z = -t / (t + 2.0);
                }
            } else {
                // |x| > 22, return +-1
                z = 1.0 - TINY;
            }
            return jx >= 0 ? z : -z;
        }

        static double hypot(double x, double y) {
            long abits = Double.doubleToRawLongBits(x);
            long bbits = Double.doubleToRawLongBits(y);
            int ha = (int) (abits >>> 32) & 0x7fffffff; // high word of x
            int hb = (int) (bbits >>> 32) & 0x7fffffff; // high word of y
            if (hb > ha) {
                long tbits = abits;
                abits = bbits;
                bbits = tbits;
                int j = ha;
                ha = hb;
                hb = j;
            }
            double a = withHigh(abits, ha); // a <- |a|
            double b = withHigh(bbits, hb); // b <- |b|
            if (ha - hb > 0x3c00000) {
                return a + b; // x / y > 2**60
            }
            int k = 0;
            if (ha > 0x5f300000) {
                // a > 2**500
                if (ha >= 0x7ff00000) {
                    // Inf or NaN
                    double w = a + b; // for sNaN
                    if (((ha & 0xfffff) | (int) abits) == 0) {
                        w = a;
                    }
                    if (((hb ^ 0x7ff00000) | (int) bbits) == 0) {
                        w = b;
                    }
                    return w;
                }
                // scale a and b by 2**-600
                ha -= 0x25800000;
                hb -= 0x25800000;
                k += 600;
                a = withHigh(abits, ha);
                b = withHigh(bbits, hb);
            }
            if (hb < 0x20b00000) {
                // b < 2**-500
                if (hb <= 0x000fffff) {
                    // subnormal b or 0
                    if ((hb | (int) bbits) == 0) {
                        return a;
                    }
                    double t1 = 0x1p1022;
                    b *= t1;
                    a *= t1;
                    k -= 1022;
                    // As in fdlibm, ha and hb keep the high words from before this scaling.
                } else {
                    // scale a and b by 2^600
                    ha += 0x25800000; // a *= 2^600
                    hb += 0x25800000; // b *= 2^600
                    k -= 600;
                    a = withHigh(abits, ha);
                    b = withHigh(bbits, hb);
                }
            }
            // medium size a and b
            double w = a - b;
            if (w > b) {
                double t1 = fromHigh(ha);
                double t2 = a - t1;
                w = TMath.sqrt(t1 * t1 - (b * (-b) - t2 * (a + t1)));
            } else {
                a = a + a;
                double y1 = fromHigh(hb);
                double y2 = b - y1;
                double t1 = fromHigh(ha + 0x00100000);
                double t2 = a - t1;
                w = TMath.sqrt(t1 * y1 - (w * (-w) - (t1 * y2 + t2 * b)));
            }
            if (k != 0) {
                return fromHigh(0x3ff00000 + (k << 20)) * w;
            }
            return w;
        }

        static double log(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int hx = (int) (bits >>> 32);
            int k = 0;
            if (hx < 0x00100000) {
                // x < 2**-1022
                if ((bits & 0x7fffffffffffffffL) == 0) {
                    return -TWO54 / 0.0; // log(+-0) = -inf
                }
                if (hx < 0) {
                    return (x - x) / 0.0; // log(-#) = NaN
                }
                k -= 54;
                x *= TWO54; // subnormal number, scale up x
                bits = Double.doubleToRawLongBits(x);
                hx = (int) (bits >>> 32);
            }
            if (hx >= 0x7ff00000) {
                return x + x;
            }
            k += (hx >> 20) - 1023;
            hx &= 0x000fffff;
            int i = (hx + 0x95f64) & 0x100000;
            x = withHigh(bits, hx | (i ^ 0x3ff00000)); // normalize x or x/2
            k += i >> 20;
            double f = x - 1.0;
            double dk;
            double r;
            if ((0x000fffff & (2 + hx)) < 3) {
                // |f| < 2**-20
                if (f == 0.0) {
                    if (k == 0) {
                        return 0.0;
                    }
                    dk = k;
                    return dk * LN2_HI + dk * LN2_LO;
                }
                r = f * f * (0.5 - 0.33333333333333333 * f);
                if (k == 0) {
                    return f - r;
                }
                dk = k;
                return dk * LN2_HI - ((r - dk * LN2_LO) - f);
            }
            double s = f / (2.0 + f);
            dk = k;
            double z = s * s;
            i = hx - 0x6147a;
            double w = z * z;
            int j = 0x6b851 - hx;
            double t1 = w * (LG2 + w * (LG4 + w * LG6));
            double t2 = z * (LG1 + w * (LG3 + w * (LG5 + w * LG7)));
            i |= j;
            r = t2 + t1;
            if (i > 0) {
                double hfsq = 0.5 * f * f;
                if (k == 0) {
                    return f - (hfsq - s * (hfsq + r));
                }
                return dk * LN2_HI - ((hfsq - (s * (hfsq + r) + dk * LN2_LO)) - f);
            }
            if (k == 0) {
                return f - s * (f - r);
            }
            return dk * LN2_HI - ((s * (f - r) - dk * LN2_LO) - f);
        }

        static double log10(double x) {
            long bits = Double.doubleToRawLongBits(x);
            int hx = (int) (bits >>> 32);
            int k = 0;
            if (hx < 0x00100000) {
                // x < 2**-1022
                if ((bits & 0x7fffffffffffffffL) == 0) {
                    return -TWO54 / 0.0; // log(+-0) = -inf
                }
                if (hx < 0) {
                    return (x - x) / 0.0; // log(-#) = NaN
                }
                k -= 54;
                x *= TWO54; // subnormal number, scale up x
                bits = Double.doubleToRawLongBits(x);
                hx = (int) (bits >>> 32);
            }
            if (hx >= 0x7ff00000) {
                return x + x;
            }
            k += (hx >> 20) - 1023;
            int i = k >>> 31;
            hx = (hx & 0x000fffff) | ((0x3ff - i) << 20);
            double y = k + i;
            x = withHigh(bits, hx);
            double z = y * LOG10_2LO + IVLN10 * log(x);
            return z + y * LOG10_2HI;
        }
    }

    public static double log10(double a) {
        return Fdlibm.log10(a);
    }

    public static double sqrt(double a) {
        return TMath.sqrt(a);
    }

    public static double cbrt(double a) {
        return Fdlibm.cbrt(a);
    }

    public static double IEEEremainder(double f1, double f2) {
        return TMath.IEEEremainder(f1, f2);
    }

    public static double ceil(double a) {
        return TMath.ceil(a);
    }

    public static double floor(double a) {
        return TMath.floor(a);
    }

    public static double rint(double a) {
        return TMath.rint(a);
    }

    public static double atan2(double y, double x) {
        return TMath.atan2(y, x);
    }

    public static double pow(double a, double b) {
        return TMath.pow(a, b);
    }

    public static int round(float a) {
        return TMath.round(a);
    }

    public static long round(double a) {
        return TMath.round(a);
    }

    public static int floorDiv(int a, int b) {
        return TMath.floorDiv(a, b);
    }

    public static long floorDiv(long a, int b) {
        return TMath.floorDiv(a, b);
    }

    public static long floorDiv(long a, long b) {
        return TMath.floorDiv(a, b);
    }

    public static int floorMod(int a, int b) {
        return TMath.floorMod(a, b);
    }

    public static int floorMod(long a, int b) {
        return TMath.floorMod(a, b);
    }

    public static long floorMod(long a, long b) {
        return TMath.floorMod(a, b);
    }

    public static int addExact(int a, int b) {
        return TMath.addExact(a, b);
    }

    public static long addExact(long a, long b) {
        return TMath.addExact(a, b);
    }

    public static int subtractExact(int a, int b) {
        return TMath.subtractExact(a, b);
    }

    public static long subtractExact(long a, long b) {
        return TMath.subtractExact(a, b);
    }

    public static int multiplyExact(int a, int b) {
        return TMath.multiplyExact(a, b);
    }

    public static long multiplyExact(long a, int b) {
        return TMath.multiplyExact(a, b);
    }

    public static long multiplyExact(long a, long b) {
        return TMath.multiplyExact(a, b);
    }

    public static int divideExact(int a, int b) {
        return TMath.divideExact(a, b);
    }

    public static long divideExact(long a, long b) {
        return TMath.divideExact(a, b);
    }

    public static int incrementExact(int a) {
        return TMath.incrementExact(a);
    }

    public static long incrementExact(long a) {
        return TMath.incrementExact(a);
    }

    public static int decrementExact(int a) {
        return TMath.decrementExact(a);
    }

    public static long decrementExact(long a) {
        return TMath.decrementExact(a);
    }

    public static int negateExact(int a) {
        return TMath.negateExact(a);
    }

    public static long negateExact(long a) {
        return TMath.negateExact(a);
    }

    public static int toIntExact(long value) {
        return TMath.toIntExact(value);
    }

    public static double random() {
        return TMath.random();
    }

    public static int abs(int a) {
        return TMath.abs(a);
    }

    public static long abs(long a) {
        return TMath.abs(a);
    }

    public static float abs(float a) {
        return TMath.abs(a);
    }

    public static double abs(double a) {
        return TMath.abs(a);
    }

    public static int max(int a, int b) {
        return TMath.max(a, b);
    }

    public static long max(long a, long b) {
        return TMath.max(a, b);
    }

    public static float max(float a, float b) {
        return TMath.max(a, b);
    }

    public static double max(double a, double b) {
        return TMath.max(a, b);
    }

    public static int min(int a, int b) {
        return TMath.min(a, b);
    }

    public static long min(long a, long b) {
        return TMath.min(a, b);
    }

    public static float min(float a, float b) {
        return TMath.min(a, b);
    }

    public static double min(double a, double b) {
        return TMath.min(a, b);
    }

    public static double ulp(double d) {
        return TMath.ulp(d);
    }

    public static float ulp(float f) {
        return TMath.ulp(f);
    }

    public static double signum(double d) {
        return TMath.signum(d);
    }

    public static float signum(float f) {
        return TMath.signum(f);
    }

    public static double sinh(double x) {
        return Fdlibm.sinh(x);
    }

    public static double cosh(double x) {
        return Fdlibm.cosh(x);
    }

    public static double tanh(double x) {
        return Fdlibm.tanh(x);
    }

    public static double hypot(double x, double y) {
        return Fdlibm.hypot(x, y);
    }

    public static double fma(double a, double b, double c) {
        return TMath.fma(a, b, c);
    }

    public static float fma(float a, float b, float c) {
        return TMath.fma(a, b, c);
    }

    public static double expm1(double x) {
        return Fdlibm.expm1(x);
    }

    public static double log1p(double x) {
        return Fdlibm.log1p(x);
    }

    public static double copySign(double magnitude, double sign) {
        return TMath.copySign(magnitude, sign);
    }

    public static float copySign(float magnitude, float sign) {
        return TMath.copySign(magnitude, sign);
    }

    public static int getExponent(float f) {
        return TMath.getExponent(f);
    }

    public static int getExponent(double d) {
        return TMath.getExponent(d);
    }

    public static double nextAfter(double start, double direction) {
        return TMath.nextAfter(start, direction);
    }

    public static float nextAfter(float start, double direction) {
        return TMath.nextAfter(start, direction);
    }

    public static double nextUp(double d) {
        return TMath.nextUp(d);
    }

    public static float nextUp(float f) {
        return TMath.nextUp(f);
    }

    public static int clamp(long value, int min, int max) {
        return TMath.clamp(value, min, max);
    }

    public static long clamp(long value, long min, long max) {
        return TMath.clamp(value, min, max);
    }

    public static double clamp(double value, double min, double max) {
        return TMath.clamp(value, min, max);
    }

    public static float clamp(float value, float min, float max) {
        return TMath.clamp(value, min, max);
    }
}
