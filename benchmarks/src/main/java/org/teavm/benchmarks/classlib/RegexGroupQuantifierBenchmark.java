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
package org.teavm.benchmarks.classlib;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Common patterns with capturing groups under quantifiers (greedy, reluctant, possessive, counted), and split.
 * Every benchmark processes about 1K chars.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class RegexGroupQuantifierBenchmark {
    private static final Pattern IP = Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern KEY_VALUE = Pattern.compile("(\\w+)=(\\w+)(?:;|$)");
    private static final Pattern REPEATED_GROUP = Pattern.compile("(\\w)+@");
    private static final Pattern RELUCTANT_COUNTED = Pattern.compile("(ab){1,3}?c");
    private static final Pattern POSSESSIVE_GROUP = Pattern.compile("(?:\\w+,)*+end");
    private static final Pattern POSSESSIVE_COUNTED = Pattern.compile("(\\w){2,8}+\\.");
    private static final Pattern SPLIT_COMMA = Pattern.compile("\\s*,\\s*");
    private static final Pattern SPLIT_LOOKAHEAD = Pattern.compile("(?=[A-Z])");

    private String ips;
    private String keyValues;
    private String emails;
    private String abcs;
    private String commaList;
    private String camelCase;
    private String sentences;
    private String dottedWords;

    @Setup
    public void setup() {
        var ipsBuilder = new StringBuilder();
        var kvBuilder = new StringBuilder();
        var emailBuilder = new StringBuilder();
        var abcBuilder = new StringBuilder();
        var listBuilder = new StringBuilder();
        var camelBuilder = new StringBuilder();
        var sentenceBuilder = new StringBuilder();
        for (var i = 0; ipsBuilder.length() < 1024; ++i) {
            ipsBuilder.append(i % 256).append('.').append(i * 7 % 256).append('.').append(i * 13 % 256)
                    .append('.').append(i * 31 % 256).append(i % 3 == 0 ? " x " : " ");
            kvBuilder.append("key").append(i).append('=').append("value").append(i * 3).append(';');
            emailBuilder.append("user").append(i).append("@host ");
            abcBuilder.append("ab".repeat(i % 4 + 1)).append(i % 2 == 0 ? "c" : "x");
            listBuilder.append(i > 0 ? " , " : "").append("item").append(i);
            camelBuilder.append("Word").append((char) ('a' + i % 26));
            sentenceBuilder.append("word").append(i).append(i % 2 == 0 ? ". " : ", ");
        }
        ips = ipsBuilder.toString();
        keyValues = kvBuilder.toString();
        emails = emailBuilder.toString();
        abcs = abcBuilder.toString();
        commaList = listBuilder.toString();
        camelCase = camelBuilder.toString();
        dottedWords = sentenceBuilder.toString();
        sentences = "a,".repeat(500) + "end";
    }

    private static int count(Matcher matcher) {
        var result = 0;
        while (matcher.find()) {
            result += matcher.end() - matcher.start();
        }
        return result;
    }

    private static int countGroups(Matcher matcher) {
        var result = 0;
        while (matcher.find()) {
            for (var g = 1; g <= matcher.groupCount(); ++g) {
                result += matcher.start(g);
            }
        }
        return result;
    }

    /**
     * Counted greedy group, find loop over dotted quads.
     */
    @Benchmark
    public int findIps() {
        return countGroups(IP.matcher(ips));
    }

    /**
     * Two capturing groups in a find loop.
     */
    @Benchmark
    public int findKeyValues() {
        return countGroups(KEY_VALUE.matcher(keyValues));
    }

    /**
     * Greedy repeated capturing group, capture of the last iteration.
     */
    @Benchmark
    public int findRepeatedGroup() {
        return countGroups(REPEATED_GROUP.matcher(emails));
    }

    /**
     * Reluctant counted group.
     */
    @Benchmark
    public int findReluctantCounted() {
        return count(RELUCTANT_COUNTED.matcher(abcs));
    }

    /**
     * Possessive group loop over the whole input.
     */
    @Benchmark
    public boolean possessiveGroup() {
        return POSSESSIVE_GROUP.matcher(sentences).matches();
    }

    /**
     * Possessive counted capturing group.
     */
    @Benchmark
    public int findPossessiveCounted() {
        return countGroups(POSSESSIVE_COUNTED.matcher(dottedWords));
    }

    /**
     * Split on a pattern with surrounding whitespace.
     */
    @Benchmark
    public int splitComma() {
        return SPLIT_COMMA.split(commaList).length;
    }

    /**
     * Split on a zero-width lookahead, which makes a zero-width match at the start.
     */
    @Benchmark
    public int splitLookahead() {
        return SPLIT_LOOKAHEAD.split(camelCase).length;
    }
}
