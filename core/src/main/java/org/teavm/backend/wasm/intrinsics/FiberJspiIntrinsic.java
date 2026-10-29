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

import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.model.MethodReference;
import org.teavm.runtime.Fiber;

/** Implements the fiber operations supplied by the JavaScript Promise Integration runtime. */
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
