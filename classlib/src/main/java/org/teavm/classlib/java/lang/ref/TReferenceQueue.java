/*
 *  Copyright 2015 Alexey Andreev.
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
package org.teavm.classlib.java.lang.ref;

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.TThread;
import org.teavm.classlib.java.lang.TThreadInterruptHandler;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;

public class TReferenceQueue<T> {
    private RemoveCallback firstCallback;
    private RemoveCallback lastCallback;

    public TReference<T> poll() {
        return null;
    }

    public TReference<T> remove() throws InterruptedException {
        return remove(0);
    }

    @Async
    public native TReference<T> remove(long timeout) throws InterruptedException;

    public void remove(long timeout, AsyncCallback<TReference<T>> callback) {
        var ref = poll();
        if (ref != null) {
            callback.complete(ref);
        } else {
            // As in the JDK, a thread interrupted before it waits throws, and the throw clears its interrupt status.
            if (TThread.interrupted()) {
                postInterruptedException(callback);
                return;
            }
            var callbackWrapper = new RemoveCallback(callback);
            if (timeout != 0) {
                callbackWrapper.id = PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()
                        ? EventQueue.offerAfter(callbackWrapper, timeout)
                        // A host timer takes an int delay, so a timeout over Integer.MAX_VALUE ms (about 24.8 days)
                        // ends after that delay, as in Thread.sleep.
                        : Platform.schedule(callbackWrapper, (int) Math.min(timeout, Integer.MAX_VALUE));
            }
            TThread.currentThread().interruptHandler = callbackWrapper;
            registerCallback(callbackWrapper);
        }
    }

    // Post the error, as TArrayBlockingQueue.WaitHandler.interrupted() does. A direct call resumed the waiter inside
    // Thread.interrupt(), which fails on JS.
    private static void postInterruptedException(AsyncCallback<?> callback) {
        if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
            EventQueue.offer(() -> callback.error(new InterruptedException()));
        } else {
            Platform.postpone(() -> callback.error(new InterruptedException()));
        }
    }

    private void registerCallback(RemoveCallback callback) {
        callback.prev = lastCallback;
        if (lastCallback != null) {
            lastCallback.next = callback;
        } else {
            firstCallback = callback;
        }
        lastCallback = callback;
    }

    protected boolean reportNext(TReference<T> ref) {
        if (firstCallback == null) {
            return false;
        }
        var callback = firstCallback;
        callback.complete(ref);
        return true;
    }

    private class RemoveCallback implements EventQueue.Event, PlatformRunnable, AsyncCallback<TReference<T>>,
            TThreadInterruptHandler {
        RemoveCallback next;
        RemoveCallback prev;
        // -1 while no timer is scheduled. On C, event ids start at 0.
        int id = -1;
        AsyncCallback<TReference<T>> callback;

        // The waiting thread (see TArrayBlockingQueue.WaitHandler).
        final TThread thread = TThread.currentThread();

        RemoveCallback(AsyncCallback<TReference<T>> callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            complete(null);
        }

        @Override
        public void complete(TReference<T> result) {
            var callback = this.callback;
            if (callback != null) {
                remove();
                callback.complete(result);
            }
        }

        @Override
        public void error(Throwable e) {
            var callback = this.callback;
            if (callback != null) {
                remove();
                callback.error(e);
            }
        }

        @Override
        public void interrupted() {
            var callback = this.callback;
            if (callback != null) {
                remove();
                thread.clearInterruptStatus();
                postInterruptedException(callback);
            }
        }

        private void remove() {
            // A wait that got a reference or was interrupted does not keep its timer, which would keep the
            // callback, and with it the waiting thread, until the timeout.
            if (id >= 0) {
                if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                    EventQueue.kill(id);
                } else {
                    Platform.killSchedule(id);
                }
                id = -1;
            }
            if (thread.interruptHandler == this) {
                thread.interruptHandler = null;
            }
            callback = null;
            if (prev != null) {
                prev.next = next;
            } else {
                firstCallback = next;
            }
            if (next != null) {
                next.prev = prev;
            } else {
                lastCallback = prev;
            }
            next = null;
            prev = null;
        }
    }
}
