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
package org.teavm.wasm;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.backend.wasm.WasmGCTarget;
import org.teavm.browserrunner.BrowserRunDescriptor;
import org.teavm.browserrunner.BrowserRunner;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMOptimizationLevel;
import org.teavm.vm.TeaVMTarget;

/**
 * A class that is missing from the class path, used only in signatures (a parameter, return, or field type), is not a
 * link error, so TeaVM writes a module. Each shape below lets a value of such a class meet a slot of a known type
 * (throw, call argument, return, field store, a merge with a known type). Each program is built with the missing
 * class hidden from the class source, run on Wasm GC (which validates the module first) at two optimization levels,
 * and on JS. A cast or instanceof that names the missing class is a link error, which stops the build before code
 * generation, so this test has no such shape.
 */
public class MissingClassTypeTest {
    // A constant, not MissingException.class.getName(): a class literal of the missing class is a link error.
    private static final String MISSING = "org.teavm.wasm.MissingClassTypeTest$MissingException";
    private static boolean wasmGCNeeded = Boolean.parseBoolean(System.getProperty("teavm.junit.wasm-gc", "true"));
    private static boolean jsNeeded = Boolean.parseBoolean(System.getProperty("teavm.junit.js", "true"));
    private static File targetFile = new File(new File(System.getProperty("teavm.junit.target")),
            "missing-class-type");
    private static BrowserRunner browserRunner = new BrowserRunner(
            targetFile,
            "JAVASCRIPT",
            BrowserRunner.pickBrowser(System.getProperty("teavm.junit.js.runner")),
            false
    );

    @BeforeAll
    public static void start() {
        if (wasmGCNeeded || jsNeeded) {
            browserRunner.start();
        }
    }

    @AfterAll
    public static void stop() {
        if (wasmGCNeeded || jsNeeded) {
            browserRunner.stop();
        }
    }

    @Test
    public void throwOfParameter() throws IOException {
        check(ThrowParameter.class);
    }

    @Test
    public void throwOfReturnValue() throws IOException {
        check(ThrowReturnValue.class);
    }

    @Test
    public void throwOfField() throws IOException {
        check(ThrowField.class);
    }

    @Test
    public void callArgument() throws IOException {
        check(CallArgument.class);
    }

    @Test
    public void returnAsKnownType() throws IOException {
        check(ReturnAsKnownType.class);
    }

    @Test
    public void storeAsKnownType() throws IOException {
        check(StoreAsKnownType.class);
    }

    @Test
    public void mergeWithKnownType() throws IOException {
        check(MergeWithKnownType.class);
    }

    private void check(Class<?> entry) throws IOException {
        var name = entry.getSimpleName();
        if (wasmGCNeeded) {
            for (var level : List.of(TeaVMOptimizationLevel.FULL, TeaVMOptimizationLevel.SIMPLE)) {
                var dir = name + "-wasm-gc-" + level.name().toLowerCase();
                var target = new WasmGCTarget();
                target.setObfuscated(false);
                build(target, entry, level, dir, "test.wasm");
                browserRunner.runTest(new BrowserRunDescriptor(dir,
                        "resources/org/teavm/wasm-integration/missing-class/launcher.js", false, List.of(),
                        dir + "/test.wasm", false));
            }
        }
        if (jsNeeded) {
            var dir = name + "-js";
            build(new JavaScriptTarget(), entry, TeaVMOptimizationLevel.SIMPLE, dir, "test.js");
            browserRunner.runTest(new BrowserRunDescriptor(dir, "tests/" + dir + "/test.js", false, List.of(),
                    null, false));
        }
    }

    private static void build(TeaVMTarget target, Class<?> entry, TeaVMOptimizationLevel level, String dir,
            String fileName) {
        var builder = new TeaVMBuilder(target);
        var classSource = builder.getClassSource();
        builder.setClassSource(name -> name.equals(MISSING) ? null : classSource.get(name));
        TeaVM vm = builder.build();
        vm.installPlugins();
        vm.setEntryPoint(entry.getName());
        vm.setOptimizationLevel(level);
        var outputDir = new File(targetFile, dir);
        outputDir.mkdirs();
        vm.build(outputDir, fileName);
        if (!vm.getProblemProvider().getSevereProblems().isEmpty()) {
            TeaVMProblemRenderer.describeProblems(vm, new ConsoleTeaVMToolLog(false));
            throw new AssertionError("TeaVM compilation error in " + dir);
        }
    }

    // Hidden from the class source in every build: it exists only for javac.
    public static class MissingException extends IOException {
    }

    // The shapes read the missing-class value from here, not from the test class: TeaVM must not compile the test
    // class's static initializer (the browser runner, system properties) into the programs.
    static class Source {
        static MissingException field;

        static MissingException make() {
            return field;
        }
    }

    public static class ThrowParameter {
        public static void main(String[] args) {
            try {
                throwIt(null);
            } catch (IOException | RuntimeException e) {
                // a missing class has only null values
            }
        }

        static void throwIt(MissingException e) throws IOException {
            if (e != null) {
                throw e;
            }
        }
    }

    public static class ThrowReturnValue {
        public static void main(String[] args) {
            try {
                if (Source.field == null) {
                    throw Source.make();
                }
            } catch (IOException | RuntimeException e) {
                // throw null is a NullPointerException
            }
        }
    }

    public static class ThrowField {
        public static void main(String[] args) {
            try {
                if (Source.field != null) {
                    throw Source.field;
                }
            } catch (IOException e) {
                // unreachable: the field is null
            }
        }
    }

    public static class CallArgument {
        public static void main(String[] args) {
            log(Source.make());
        }

        static void log(Throwable t) {
            System.out.println("log " + t);
        }
    }

    public static class ReturnAsKnownType {
        public static void main(String[] args) {
            System.out.println(asThrowable());
        }

        static Throwable asThrowable() {
            return Source.make();
        }
    }

    public static class StoreAsKnownType {
        static Throwable sink;

        public static void main(String[] args) {
            sink = Source.make();
            System.out.println(sink);
        }
    }

    public static class MergeWithKnownType {
        public static void main(String[] args) {
            try {
                IOException e = args.length > 100 ? Source.make() : new IOException("merged");
                throw e;
            } catch (IOException e) {
                // expected: the known branch
            }
        }
    }
}
