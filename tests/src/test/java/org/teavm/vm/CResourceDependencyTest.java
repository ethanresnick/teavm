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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.teavm.backend.c.CTarget;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.backend.c.runtime.CResources;
import org.teavm.classlib.support.Reflectable;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class CResourceDependencyTest {
    private static final MethodReference CREATE = new MethodReference(CResources.class, "create",
            String.class, int.class, int.class, CResources.Resource.class);
    private static final MethodReference ACQUIRE = new MethodReference(CResources.class, "acquireResources",
            CResources.Resource[].class);
    private static final MethodReference INITIALIZER = new MethodReference(CResources.class, "<clinit>", void.class);

    static Stream<TeaVMOptimizationLevel> levels() {
        return Boolean.parseBoolean(System.getProperty("teavm.junit.optimized", "true"))
                ? Stream.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)
                : Stream.of(TeaVMOptimizationLevel.SIMPLE);
    }

    @ParameterizedTest
    @MethodSource("levels")
    public void programWithoutResourcesDoesNotLinkFactory(TeaVMOptimizationLevel level) {
        var vm = compile(NoResources.class, level, false);
        assertNull(vm.getDependencyInfo().getMethod(CREATE));
        assertNull(vm.getDependencyInfo().getMethod(ACQUIRE));
        assertNull(vm.getDependencyInfo().getMethod(INITIALIZER));
    }

    @ParameterizedTest
    @MethodSource("levels")
    public void directReaderRetainsFactory(TeaVMOptimizationLevel level) {
        assertResourceDependencies(compile(DirectReader.class, level, true));
    }

    @ParameterizedTest
    @MethodSource("levels")
    public void reflectiveReaderRetainsFactory(TeaVMOptimizationLevel level) {
        assertResourceDependencies(compile(ReflectiveReader.class, level, true));
    }

    private void assertResourceDependencies(TeaVM vm) {
        var dependencies = vm.getDependencyInfo();
        var create = dependencies.getMethod(CREATE);
        assertNotNull(create);
        assertTrue(create.isUsed());
        assertTrue(dependencies.getMethod(INITIALIZER).isUsed());
        var result = dependencies.getMethod(ACQUIRE).getResult();
        var resourceType = ValueType.object(CResources.Resource.class.getName());
        assertAll(
                () -> assertTrue(create.getVariable(1).hasType(ValueType.object(String.class.getName())),
                        "native factory name argument"),
                () -> assertTrue(result.hasType(ValueType.arrayOf(resourceType)), "native array result"),
                () -> assertTrue(result.getArrayItem().hasType(resourceType), "native array elements"));
    }

    private TeaVM compile(Class<?> entryPoint, TeaVMOptimizationLevel level, boolean resourcesExpected) {
        assumeTrue(Boolean.parseBoolean(System.getProperty("teavm.junit.c", "true")));
        var files = new HashSet<String>();
        var manifest = new ByteArrayOutputStream();
        var vm = new TeaVMBuilder(new CTarget(new CNameProvider())).build();
        vm.installPlugins();
        vm.setEntryPoint(entryPoint.getName());
        vm.setOptimizationLevel(level);
        vm.build(name -> {
            files.add(name);
            return name.equals("all.txt") ? manifest : OutputStream.nullOutputStream();
        }, "test");
        assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty(),
                () -> vm.getProblemProvider().getSevereProblems().toString());
        if (resourcesExpected) {
            assertTrue(manifest.toString(StandardCharsets.UTF_8).contains("CResources"));
            assertTrue(files.contains("resources_gen.h"));
        } else {
            assertFalse(manifest.toString(StandardCharsets.UTF_8).contains("CResources"));
            assertFalse(files.contains("resources_gen.h"));
        }
        return vm;
    }

    public static final class NoResources {
        private NoResources() {
        }

        public static void main(String[] args) {
        }
    }

    public static final class DirectReader {
        static InputStream sink;

        private DirectReader() {
        }

        public static void main(String[] args) {
            sink = ClassLoader.getSystemResourceAsStream(args.length > 0 ? args[0] : "resources-for-test/2");
        }
    }

    public static final class ReflectiveReader {
        static Object sink;

        private ReflectiveReader() {
        }

        public static void main(String[] args) throws Exception {
            sink = Reader.class.getDeclaredMethod("read", String.class).invoke(null, "resources-for-test/2");
        }
    }

    public static final class Reader {
        private Reader() {
        }

        @Reflectable
        public static InputStream read(String name) {
            return ClassLoader.getSystemResourceAsStream(name);
        }
    }
}
