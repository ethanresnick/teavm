/*
 *  Copyright 2018 Alexey Andreev.
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
package org.teavm.runtime;

import java.util.Arrays;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.AsyncCallback;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@StaticInit
public final class Fiber {
    public static final int STATE_RUNNING = 0;
    public static final int STATE_SUSPENDING = 1;
    public static final int STATE_RESUMING = 2;
    public static int userThreadCount = 1;

    private int[] intValues;
    private int intTop;
    private long[] longValues;
    private int longTop;
    private float[] floatValues;
    private int floatTop;
    private double[] doubleValues;
    private int doubleTop;
    private Object[] objectValues;
    private int objectTop;
    private int state;
    private FiberRunner runner;
    private Object result;
    private Throwable exception;
    private boolean isPendingResume;
    private boolean daemon;

    private static Fiber current;
    private static PendingCall lastPendingCall;

    private Fiber(FiberRunner runner, boolean daemon) {
        this.runner = runner;
        this.daemon = daemon;
    }

    public void push(int value) {
        if (intValues == null) {
            intValues = new int[4];
        } else if (intTop + 1 == intValues.length) {
            intValues = Arrays.copyOf(intValues, intValues.length * 3 / 2);
        }
        intValues[intTop++] = value;
    }

    public void push(long value) {
        if (longValues == null) {
            longValues = new long[4];
        } else if (longTop + 1 == longValues.length) {
            longValues = Arrays.copyOf(longValues, longValues.length * 3 / 2);
        }
        longValues[longTop++] = value;
    }

    public void push(float value) {
        if (floatValues == null) {
            floatValues = new float[4];
        } else if (floatTop + 1 == floatValues.length) {
            floatValues = Arrays.copyOf(floatValues, floatValues.length * 3 / 2);
        }
        floatValues[floatTop++] = value;
    }

    public void push(double value) {
        if (doubleValues == null) {
            doubleValues = new double[4];
        } else if (doubleTop + 1 == doubleValues.length) {
            doubleValues = Arrays.copyOf(doubleValues, doubleValues.length * 3 / 2);
        }
        doubleValues[doubleTop++] = value;
    }

    public void push(Object value) {
        if (objectValues == null) {
            objectValues = new Object[4];
        } else if (objectTop + 1 == objectValues.length) {
            // Arrays.copyOf(Object[]) calls Array.newInstance, which can be suspendable.
            // This method runs while the fiber suspends, so it must not call suspendable code.
            var newObjectValues = new Object[objectValues.length * 3 / 2];
            for (var i = 0; i < objectTop; ++i) {
                newObjectValues[i] = objectValues[i];
            }
            objectValues = newObjectValues;
        }
        objectValues[objectTop++] = value;
    }

    public static void reversePush(int value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(long value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(float value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(double value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(Object value, Fiber fiber) {
        fiber.push(value);
    }

    public static void reversePush(PlatformObject value, Fiber fiber) {
        fiber.push(new PlatformObjectWrapper(value));
    }

    public static void reversePush(PlatformFunction value, Fiber fiber) {
        fiber.push(new PlatformFunctionWrapper(value));
    }

    public static void reversePush(PlatformException value, Fiber fiber) {
        fiber.push(new PlatformExceptionWrapper(value));
    }

    @Unmanaged
    public int popInt() {
        return intValues[--intTop];
    }

    @Unmanaged
    public long popLong() {
        return longValues[--longTop];
    }

    @Unmanaged
    public float popFloat() {
        return floatValues[--floatTop];
    }

    @Unmanaged
    public double popDouble() {
        return doubleValues[--doubleTop];
    }

    @Unmanaged
    public Object popObject() {
        Object result = objectValues[--objectTop];
        objectValues[objectTop] = null;
        return result;
    }

    public PlatformObject popPlatformObject() {
        var wrapper = (PlatformObjectWrapper) popObject();
        return wrapper.object;
    }

    public PlatformFunction popPlatformFunction() {
        var wrapper = (PlatformFunctionWrapper) popObject();
        return wrapper.object;
    }

    public PlatformException popPlatformException() {
        var wrapper = (PlatformExceptionWrapper) popObject();
        return wrapper.object;
    }

    @Unmanaged
    public static Fiber current() {
        return current;
    }

    @Unmanaged
    public boolean isSuspending() {
        return state == STATE_SUSPENDING;
    }

    @Unmanaged
    public static boolean isPresentAndSuspending(Fiber fiber) {
        return fiber != null && fiber.isSuspending();
    }

    @Unmanaged
    public boolean isResuming() {
        return state == STATE_RESUMING;
    }

    @Unmanaged
    public static boolean isPresentAndResuming(Fiber fiber) {
        return fiber != null && fiber.isResuming();
    }

    @Unmanaged
    public static boolean getBoolean(Object v) {
        return v != null ? (Boolean) v : false;
    }

    @Unmanaged
    public static byte getByte(Object v) {
        return v != null ? (Byte) v : 0;
    }

    @Unmanaged
    public static short getShort(Object v) {
        return v != null ? (Short) v : 0;
    }

    @Unmanaged
    public static int getInt(Object v) {
        return v != null ? (Integer) v : 0;
    }

    @Unmanaged
    public static char getChar(Object v) {
        return v != null ? (Character) v : 0;
    }

    @Unmanaged
    public static long getLong(Object v) {
        return v != null ? (Long) v : 0;
    }

    @Unmanaged
    public static float getFloat(Object v) {
        return v != null ? (Float) v : 0;
    }

    @Unmanaged
    public static double getDouble(Object v) {
        return v != null ? (Double) v : 0;
    }

    public static Object suspend(AsyncCall call) throws Throwable {
        Fiber fiber = current();
        if (fiber == null) {
            throw new IllegalStateException("Suspension point reached from non-threading context "
                    + "(perhaps, from native JS method). See https://teavm.org/docs/runtime/coroutines.html "
                    + "('Interaction with JavaScript' section)");
        }
        // Keep the platform guard at each call site so dependency analysis can discard JSPI calls on C.
        if (PlatformDetector.isWebAssemblyGC() && jspiEnabled()) {
            return suspendOnJspiStack(fiber, Thread.currentThread(), call);
        }
        if (fiber.isResuming()) {
            fiber.state = STATE_RUNNING;
            // Take the outcome of this call and clear it, so that it does not leak into a later call. Without
            // this, the exception of one failed call was thrown again by every later call that completed normally.
            Throwable exception = fiber.exception;
            Object result = fiber.result;
            fiber.exception = null;
            fiber.result = null;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
        // Thread.currentThread() can be suspendable (it can initialize Thread). Suspendable code reads the fiber's
        // state, so it must not run while the fiber resumes.
        Thread javaThread = Thread.currentThread();
        PendingCall pendingCall = new PendingCall(call, lastPendingCall);
        if (lastPendingCall != null) {
            lastPendingCall.next = pendingCall;
        }
        lastPendingCall = pendingCall;

        pendingCall.callback = new AsyncCallbackImpl(pendingCall, javaThread, fiber);
        // The call can run suspendable code that does not suspend. That code must see a running fiber,
        // otherwise it unwinds as if it suspended.
        call.run(pendingCall.callback);
        fiber.state = STATE_SUSPENDING;
        return null;
    }

    static class AsyncCallbackImpl implements AsyncCallback<Object> {
        PendingCall pendingCall;
        Thread javaThread;
        Fiber fiber;

        AsyncCallbackImpl(PendingCall pendingCall, Thread javaThread, Fiber fiber) {
            this.pendingCall = pendingCall;
            this.javaThread = javaThread;
            this.fiber = fiber;
        }

        @Override
        public void complete(Object result) {
            if (Fiber.current == this.fiber) {
                fiber.result = result;
                fiber.isPendingResume = true;
                removePendingCall();
            } else {
                // jspi: begin
                if (PlatformDetector.isWebAssemblyGC() && jspiEnabled()) {
                    // The waiting fiber resumes in a microtask, after this code returns. It sets its own thread.
                    Fiber fiber = this.fiber;
                    this.fiber = null;
                    javaThread = null;
                    fiber.result = result;
                    removePendingCall();
                    jspiUnpark(fiber);
                    return;
                }
                // jspi: end
                // kafka-wasm patch: the resumed fiber runs nested inside this call, then this code goes on. Restore
                // the completer's current thread afterwards. Upstream left it set to the resumed thread, so code that
                // completes a callback on its own thread (Thread.interrupt of a thread blocked in
                // ArrayBlockingQueue.take) then ran as the other thread: its currentThread and ThreadLocals.
                Thread former = Thread.currentThread();
                setCurrentThread(javaThread);
                javaThread = null;
                Fiber fiber = this.fiber;
                this.fiber = null;
                fiber.result = result;
                removePendingCall();
                fiber.resume();
                if (former != null && Thread.currentThread() != former) {
                    setCurrentThread(former);
                }
            }
        }

        @Override
        public void error(Throwable e) {
            if (Fiber.current == this.fiber) {
                fiber.exception = e;
                fiber.isPendingResume = true;
                removePendingCall();
            } else {
                // jspi: begin
                if (PlatformDetector.isWebAssemblyGC() && jspiEnabled()) {
                    Fiber fiber = this.fiber;
                    this.fiber = null;
                    javaThread = null;
                    fiber.exception = e;
                    removePendingCall();
                    jspiUnpark(fiber);
                    return;
                }
                // jspi: end
                // kafka-wasm patch: restore the completer's current thread (see complete).
                Thread former = Thread.currentThread();
                setCurrentThread(javaThread);
                javaThread = null;
                Fiber fiber = this.fiber;
                this.fiber = null;
                fiber.exception = e;
                removePendingCall();
                fiber.resume();
                if (former != null && Thread.currentThread() != former) {
                    setCurrentThread(former);
                }
            }
        }

        private void removePendingCall() {
            if (pendingCall.previous != null) {
                pendingCall.previous.next = pendingCall.next;
            }
            if (pendingCall.next != null) {
                pendingCall.next.previous = pendingCall.previous;
            }
            if (pendingCall == lastPendingCall) {
                lastPendingCall = pendingCall.previous;
            }
            pendingCall = null;
        }
    }

    static native void setCurrentThread(Thread thread);

    public static void start(FiberRunner runner, boolean daemon) {
        // jspi: begin
        if (PlatformDetector.isWebAssemblyGC() && jspiEnabled()) {
            // The host runs jspiRun on a new stack (WebAssembly.promising). The call returns when the new fiber parks
            // or ends; the caller's fiber is current again after it.
            Fiber fiber = new Fiber(runner, daemon);
            Fiber former = current;
            current = null;
            jspiStart(fiber);
            current = former;
            return;
        }
        // jspi: end
        new Fiber(runner, daemon).start();
    }

    // jspi: begin
    /**
     * kafka-wasm, JSPI mode (WasmGCJspi): each fiber runs on its own stack, so a suspension does not unwind the
     * Java stack. The @Async body runs first. When it completed the callback already (AsyncCallbackImpl saw this
     * fiber as current), the result is ready and nothing waits. Otherwise the fiber parks: jspiPark is a
     * WebAssembly.Suspending import, and the callback's jspiUnpark resolves its promise. The stack then resumes in a
     * microtask, and this method sets the current thread again, because the code that completed the callback does
     * not do that in this mode.
     */
    private static Object suspendOnJspiStack(Fiber fiber, Thread javaThread, AsyncCall call) throws Throwable {
        PendingCall pendingCall = new PendingCall(call, lastPendingCall);
        if (lastPendingCall != null) {
            lastPendingCall.next = pendingCall;
        }
        lastPendingCall = pendingCall;
        pendingCall.callback = new AsyncCallbackImpl(pendingCall, javaThread, fiber);
        fiber.state = STATE_RUNNING;
        call.run(pendingCall.callback);
        if (fiber.isPendingResume) {
            fiber.isPendingResume = false;
        } else {
            fiber.state = STATE_SUSPENDING;
            current = null;
            jspiPark(fiber);
            current = fiber;
            fiber.state = STATE_RUNNING;
            setCurrentThread(javaThread);
        }
        Throwable exception = fiber.exception;
        Object result = fiber.result;
        fiber.exception = null;
        fiber.result = null;
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    /** kafka-wasm, JSPI mode: the entry of a fiber's own stack. The host calls it through WebAssembly.promising. */
    static void jspiRun(Fiber fiber) {
        current = fiber;
        try {
            fiber.runner.run();
        } catch (Throwable e) {
            // The host rethrows the exception from a microtask, and the process ends. Node then prints only
            // "Exception [WebAssembly.Exception] {}", so print the Java exception first.
            reportEscaped(e);
            throw e;
        } finally {
            current = null;
        }
        if (!fiber.daemon && --userThreadCount == 0) {
            EventQueue.stop();
        }
    }

    private static void reportEscaped(Throwable e) {
        try {
            System.err.println("kafka-wasm: an exception left a thread's runner; the process ends:");
            e.printStackTrace();
        } catch (Throwable ignored) {
            // The report must not replace the exception.
        }
    }

    /** kafka-wasm: true when the module is built for JSPI (WasmGCJspi). A build-time constant. */
    @org.teavm.interop.Intrinsified
    private static native boolean jspiEnabled();

    @org.teavm.interop.Intrinsified
    private static native void jspiStart(Fiber fiber);

    @org.teavm.interop.Intrinsified
    private static native void jspiPark(Fiber fiber);

    @org.teavm.interop.Intrinsified
    private static native void jspiUnpark(Fiber fiber);
    // jspi: end

    static void startMain(String[] args) {
        start(() -> runMain(args), false);
    }

    public static native void runMain(String[] args);

    private void start() {
        Fiber former = current;
        current = this;
        while (true) {
            runner.run();
            if (!isPendingResume) {
                break;
            }
            isPendingResume = false;
            state = STATE_RESUMING;
        }
        current = former;
        if (!isSuspending() && !daemon && --userThreadCount == 0) {
            EventQueue.stop();
        }
    }

    void resume() {
        state = STATE_RESUMING;
        start();
    }

    public interface FiberRunner {
        void run();
    }

    public interface AsyncCall {
        void run(AsyncCallback<?> callback);
    }

    static class PendingCall {
        AsyncCall value;
        PendingCall next;
        PendingCall previous;
        AsyncCallbackImpl callback;

        PendingCall(AsyncCall value, PendingCall previous) {
            this.value = value;
            this.next = null;
            this.previous = previous;
        }
    }

    public static class PlatformObjectWrapper {
        public final PlatformObject object;

        PlatformObjectWrapper(PlatformObject object) {
            this.object = object;
        }
    }

    public static class PlatformFunctionWrapper {
        public final PlatformFunction object;

        PlatformFunctionWrapper(PlatformFunction object) {
            this.object = object;
        }
    }

    public static class PlatformExceptionWrapper {
        public final PlatformException object;

        PlatformExceptionWrapper(PlatformException object) {
            this.object = object;
        }
    }

    public static class PlatformObject {
    }

    public static class PlatformFunction {
    }

    public static class PlatformException {
    }
}
