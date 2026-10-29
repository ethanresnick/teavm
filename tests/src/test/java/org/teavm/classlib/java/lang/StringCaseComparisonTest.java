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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class StringCaseComparisonTest {
    private static final String[][] GROUPS = {
        { "I", "i", "\u0130", "\u0131" }, { "\u03a3", "\u03c3", "\u03c2" },
        { "K", "k", "\u212a" }, { "S", "s", "\u017f" }, { "\u00b5", "\u039c", "\u03bc" },
        { "\ud801\udc00", "\ud801\udc28" }
    };

    @Test
    public void asciiPairsMatchCanonicalFold() {
        for (int a = 0; a < 128; a++) {
            for (int b = 0; b < 128; b++) {
                String first = String.valueOf((char) a);
                String second = String.valueOf((char) b);
                int expected = Character.toLowerCase(Character.toUpperCase(a))
                        - Character.toLowerCase(Character.toUpperCase(b));
                assertEquals(Integer.signum(expected), Integer.signum(first.compareToIgnoreCase(second)));
                assertEquals(expected == 0, first.equalsIgnoreCase(second));
                assertEquals(expected == 0, first.regionMatches(true, 0, second, 0, 1));
            }
        }
    }

    @Test
    public void caseMappingGapsRemainUnchanged() {
        for (int codePoint : new int[] { 0x0378, 0x0379, 0x03a2, 0x0557, 0x0558 }) {
            assertEquals(codePoint, Character.toUpperCase(codePoint));
            assertEquals(codePoint, Character.toLowerCase(codePoint));
            assertEquals(codePoint, Character.toTitleCase(codePoint));
        }
    }

    @Test
    public void equalityFoldsCodePoints() {
        // Java SE 21 String.equalsIgnoreCase defines lower(upper(codePoint)) comparison.
        for (var group : GROUPS) {
            for (String a : group) {
                for (String b : group) {
                    assertTrue(a.equalsIgnoreCase(b));
                }
            }
        }
        assertFalse("I".equalsIgnoreCase(null));
        assertFalse("ss".equalsIgnoreCase("\u00df"));
        assertFalse("abc".equalsIgnoreCase("ABCD"));
    }

    @Test
    public void orderingFoldsCodePoints() {
        // Java SE 21 compareToIgnoreCase removes case differences with simple code-point folding.
        for (var group : GROUPS) {
            for (String a : group) {
                for (String b : group) {
                    assertEquals(0, a.compareToIgnoreCase(b));
                    assertEquals(0, String.CASE_INSENSITIVE_ORDER.compare(a, b));
                }
            }
        }
        assertTrue("Abc".compareToIgnoreCase("abd") < 0);
        assertTrue("abcD".compareToIgnoreCase("ABC") > 0);
        assertTrue("b".compareToIgnoreCase("A") > 0);
    }

    @Test
    public void regionsFoldOnlyWithinTheirBoundaries() {
        // Java SE 21 regionMatches compares substring(offset, offset + len).codePoints().
        for (var group : GROUPS) {
            for (String a : group) {
                for (String b : group) {
                    assertTrue(("x" + a + "y").regionMatches(true, 1, "z" + b + "q", 1, a.length()));
                }
            }
        }
        String upper = "x\ud801\udc00y";
        String lower = "x\ud801\udc28y";
        assertTrue(upper.regionMatches(true, 1, lower, 1, 1));
        assertTrue(upper.regionMatches(true, 1, lower, 1, 2));
        assertFalse(upper.regionMatches(true, 2, lower, 2, 1));
        assertFalse(upper.regionMatches(false, 1, lower, 1, 2));
        assertTrue("\ud801".regionMatches(true, 0, upper, 1, 1));
        assertFalse("\udc00".regionMatches(true, 0, lower, 2, 1));
        assertTrue("A".regionMatches(true, 0, "a", 0, 1));
        assertFalse("A".regionMatches(false, 0, "a", 0, 1));
    }

    @Test
    public void regionBoundsCannotOverflow() {
        for (boolean ignoreCase : new boolean[] { false, true }) {
            assertFalse("a".regionMatches(ignoreCase, Integer.MAX_VALUE, "a", 0, 1));
            assertFalse("a".regionMatches(ignoreCase, 0, "a", Integer.MAX_VALUE, 1));
            assertFalse("a".regionMatches(ignoreCase, 1, "a", 1, Integer.MAX_VALUE));
            assertTrue("a".regionMatches(ignoreCase, 1, "a", 1, 0));
            assertFalse("a".regionMatches(ignoreCase, -1, "a", 0, 0));
        }
        assertFalse("a".regionMatches(Integer.MAX_VALUE, "a", 0, 1));
        assertFalse("a".regionMatches(0, "a", Integer.MAX_VALUE, 1));
    }

    @Test
    public void supplementaryOrderingMatchesJdk() {
        // Match OpenJDK 21/25 code-point ordering despite the Javadoc wording about folded compareTo.
        assertTrue("\ud801\udc00".compareToIgnoreCase("\ue000") > 0);
        assertTrue("\ue000".compareToIgnoreCase("\ud801\udc28") < 0);
        assertTrue("\ud801\udc00".compareToIgnoreCase("\ud801") > 0);
        assertTrue("\ud801x".compareToIgnoreCase("\ud801\udc28") < 0);
    }
    @Test
    public void everyCaseMappingMatchesFoldedOperands() {
        // Differential property against the Javadoc's code-point fold, including supplementary mappings.
        for (int codePoint = 0; codePoint <= Character.MAX_CODE_POINT; codePoint++) {
            int upper = Character.toUpperCase(codePoint);
            int folded = Character.toLowerCase(upper);
            if (folded != codePoint) {
                String first = new String(Character.toChars(codePoint));
                String second = new String(Character.toChars(folded));
                String foldedFirst = fold(first);
                String foldedSecond = fold(second);
                boolean equal = foldedFirst.equals(foldedSecond);
                assertEquals(equal, first.equalsIgnoreCase(second), Integer.toHexString(codePoint));
                assertEquals(Integer.signum(foldedFirst.compareTo(foldedSecond)),
                        Integer.signum(first.compareToIgnoreCase(second)), Integer.toHexString(codePoint));
                assertEquals(equal, ("x" + first + "y").regionMatches(true, 1, "z" + second, 1, first.length()),
                        Integer.toHexString(codePoint));
            }
        }
    }

    @Test
    public void comparisonsMatchFoldedCodePointSequences() {
        String[] values = { "", "ABC", "abc", "AbD", "\u03c2", "\u03c3", "\u0131", "I", "\ue000",
                "K", "\u212a", "S", "\u017f", "\ud801", "\udc00", "\ud801\udc00",
                "\ud801\udc28", "\ud801x", "x\ud801\udc28y" };
        for (String first : values) {
            for (String second : values) {
                String a = fold(first);
                String b = fold(second);
                assertEquals(Integer.signum(compareCodePoints(a, b)),
                        Integer.signum(first.compareToIgnoreCase(second)));
                assertEquals(a.equals(b), first.equalsIgnoreCase(second));
                for (int start = 0; start <= first.length(); start++) {
                    for (int otherStart = 0; otherStart <= second.length(); otherStart++) {
                        int limit = Math.min(first.length() - start, second.length() - otherStart);
                        for (int length = 0; length <= limit; length++) {
                            boolean expected = fold(first.substring(start, start + length))
                                    .equals(fold(second.substring(otherStart, otherStart + length)));
                            assertEquals(expected, first.regionMatches(true, start, second, otherStart, length),
                                    java.util.Arrays.toString(first.chars().toArray()) + ":" + start + " vs "
                                            + java.util.Arrays.toString(second.chars().toArray()) + ":" + otherStart
                                            + " length=" + length);
                        }
                    }
                }
            }
        }
    }

    @Test
    @SkipJVM
    public void matchingSurrogatesStillRespectDifferentPartners() {
        // JDK 25 incorrectly reports these distinct three-unit regions equal after skipping equal high surrogates.
        String first = "\ud800\udc00\ud800\udc00";
        String second = "\ud800\ud800\udc00\udc00";
        assertFalse(first.equalsIgnoreCase(second));
        assertTrue(first.compareToIgnoreCase(second) > 0);
        for (int start = 0; start <= first.length(); start++) {
            for (int otherStart = 0; otherStart <= second.length(); otherStart++) {
                int limit = Math.min(first.length() - start, second.length() - otherStart);
                for (int length = 0; length <= limit; length++) {
                    boolean expected = fold(first.substring(start, start + length))
                            .equals(fold(second.substring(otherStart, otherStart + length)));
                    assertEquals(expected, first.regionMatches(true, start, second, otherStart, length));
                }
            }
        }
    }

    private static int compareCodePoints(String first, String second) {
        return java.util.Arrays.compare(first.codePoints().toArray(), second.codePoints().toArray());
    }

    private static String fold(String value) {
        var result = new StringBuilder();
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            result.appendCodePoint(Character.toLowerCase(Character.toUpperCase(codePoint)));
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
