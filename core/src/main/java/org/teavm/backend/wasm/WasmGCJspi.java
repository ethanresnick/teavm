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
package org.teavm.backend.wasm;

import java.util.Properties;
import java.util.function.BooleanSupplier;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.optimization.ConstantConditionElimination;
import org.teavm.model.optimization.GlobalValueNumbering;
import org.teavm.model.optimization.UnreachableBasicBlockEliminator;
import org.teavm.runtime.Fiber;

/**
 * Selects JavaScript Promise Integration with the per-build property {@code teavm.wasm.jspi=true}.
 * The default uses coroutine transformation. JSPI builds require the host's WebAssembly.Suspending and
 * WebAssembly.promising APIs; the standard Wasm GC JavaScript runtime supplies the fiber imports.
 */
public final class WasmGCJspi implements ClassHolderTransformer {
    public static final String PROPERTY = "teavm.wasm.jspi";

    private final BooleanSupplier enabled;

    public WasmGCJspi(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (!cls.getName().equals(Fiber.class.getName())
                && !cls.getName().startsWith(Fiber.class.getName() + "$")) {
            return;
        }
        context.getIncrementalCache().setNoCache(cls.getName());
        for (var method : cls.getMethods()) {
            var program = method.getProgram();
            if (program == null) {
                continue;
            }
            boolean changed = false;
            for (var block : program.getBasicBlocks()) {
                for (var instruction : block) {
                    if (instruction instanceof InvokeInstruction) {
                        var invoke = (InvokeInstruction) instruction;
                        if (invoke.getMethod().getClassName().equals(Fiber.class.getName())
                                && invoke.getMethod().getName().equals("isJspi")) {
                            var constant = new IntegerConstantInstruction();
                            constant.setConstant(enabled.getAsBoolean() ? 1 : 0);
                            constant.setReceiver(invoke.getReceiver());
                            constant.setLocation(invoke.getLocation());
                            invoke.replace(constant);
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                do {
                    changed = new GlobalValueNumbering(true).optimize(program)
                            | new ConstantConditionElimination().optimize(method.getDescriptor(), program);
                    new UnreachableBasicBlockEliminator().optimize(program);
                } while (changed);
            }
        }
    }

    public static boolean enabled(Properties properties) {
        return Boolean.parseBoolean(properties.getProperty(PROPERTY, "false"));
    }
}
