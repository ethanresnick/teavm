/*
 *  Copyright 2026 Alexey Andreev.
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
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class DoubleToStringBenchmark {
    private static final int COUNT = 256;

    /**
     * Kind of values to format:
     * <ul>
     *   <li>{@code short}: numbers with few significant digits, like 12.34;</li>
     *   <li>{@code random}: random numbers between 0 and 1000;</li>
     *   <li>{@code large}: random numbers between 2^50 and 2^80, where the exact rounding interval
     *   check applies.</li>
     * </ul>
     */
    @Param({ "short", "random", "large" })
    public String kind;

    private double[] doubles;
    private float[] floats;

    @Setup
    public void setup() {
        var random = new Random(42);
        doubles = new double[COUNT];
        floats = new float[COUNT];
        for (var i = 0; i < COUNT; ++i) {
            switch (kind) {
                case "short":
                    doubles[i] = random.nextInt(10000) / 100.0;
                    break;
                case "random":
                    doubles[i] = random.nextDouble() * 1000;
                    break;
                default:
                    doubles[i] = Math.scalb(1 + random.nextDouble(), 50 + random.nextInt(30));
                    break;
            }
            floats[i] = (float) doubles[i];
        }
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public int doubleToString() {
        var result = 0;
        for (var v : doubles) {
            result += Double.toString(v).length();
        }
        return result;
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public int floatToString() {
        var result = 0;
        for (var v : floats) {
            result += Float.toString(v).length();
        }
        return result;
    }
}
