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

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.teavm.backend.c.CTarget;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.backend.wasm.WasmGCTarget;
import org.teavm.browserrunner.BrowserRunDescriptor;
import org.teavm.browserrunner.BrowserRunner;
import org.teavm.jso.JSObject;
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
 * on JS, and on C. ReferenceResolver compiles a cast to the missing class into a throw of ClassCastException, and an
 * instanceof of it into false, and turns creating an instance or array of it into a link error, so the class has no
 * non-null values. A class on the class path that extends or implements it fails to link; the last tests check that.
 */
public class MissingClassTypeTest {
    // A constant, not MissingException.class.getName(): a class literal of the missing class is a link error.
    private static final String MISSING = "org.teavm.wasm.MissingClassTypeTest$MissingException";
    private static final String MISSING_INTERFACE = "org.teavm.wasm.MissingClassTypeTest$MissingInterface";
    private static boolean wasmGCNeeded = Boolean.parseBoolean(System.getProperty("teavm.junit.wasm-gc", "true"));
    private static boolean jsNeeded = Boolean.parseBoolean(System.getProperty("teavm.junit.js", "true"));
    private static String cCompiler = System.getProperty("teavm.junit.c.compiler");
    // The C run below does not set up an environment script (as Windows needs), so it runs only without one.
    private static boolean cNeeded = Boolean.parseBoolean(System.getProperty("teavm.junit.c", "true"))
            && cCompiler != null && !cCompiler.isEmpty()
            && System.getProperty("teavm.junit.c.envScript", "").isEmpty();
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

    @Test
    public void catchOfMissingClass() throws IOException {
        check(CatchMissing.class);
    }

    @Test
    public void castToMissingClass() throws IOException {
        check(CastToMissing.class);
    }

    @Test
    public void nonNullCastToMissingClass() throws IOException {
        check(NonNullCastToMissing.class);
    }

    @Test
    public void instanceOfMissingClass() throws IOException {
        check(InstanceOfMissing.class);
    }

    @Test
    public void arraysOfMissingClass() throws IOException {
        check(ArraysOfMissing.class);
    }

    @Test
    public void unlinkedClass() throws IOException {
        check(UnlinkedClass.class);
    }

    @Test
    public void receiverOfKnownType() throws IOException {
        check(ReceiverOfKnownType.class);
    }

    // The bottom type is sound only if no value of a missing class can be non-null. A class on the class path that
    // extends or implements the missing class would have such values, so it must not link.
    @Test
    public void subclassOfMissingClassDoesNotLink() {
        checkLinkError(SubclassOfMissing.class, "extends " + MISSING);
    }

    @Test
    public void subclassAfterCatchOfMissingClassDoesNotLink() {
        checkLinkError(CatchThenSubclass.class, MISSING + " was not found");
    }

    @Test
    public void implementationOfMissingInterfaceDoesNotLink() {
        checkLinkError(ImplOfMissingInterface.class, "implements " + MISSING_INTERFACE);
    }

    @Test
    public void lambdaOfMissingInterfaceDoesNotLink() {
        checkLinkError(LambdaOfMissingInterface.class, "extends " + MISSING_INTERFACE);
    }

    @ParameterizedTest
    @MethodSource("missingJSParents")
    public void javaScriptInterfaceWithMissingParentDoesNotCrash(String backend, TeaVMOptimizationLevel level,
            Class<?> entry) {
        TeaVMTarget target;
        String fileName;
        switch (backend) {
            case "js":
                assumeTrue(jsNeeded);
                target = new JavaScriptTarget();
                fileName = "test.js";
                break;
            case "wasm-gc":
                assumeTrue(wasmGCNeeded);
                target = new WasmGCTarget();
                fileName = "test.wasm";
                break;
            default:
                assumeTrue(cNeeded);
                target = new CTarget(new CNameProvider());
                fileName = "";
                break;
        }
        checkLinkError(buildVm(target, entry, level, "missing-js-parent-" + backend + "-" + level
                + "-" + entry.getSimpleName(), fileName), MISSING_INTERFACE);
    }

    private static List<Arguments> missingJSParents() {
        var cases = new ArrayList<Arguments>();
        for (var backend : List.of("js", "wasm-gc", "c")) {
            for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
                for (var entry : List.of(ImplementJSInterface.class, ImplementTransitiveJSInterface.class)) {
                    cases.add(Arguments.of(backend, level, entry));
                }
            }
        }
        return cases;
    }

    private void checkLinkError(Class<?> entry, String expectedMessagePart) {
        var name = entry.getSimpleName() + "-link";
        var vm = buildVm(new WasmGCTarget(), entry, TeaVMOptimizationLevel.SIMPLE, name, "test.wasm");
        checkLinkError(vm, expectedMessagePart);
    }

    private void checkLinkError(TeaVM vm, String expectedMessagePart) {
        var log = new StringBuilder();
        TeaVMProblemRenderer.describeProblems(vm, new ConsoleTeaVMToolLog(false) {
            @Override
            public void error(String text) {
                log.append(text).append('\n');
            }
        });
        if (!log.toString().contains(expectedMessagePart)) {
            throw new AssertionError("Expected a link error containing \"" + expectedMessagePart + "\", got:\n"
                    + log);
        }
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
        if (cNeeded) {
            runC(entry, name + "-c");
        }
    }

    // C has no validation step, but must compile the program and run it to a normal exit.
    private static void runC(Class<?> entry, String dir) throws IOException {
        build(new CTarget(new CNameProvider()), entry, TeaVMOptimizationLevel.SIMPLE, dir, "");
        var outputDir = new File(targetFile, dir);
        try {
            var compile = new ProcessBuilder(new File(cCompiler).getAbsolutePath()).directory(outputDir)
                    .inheritIO().start();
            if (compile.waitFor() != 0) {
                throw new AssertionError("C compiler error in " + dir);
            }
            var run = new ProcessBuilder(new File(outputDir, "run_test").getAbsolutePath()).directory(outputDir)
                    .inheritIO().start();
            var exitCode = run.waitFor();
            if (exitCode != 0) {
                throw new AssertionError("C program " + dir + " exited with code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static void build(TeaVMTarget target, Class<?> entry, TeaVMOptimizationLevel level, String dir,
            String fileName) {
        var vm = buildVm(target, entry, level, dir, fileName);
        if (!vm.getProblemProvider().getSevereProblems().isEmpty()) {
            TeaVMProblemRenderer.describeProblems(vm, new ConsoleTeaVMToolLog(false));
            throw new AssertionError("TeaVM compilation error in " + dir);
        }
    }

    private static TeaVM buildVm(TeaVMTarget target, Class<?> entry, TeaVMOptimizationLevel level, String dir,
            String fileName) {
        var builder = new TeaVMBuilder(target);
        var classSource = builder.getClassSource();
        builder.setClassSource(name -> name.equals(MISSING) || name.equals(MISSING_INTERFACE)
                ? null : classSource.get(name));
        TeaVM vm = builder.build();
        vm.installPlugins();
        vm.setEntryPoint(entry.getName());
        vm.setOptimizationLevel(level);
        var outputDir = new File(targetFile, dir);
        outputDir.mkdirs();
        vm.build(outputDir, fileName);
        return vm;
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

    public interface MissingInterface {
        void run();
    }

    public interface JSInterfaceWithMissingParent extends JSObject, MissingInterface {
    }

    public static class JSImplementation implements JSInterfaceWithMissingParent {
        @Override
        public void run() {
        }
    }

    public static class ImplementJSInterface {
        public static void main(String[] args) {
            Sinks.objectSink = new JSImplementation();
        }
    }

    public interface MissingParentFirst extends MissingInterface, JSObject {
    }

    public interface TransitiveJSInterface extends MissingParentFirst {
    }

    public static class TransitiveJSImplementation implements TransitiveJSInterface {
        @Override
        public void run() {
        }
    }

    public static class ImplementTransitiveJSInterface {
        public static void main(String[] args) {
            Sinks.objectSink = new TransitiveJSImplementation();
        }
    }

    public static class SubOfMissing extends MissingException {
    }

    public static class ImplOfMissing implements MissingInterface {
        @Override
        public void run() {
        }
    }

    static class Sinks {
        static Object objectSink;

        static void takeMissing(MissingException e) {
            objectSink = e;
        }

        static void mayThrowMissing() throws MissingException {
            objectSink = null;
        }

        static void takeMissingInterface(MissingInterface e) {
            objectSink = e;
        }

        static MissingException[] missingArray;
        static MissingException[][] missingArray2;

        static void takeThrowables(Throwable[] array) {
            objectSink = array;
        }
    }

    public static class CatchMissing {
        public static void main(String[] args) {
            try {
                if (args.length > 100) {
                    throw new java.io.IOException();
                }
                Sinks.takeMissing(null);
            } catch (MissingException e) {
                Sinks.objectSink = e;
            } catch (IOException e) {
                Sinks.objectSink = e;
            }
            System.out.println("catch " + Sinks.objectSink);
        }
    }

    public static class CastToMissing {
        public static void main(String[] args) {
            Object o = args.length > 100 ? new Object() : null;
            try {
                Sinks.takeMissing((MissingException) o);
                System.out.println("cast ok");
            } catch (ClassCastException e) {
                System.out.println("cast CCE");
            }
        }
    }

    public static class NonNullCastToMissing {
        public static void main(String[] args) {
            Object value = new IOException("known");
            try {
                Sinks.takeMissing((MissingException) value);
            } catch (ClassCastException e) {
                return;
            }
            throw new AssertionError("A non-null value cannot have a missing type");
        }
    }

    public static class InstanceOfMissing {
        public static void main(String[] args) {
            Object value = args.length > 100 ? null : new IOException("known");
            if (value instanceof MissingException) {
                throw new AssertionError("A value cannot be an instance of a missing class");
            }
            Object nullValue = Source.make();
            if (nullValue instanceof MissingException) {
                throw new AssertionError("Null cannot be an instance of a missing class");
            }
        }
    }

    public static class SubclassOfMissing {
        public static void main(String[] args) {
            Sinks.takeMissing(new SubOfMissing());
            System.out.println("sub " + (Sinks.objectSink != null));
        }
    }

    public static class ImplOfMissingInterface {
        public static void main(String[] args) {
            Sinks.takeMissingInterface(new ImplOfMissing());
            System.out.println("impl " + (Sinks.objectSink != null));
        }
    }

    public static class CatchThenSubclass {
        public static void main(String[] args) {
            try {
                Sinks.mayThrowMissing();
            } catch (MissingException e) {
                Sinks.objectSink = e;
            }
            Sinks.takeMissing(new SubOfMissing());
            System.out.println("catch-then-sub " + (Sinks.objectSink != null));
        }
    }

    public static class LambdaOfMissingInterface {
        public static void main(String[] args) {
            Sinks.takeMissingInterface(() -> { });
            System.out.println("lambda " + (Sinks.objectSink != null));
        }
    }

    public static class ArraysOfMissing {
        public static void main(String[] args) {
            MissingException[] a = Sinks.missingArray;
            Sinks.takeThrowables(a);
            Object[] o = Sinks.missingArray2;
            Sinks.objectSink = o;
            Throwable[] target = new Throwable[1];
            if (a != null) {
                System.arraycopy(a, 0, target, 0, 1);
                System.out.println(a.length);
                Throwable t = a[0];
                System.out.println(t);
                a[0] = null;
                MissingException e = a[0];
                Sinks.takeMissing(e);
                MissingException[] row = Sinks.missingArray2[0];
                Sinks.objectSink = row.clone();
            }
            Throwable[] merged = args.length > 100 ? Sinks.missingArray : target;
            System.out.println("arrays " + merged.length);
        }
    }

    public static class ReceiverOfKnownType {
        public static void main(String[] args) {
            Throwable t = Source.make();
            System.out.println("instanceof " + (t instanceof IOException));
            System.out.println("eq " + (t == Sinks.objectSink));
            if (t != null) {
                System.out.println(t.getMessage());
                System.out.println(t.hashCode());
                System.out.println(t.getClass());
                synchronized (t) {
                    System.out.println("locked");
                }
            }
            Object o = args.length > 100 ? Source.make() : "known";
            System.out.println("merged " + o.toString());
        }
    }

    // On the class path, but never instantiated: it keeps the Object mapping, and must still compile and run.
    public static class NeverCreated extends IOException {
        public void touch() {
        }
    }

    static class UnlinkedSource {
        static NeverCreated field;
    }

    public static class UnlinkedClass {
        public static void main(String[] args) {
            try {
                NeverCreated e = UnlinkedSource.field;
                if (e != null) {
                    e.touch();
                    throw e;
                }
                Throwable t = args.length > 100 ? e : new IOException("known");
                System.out.println(t.getMessage());
            } catch (IOException e) {
                System.out.println("caught");
            }
        }
    }
}
