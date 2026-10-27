/*
 *  Copyright 2026 TeaVM contributors.
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
package org.teavm.classlib.java.util.regex;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Random small patterns over groups and quantifiers (greedy, reluctant, possessive), run against random short
 * strings. {@link #describe} renders everything the JDK reports for a case: matches() and its group spans, each
 * find() and its group spans, and split results. The cases come from a fixed seed, so the same cases are run on
 * every platform.
 */
final class RegexDifferentialCases {
    private static final String[] ATOMS = { "a", "b", ".", "\\b", "a?", "[ab]" };
    private static final String[] QUANTIFIERS = { "?", "*", "+", "{0,2}", "{1,2}", "{2}", "{0}", "{1,}" };
    private static final String[] MODES = { "", "?", "+" };
    private static final int MAX_FINDS = 10;

    private int seed;

    RegexDifferentialCases(int seed) {
        this.seed = seed;
    }

    private int next(int bound) {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFFFF) % bound;
    }

    String nextPattern() {
        StringBuilder sb = new StringBuilder();
        term(sb, 0);
        int extra = next(2);
        for (int i = 0; i < extra; ++i) {
            term(sb, 0);
        }
        return sb.toString();
    }

    private void term(StringBuilder sb, int depth) {
        int kind = depth >= 2 ? 0 : next(4);
        if (kind == 0) {
            String atom = ATOMS[next(ATOMS.length)];
            sb.append(atom);
            if (next(3) == 0 && !atom.endsWith("?") && !atom.equals("\\b")) {
                quantifier(sb);
            }
            return;
        }
        sb.append(next(4) == 0 ? "(?:" : "(");
        term(sb, depth + 1);
        if (next(3) == 0) {
            sb.append('|');
            if (next(4) != 0) {
                term(sb, depth + 1);
            }
        } else if (next(2) == 0) {
            term(sb, depth + 1);
        }
        sb.append(')');
        if (next(5) != 0) {
            quantifier(sb);
        }
    }

    private void quantifier(StringBuilder sb) {
        sb.append(QUANTIFIERS[next(QUANTIFIERS.length)]);
        sb.append(MODES[next(MODES.length)]);
    }

    String nextInput() {
        int length = next(6);
        char[] chars = new char[length];
        for (int i = 0; i < length; ++i) {
            chars[i] = "abc".charAt(next(3));
        }
        return new String(chars);
    }

    static String describe(String regex, String input) {
        StringBuilder sb = new StringBuilder();
        sb.append('/').append(regex).append("/ on \"").append(input).append("\": ");
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            sb.append("matches");
            groups(sb, matcher);
        } else {
            sb.append("no match");
        }
        matcher = pattern.matcher(input);
        sb.append("; find");
        for (int i = 0; i < MAX_FINDS && matcher.find(); ++i) {
            groups(sb, matcher);
        }
        sb.append("; split ").append(Arrays.toString(pattern.split(input)));
        sb.append("; split -1 ").append(Arrays.toString(pattern.split(input, -1)));
        return sb.toString();
    }

    private static void groups(StringBuilder sb, Matcher matcher) {
        sb.append(" [");
        for (int g = 0; g <= matcher.groupCount(); ++g) {
            if (g > 0) {
                sb.append(' ');
            }
            sb.append(matcher.start(g)).append(',').append(matcher.end(g));
        }
        sb.append(']');
    }
}
