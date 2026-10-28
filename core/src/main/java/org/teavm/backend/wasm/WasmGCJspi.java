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

/**
 * kafka-wasm: selects the JSPI mode of the Wasm GC backend. In this mode each green thread (Fiber) runs on its own
 * stack, which the host creates with WebAssembly.promising, and a suspension parks that stack through a
 * WebAssembly.Suspending import. The generator then skips the coroutine transformation.
 *
 * <p>The mode is opt-in; kafka-wasm enables it in its Maven configuration. The TeaVM property
 * {@value #PROPERTY} (a build's {@code <properties>}) selects it for
 * one build; without it, the JVM system property of the same name selects it for every build of the JVM. The value
 * "false" selects the coroutine transformation.
 *
 * <p>The setting is static: WasmGCTarget.setController sets it for each build. TeaVM calls setController after
 * the dependency analysis, so code that runs during the analysis must not depend on the mode (it may still see the
 * previous build's mode). Builds that run at the same time in one JVM (Maven -T) must not choose different modes.
 */
public final class WasmGCJspi {
    public static final String PROPERTY = "teavm.wasm.jspi";
    private static volatile String buildValue;

    private WasmGCJspi() {
    }

    /** Called by WasmGCTarget when a build starts. */
    public static void configure(Properties properties) {
        buildValue = properties.getProperty(PROPERTY);
    }

    public static boolean enabled() {
        String value = buildValue;
        if (value == null) {
            value = System.getProperty(PROPERTY);
        }
        return value != null && value.trim().equalsIgnoreCase("true");
    }
}
