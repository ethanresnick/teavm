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
package org.teavm.classlib.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * The property tests compare UUID with a small reference model written in this class. On the JVM
 * the same tests run against java.util.UUID, which checks the model itself against the JDK.
 */
@TeaVMTest
public class UUIDTest {
    private static final long[] EXTREMES = { 0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0xffffffffL,
            0x80000000L, 0x7fffffffL, 0xffffffff00000000L, 0x0000000100000000L, 0x00000000ffffffffL,
            0x8000000000000000L | 0xffffffffL };

    @Test
    public void bitsAndFields() {
        var uuid = new UUID(0x123e4567e89b12d3L, 0xa456426614174000L);
        assertEquals(0x123e4567e89b12d3L, uuid.getMostSignificantBits());
        assertEquals(0xa456426614174000L, uuid.getLeastSignificantBits());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", uuid.toString());
        assertEquals(1, uuid.version());
        assertEquals(2, uuid.variant());
        assertEquals(1256478162, uuid.hashCode());
        assertEquals(uuid, UUID.fromString(uuid.toString()));
    }

    @Test
    public void randomBitsRoundTrip() {
        var random = new Random(37);
        for (var i = 0; i < 5000; ++i) {
            long msb = randomLong(random);
            long lsb = randomLong(random);
            checkBits(msb, lsb);
        }
        for (var msb : EXTREMES) {
            for (var lsb : EXTREMES) {
                checkBits(msb, lsb);
            }
        }
    }

    private static long randomLong(Random random) {
        // Mix in values with long runs of zero or one bits.
        switch (random.nextInt(4)) {
            case 0:
                return random.nextLong() >>> random.nextInt(64);
            case 1:
                return random.nextLong() >> random.nextInt(64) | Long.MIN_VALUE;
            default:
                return random.nextLong();
        }
    }

    private static void checkBits(long msb, long lsb) {
        var uuid = new UUID(msb, lsb);
        assertEquals(msb, uuid.getMostSignificantBits());
        assertEquals(lsb, uuid.getLeastSignificantBits());
        var expectedString = modelToString(msb, lsb);
        assertEquals(expectedString, uuid.toString());
        assertEquals(expectedString, uuid.toString());
        assertEquals(expectedString, UUID.fromString(expectedString).toString());
        assertEquals(uuid, UUID.fromString(expectedString));
        assertEquals(uuid, UUID.fromString(expectedString.toUpperCase()));
        long hilo = msb ^ lsb;
        assertEquals((int) (hilo >> 32) ^ (int) hilo, uuid.hashCode());
        assertEquals(modelVersion(msb), uuid.version());
        assertEquals(modelVariant(lsb), uuid.variant());
        assertEquals(new UUID(msb, lsb), uuid);
        assertEquals(0, uuid.compareTo(new UUID(msb, lsb)));
        assertNotEquals(new UUID(msb ^ 1, lsb), uuid);
        assertNotEquals(new UUID(msb, lsb ^ Long.MIN_VALUE), uuid);
    }

    // toString(): "UUID = <time_low> "-" <time_mid> "-" <time_high_and_version> "-"
    // <variant_and_sequence> "-" <node>", with 8, 4, 4, 4 and 12 hex digits.
    private static String modelToString(long msb, long lsb) {
        return pad(msb >>> 32, 8) + "-" + pad((msb >>> 16) & 0xffff, 4) + "-" + pad(msb & 0xffff, 4) + "-"
                + pad(lsb >>> 48, 4) + "-" + pad(lsb & 0xffffffffffffL, 12);
    }

    private static String pad(long value, int digits) {
        var s = Long.toHexString(value);
        var sb = new StringBuilder();
        for (var i = s.length(); i < digits; ++i) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    // version(): "The version number associated with this UUID describes how this UUID was
    // generated." It is the high nibble of time_hi_and_version.
    private static int modelVersion(long msb) {
        return (int) (msb >>> 12) & 0xf;
    }

    // variant(): "0 Reserved for NCS backward compatibility, 2 IETF RFC 4122 (Leach-Salz),
    // 6 Reserved, Microsoft Corporation backward compatibility, 7 Reserved for future definition".
    private static int modelVariant(long lsb) {
        int top = (int) (lsb >>> 61);
        switch (top) {
            case 0b000:
            case 0b001:
            case 0b010:
            case 0b011:
                return 0;
            case 0b100:
            case 0b101:
                return 2;
            default:
                return top;
        }
    }

    @Test
    public void variants() {
        assertEquals(0, new UUID(0, 0).variant());
        assertEquals(6, new UUID(0, 0xc000000000000000L).variant());
        assertEquals(7, new UUID(0, 0xe000000000000000L).variant());
        // Every value of the top three bits, with the remaining bits all zero or all one.
        for (var top = 0L; top < 8; ++top) {
            for (var rest : new long[] { 0, 0x1fffffffffffffffL }) {
                long lsb = (top << 61) | rest;
                assertEquals(modelVariant(lsb), new UUID(0, lsb).variant());
            }
        }
        for (var version = 0L; version < 16; ++version) {
            assertEquals(version, new UUID((version << 12) | 0xffffffffffff0fffL, 0).version());
            assertEquals(version, new UUID(version << 12, 0).version());
        }
    }

    @Test
    public void fromStringMasksEachGroup() {
        var shortGroups = UUID.fromString("1-2-3-4-5");
        assertEquals(0x100020003L, shortGroups.getMostSignificantBits());
        assertEquals(0x4000000000005L, shortGroups.getLeastSignificantBits());
        assertEquals("00000001-0002-0003-0004-000000000005", shortGroups.toString());
        assertEquals("23456789-2345-0001-0001-34567890abcd",
                UUID.fromString("123456789-12345-1-1-1234567890abcd").toString());
    }

    @Test
    public void fromStringRejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString("1-2-3-4"));
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString("1-2-3-4-5-6"));
        assertThrows(IllegalArgumentException.class, () -> UUID.fromString(""));
        assertThrows(IllegalArgumentException.class,
                () -> UUID.fromString("123e4567-e89b-12d3-a456-4266141740000"));
        assertThrows(NumberFormatException.class, () -> UUID.fromString("1--3-4-5"));
        assertThrows(NumberFormatException.class, () -> UUID.fromString("1-2-3-4-"));
        assertThrows(NumberFormatException.class, () -> UUID.fromString("-1-2-3-4"));
        // Canonical length and dashes, but a character that is not a hex digit.
        assertThrows(NumberFormatException.class, () -> UUID.fromString("123e4567-e89b-12d3-a456-42661417400g"));
        assertThrows(NumberFormatException.class, () -> UUID.fromString("g23e4567-e89b-12d3-a456-426614174000"));
        assertThrows(NumberFormatException.class, () -> UUID.fromString("123e4567-e89b-12d3-a456-42661417 000"));
        assertThrows(IllegalArgumentException.class,
                () -> UUID.fromString("123e4567-e89b-12d3-a456-4266-4174000"));
        // Canonical shape, with a sign at the start of a group: the general parser accepts it.
        assertEquals(new UUID(0x0234567812345678L, 0x1234123456789abcL),
                UUID.fromString("+2345678-1234-5678-1234-123456789abc"));
    }

    // Inputs with the canonical length and dashes, and arbitrary characters in the digit positions.
    @Test
    public void fromStringCanonicalShapeMatchesModel() {
        var random = new Random(3737);
        var alphabet = "0123456789abcdefABCDEF0123456789abcdefABCDEFgG+- x";
        for (var i = 0; i < 5000; ++i) {
            var sb = new StringBuilder();
            var bad = random.nextInt(4) == 0;
            for (var j = 0; j < 36; ++j) {
                if (j == 8 || j == 13 || j == 18 || j == 23) {
                    sb.append('-');
                } else if (bad && random.nextInt(16) == 0) {
                    sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
                } else {
                    sb.append(alphabet.charAt(random.nextInt(22)));
                }
            }
            checkAgainstModel(sb.toString());
        }
    }

    // Groups of random length, including empty, short and over-long ones, and random group counts.
    @Test
    public void fromStringNonCanonicalMatchesModel() {
        var random = new Random(373737);
        var alphabet = "0123456789abcdefABCDEF";
        for (var i = 0; i < 5000; ++i) {
            var sb = new StringBuilder();
            var groups = 3 + random.nextInt(4);
            for (var g = 0; g < groups; ++g) {
                if (g > 0) {
                    sb.append('-');
                }
                if (random.nextInt(10) == 0) {
                    sb.append('+');
                }
                var length = random.nextInt(14);
                for (var j = 0; j < length; ++j) {
                    sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
            }
            checkAgainstModel(sb.toString());
        }
    }

    private static void checkAgainstModel(String s) {
        Class<?> expectedException = null;
        long[] expected = null;
        try {
            expected = modelFromString(s);
        } catch (NumberFormatException e) {
            expectedException = NumberFormatException.class;
        } catch (IllegalArgumentException e) {
            expectedException = IllegalArgumentException.class;
        }
        UUID actual;
        try {
            actual = UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getClass(), s);
            return;
        }
        assertEquals(null, expectedException, s);
        assertEquals(expected[0], actual.getMostSignificantBits(), s);
        assertEquals(expected[1], actual.getLeastSignificantBits(), s);
    }

    // The JDK's fromString: at most 36 characters and exactly five dash-separated groups, else
    // IllegalArgumentException. Each group is parsed as a hex long (NumberFormatException if it
    // isn't one) and masked to its field width.
    private static long[] modelFromString(String s) {
        if (s.length() > 36) {
            throw new IllegalArgumentException();
        }
        var groups = s.split("-", -1);
        if (groups.length != 5) {
            throw new IllegalArgumentException();
        }
        var values = new long[5];
        for (var i = 0; i < 5; ++i) {
            values[i] = Long.parseLong(groups[i], 16);
        }
        long msb = ((values[0] & 0xffffffffL) << 32) | ((values[1] & 0xffff) << 16) | (values[2] & 0xffff);
        long lsb = ((values[3] & 0xffff) << 48) | (values[4] & 0xffffffffffffL);
        return new long[] { msb, lsb };
    }

    // compareTo(): "The first of two UUIDs is greater than the second if the most significant field
    // in which the UUIDs differ is greater for the first UUID." The JDK compares each half as a
    // signed long.
    @Test
    public void compareToIsSigned() {
        assertEquals(-1, new UUID(-1L, 0).compareTo(new UUID(1L, 0)));
        assertEquals(-1, new UUID(0, -1L).compareTo(new UUID(0, 1L)));
        assertEquals(0, new UUID(3, 4).compareTo(new UUID(3, 4)));
        for (var msb1 : EXTREMES) {
            for (var lsb1 : EXTREMES) {
                for (var msb2 : EXTREMES) {
                    for (var lsb2 : EXTREMES) {
                        checkCompare(msb1, lsb1, msb2, lsb2);
                    }
                }
            }
        }
        var random = new Random(37373737);
        for (var i = 0; i < 5000; ++i) {
            long msb = randomLong(random);
            long lsb = randomLong(random);
            // Often share a half, or the high or low int of a half.
            long msb2 = random.nextBoolean() ? msb : msb ^ randomMask(random);
            long lsb2 = random.nextBoolean() ? lsb : lsb ^ randomMask(random);
            checkCompare(msb, lsb, msb2, lsb2);
        }
    }

    private static long randomMask(Random random) {
        return randomLong(random) & (random.nextBoolean() ? -1L : 0xffffffffL);
    }

    private static void checkCompare(long msb1, long lsb1, long msb2, long lsb2) {
        int expected = msb1 != msb2 ? Long.compare(msb1, msb2) : Long.compare(lsb1, lsb2);
        int actual = new UUID(msb1, lsb1).compareTo(new UUID(msb2, lsb2));
        assertEquals(Integer.signum(expected), Integer.signum(actual));
        assertEquals(expected == 0, new UUID(msb1, lsb1).equals(new UUID(msb2, lsb2)));
    }

    // randomUUID(): "The UUID is generated using a cryptographically strong pseudo random number
    // generator." A type 4 UUID has version 4 and the IETF variant (2).
    @Test
    public void randomUUIDIsVersion4AndDistinct() {
        var seen = new HashSet<UUID>();
        var ones = new int[128];
        var count = 2000;
        for (var i = 0; i < count; i++) {
            var uuid = UUID.randomUUID();
            assertEquals(4, uuid.version());
            assertEquals(2, uuid.variant());
            assertTrue(seen.add(uuid));
            assertEquals(uuid, UUID.fromString(uuid.toString()));
            long msb = uuid.getMostSignificantBits();
            long lsb = uuid.getLeastSignificantBits();
            for (var bit = 0; bit < 64; ++bit) {
                ones[bit] += (int) (msb >>> (63 - bit)) & 1;
                ones[64 + bit] += (int) (lsb >>> (63 - bit)) & 1;
            }
        }
        // Every bit other than the six fixed ones varies, and is set about half the time.
        for (var bit = 0; bit < 128; ++bit) {
            var fixed = (bit >= 48 && bit < 52) || bit == 64 || bit == 65;
            if (!fixed) {
                assertTrue(ones[bit] > count * 4 / 10 && ones[bit] < count * 6 / 10,
                        "bit " + bit + ": " + ones[bit]);
            }
        }
    }
}
