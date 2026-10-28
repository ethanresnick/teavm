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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RandomAccessFileBenchmark {
    private static final int COUNT = 256;
    private final byte[] buffer = new byte[128];
    private RandomAccessFile file;
    private File path;

    @Setup
    public void setup() throws IOException {
        path = new File("raf-benchmark");
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = (byte) i;
        }
        try (var out = new FileOutputStream(path)) {
            out.write(buffer);
        }
        file = new RandomAccessFile(path, "rw");
        file.write(buffer);
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public int read() throws IOException {
        int result = 0;
        for (int i = 0; i < COUNT; ++i) {
            file.seek(0);
            result += file.read(buffer) + buffer[i & 127];
        }
        return result;
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public long write() throws IOException {
        for (int i = 0; i < COUNT; ++i) {
            buffer[0] = (byte) i;
            file.seek(0);
            file.write(buffer);
        }
        file.seek(0);
        return file.read();
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public long openClose() throws IOException {
        long result = 0;
        for (int i = 0; i < COUNT; ++i) {
            try (var reader = new RandomAccessFile(path, "r")) {
                result += reader.length();
            }
        }
        return result;
    }

    @Benchmark
    @OperationsPerInvocation(COUNT)
    public long resize() throws IOException {
        long result = 0;
        for (int i = 0; i < COUNT; ++i) {
            file.seek(i & 31);
            file.setLength(64 + (i & 31));
            file.setLength(128);
            result += file.getFilePointer();
        }
        return result;
    }
}
