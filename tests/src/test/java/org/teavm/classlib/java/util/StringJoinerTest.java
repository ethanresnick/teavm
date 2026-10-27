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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class StringJoinerTest {
    @Test
    public void joinsWithPrefixAndSuffix() {
        var joiner = new StringJoiner(", ", "[", "]");
        // Class doc: "Prior to adding something to the StringJoiner, its sj.toString() method will, by default,
        // return prefix + suffix."
        assertEquals("[]", joiner.toString());
        assertEquals(2, joiner.length());
        // add: "Returns: a reference to this StringJoiner"
        assertSame(joiner, joiner.add("a"));
        // add: "If newElement is null, then "null" is added."
        joiner.add(null);
        assertEquals("[a, null]", joiner.toString());
        assertEquals(9, joiner.length());
    }

    @Test
    public void delimiterOnlyConstructor() {
        // StringJoiner(CharSequence): "with no prefix or suffix"
        var joiner = new StringJoiner("-");
        assertEquals("", joiner.toString());
        assertEquals(0, joiner.length());
        joiner.add("a");
        assertEquals("a", joiner.toString());
        joiner.add(null).add("");
        assertEquals("a-null-", joiner.toString());
        assertEquals(7, joiner.length());
    }

    @Test
    public void copiesArguments() {
        // Constructor: "using copies of the supplied prefix, delimiter and suffix"
        var delimiter = new StringBuilder(",");
        var prefix = new StringBuilder("<");
        var suffix = new StringBuilder(">");
        var joiner = new StringJoiner(delimiter, prefix, suffix);
        delimiter.append("!");
        prefix.append("!");
        suffix.append("!");

        // add: "Adds a copy of the given CharSequence value"
        var element = new StringBuilder("x");
        joiner.add(element);
        element.append("!");
        joiner.add(element);
        assertEquals("<x,x!>", joiner.toString());

        // setEmptyValue: "A copy of the emptyValue parameter is made for this purpose."
        var emptyValue = new StringBuilder("E");
        var empty = new StringJoiner(",").setEmptyValue(emptyValue);
        emptyValue.append("!");
        assertEquals("E", empty.toString());
    }

    @Test
    public void mergeAddsContentWithoutPrefixAndSuffix() {
        var joiner = new StringJoiner(", ", "[", "]");
        joiner.add("a").add("b");
        var other = new StringJoiner("-", "(", ")");
        other.add("x").add("y");
        // merge: "Returns: This StringJoiner"; "elements from the other StringJoiner are concatenated with that
        // delimiter and the result is appended to this StringJoiner as a single element."
        assertSame(joiner, joiner.merge(other));
        assertEquals("[a, b, x-y]", joiner.toString());
        assertEquals(11, joiner.length());
        // The other joiner is not changed.
        assertEquals("(x-y)", other.toString());

        // "If the given StringJoiner is empty, the call has no effect." An empty value doesn't make it non-empty.
        var empty = new StringJoiner(",");
        empty.merge(new StringJoiner("-", "(", ")").setEmptyValue("EMPTY"));
        assertEquals("", empty.toString());
        empty.setEmptyValue("NONE");
        empty.merge(new StringJoiner("-"));
        assertEquals("NONE", empty.toString());

        // A merge into an empty joiner makes it non-empty and uses this joiner's prefix and suffix.
        var target = new StringJoiner(",", "{", "}").setEmptyValue("NONE");
        target.merge(other);
        assertEquals("{x-y}", target.toString());

        // "A StringJoiner is empty if add() has never been called": a joiner whose only element is "" is not
        // empty, so merging it adds an empty element.
        var withEmptyElement = new StringJoiner("-", "(", ")").add("");
        var joiner2 = new StringJoiner(",").add("a");
        joiner2.merge(withEmptyElement);
        assertEquals("a,", joiner2.toString());
        var joiner3 = new StringJoiner(",", "[", "]").setEmptyValue("NONE");
        joiner3.merge(withEmptyElement);
        assertEquals("[]", joiner3.toString());

        // "and if merge() has never been called with a non-empty StringJoiner argument": after such a merge
        // the joiner is non-empty, and merging it into another joiner adds its content.
        var merged = new StringJoiner("+").merge(new StringJoiner("*").add("m"));
        var joiner4 = new StringJoiner(",").merge(merged);
        assertEquals("m", joiner4.toString());

        // Throws: "NullPointerException - if the other StringJoiner is null"
        assertThrows(NullPointerException.class, () -> joiner.merge(null));
        assertEquals("[a, b, x-y]", joiner.toString());
    }

    @Test
    public void mergeWithItself() {
        var self = new StringJoiner("+", "[", "]");
        self.add("p").add("q");
        self.merge(self);
        assertEquals("[p+q+p+q]", self.toString());
        self.merge(self);
        assertEquals("[p+q+p+q+p+q+p+q]", self.toString());

        // An empty joiner merged into itself stays empty.
        var empty = new StringJoiner(",", "<", ">").setEmptyValue("E");
        empty.merge(empty);
        assertEquals("E", empty.toString());
    }

    @Test
    public void emptyValue() {
        var joiner = new StringJoiner(",", "<", ">");
        // setEmptyValue: "Returns: this StringJoiner itself so the calls may be chained"
        assertSame(joiner, joiner.setEmptyValue("EMPTY"));
        assertEquals("EMPTY", joiner.toString());
        // length: "if no add methods have been called, then the length of the String representation (either
        // prefix + suffix or emptyValue) will be returned."
        assertEquals(5, joiner.length());
        // setEmptyValue: "once an add method has been called, the StringJoiner is no longer considered empty,
        // even if the element(s) added correspond to the empty String."
        joiner.add("");
        assertEquals("<>", joiner.toString());
        assertEquals(2, joiner.length());
        // The empty value doesn't matter for a non-empty joiner, and a later call doesn't change the result.
        joiner.setEmptyValue("OTHER");
        assertEquals("<>", joiner.toString());
        // An empty value can be the empty string, and can be replaced.
        var joiner2 = new StringJoiner(",", "<", ">").setEmptyValue("A").setEmptyValue("");
        assertEquals("", joiner2.toString());
        assertEquals(0, joiner2.length());
        // Throws: "NullPointerException - when the emptyValue parameter is null"
        assertThrows(NullPointerException.class, () -> new StringJoiner(",").setEmptyValue(null));
    }

    @Test
    public void nullArgumentsAreRejected() {
        // Throws: "NullPointerException - if delimiter is null"
        assertThrows(NullPointerException.class, () -> new StringJoiner(null));
        // Throws: "NullPointerException - if prefix, delimiter, or suffix is null"
        assertThrows(NullPointerException.class, () -> new StringJoiner(null, "[", "]"));
        assertThrows(NullPointerException.class, () -> new StringJoiner(",", null, "]"));
        assertThrows(NullPointerException.class, () -> new StringJoiner(",", "[", null));
    }

    // Random sequences of add, merge and setEmptyValue, checked against a model. toString follows the doc:
    // "the prefix, the values added so far separated by the delimiter, and the suffix, unless no elements have
    // been added in which case, the prefix + suffix or the emptyValue characters are returned."
    // length: "The value should be equivalent to toString().length()."
    @Test
    public void matchesModel() {
        var random = new Random(12345);
        String[] values = { "", "a", "bc", "def", null };
        String[] separators = { "", ",", ", ", "--" };
        for (var round = 0; round < 200; ++round) {
            var model = new Model(pick(random, separators), pick(random, separators), pick(random, separators));
            var others = new ArrayList<Model>();
            for (var i = 0; i < 3; ++i) {
                var other = new Model(pick(random, separators), "(", ")");
                for (var j = random.nextInt(3); j > 0; --j) {
                    other.add(pick(random, values));
                }
                others.add(other);
            }
            for (var step = 0; step < 12; ++step) {
                switch (random.nextInt(5)) {
                    case 0:
                    case 1:
                        model.add(pick(random, values));
                        break;
                    case 2:
                        model.merge(others.get(random.nextInt(others.size())));
                        break;
                    case 3:
                        model.merge(model);
                        break;
                    default:
                        model.setEmptyValue(random.nextBoolean() ? "" : "EMPTY" + step);
                        break;
                }
                var expected = model.expected();
                var context = "round " + round + ", step " + step;
                assertEquals(expected, model.joiner.toString(), context);
                assertEquals(expected.length(), model.joiner.length(), context);
            }
        }
    }

    private static String pick(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static class Model {
        final StringJoiner joiner;
        final String delimiter;
        final String prefix;
        final String suffix;
        final List<String> elements = new ArrayList<>();
        String emptyValue;

        Model(String delimiter, String prefix, String suffix) {
            this.delimiter = delimiter;
            this.prefix = prefix;
            this.suffix = suffix;
            joiner = new StringJoiner(delimiter, prefix, suffix);
        }

        void add(String value) {
            joiner.add(value);
            elements.add(value == null ? "null" : value);
        }

        void merge(Model other) {
            var content = joinElements(other);
            joiner.merge(other.joiner);
            if (content != null) {
                elements.add(content);
            }
        }

        void setEmptyValue(String value) {
            joiner.setEmptyValue(value);
            emptyValue = value;
        }

        String expected() {
            if (elements.isEmpty()) {
                return emptyValue != null ? emptyValue : prefix + suffix;
            }
            return prefix + joinElements(this) + suffix;
        }

        private static String joinElements(Model model) {
            if (model.elements.isEmpty()) {
                return null;
            }
            var sb = new StringBuilder();
            for (var i = 0; i < model.elements.size(); ++i) {
                if (i > 0) {
                    sb.append(model.delimiter);
                }
                sb.append(model.elements.get(i));
            }
            return sb.toString();
        }
    }
}
