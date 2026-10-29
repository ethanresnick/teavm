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
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import org.junit.jupiter.api.Test;
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
    public void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(null, "AES"));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[0], "AES"));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[1], null));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[2], 1, 2, "AES"));
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> new SecretKeySpec(new byte[2], 0, -1, "AES"));
    }

    @Test
    public void destroyUsesInterfaceDefault() {
        var key = new SecretKeySpec(new byte[] { 1 }, "AES");
        assertFalse(key.isDestroyed());
        assertThrows(DestroyFailedException.class, key::destroy);
        assertFalse(key.isDestroyed());
    }
}
