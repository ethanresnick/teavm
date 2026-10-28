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
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.model.MethodReference;
import org.teavm.runtime.EventQueue;

public class EventQueueIntrinsic implements WasmGCBodyIntrinsic {
    private WasmGCCodeGenContext context;

    public EventQueueIntrinsic(WasmGCCodeGenContext context) {
        this.context = context;
    }

    @Override
    public void apply(MethodReference method, WasmFunction function) {
        var eventType = context.classInfoProvider().getClassInfo(EventQueue.Event.class.getName()).getType();
        var eventCallbackType = context.functionTypes().of(null, eventType);

        var runtimeFn = new WasmFunction(context.functionTypes().of(WasmType.INT32, eventType,
                eventCallbackType.getReference(), WasmType.FLOAT64));
        runtimeFn.setName(context.names().topLevel("teavmAsync.offerAfter"));
        runtimeFn.setImportModule("teavmAsync");
        runtimeFn.setImportName("offerAfter");
        context.module().functions.add(runtimeFn);

        var callerFn = context.functions().forStaticMethod(new MethodReference(EventQueue.class, "run",
                EventQueue.Event.class, void.class));
        callerFn.setReferenced(true);

        var eventVar = new WasmLocal(eventType, "event");
        var delayVar = new WasmLocal(WasmType.FLOAT64, "delay");
        function.add(eventVar);
        function.add(delayVar);

        var body = function.getBody().builder();
        body
                .getLocal(eventVar)
                .funcRef(callerFn)
                .getLocal(delayVar)
                .call(runtimeFn);
    }
}
