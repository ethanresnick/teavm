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
import org.teavm.dependency.MethodDependency;
import org.teavm.interop.Async;
import org.teavm.model.MethodReference;
import org.teavm.runtime.ClassInitializationLock;

/**
 * Links {@link ClassInitializationLock} only when the program can have an async class initializer.
 * AsyncMethodFinder marks a method async only when the program reaches a method annotated with {@link Async},
 * so a program without one has no async initializer. Linking the lock unconditionally would make every program
 * async, since the lock itself waits.
 */
public class ClassInitializationLockDependencyListener extends AbstractDependencyListener {
    public static final MethodReference ENTER = new MethodReference(ClassInitializationLock.class,
            "enter", String.class, int.class);
    public static final MethodReference EXIT = new MethodReference(ClassInitializationLock.class,
            "exit", String.class, void.class);
    private boolean linked;

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        if (linked) {
            return;
        }
        var reader = method.getMethod();
        if (reader != null && reader.getAnnotations().get(Async.class.getName()) != null) {
            linked = true;
            agent.linkMethod(ENTER).propagate(1, String.class).use();
            agent.linkMethod(EXIT).propagate(1, String.class).use();
        }
    }
}
