/*
 * Copyright 2026 Alexey Andreev.
 * Licensed under the Apache License, Version 2.0.
 */
// Run with: node core/src/test/js/jspi-runtime.mjs <compiled Wasm GC runtime module>
import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";

const { defaults } = await import(pathToFileURL(resolve(process.argv[2])));
const original = globalThis.WebAssembly;
const originalQueueMicrotask = globalThis.queueMicrotask;
let wrappers = 0;
let suspending = 0;
const api = Object.create(original);
api.Suspending = function(callback) {
    ++suspending;
    return callback;
};
api.promising = run => {
    ++wrappers;
    return fiber => {
        try {
            return Promise.resolve(run(fiber));
        } catch (error) {
            return Promise.reject(error);
        }
    };
};

function imports() {
    const result = {};
    defaults(result, {}, false);
    return result;
}

try {
    globalThis.WebAssembly = api;
    const first = imports();
    assert.equal(suspending, 0);
    assert.equal(wrappers, 0);
    const a = first.teavmJspi;
    assert.equal(a, first.teavmJspi);
    assert.equal(suspending, 1);
    const b = imports().teavmJspi;
    assert.equal(suspending, 2);
    const fiber = {};
    let resumed = false;
    const waiting = a.park(fiber).then(() => { resumed = true; });
    assert.equal(resumed, false);
    assert.throws(() => b.unpark(fiber));
    a.unpark(fiber);
    assert.equal(resumed, false);
    assert.throws(() => a.unpark(fiber));
    await waiting;
    assert.equal(resumed, true);
    const run = () => {};
    a.start(fiber, run);
    a.start(fiber, run);
    assert.equal(wrappers, 1);
    b.start(fiber, run);
    assert.equal(wrappers, 2);

    const failure = new Error();
    let report;
    globalThis.queueMicrotask = callback => { report = callback; };
    a.start(fiber, () => { throw failure; });
    await Promise.resolve();
    assert.throws(report, error => error === failure);
    globalThis.queueMicrotask = originalQueueMicrotask;

    const custom = imports();
    custom.teavmJspi = { custom: true };
    assert.deepEqual(custom.teavmJspi, { custom: true });
    assert.equal(suspending, 2);

    for (const value of [null, undefined]) {
        const overridden = imports();
        overridden.teavmJspi = value;
        assert.equal(overridden.teavmJspi, value);
        assert.equal(overridden.teavmJspi, value);
        const initialized = imports();
        initialized.teavmJspi;
        initialized.teavmJspi = value;
        assert.equal(initialized.teavmJspi, value);
    }

    for (const missing of ["Suspending", "promising", "both"]) {
        api.Suspending = missing === "Suspending" || missing === "both" ? undefined : function() {};
        api.promising = missing === "promising" || missing === "both" ? undefined : () => {};
        const unsupported = imports();
        assert.throws(() => unsupported.teavmJspi.start(fiber, run));
        assert.throws(() => unsupported.teavmJspi.park(fiber));
        assert.throws(() => unsupported.teavmJspi.unpark(fiber));
    }
    console.log("JSPI host lifecycle controls passed");
} finally {
    globalThis.WebAssembly = original;
    globalThis.queueMicrotask = originalQueueMicrotask;
}
