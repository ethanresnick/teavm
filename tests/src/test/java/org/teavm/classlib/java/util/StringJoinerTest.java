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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class StringJoinerTest {
    @Test
    public void joinsWithPrefixAndSuffix() {
        var joiner = new StringJoiner(", ", "[", "]");
        assertEquals("[]", joiner.toString());
        assertEquals(2, joiner.length());
        assertSame(joiner, joiner.add("a"));
        joiner.add(null);
        assertEquals("[a, null]", joiner.toString());
        assertEquals(9, joiner.length());
    }

    @Test
    public void mergeAddsContentWithoutPrefixAndSuffix() {
        var joiner = new StringJoiner(", ", "[", "]");
        joiner.add("a").add("b");
        var other = new StringJoiner("-", "(", ")");
        other.add("x").add("y");
        assertSame(joiner, joiner.merge(other));
        assertEquals("[a, b, x-y]", joiner.toString());
        assertEquals(11, joiner.length());

        // An empty joiner adds nothing, not even its prefix and suffix or an empty element.
        var empty = new StringJoiner(",");
        empty.merge(new StringJoiner("-", "(", ")"));
        assertEquals("", empty.toString());

        // Merging a joiner into itself, as in the JDK.
        var self = new StringJoiner("+");
        self.add("p").add("q");
        self.merge(self);
        assertEquals("p+q+p+q", self.toString());
        assertThrows(NullPointerException.class, () -> self.merge(null));
    }

    @Test
    public void emptyValue() {
        var joiner = new StringJoiner(",", "<", ">").setEmptyValue("EMPTY");
        assertEquals("EMPTY", joiner.toString());
        assertEquals(5, joiner.length());
        // After any add, even of an empty string, the joiner is no longer empty.
        joiner.add("");
        assertEquals("<>", joiner.toString());
        assertEquals(2, joiner.length());
        assertThrows(NullPointerException.class, () -> new StringJoiner(",").setEmptyValue(null));
    }

    @Test
    public void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new StringJoiner(null));
        assertThrows(NullPointerException.class, () -> new StringJoiner(",", null, "]"));
        assertThrows(NullPointerException.class, () -> new StringJoiner(",", "[", null));
    }
}
