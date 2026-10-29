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
package org.teavm.classlib.javax.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class SecretKeySpecTest {
    @Test
    public void copiesInputAndOutput() {
        byte[] bytes = { 9, 1, 2, 3, 9 };
        var key = new SecretKeySpec(bytes, 1, 3, "HmacSHA256");
        bytes[1] = 8;
        byte[] encoded = key.getEncoded();
        encoded[0] = 7;
        assertArrayEquals(new byte[] { 1, 2, 3 }, key.getEncoded());
        assertEquals("RAW", key.getFormat());
        assertEquals("HmacSHA256", key.getAlgorithm());
        KeySpec spec = key;
        assertEquals(key, spec);
    }

    @Test
    public void equalityAndHashRespectAlgorithmAliases() {
        var key = new SecretKeySpec(new byte[] { 1, 2, 3 }, "TripleDES");
        var alias = new SecretKeySpec(new byte[] { 1, 2, 3 }, "DESede");
        assertEquals(key, alias);
        assertEquals(alias, key);
        assertEquals(key.hashCode(), alias.hashCode());
        var lower = new SecretKeySpec(new byte[] { 1, 2, 3 }, "tripledes");
        assertEquals(key, lower);
        assertEquals(key.hashCode(), lower.hashCode());
        assertNotEquals(key, new SecretKeySpec(new byte[] { 1, 2, 4 }, "DESede"));
        assertNotEquals(key, new SecretKeySpec(new byte[] { 1, 2, 3 }, "AES"));
        assertNotEquals(key, null);
    }

    @Test
    public void algorithmComparisonUsesCaseInsensitiveCharacters() {
        // Java SE 21 SecretKeySpec.equals: algorithm names are compared case-insensitively.
        String[][] groups = { { "I", "i", "\u0130", "\u0131" }, { "\u03a3", "\u03c3", "\u03c2" },
                { "\ud801\udc00", "\ud801\udc28" } };
        for (String[] group : groups) {
            for (String first : group) {
                for (String second : group) {
                    assertEquals(new SecretKeySpec(new byte[] { 1 }, first),
                            new SecretKeySpec(new byte[] { 1 }, second));
                }
            }
        }
    }

    @Test
    @SkipJVM
    public void equalUnicodeAlgorithmsHaveEqualHashes() {
        // SecretKeySpec.hashCode requires equal objects to have equal hashes.
        // JDK 25 violates this for dotted/dotless I and final sigma; use the normative contract.
        String[] algorithms = { "I", "i", "\u0130", "\u0131", "\u03a3", "\u03c3", "\u03c2" };
        for (String first : algorithms) {
            for (String second : algorithms) {
                var a = new SecretKeySpec(new byte[] { 1 }, first);
                var b = new SecretKeySpec(new byte[] { 1 }, second);
                if (a.equals(b)) {
                    assertEquals(a.hashCode(), b.hashCode());
                }
            }
        }
    }

    @Test
    public void everySliceIsCopied() {
        // Java SE 21: the selected bytes are copied; getEncoded returns a new array each time.
        byte[] bytes = { -128, -1, 0, 1, 127 };
        for (int offset = 0; offset <= bytes.length; offset++) {
            for (int length = 0; length <= bytes.length - offset; length++) {
                var key = new SecretKeySpec(bytes, offset, length, "AES");
                byte[] encoded = key.getEncoded();
                assertEquals(length, encoded.length);
                for (int i = 0; i < length; i++) {
                    assertEquals(bytes[offset + i], encoded[i]);
                    encoded[i] ^= 1;
                }
                assertArrayEquals(java.util.Arrays.copyOfRange(bytes, offset, offset + length), key.getEncoded());
            }
        }
    }

    @Test
    public void comparesForeignKeysAndEveryDifferentByte() {
        // SecretKeySpec.equals accepts SecretKey instances with matching algorithm and encoding.
        var key = new SecretKeySpec(new byte[] { -1, 0, 1, 127 }, "AES");
        assertEquals(key, new RawKey("aes", new byte[] { -1, 0, 1, 127 }));
        assertNotEquals(key, new RawKey("AES", null));
        assertNotEquals(key, new RawKey("AES", new byte[0]));
        assertNotEquals(key, new RawKey("AES", new byte[] { -1, 0, 1 }));
        assertNotEquals(key, new RawKey("AES", new byte[] { -1, 0, 1, 127, 0 }));
        for (int i = 0; i < 4; i++) {
            var bytes = key.getEncoded();
            bytes[i] ^= 1;
            assertNotEquals(key, new RawKey("AES", bytes));
        }
    }

    private static class RawKey implements SecretKey {
        private final String algorithm;
        private final byte[] bytes;

        RawKey(String algorithm, byte[] bytes) {
            this.algorithm = algorithm;
            this.bytes = bytes;
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return bytes;
        }
    }

    @Test
    public void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(null, "AES"));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[0], "AES"));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[1], null));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[2], 1, 2, "AES"));
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> new SecretKeySpec(new byte[2], 0, -1, "AES"));
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> new SecretKeySpec(new byte[2], -1, 1, "AES"));
        assertThrows(IllegalArgumentException.class,
                () -> new SecretKeySpec(new byte[2], Integer.MAX_VALUE, 1, "AES"));
        assertThrows(IllegalArgumentException.class,
                () -> new SecretKeySpec(new byte[2], 1, Integer.MAX_VALUE, "AES"));
    }

    @Test
    public void destroyUsesInterfaceDefault() {
        var key = new SecretKeySpec(new byte[] { 1 }, "AES");
        assertFalse(key.isDestroyed());
        assertThrows(DestroyFailedException.class, key::destroy);
        assertFalse(key.isDestroyed());
    }
}
