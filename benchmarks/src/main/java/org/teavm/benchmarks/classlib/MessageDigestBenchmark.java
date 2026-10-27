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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class MessageDigestBenchmark {
    @Param("SHA-256")
    public String algorithm;

    @Param({ "1024", "65536" })
    public int size;

    private MessageDigest digest;
    private byte[] data;
    private ByteBuffer buffer;

    @Setup
    public void setup() throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance(algorithm);
        data = new byte[size];
        for (var i = 0; i < size; ++i) {
            data[i] = (byte) (i * 31 + 7);
        }
        buffer = ByteBuffer.wrap(data);
    }

    /**
     * Digests the whole input with one call.
     */
    @Benchmark
    public byte[] oneShot() {
        return digest.digest(data);
    }

    /**
     * Digests the input in 64-byte updates, which shows the cost of each update call.
     */
    @Benchmark
    public byte[] chunks64() {
        for (var offset = 0; offset < size; offset += 64) {
            digest.update(data, offset, 64);
        }
        return digest.digest();
    }

    /**
     * Digests a heap buffer with update(ByteBuffer), which may read its backing array or copy it out.
     */
    @Benchmark
    public byte[] heapByteBuffer() {
        buffer.clear();
        digest.update(buffer);
        return digest.digest();
    }
}
