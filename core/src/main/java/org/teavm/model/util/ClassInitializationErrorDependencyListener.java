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
package org.teavm.model.util;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependency;
import org.teavm.interop.Address;
import org.teavm.interop.StaticInit;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.ClassInitializationErrors;
import org.teavm.runtime.ExceptionHandling;

/** Keeps cause-recording support out of programs whose initializers cannot throw. */
public class ClassInitializationErrorDependencyListener extends AbstractDependencyListener {
    public enum Backend {
        JAVASCRIPT, WASM_GC, C
    }

    public static final MethodReference WRAP = new MethodReference(ClassInitializationErrors.class,
            "wrap", Throwable.class, Throwable.class);
    public static final MethodReference FAIL = new MethodReference(ClassInitializationErrors.class,
            "fail", Throwable.class, void.class);
    public static final MethodReference SAVE_C = new MethodReference(ClassInitializationErrors.class,
            "saveC", Address.class, Throwable.class, void.class);
    public static final MethodReference FAIL_C = new MethodReference(ClassInitializationErrors.class,
            "failC", Address.class, void.class);
    public static final MethodReference MARK_CAUSE = new MethodReference(ExceptionHandling.class,
            "markInitializerFailed", Throwable.class, void.class);
    private final Backend backend;
    private boolean linked;

    public ClassInitializationErrorDependencyListener(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        if (!method.getReference().getName().equals("<clinit>")) {
            return;
        }
        var cls = agent.getClassSource().get(method.getReference().getClassName());
        if (cls != null && cls.getAnnotations().get(StaticInit.class.getName()) != null) {
            return;
        }
        method.getThrown().addConsumer(type -> {
            if (linked) {
                return;
            }
            linked = true;
            switch (backend) {
                case JAVASCRIPT:
                    agent.linkMethod(new MethodReference(Throwable.class, "initCause", Throwable.class,
                            Throwable.class)).propagate(0, NoClassDefFoundError.class)
                            .propagate(1, ExceptionInInitializerError.class).use();
                    break;
                case WASM_GC:
                    agent.linkMethod(WRAP).propagate(1, Throwable.class).use();
                    agent.linkMethod(FAIL).propagate(1, ExceptionInInitializerError.class).use();
                    break;
                case C:
                    agent.linkMethod(MARK_CAUSE).propagate(1, Throwable.class).use();
                    agent.linkMethod(SAVE_C).propagate(2, Throwable.class).use();
                    agent.linkMethod(FAIL_C).use();
                    break;
            }
        });
    }

    public static boolean canFail(DependencyInfo dependencies, String className) {
        var cls = dependencies.getClassSource().get(className);
        if (cls != null && cls.getAnnotations().get(StaticInit.class.getName()) != null) {
            return false;
        }
        var method = dependencies.getMethod(new MethodReference(className, "<clinit>", ValueType.VOID));
        return method != null && method.getThrown().getTypes().length > 0;
    }
}
