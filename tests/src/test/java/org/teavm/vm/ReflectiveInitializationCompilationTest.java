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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.teavm.backend.c.CTarget;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.classlib.support.Reflectable;
import org.teavm.model.MethodReference;

public class ReflectiveInitializationCompilationTest {
    @Test
    public void cRejectsSuspendingReflectiveInitializer() {
        var checks = new ArrayList<Executable>();
        for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
            for (var entry : List.of(AsyncProgram.class, AsyncFieldProgram.class, AsyncEnumProgram.class,
                    AsyncConstructorProgram.class, AsyncClassNewInstanceProgram.class)) {
                checks.add(() -> {
                    var vm = compile(entry, level);
                    assertTrue(vm.getProblemProvider().getSevereProblems().stream().anyMatch(p ->
                            p.getText().contains(
                                    "does not support reflective initialization of a class that can suspend")),
                            entry.getSimpleName() + " " + level);
                });
            }
        }
        assertAll(checks);
    }

    @Test
    public void cAcceptsSynchronousReflectiveInitializer() {
        for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
            var vm = compile(SyncProgram.class, level);
            assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty());
        }
    }

    @Test
    public void cAcceptsLookupWithoutInitializingAsyncClass() {
        for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
            var vm = compile(LookupProgram.class, level);
            assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty());
            var initializer = vm.getDependencyInfo().getMethod(
                    new MethodReference(AsyncTarget.class, "<clinit>", void.class));
            assertTrue(initializer == null || !initializer.isUsed());
        }
    }

    public static class LookupProgram {
        public static void main(String[] args) throws ClassNotFoundException {
            boolean initialize = false;
            boolean alias = initialize;
            Class.forName(AsyncTarget.class.getName(), alias, LookupProgram.class.getClassLoader());
        }
    }

    private TeaVM compile(Class<?> entryPoint, TeaVMOptimizationLevel level) {
        var vm = new TeaVMBuilder(new CTarget(new CNameProvider())).build();
        vm.installPlugins();
        vm.setEntryPoint(entryPoint.getName());
        vm.setOptimizationLevel(level);
        vm.build(fileName -> new ByteArrayOutputStream(), "test");
        return vm;
    }

    public static class AsyncProgram {
        public static void main(String[] args) throws ClassNotFoundException {
            Class.forName(AsyncTarget.class.getName());
        }
    }

    public static class AsyncFieldProgram {
        public static void main(String[] args) throws Exception {
            AsyncTarget.class.getField("value").getInt(null);
        }
    }

    public static class AsyncConstructorProgram {
        public static void main(String[] args) throws Exception {
            AsyncTarget.class.getConstructor().newInstance();
        }
    }

    public static class AsyncClassNewInstanceProgram {
        @SuppressWarnings("deprecation")
        public static void main(String[] args) throws Exception {
            AsyncTarget.class.newInstance();
        }
    }

    public static class AsyncEnumProgram {
        public static void main(String[] args) {
            Enum.valueOf(AsyncEnum.class, "ITEM");
        }
    }

    enum AsyncEnum {
        ITEM;

        static {
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    public static class AsyncTarget {
        @Reflectable
        public static int value;

        @Reflectable
        public AsyncTarget() {
        }

        static {
            pause();
        }
    }

    public static class SyncProgram {
        public static void main(String[] args) throws ClassNotFoundException {
            Class.forName(SyncTarget.class.getName());
        }
    }

    static class SyncTarget {
        static int value = (int) System.currentTimeMillis();
    }
}
