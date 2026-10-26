/*
 *  Copyright 2015 Alexey Andreev.
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

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TComparable;
import org.teavm.classlib.java.security.TSecureRandom;
import org.teavm.jso.crypto.Crypto;

public final class TUUID implements TComparable<TUUID>, TSerializable {
    private final long mostSigBits;
    private final long leastSigBits;

    public TUUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    public static TUUID randomUUID() {
        var bytes = new byte[16];
        if (!PlatformDetector.isC() && Crypto.isSupported()) {
            new TSecureRandom().nextBytes(bytes);
        } else {
            for (var i = 0; i < bytes.length; ++i) {
                bytes[i] = (byte) (Math.random() * 256);
            }
        }
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        long msb = 0;
        long lsb = 0;
        for (var i = 0; i < 8; ++i) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (var i = 8; i < 16; ++i) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new TUUID(msb, lsb);
    }

    public static TUUID fromString(String name) {
        // As in the JDK, each group is masked to the width of its field.
        int len = name.length();
        if (len > 36) {
            throw new IllegalArgumentException();
        }
        int dash1 = name.indexOf('-');
        int dash2 = name.indexOf('-', dash1 + 1);
        int dash3 = name.indexOf('-', dash2 + 1);
        int dash4 = name.indexOf('-', dash3 + 1);
        int dash5 = name.indexOf('-', dash4 + 1);
        if (dash4 < 0 || dash5 >= 0) {
            throw new IllegalArgumentException();
        }
        long msb = Long.parseLong(name.substring(0, dash1), 16) & 0xffffffffL;
        msb <<= 16;
        msb |= Long.parseLong(name.substring(dash1 + 1, dash2), 16) & 0xffffL;
        msb <<= 16;
        msb |= Long.parseLong(name.substring(dash2 + 1, dash3), 16) & 0xffffL;
        long lsb = Long.parseLong(name.substring(dash3 + 1, dash4), 16) & 0xffffL;
        lsb <<= 48;
        lsb |= Long.parseLong(name.substring(dash4 + 1, len), 16) & 0xffffffffffffL;
        return new TUUID(msb, lsb);
    }

    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    public long getMostSignificantBits() {
        return mostSigBits;
    }

    public int version() {
        return (int) ((mostSigBits >> 12) & 0x0f);
    }

    public int variant() {
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62))) & (leastSigBits >> 63));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TUUID) {
            TUUID other = (TUUID) o;
            return mostSigBits == other.mostSigBits && leastSigBits == other.leastSigBits;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return ((int) (hilo >> 32)) ^ (int) hilo;
    }

    @Override
    public String toString() {
        return digits(mostSigBits >> 32, 8) + "-" + digits(mostSigBits >> 16, 4) + "-" + digits(mostSigBits, 4)
                + "-" + digits(leastSigBits >> 48, 4) + "-" + digits(leastSigBits, 12);
    }

    private static String digits(long value, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (value & (hi - 1))).substring(1);
    }

    @Override
    public int compareTo(TUUID other) {
        int result = Long.compare(mostSigBits, other.mostSigBits);
        return result != 0 ? result : Long.compare(leastSigBits, other.leastSigBits);
    }
}
