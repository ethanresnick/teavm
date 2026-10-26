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
package org.teavm.classlib.java.util;

import org.teavm.classlib.impl.RandomUtils;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.util.random.TRandomGenerator;

public class TRandom extends TObject implements TRandomGenerator, TSerializable {
    /** A stored gaussian value for nextGaussian() */
    private double storedGaussian;

    /** Whether storedGuassian value is valid */
    private boolean haveStoredGaussian;

    // Could be improved more efficiently for JS backend
    private long seed;

    public TRandom() {
        this((long) (Math.random() * ((1L << 48)) - 1));
    }

    public TRandom(@SuppressWarnings("unused") long seed) {
        setSeed(seed);
    }

    public void setSeed(@SuppressWarnings("unused") long seed) {
        this.seed = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
    }

    @Override
    public int nextInt() {
        return next(32);
    }

    protected int next(int bits) {
        var newSeed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        seed = newSeed;
        return (int) (newSeed >> (48 - bits));
    }

    @Override
    public long nextLong() {
        return ((long) nextInt() << 32) + nextInt();
    }

    // The JDK's algorithms for the methods below, so a seeded Random gives the JDK's sequences. The RandomGenerator
    // defaults differ. The default nextInt(bound) masks the low bits of nextInt(), and the low bits of a linear
    // congruential generator have a short period.

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException();
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            r = (int) ((bound * (long) r) >> 31);
        } else {
            int u = r;
            while (true) {
                r = u % bound;
                if (u - r + m >= 0) {
                    break;
                }
                // rejected: u was in the incomplete last range
                u = next(31);
            }
        }
        return r;
    }

    /** The JDK's RandomSupport.boundedNextInt. */
    @Override
    public int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException();
        }
        int r = nextInt();
        int n = bound - origin;
        int m = n - 1;
        if ((n & m) == 0) {
            r = (r & m) + origin;
        } else if (n > 0) {
            int u = r >>> 1;
            while (true) {
                r = u % n;
                if (u + m - r >= 0) {
                    break;
                }
                u = nextInt() >>> 1;
            }
            r += origin;
        } else {
            while (r < origin || r >= bound) {
                r = nextInt();
            }
        }
        return r;
    }

    /** The JDK's RandomSupport.boundedNextLong(rng, bound). */
    @Override
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException();
        }
        long m = bound - 1;
        long r = nextLong();
        if ((bound & m) == 0L) {
            r &= m;
        } else {
            long u = r >>> 1;
            while (true) {
                r = u % bound;
                if (u + m - r >= 0L) {
                    break;
                }
                u = nextLong() >>> 1;
            }
        }
        return r;
    }

    /** The JDK's RandomSupport.boundedNextLong(rng, origin, bound). */
    @Override
    public long nextLong(long origin, long bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException();
        }
        long r = nextLong();
        long n = bound - origin;
        long m = n - 1;
        if ((n & m) == 0L) {
            r = (r & m) + origin;
        } else if (n > 0L) {
            long u = r >>> 1;
            while (true) {
                r = u % n;
                if (u + m - r >= 0L) {
                    break;
                }
                u = nextLong() >>> 1;
            }
            r += origin;
        } else {
            while (r < origin || r >= bound) {
                r = nextLong();
            }
        }
        return r;
    }

    @Override
    public boolean nextBoolean() {
        return next(1) != 0;
    }

    @Override
    public void nextBytes(byte[] bytes) {
        for (int i = 0, len = bytes.length; i < len;) {
            for (int rnd = nextInt(), n = Math.min(len - i, Integer.SIZE / Byte.SIZE); n-- > 0; rnd >>= Byte.SIZE) {
                bytes[i++] = (byte) rnd;
            }
        }
    }

    @Override
    public float nextFloat() {
        return next(24) * 0x1p-24f;
    }

    @Override
    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * 0x1p-53;
    }

    /**
     * Generate a random number with Gaussian distribution:
     * centered around 0 with a standard deviation of 1.0.
     */
    @Override
    public double nextGaussian() {
        /*
         * This implementation uses the polar method to generate two gaussian
         * values at a time. One is returned, and the other is stored to be returned
         * next time.
         */
        if (haveStoredGaussian) {
            haveStoredGaussian = false;
            return storedGaussian;
        }

        double[] pair = RandomUtils.pairGaussian(this::nextDouble);
        haveStoredGaussian = true;
        storedGaussian = pair[1];

        return pair[0];
    }
}
