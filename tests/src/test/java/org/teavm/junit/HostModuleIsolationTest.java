/*
 *  Copyright 2012 Alexey Andreev.
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
package org.teavm.junit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.teavm.backend.c.CTarget;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.backend.javascript.JavaScriptTarget;
import org.teavm.backend.wasm.WasmGCTarget;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.interop.Address;
import org.teavm.jso.JSObject;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.parsing.ClasspathResourceProvider;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.platform.Platform;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMBuilder;
import org.teavm.vm.TeaVMOptimizationLevel;
import org.teavm.vm.TeaVMTarget;

public class HostModuleIsolationTest {
    @TempDir
    File output;

    @Test
    public void sharedJUnitClassSourceUsesOnlyClasslib() {
        assertNull(TeaVMTestInfrastructure.classSource.get("javax.crypto.Cipher"));
        assertNotNull(TeaVMTestInfrastructure.classSource.get("java.util.concurrent.Future"));
        assertNotNull(TeaVMTestInfrastructure.classSource.get("javax.crypto.spec.SecretKeySpec"));
    }

    @Test
    public void missingSubstituteCannotFallBackThroughAnotherLoader() {
        var parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override
            public Enumeration<URL> getResources(String name) throws java.io.IOException {
                return name.equals("org/teavm/classlib/java/util/concurrent/TFuture.class")
                        ? Collections.emptyEnumeration() : super.getResources(name);
            }
        };
        try (var provider = new ClasspathResourceProvider(loader)) {
            var source = new ClasspathClassHolderSource(provider, new ReferenceCache(), loader);
            assertNull(source.get("java.util.concurrent.Future"));
            assertNotNull(source.get("javax.crypto.spec.SecretKeySpec"));
        }
    }

    @Test
    public void junitCompilerResourceLookupAlsoExcludesHostModules() {
        var support = new JSPlatformSupport(TeaVMTestInfrastructure.classSource,
                TeaVMTestInfrastructure.referenceCache);
        var result = support.compile(TeaVMTestConfiguration.JS_DEFAULT, JavaScriptTarget::new,
                Program.class.getName(), output, ".js", null, vm -> {
                    assertNull(vm.getResourceProvider().getResource("javax/crypto/Cipher.class"));
                    assertNotNull(vm.getResourceProvider()
                            .getResource("org/teavm/classlib/java/util/concurrent/TFuture.class"));
                }, "isolated");
        assertTrue(result.success, result.errorMessage);
        assertNull(result.throwable);
    }

    @Test
    public void explicitClasspathCompilesWithoutRuntimeModules() throws Exception {
        var entries = new ArrayList<File>();
        for (var cls : List.of(getClass(), TObject.class, TeaVM.class, Platform.class, Address.class, JSObject.class,
                Class.forName("org.teavm.jso.impl.JS"), org.teavm.jso.browser.Window.class)) {
            entries.add(new File(cls.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        try (var provider = ResourceProvider.ofClassPath(entries)) {
            assertNull(provider.getResource("java/lang/Object.class"));
            assertNull(provider.getResource("javax/crypto/Cipher.class"));
            var loader = getClass().getClassLoader();
            var source = new ClasspathClassHolderSource(provider, new ReferenceCache(), loader);
            assertNotNull(source.get("java.lang.Object"));
            assertNotNull(source.get("java.util.concurrent.Future"));
            assertNull(source.get("javax.crypto.Cipher"));
            for (var factory : List.<Supplier<TeaVMTarget>>of(JavaScriptTarget::new, WasmGCTarget::new,
                    () -> new CTarget(new CNameProvider()))) {
                for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
                    var vm = new TeaVMBuilder(factory.get()).setClassLoader(loader)
                            .setClassSource(source).setResourceProvider(provider).build();
                    vm.installPlugins();
                    vm.setEntryPoint(Program.class.getName());
                    vm.setOptimizationLevel(level);
                    vm.build(name -> new ByteArrayOutputStream(), "isolated");
                    assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty(),
                            vm.getProblemProvider().getSevereProblems().stream()
                                    .map(p -> p.getText() + java.util.Arrays.toString(p.getParams()))
                                    .collect(java.util.stream.Collectors.joining("\n")));
                }
            }
        }
    }

    public static class Program {
        public static void main(String[] args) throws Exception {
            var key = new SecretKeySpec(new byte[] { 1 }, "AES");
            var executor = Executors.newFixedThreadPool(1);
            try {
                System.out.println(executor.<String>invokeAny(List.of(key::getAlgorithm)));
            } finally {
                executor.shutdown();
            }
        }
    }
}
