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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class UUIDTest {
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
    public void variants() {
        assertEquals(0, new UUID(0, 0).variant());
        assertEquals(6, new UUID(0, 0xc000000000000000L).variant());
        assertEquals(7, new UUID(0, 0xe000000000000000L).variant());
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
        assertThrows(NumberFormatException.class, () -> UUID.fromString("1--3-4-5"));
    }

    @Test
    public void compareToIsSigned() {
        assertEquals(-1, new UUID(-1L, 0).compareTo(new UUID(1L, 0)));
        assertEquals(-1, new UUID(0, -1L).compareTo(new UUID(0, 1L)));
        assertEquals(0, new UUID(3, 4).compareTo(new UUID(3, 4)));
    }

    @Test
    public void randomUUIDIsVersion4AndDistinct() {
        var seen = new HashSet<UUID>();
        for (var i = 0; i < 1000; i++) {
            var uuid = UUID.randomUUID();
            assertEquals(4, uuid.version());
            assertEquals(2, uuid.variant());
            assertTrue(seen.add(uuid));
        }
    }
}
