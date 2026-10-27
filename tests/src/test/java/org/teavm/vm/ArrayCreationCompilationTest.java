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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.teavm.backend.c.CTarget;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.backend.wasm.WasmGCTarget;
import org.teavm.model.MethodReference;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMToolLog;

/**
 * Compiles tiny programs that create arrays through per-class array metadata (multi-dimensional arrays,
 * enum constants) but never link {@link Array#newInstance(Class, int)}. The TeaVM JUnit harness always
 * links it (through {@code ArrayList}), which hides compiler crashes in such programs, so these programs
 * are compiled directly. Their run-time behaviour is checked by {@link ArrayCreationMetadataTest}.
 */
public class ArrayCreationCompilationTest {
    private static final MethodReference ARRAY_NEW_INSTANCE = new MethodReference(Array.class, "newInstance",
            Class.class, int.class, Object.class);

    @Test
    public void multiDimensionalArrays() {
        compileOnAllBackends(MultiArrayProgram.class);
    }

    @Test
    public void primitiveMultiDimensionalArray() {
        compileOnAllBackends(IntMultiArrayProgram.class);
    }

    @Test
    public void objectMultiDimensionalArray() {
        compileOnAllBackends(ObjectMultiArrayProgram.class);
    }

    @Test
    public void enumValueOf() {
        compileOnAllBackends(EnumValueOfProgram.class);
    }

    @Test
    public void enumSetNoneOf() {
        compileOnAllBackends(EnumSetProgram.class);
    }

    @Test
    public void streamCollect() {
        compileOnAllBackends(StreamCollectProgram.class);
    }

    private void compileOnAllBackends(Class<?> entryPoint) {
        var levels = new ArrayList<TeaVMOptimizationLevel>();
        levels.add(TeaVMOptimizationLevel.SIMPLE);
        if (Boolean.parseBoolean(System.getProperty("teavm.junit.optimized", "true"))) {
            levels.add(TeaVMOptimizationLevel.FULL);
        }
        for (var level : levels) {
            if (isEnabled("teavm.junit.js")) {
                compile(entryPoint, level, "JS", () -> {
                    var target = new JavaScriptTarget();
                    target.setStrict(true);
                    return target;
                });
            }
            if (isEnabled("teavm.junit.wasm-gc")) {
                compile(entryPoint, level, "Wasm GC", () -> {
                    var target = new WasmGCTarget();
                    target.setObfuscated(false);
                    return target;
                });
            }
            if (isEnabled("teavm.junit.c")) {
                compile(entryPoint, level, "C", () -> new CTarget(new CNameProvider()));
            }
        }
    }

    private static boolean isEnabled(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true"));
    }

    private void compile(Class<?> entryPoint, TeaVMOptimizationLevel level, String backend,
            Supplier<TeaVMTarget> targetFactory) {
        var description = entryPoint.getSimpleName() + " on " + backend + " (" + level + ")";
        var vm = new TeaVMBuilder(targetFactory.get()).build();
        vm.installPlugins();
        vm.setEntryPoint(entryPoint.getName());
        vm.setOptimizationLevel(level);
        try {
            vm.build(fileName -> new ByteArrayOutputStream(), "test");
        } catch (RuntimeException e) {
            throw new AssertionError("Compiler crashed on " + description, e);
        }
        if (!vm.getProblemProvider().getSevereProblems().isEmpty()) {
            var log = new Log();
            TeaVMProblemRenderer.describeProblems(vm, log);
            fail("Compilation errors in " + description + ":\n" + log.sb);
        }
        assertNull(vm.getDependencyInfo().getMethod(ARRAY_NEW_INSTANCE),
                "Array.newInstance must not be linked into " + description + ", otherwise the test proves nothing");
    }

    public static final class MultiArrayProgram {
        static Object sink;

        private MultiArrayProgram() {
        }

        public static void main(String[] args) {
            var n = args.length + 3;
            sink = new int[16][16];
            sink = new byte[n][2];
            sink = new short[n][2];
            sink = new char[n][2];
            sink = new long[n][2];
            sink = new float[n][2];
            sink = new double[n][2];
            sink = new boolean[n][2];
            sink = new int[n][n][n];
            sink = new long[2][n][2][n];
            sink = new String[n][2];
            sink = new Object[2][n][4];
            sink = new int[n][];
            sink = new int[n][2][];
            sink = new String[n][];
            sink = new String[2][n][];
        }
    }

    public static final class IntMultiArrayProgram {
        static int[][] sink;

        private IntMultiArrayProgram() {
        }

        public static void main(String[] args) {
            sink = new int[16][16];
        }
    }

    public static final class ObjectMultiArrayProgram {
        static Object[][][] sink;

        private ObjectMultiArrayProgram() {
        }

        public static void main(String[] args) {
            sink = new Object[args.length + 1][2][3];
        }
    }

    public static final class EnumValueOfProgram {
        static Object sink;

        private EnumValueOfProgram() {
        }

        public static void main(String[] args) {
            sink = Color.valueOf(args.length > 0 ? args[0] : "GREEN");
        }
    }

    public static final class EnumSetProgram {
        static Object sink;

        private EnumSetProgram() {
        }

        public static void main(String[] args) {
            var set = EnumSet.noneOf(Color.class);
            set.add(Color.BLUE);
            sink = set;
        }
    }

    public static final class StreamCollectProgram {
        static Object sink;

        private StreamCollectProgram() {
        }

        public static void main(String[] args) {
            // Collector.of builds an EnumSet of collector characteristics
            sink = Stream.of("a", "b").collect(Collectors.joining(","));
        }
    }

    enum Color {
        RED,
        GREEN,
        BLUE
    }

    static class Log implements TeaVMToolLog {
        final StringBuilder sb = new StringBuilder();

        @Override
        public void info(String text) {
            appendLine(text);
        }

        @Override
        public void debug(String text) {
            appendLine(text);
        }

        @Override
        public void warning(String text) {
            appendLine(text);
        }

        @Override
        public void error(String text) {
            appendLine(text);
        }

        @Override
        public void info(String text, Throwable e) {
            appendLine(text);
        }

        @Override
        public void debug(String text, Throwable e) {
            appendLine(text);
        }

        @Override
        public void warning(String text, Throwable e) {
            appendLine(text);
        }

        @Override
        public void error(String text, Throwable e) {
            appendLine(text);
        }

        private void appendLine(String text) {
            sb.append(text).append('\n');
        }
    }
}
