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
package org.teavm.benchmarks.classlib;

import java.util.HashMap;
import java.util.UUID;
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
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class UUIDBenchmark {
    private static final int COUNT = 64;

    private UUID[] uuids;
    private String[] strings;
    private UUID[] copies;
    private HashMap<UUID, Integer> map;

    @Setup
    public void setup() {
        uuids = new UUID[COUNT];
        strings = new String[COUNT];
        copies = new UUID[COUNT];
        map = new HashMap<>();
        for (var i = 0; i < COUNT; ++i) {
            uuids[i] = UUID.randomUUID();
            strings[i] = uuids[i].toString();
            copies[i] = UUID.fromString(strings[i]);
            map.put(uuids[i], i);
        }
    }

    @Benchmark
    public void randomUUID(Blackhole blackhole) {
        blackhole.consume(UUID.randomUUID());
    }

    @Benchmark
    public void randomUUIDToString(Blackhole blackhole) {
        blackhole.consume(UUID.randomUUID().toString());
    }

    @Benchmark
    public void toStringAll(Blackhole blackhole) {
        for (var uuid : uuids) {
            blackhole.consume(uuid.toString());
        }
    }

    @Benchmark
    public void fromStringAll(Blackhole blackhole) {
        for (var string : strings) {
            blackhole.consume(UUID.fromString(string));
        }
    }

    @Benchmark
    public void mapLookupAll(Blackhole blackhole) {
        for (var uuid : copies) {
            blackhole.consume(map.get(uuid));
        }
    }
}
