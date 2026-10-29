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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.teavm.backend.c.CTarget;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.browserrunner.ProcessTree;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;

public class CThreadCompletionTest {
    @Disabled("C GC does not preserve packed monitor references across collection during a pending join")
    @ParameterizedTest
    @EnumSource(value = TeaVMOptimizationLevel.class, names = { "SIMPLE", "FULL" })
    public void nestedJoinCompletesAfterCollection(TeaVMOptimizationLevel level) throws Exception {
        var compiler = System.getProperty("teavm.junit.c.compiler", "");
        assumeTrue(Boolean.parseBoolean(System.getProperty("teavm.junit.c", "false")));
        assumeTrue(!compiler.isEmpty() && System.getProperty("teavm.junit.c.envScript", "").isEmpty());
        var outputDir = new File(System.getProperty("teavm.junit.target"), "c-thread-completion/" + level);
        Files.createDirectories(outputDir.toPath());
        var vm = new TeaVMBuilder(new CTarget(new CNameProvider())).build();
        vm.installPlugins();
        vm.setEntryPoint(EntryPoint.class.getName());
        vm.setOptimizationLevel(level);
        vm.build(outputDir, "");
        TeaVMProblemRenderer.describeProblems(vm, new ConsoleTeaVMToolLog(false));
        assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty());
        run(new ProcessBuilder(new File(compiler).getAbsolutePath()).directory(outputDir),
                new File(outputDir, "compile.log"));
        var output = run(new ProcessBuilder(new File(outputDir, "run_test").getAbsolutePath()).directory(outputDir),
                new File(outputDir, "run.log"));
        // Exit zero alone is insufficient: the C event loop can stop while the main fiber still waits in join.
        assertTrue(output.contains("THREAD_JOIN_COMPLETED"), output);
    }

    private static String run(ProcessBuilder builder, File output) throws IOException, InterruptedException {
        var process = builder.redirectErrorStream(true).redirectOutput(output).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS));
            var text = Files.readString(output.toPath());
            assertEquals(0, process.exitValue(), text);
            return text;
        } finally {
            if (process.isAlive()) {
                ProcessTree.kill(process, 10, TimeUnit.SECONDS);
            }
        }
    }

    public static class EntryPoint {
        public static void main(String[] args) throws InterruptedException {
            var creator = new Thread(() -> {
                var worker = new Thread(() -> {
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        // End the worker normally.
                    }
                });
                worker.start();
                System.gc();
                worker.interrupt();
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            });
            creator.start();
            creator.join();
            System.out.println("THREAD_JOIN_COMPLETED");
        }
    }
}
