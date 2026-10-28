/*
 *  Copyright 2018 konsoletyper.
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
package org.teavm.classlib.java.util.concurrent;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.TInterruptedException;
import org.teavm.classlib.java.lang.TThread;
import org.teavm.classlib.java.lang.TThreadInterruptHandler;
import org.teavm.classlib.java.util.TAbstractQueue;
import org.teavm.classlib.java.util.TCollection;
import org.teavm.classlib.java.util.TIterator;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.platform.Platform;
import org.teavm.platform.PlatformRunnable;
import org.teavm.runtime.EventQueue;

public class TArrayBlockingQueue<E> extends TAbstractQueue<E> implements TBlockingQueue<E> {
    private Object[] array;
    private int head;
    private int tail;
    private Queue<WaitHandler> waitHandlers;

    public TArrayBlockingQueue(int capacity) {
        this(capacity, false);
    }

    public TArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1");
        }
        array = new Object[capacity];
    }

    public TArrayBlockingQueue(int capacity, boolean fair, Collection<? extends E> c) {
        if (capacity < 1 || capacity < Objects.requireNonNull(c).size()) {
            throw new IllegalArgumentException("Capacity must be at least 1 or collection's size");
        }
        if (c.size() > capacity) {
            throw new IllegalArgumentException();
        }
        array = new Object[capacity];

        int index = 0;
        for (E e : c) {
            array[index++] = Objects.requireNonNull(e);
        }
        tail = index == capacity ? 0 : index;
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        if (isFull()) {
            throw new IllegalStateException("This blocking queue is full");
        }
        addImpl(e);
        return true;
    }

    @Override
    public boolean offer(E e) {
        Objects.requireNonNull(e);
        if (isFull()) {
            return false;
        }
        addImpl(e);
        return true;
    }

    @Override
    public void put(E e) throws InterruptedException {
        // A blocking call that does not block is a preemption point, as in the blocking acquires of
        // java.util.concurrent.locks. Green threads switch only when a thread blocks or yields, so a thread that loops
        // on put and take without blocking would stop every other thread. Thread.yield() switches only after the
        // thread has run for its time slice. offer(E) and poll() return at once, so they do not yield.
        Thread.yield();
        Objects.requireNonNull(e);
        while (isFull()) {
            waitForChange(0);
        }
        addImpl(e);
    }

    @Override
    public boolean offer(E e, long timeout, TTimeUnit unit) throws InterruptedException {
        Thread.yield(); // see put()
        Objects.requireNonNull(e);
        if (isFull()) {
            long timeLimit = timeLimit(unit.toMillis(timeout));
            while (isFull()) {
                if (!waitForChange(timeLimit)) {
                    return false;
                }
            }
        }
        addImpl(e);
        return true;
    }

    @Override
    public E poll() {
        if (isEmpty()) {
            return null;
        }
        return removeImpl();
    }

    @Override
    public E take() throws InterruptedException {
        Thread.yield(); // see put()
        while (isEmpty()) {
            waitForChange(0);
        }
        return removeImpl();
    }

    @Override
    public E poll(long timeout, TTimeUnit unit) throws InterruptedException {
        Thread.yield(); // see put()
        if (isEmpty()) {
            long timeLimit = timeLimit(unit.toMillis(timeout));
            while (isEmpty()) {
                if (!waitForChange(timeLimit)) {
                    return null;
                }
            }
        }
        return removeImpl();
    }

    @Override
    @SuppressWarnings("unchecked")
    public E peek() {
        if (isEmpty()) {
            return null;
        }
        return (E) array[head];
    }

    @Override
    public int size() {
        if (head < tail) {
            return tail - head;
        } else if (array[head] != null) {
            return tail + array.length - head;
        } else {
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        return head == tail && array[head] == null;
    }

    @Override
    public int remainingCapacity() {
        return array.length - size();
    }

    @Override
    public boolean remove(Object o) {
        if (isEmpty()) {
            return false;
        } else if (head < tail) {
            for (int i = head; i < tail; ++i) {
                if (array[i].equals(o)) {
                    removeAt(i);
                    return true;
                }
            }
            return false;
        } else {
            for (int i = head; i < array.length; ++i) {
                if (array[i].equals(o)) {
                    removeAt(i);
                    return true;
                }
            }

            for (int i = 0; i < tail; ++i) {
                if (array[i].equals(o)) {
                    removeAt(i);
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public boolean contains(Object o) {
        if (isEmpty()) {
            return false;
        } else if (head < tail) {
            for (int i = head; i < tail; ++i) {
                if (array[i].equals(o)) {
                    return true;
                }
            }
            return false;
        } else {
            for (int i = head; i < array.length; ++i) {
                if (array[i].equals(o)) {
                    return true;
                }
            }
            for (int i = 0; i < tail; ++i) {
                if (array[i].equals(o)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Object[] toArray() {
        if (isEmpty()) {
            return new Object[0];
        } else if (head < tail) {
            return Arrays.copyOfRange(array, head, tail);
        } else {
            Object[] result = new Object[size()];
            System.arraycopy(array, head, result, 0, array.length - head);
            System.arraycopy(array, 0, result, array.length - head, tail);
            return result;
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "SuspiciousSystemArraycopy" })
    public <T> T[] toArray(T[] a) {
        if (isEmpty()) {
            if (a.length > 0) {
                a[0] = null;
            }
            return a;
        }

        int size = size();
        if (size > a.length) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        } else if (size < a.length) {
            a[size] = null;
        }

        if (head < tail) {
            System.arraycopy(array, head, a, 0, size);
        } else {
            System.arraycopy(array, head, a, 0, array.length - head);
            System.arraycopy(array, 0, a, array.length - head, tail);
        }

        return a;
    }

    @Override
    public void clear() {
        if (isEmpty()) {
            return;
        }
        if (head < tail) {
            for (int i = head; i < tail; ++i) {
                array[i] = null;
            }
        } else {
            for (int i = head; i < array.length; ++i) {
                array[i] = null;
            }
            for (int i = 0; i < tail; ++i) {
                array[i] = null;
            }
        }

        head = 0;
        tail = 0;
        notifyChange();
    }

    @Override
    public int drainTo(TCollection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(TCollection<? super E> c, int maxElements) {
        if (c == this) {
            throw new IllegalArgumentException("The specified collection is this queue");
        }
        Objects.requireNonNull(c);
        if (maxElements <= 0) {
            return 0;
        }
        maxElements = Math.min(maxElements, size());
        for (int i = 0; i < maxElements; ++i) {
            c.add(removeImpl());
        }
        return maxElements;
    }

    @Override
    public TIterator<E> iterator() {
        return new TIterator<E>() {
            int index = head;
            int removeIndex = -1;
            int remaining = size();

            @Override
            public boolean hasNext() {
                // remaining ends the iteration. The null check stops it early when another operation took elements.
                return remaining > 0 && array[index] != null;
            }

            @Override
            public E next() {
                if (remaining == 0) {
                    throw new NoSuchElementException();
                }
                --remaining;
                removeIndex = index;
                @SuppressWarnings("unchecked")
                E result = (E) array[index++];
                if (result == null) {
                    throw new NoSuchElementException();
                }
                if (index == array.length) {
                    index = 0;
                }
                return result;
            }

            @Override
            public void remove() {
                if (removeIndex < 0 || array[removeIndex] == null) {
                    throw new IllegalStateException();
                }
                removeAt(removeIndex);
                index = removeIndex;
                removeIndex = -1;
            }
        };
    }

    private void removeAt(int index) {
        if (index < tail) {
            shiftElements(index, tail);
            --tail;
        } else {
            shiftElements(index, array.length);
            array[array.length - 1] = array[0];
            shiftElements(0, tail);
            if (--tail < 0) {
                tail = array.length - 1;
            }
        }
        array[tail] = null;
        notifyChange();
    }

    private void shiftElements(int from, int to) {
        int remaining = to - from - 1;
        if (remaining > 0) {
            System.arraycopy(array, from + 1, array, from, remaining);
        }
    }

    private void addImpl(E e) {
        array[tail++] = e;
        if (tail == array.length) {
            tail = 0;
        }
        notifyChange();
    }

    private E removeImpl() {
        @SuppressWarnings("unchecked")
        E result = (E) array[head];
        array[head++] = null;
        if (head == array.length) {
            head = 0;
        }
        notifyChange();
        return result;
    }

    private void notifyChange() {
        if (waitHandlers == null) {
            return;
        }
        while (!waitHandlers.isEmpty()) {
            WaitHandler handler = waitHandlers.remove();
            if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                EventQueue.offer(handler::changed);
            } else {
                Platform.postpone(handler::changed);
            }
        }
        waitHandlers = null;
    }

    // As in the JDK, a thread interrupted before it waits throws at once, and the throw clears its interrupt status.
    private boolean waitForChange(long timeLimit) throws InterruptedException {
        if (TThread.interrupted()) {
            throw new InterruptedException();
        }
        return waitForChangeImpl(timeLimit);
    }

    // The time limit of a timed wait, on EventQueue's clock. At least 1, because 0 means "no limit" in
    // waitForChange.
    private static long timeLimit(long millis) {
        return Math.max(1, EventQueue.deadlineAfter(millis));
    }

    @Async
    private native Boolean waitForChangeImpl(long timeLimit) throws InterruptedException;

    private void waitForChangeImpl(long timeLimit, AsyncCallback<Boolean> callback) {
        if (waitHandlers == null) {
            waitHandlers = new ArrayDeque<>();
        }

        WaitHandler handler = new WaitHandler(callback);
        handler.queue = this;
        waitHandlers.add(handler);
        if (timeLimit > 0) {
            handler.timer = PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()
                    ? EventQueue.scheduleAt(handler, timeLimit)
                    : Platform.scheduleAt(handler, timeLimit);
        }

        TThread.currentThread().interruptHandler = handler;
    }

    // notifyChange() takes every handler out of the queue. A wait that timed out or was interrupted removes its own
    // handler, so the queue does not keep the handler, and with it the waiting thread.
    private void removeWaitHandler(WaitHandler handler) {
        if (waitHandlers != null) {
            waitHandlers.remove(handler);
            if (waitHandlers.isEmpty()) {
                waitHandlers = null;
            }
        }
    }

    static class WaitHandler implements PlatformRunnable, TThreadInterruptHandler, EventQueue.Event {
        TArrayBlockingQueue<?> queue;
        AsyncCallback<Boolean> callback;
        boolean complete;
        EventQueue.Timer timer;

        // The waiting thread. complete() can run on another thread (interrupt) or at the top of the JS stack
        // (timeout), where TThread.currentThread() is not the waiter.
        final TThread thread = TThread.currentThread();

        WaitHandler(AsyncCallback<Boolean> callback) {
            this.callback = callback;
        }

        @Override
        public void run() {
            if (complete()) {
                return;
            }
            queue.removeWaitHandler(this);
            callback.complete(false);
        }

        @Override
        public void interrupted() {
            if (complete()) {
                return;
            }
            queue.removeWaitHandler(this);
            thread.clearInterruptStatus();
            // interrupted() runs on the interrupting thread, inside Thread.interrupt(). Resuming the waiter there
            // failed on JS ("Another thread is running" in TeaVMThread.resume). Post the error instead, as TObject's
            // wait listener and TThread's sleep handler do.
            var callback = this.callback;
            if (PlatformDetector.isLowLevel() || PlatformDetector.isWebAssemblyGC()) {
                EventQueue.offer(() -> callback.error(new TInterruptedException()));
            } else {
                Platform.postpone(() -> callback.error(new TInterruptedException()));
            }
        }

        private boolean complete() {
            if (complete) {
                return true;
            }
            complete = true;
            if (timer != null) {
                timer.kill();
                timer = null;
            }
            if (thread.interruptHandler == this) {
                thread.interruptHandler = null;
            }
            return false;
        }

        void changed() {
            if (complete()) {
                return;
            }
            callback.complete(true);
        }
    }

    private boolean isFull() {
        return head == tail && array[head] != null;
    }
}
