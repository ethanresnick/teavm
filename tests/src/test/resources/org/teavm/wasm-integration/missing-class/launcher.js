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

// Loads the Wasm GC module that the argument names (a path under /tests/), which compiles and validates it, then runs
// its main method. A module that fails validation rejects here.
this.main = function(args, callback) {
    (async () => {
        let { load } = await import("/resources/org/teavm/backend/wasm/wasm-gc-module-runtime.js");
        let teavm = await load("/tests/" + args[0], {
            stackDeobfuscator: {
                enabled: false
            }
        });
        teavm.exports.main([]);
    })().then(() => callback(), e => callback(e instanceof Error ? e : new Error(String(e))));
};
