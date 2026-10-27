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

import java.util.Random;
import java.util.concurrent.TimeUnit;
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
 * Measures {@link Math} functions that aren't a single host call or instruction everywhere: log10, cbrt, expm1,
 * log1p, sinh, cosh, tanh and hypot. Every benchmark performs {@link #SIZE} operations.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class MathFunctionsBenchmark {
    private static final int SIZE = 1024;

    private double[] positive = new double[SIZE];
    private double[] signed = new double[SIZE];
    private double[] other = new double[SIZE];

    @Setup
    public void setup() {
        var random = new Random(123);
        for (var i = 0; i < SIZE; ++i) {
            positive[i] = Math.scalb(random.nextDouble() + 0.5, random.nextInt(60) - 30);
            signed[i] = random.nextDouble() * 20 - 10;
            // Magnitudes within a factor of 16 of each other, as typical for hypot
            other[i] = positive[i] * Math.scalb(random.nextDouble() + 0.5, random.nextInt(8) - 4);
        }
    }

    @Benchmark
    public double log10() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.log10(positive[i]);
        }
        return result;
    }

    @Benchmark
    public double cbrt() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.cbrt(signed[i]);
        }
        return result;
    }

    @Benchmark
    public double expm1() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.expm1(signed[i]);
        }
        return result;
    }

    @Benchmark
    public double log1p() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.log1p(positive[i]);
        }
        return result;
    }

    @Benchmark
    public double sinh() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.sinh(signed[i]);
        }
        return result;
    }

    @Benchmark
    public double cosh() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.cosh(signed[i]);
        }
        return result;
    }

    @Benchmark
    public double tanh() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.tanh(signed[i]);
        }
        return result;
    }

    @Benchmark
    public double hypot() {
        var result = 0.0;
        for (var i = 0; i < SIZE; ++i) {
            result += Math.hypot(positive[i], other[i]);
        }
        return result;
    }
}
