/*
 *  Copyright 2016 Alexey Andreev.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.teavm.backend.wasm.WasmGCTarget;
import org.teavm.cache.CacheStatus;
import org.teavm.cache.InMemoryProgramCache;
import org.teavm.cache.InMemorySymbolTable;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;

public class JspiCompilationTest {
    @Test
    public void modeIsLocalToEachBuildAndDisabledByDefault() {
        for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
            var defaultModule = compile(null, level);
            assertFalse(importsJspi(defaultModule));
            assertTrue(importsJspi(compile("true", level)));
            assertArrayEquals(defaultModule, compile("false", level));
        }
    }

    @Test
    public void changingModeInvalidatesCachedFiberPrograms() {
        for (var level : List.of(TeaVMOptimizationLevel.SIMPLE, TeaVMOptimizationLevel.FULL)) {
            var cache = new CountingCache();
            var disabled = compile("false", level, cache);
            assertFalse(importsJspi(disabled));
            assertTrue(importsJspi(compile("true", level, cache)));
            assertArrayEquals(disabled, compile("false", level, cache));
            assertTrue(cache.hits > 0);
        }
    }

    private boolean importsJspi(byte[] module) {
        return new String(module, StandardCharsets.ISO_8859_1).contains("teavmJspi");
    }

    private byte[] compile(String jspi, TeaVMOptimizationLevel level) {
        return compile(jspi, level, null);
    }

    private byte[] compile(String jspi, TeaVMOptimizationLevel level, CountingCache cache) {
        var vm = new TeaVMBuilder(new WasmGCTarget()).build();
        if (cache != null) {
            vm.setProgramCache(cache);
            vm.setCacheStatus(new CacheStatus() {
                @Override
                public boolean isStaleClass(String className) {
                    return false;
                }

                @Override
                public boolean isStaleMethod(MethodReference method) {
                    return false;
                }
            });
        }
        vm.installPlugins();
        vm.setEntryPoint(Program.class.getName());
        vm.setOptimizationLevel(level);
        var properties = new Properties();
        if (jspi != null) {
            properties.setProperty("teavm.wasm.jspi", jspi);
        }
        vm.setProperties(properties);
        var files = new HashMap<String, ByteArrayOutputStream>();
        vm.build(name -> {
            var stream = new ByteArrayOutputStream();
            files.put(name, stream);
            return stream;
        }, "test.wasm");
        assertTrue(vm.getProblemProvider().getSevereProblems().isEmpty(),
                () -> vm.getProblemProvider().getSevereProblems().toString());
        if (cache != null) {
            cache.commit();
        }
        return files.get("test.wasm").toByteArray();
    }

    private static class CountingCache extends InMemoryProgramCache {
        int hits;

        CountingCache() {
            super(new ReferenceCache(), new InMemorySymbolTable(), new InMemorySymbolTable(),
                    new InMemorySymbolTable());
        }

        @Override
        public org.teavm.model.Program get(MethodReference method, CacheStatus status) {
            var program = super.get(method, status);
            if (program != null) {
                ++hits;
            }
            return program;
        }
    }

    public static class Program {
        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(1);
        }
    }
}
