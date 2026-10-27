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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TComparable;
import org.teavm.jso.crypto.Crypto;
import org.teavm.jso.typedarrays.Int32Array;

public final class TUUID implements TComparable<TUUID>, TSerializable {
    // The 128 bits as four ints, most significant first. Ints rather than two longs, since a long
    // is a BigInt on the JS backend.
    private final int bits0;
    private final int bits1;
    private final int bits2;
    private final int bits3;
    // toString() is often called repeatedly on the same UUID (map keys, IDs written to messages),
    // so its result is kept.
    private String string;

    public TUUID(long mostSigBits, long leastSigBits) {
        this((int) (mostSigBits >>> 32), (int) mostSigBits, (int) (leastSigBits >>> 32), (int) leastSigBits);
    }

    private TUUID(int bits0, int bits1, int bits2, int bits3) {
        this.bits0 = bits0;
        this.bits1 = bits1;
        this.bits2 = bits2;
        this.bits3 = bits3;
    }

    public static TUUID randomUUID() {
        if (!PlatformDetector.isC() && RandomSource.CRYPTO != null) {
            var buffer = RandomSource.BUFFER;
            RandomSource.CRYPTO.getRandomValues(buffer);
            return withVersion(buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3), 4);
        }
        // C has no secure random source (issue #119).
        return withVersion(weakRandomInt(), weakRandomInt(), weakRandomInt(), weakRandomInt(), 4);
    }

    // A type 3 (name-based) UUID: the MD5 hash of the name, with version 3 and the IETF variant.
    public static TUUID nameUUIDFromBytes(byte[] name) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("MD5").digest(name);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError(e);
        }
        return withVersion(bigEndianInt(hash, 0), bigEndianInt(hash, 4), bigEndianInt(hash, 8),
                bigEndianInt(hash, 12), 3);
    }

    // Puts the version in the high nibble of time_hi_and_version, and the IETF variant (binary 10)
    // on top of clock_seq_hi_and_reserved.
    private static TUUID withVersion(int b0, int b1, int b2, int b3, int version) {
        return new TUUID(b0, (b1 & 0xffff0fff) | (version << 12), (b2 & 0x3fffffff) | 0x80000000, b3);
    }

    private static int bigEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] << 24) | ((bytes[offset + 1] & 0xff) << 16) | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static int weakRandomInt() {
        return ((int) (Math.random() * 0x10000) << 16) | (int) (Math.random() * 0x10000);
    }

    // Looked up once; only reached off C, so C never links the JS types.
    private static final class RandomSource {
        static final Crypto CRYPTO = Crypto.isSupported() ? Crypto.current() : null;
        static final Int32Array BUFFER = new Int32Array(4);

        private RandomSource() {
        }
    }

    public static TUUID fromString(String name) {
        if (name.length() == 36 && name.charAt(8) == '-' && name.charAt(13) == '-' && name.charAt(18) == '-'
                && name.charAt(23) == '-') {
            int h0 = hex4(name, 0);
            int h1 = hex4(name, 4);
            int h2 = hex4(name, 9);
            int h3 = hex4(name, 14);
            int h4 = hex4(name, 19);
            int h5 = hex4(name, 24);
            int h6 = hex4(name, 28);
            int h7 = hex4(name, 32);
            if ((h0 | h1 | h2 | h3 | h4 | h5 | h6 | h7) >= 0) {
                return new TUUID((h0 << 16) | h1, (h2 << 16) | h3, (h4 << 16) | h5, (h6 << 16) | h7);
            }
        }
        return fromStringGeneral(name);
    }

    // Four ASCII hex digits as a value in [0, 0xffff], or -1 if any of them is not one.
    private static int hex4(String s, int offset) {
        int result = 0;
        for (int i = offset; i < offset + 4; ++i) {
            char c = s.charAt(i);
            int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                digit = c - ('a' - 10);
            } else if (c >= 'A' && c <= 'F') {
                digit = c - ('A' - 10);
            } else {
                return -1;
            }
            result = (result << 4) | digit;
        }
        return result;
    }

    private static TUUID fromStringGeneral(String name) {
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
        return ((long) bits2 << 32) | (bits3 & 0xffffffffL);
    }

    public long getMostSignificantBits() {
        return ((long) bits0 << 32) | (bits1 & 0xffffffffL);
    }

    public int version() {
        return (bits1 >> 12) & 0x0f;
    }

    public int variant() {
        // The top bits of the least significant half: 0xx -> 0, 10x -> 2, 110 -> 6, 111 -> 7.
        int top = bits2 >>> 29;
        if (top < 4) {
            return 0;
        } else if (top < 6) {
            return 2;
        } else {
            return top;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TUUID) {
            TUUID other = (TUUID) o;
            return bits0 == other.bits0 && bits1 == other.bits1 && bits2 == other.bits2 && bits3 == other.bits3;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // The same value as the JDK's fold of (mostSigBits ^ leastSigBits) into an int.
        return bits0 ^ bits1 ^ bits2 ^ bits3;
    }

    @Override
    public String toString() {
        var result = string;
        if (result == null) {
            result = format();
            string = result;
        }
        return result;
    }

    private String format() {
        var chars = new char[36];
        hex(chars, 0, bits0, 8);
        chars[8] = '-';
        hex(chars, 9, bits1 >>> 16, 4);
        chars[13] = '-';
        hex(chars, 14, bits1, 4);
        chars[18] = '-';
        hex(chars, 19, bits2 >>> 16, 4);
        chars[23] = '-';
        hex(chars, 24, bits2, 4);
        hex(chars, 28, bits3, 8);
        return new String(chars);
    }

    // Writes the low (4 * digits) bits of value as lower-case hex digits.
    private static void hex(char[] chars, int offset, int value, int digits) {
        for (int i = offset + digits - 1; i >= offset; --i) {
            int digit = value & 0xf;
            chars[i] = (char) (digit < 10 ? '0' + digit : ('a' - 10) + digit);
            value >>>= 4;
        }
    }

    @Override
    public int compareTo(TUUID other) {
        // A signed comparison of the most significant halves, then of the least significant ones.
        int result = compareHalves(bits0, bits1, other.bits0, other.bits1);
        return result != 0 ? result : compareHalves(bits2, bits3, other.bits2, other.bits3);
    }

    // Compares two longs given as (high, low) ints, as signed values; returns -1, 0 or 1.
    private static int compareHalves(int hi, int lo, int otherHi, int otherLo) {
        if (hi != otherHi) {
            return hi < otherHi ? -1 : 1;
        }
        return lo == otherLo ? 0 : Integer.compareUnsigned(lo, otherLo) < 0 ? -1 : 1;
    }
}
