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
package org.teavm.backend.wasm.intrinsics;

import org.teavm.backend.wasm.WasmGCJspi;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.instruction.WasmUnreachable;
import org.teavm.model.MethodReference;
import org.teavm.runtime.Fiber;

/**
 * kafka-wasm: the bodies of Fiber's JSPI natives. jspiEnabled is a build-time constant. jspiStart, jspiPark, and
 * jspiUnpark call the host imports teavmJspi.start(fiber, run), teavmJspi.park(fiber), and teavmJspi.unpark(fiber).
 * The host wraps park with WebAssembly.Suspending and calls run (Fiber.jspiRun) through WebAssembly.promising
 * (kafka-wasm: installJspi in js/teavm-imports.mjs). In coroutine mode the three bodies are unreachable: Fiber checks
 * jspiEnabled first, but they stay reachable, and that build links neither the imports nor Fiber.jspiRun.
 */
public class FiberJspiIntrinsic implements WasmGCBodyIntrinsic {
    private final WasmGCCodeGenContext context;
    private WasmFunction startImport;
    private WasmFunction parkImport;
    private WasmFunction unparkImport;

    public FiberJspiIntrinsic(WasmGCCodeGenContext context) {
        this.context = context;
    }

    @Override
    public void apply(MethodReference method, WasmFunction function) {
        var fiberType = context.classInfoProvider().getClassInfo(Fiber.class.getName()).getType();
        var body = function.getBody().builder();
        if (method.getName().equals("jspiEnabled")) {
            body.i32Const(WasmGCJspi.enabled() ? 1 : 0);
            return;
        }
        if (!WasmGCJspi.enabled()) {
            // The coroutine build never calls these (Fiber checks jspiEnabled first), but they stay reachable. Emit
            // no host import and no reference to Fiber.jspiRun, which that build does not link.
            function.add(new WasmLocal(fiberType, "fiber"));
            function.getBody().add(new WasmUnreachable());
            return;
        }
        switch (method.getName()) {
            case "jspiStart": {
                var fiber = new WasmLocal(fiberType, "fiber");
                function.add(fiber);
                if (startImport == null) {
                    var runType = context.functionTypes().of(null, fiberType);
                    startImport = createImport("start", context.functionTypes().of(null, fiberType,
                            runType.getReference()));
                }
                var runFn = context.functions().forStaticMethod(new MethodReference(Fiber.class, "jspiRun",
                        Fiber.class, void.class));
                runFn.setReferenced(true);
                body.getLocal(fiber).funcRef(runFn).call(startImport);
                break;
            }
            case "jspiPark": {
                var fiber = new WasmLocal(fiberType, "fiber");
                function.add(fiber);
                if (parkImport == null) {
                    parkImport = createImport("park", context.functionTypes().of(null, fiberType));
                }
                body.getLocal(fiber).call(parkImport);
                break;
            }
            case "jspiUnpark": {
                var fiber = new WasmLocal(fiberType, "fiber");
                function.add(fiber);
                if (unparkImport == null) {
                    unparkImport = createImport("unpark", context.functionTypes().of(null, fiberType));
                }
                body.getLocal(fiber).call(unparkImport);
                break;
            }
            default:
                throw new IllegalArgumentException(method.toString());
        }
    }

    private WasmFunction createImport(String name, org.teavm.backend.wasm.model.WasmFunctionType type) {
        var fn = new WasmFunction(type);
        fn.setName(context.names().topLevel("teavmJspi." + name));
        fn.setImportModule("teavmJspi");
        fn.setImportName(name);
        context.module().functions.add(fn);
        return fn;
    }
}
