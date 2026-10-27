/*
 *  Copyright 2026 konsoletyper.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

// Compares ArrayBlockingQueue with a model: an ArrayList that holds the elements from head to tail, and the capacity.
// The JVM run checks the model against the JDK's class.
@TeaVMTest
public class ArrayBlockingQueueModelTest {
    private static final int[] CAPACITIES = { 1, 2, 3, 4, 7 };
    private static final int OPERATIONS = 250;
    private static final int VALUES = 5;

    // Random operations from every head position, and from every size of the collection constructor's argument.
    // The values repeat, so remove(Object) and contains() meet duplicates, and the operations wrap head and tail
    // through every slot many times.
    @Test
    public void matchesModel() {
        var failures = new ArrayList<String>();
        for (var capacity : CAPACITIES) {
            for (var start = 0; start < capacity; ++start) {
                var initial = new ArrayList<Integer>();
                var queue = new ArrayBlockingQueue<Integer>(capacity);
                for (var i = 0; i < start; ++i) {
                    queue.add(-1);
                    queue.poll();
                }
                run("capacity=" + capacity + " start=" + start, queue, initial, capacity,
                        new Random(capacity * 101 + start), failures);
            }
            for (var size = 0; size <= capacity; ++size) {
                var initial = new ArrayList<Integer>();
                for (var i = 0; i < size; ++i) {
                    initial.add(i % VALUES);
                }
                // "Creates an ArrayBlockingQueue with the given (fixed) capacity, the specified access policy and
                // initially containing the elements of the given collection, added in traversal order of the
                // collection's iterator."
                var queue = new ArrayBlockingQueue<>(capacity, false, initial);
                run("capacity=" + capacity + " collection=" + size, queue, initial, capacity,
                        new Random(capacity * 103 + size), failures);
            }
        }
        assertEquals(List.of(), failures);
    }

    // Every subset of the elements, at every head position, so removals through the iterator meet each other: next to
    // each other, across the end of the array and at both ends of the queue.
    @Test
    public void removeIfEverySubsetAtEveryOffset() {
        var failures = new ArrayList<String>();
        for (var capacity = 1; capacity <= 5; ++capacity) {
            for (var start = 0; start < capacity; ++start) {
                for (var count = 0; count <= capacity; ++count) {
                    for (var mask = 0; mask < 1 << count; ++mask) {
                        var queue = new ArrayBlockingQueue<Integer>(capacity);
                        var model = new ArrayList<Integer>();
                        for (var i = 0; i < start; ++i) {
                            queue.add(-1);
                            queue.poll();
                        }
                        for (var i = 0; i < count; ++i) {
                            queue.add(i);
                            model.add(i);
                        }
                        var removeMask = mask;
                        var expectedChanged = mask != 0;
                        model.removeIf(e -> (removeMask & (1 << e)) != 0);
                        var description = "capacity=" + capacity + " start=" + start + " count=" + count
                                + " mask=" + mask;
                        try {
                            var changed = queue.removeIf(e -> (removeMask & (1 << e)) != 0);
                            check(description, "removeIf result", expectedChanged, changed);
                            checkState(description, queue, model, capacity);
                            // An offer checks where the tail is.
                            check(description, "offer result", model.size() < capacity, queue.offer(100));
                            if (model.size() < capacity) {
                                model.add(100);
                            }
                            checkState(description + " then offer", queue, model, capacity);
                        } catch (Mismatch e) {
                            failures.add(e.getMessage());
                        } catch (RuntimeException e) {
                            failures.add(description + ": " + describe(e));
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    // drainTo: "NullPointerException - if the specified collection is null", also when there is nothing to drain.
    @Test
    public void drainToNullThrows() {
        var queue = new ArrayBlockingQueue<Integer>(2);
        assertThrows(NullPointerException.class, () -> queue.drainTo(null));
        assertThrows(NullPointerException.class, () -> queue.drainTo(null, 1));
        queue.add(1);
        assertThrows(NullPointerException.class, () -> queue.drainTo(null, 0));
        assertEquals(List.of(1), new ArrayList<>(queue));
    }

    // Accepted gap. "The returned iterator is weakly consistent": such iterators "are guaranteed to traverse elements
    // as they existed upon construction exactly once" (java.util.concurrent package summary). TeaVM's iterator walks
    // the array slots, so a poll() or remove(Object) by the same thread between next() calls moves the elements
    // under it. Here it finds the polled head slot empty and stops at once, without 1 and 2.
    @Test
    @Disabled("TeaVM's ArrayBlockingQueue iterator is not weakly consistent across other changes to the queue")
    public void iteratorSeesRemainingElementsAfterPoll() {
        var queue = new ArrayBlockingQueue<Integer>(4);
        queue.addAll(List.of(0, 1, 2));
        var it = queue.iterator();
        queue.poll();
        var seen = new ArrayList<Integer>();
        while (it.hasNext()) {
            seen.add(it.next());
        }
        assertTrue(seen.containsAll(List.of(1, 2)), "seen " + seen);
    }

    private static void run(String description, ArrayBlockingQueue<Integer> queue, List<Integer> initial,
            int capacity, Random random, List<String> failures) {
        var model = new ArrayList<>(initial);
        var step = 0;
        var operation = "initial";
        try {
            checkState(description + " " + operation, queue, model, capacity);
            for (step = 0; step < OPERATIONS; ++step) {
                operation = apply(queue, model, capacity, random);
                checkState(description + " step " + step + " " + operation, queue, model, capacity);
            }
        } catch (Mismatch e) {
            // A mismatch in an operation's own result names only the operation.
            var message = e.getMessage();
            failures.add(message.startsWith(description) ? message : description + " step " + step + " " + message);
        } catch (RuntimeException e) {
            failures.add(description + " step " + step + " " + operation + ": " + describe(e));
        }
    }

    // Applies one random operation to the queue and to the model, checks its result, and returns its description.
    private static String apply(ArrayBlockingQueue<Integer> queue, List<Integer> model, int capacity, Random random) {
        var value = random.nextInt(VALUES);
        var full = model.size() == capacity;
        switch (random.nextInt(20)) {
            case 0:
            case 1:
            case 2: {
                // "Inserts the specified element at the tail of this queue if it is possible to do so immediately
                // without exceeding the queue's capacity, returning true upon success and false if this queue is
                // full."
                var op = "offer(" + value + ")";
                check(op, "result", !full, queue.offer(value));
                if (!full) {
                    model.add(value);
                }
                return op;
            }
            case 3: {
                // "...throwing an IllegalStateException if this queue is full."
                var op = "add(" + value + ")";
                var thrown = false;
                try {
                    queue.add(value);
                } catch (IllegalStateException e) {
                    thrown = true;
                }
                check(op, "IllegalStateException", full, thrown);
                if (!full) {
                    model.add(value);
                }
                return op;
            }
            case 4:
            case 5: {
                // poll(): "the head of this queue, or null if this queue is empty"
                check("poll()", "result", model.isEmpty() ? null : model.remove(0), queue.poll());
                return "poll()";
            }
            case 6: {
                // remove(): "throws an exception if this queue is empty"
                var thrown = false;
                Integer result = null;
                try {
                    result = queue.remove();
                } catch (NoSuchElementException e) {
                    thrown = true;
                }
                check("remove()", "NoSuchElementException", model.isEmpty(), thrown);
                if (!thrown) {
                    check("remove()", "result", model.remove(0), result);
                }
                return "remove()";
            }
            case 7: {
                // "Removes a single instance of the specified element from this queue, if it is present."
                // "Returns true if this queue contained the specified element"
                var op = "remove(" + value + ")";
                check(op, "result", model.remove(Integer.valueOf(value)), queue.remove(Integer.valueOf(value)));
                return op;
            }
            case 8: {
                // "Returns true if this queue contains the specified element."
                var op = "contains(" + value + ")";
                check(op, "result", model.contains(value), queue.contains(value));
                return op;
            }
            case 9: {
                var divisor = 1 + random.nextInt(3);
                var op = "removeIf(e % " + divisor + " == " + value % divisor + ")";
                check(op, "result", model.removeIf(e -> e % divisor == value % divisor),
                        queue.removeIf(e -> e % divisor == value % divisor));
                return op;
            }
            case 10: {
                var values = randomValues(random);
                var op = "removeAll(" + values + ")";
                check(op, "result", model.removeAll(values), queue.removeAll(values));
                return op;
            }
            case 11: {
                var values = randomValues(random);
                var op = "retainAll(" + values + ")";
                check(op, "result", model.retainAll(values), queue.retainAll(values));
                return op;
            }
            case 12: {
                // "Removes all available elements from this queue and adds them to the given collection."
                // "Returns: the number of elements transferred"
                var target = new ArrayList<Integer>(List.of(-1));
                var result = queue.drainTo(target);
                var expected = new ArrayList<Integer>(List.of(-1));
                expected.addAll(model);
                check("drainTo(c)", "result", model.size(), result);
                check("drainTo(c)", "target", expected, target);
                model.clear();
                return "drainTo(c)";
            }
            case 13: {
                // "Removes at most the given number of available elements from this queue and adds them to the given
                // collection."
                var max = random.nextInt(capacity + 3) - 1;
                var op = "drainTo(c, " + max + ")";
                var target = new ArrayList<Integer>(List.of(-1));
                var result = queue.drainTo(target, max);
                var count = Math.max(0, Math.min(max, model.size()));
                var expected = new ArrayList<Integer>(List.of(-1));
                expected.addAll(model.subList(0, count));
                check(op, "result", count, result);
                check(op, "target", expected, target);
                model.subList(0, count).clear();
                return op;
            }
            case 14: {
                // "Returns an iterator over the elements in this queue in proper sequence. The elements will be
                // returned in order from first (head) to last (tail)."
                // Iterator.next() throws "NoSuchElementException - if the iteration has no more elements"
                var it = queue.iterator();
                var seen = new ArrayList<Integer>();
                while (it.hasNext() && seen.size() <= capacity) {
                    seen.add(it.next());
                }
                check("iterate", "elements", model, seen);
                var thrown = false;
                try {
                    it.next();
                } catch (NoSuchElementException e) {
                    thrown = true;
                }
                check("iterate", "NoSuchElementException at the end", true, thrown);
                return "iterate";
            }
            case 15: {
                // Iterator.remove(): "Removes from the underlying collection the last element returned by this
                // iterator". It throws "IllegalStateException - if the next method has not yet been called, or the
                // remove method has already been called after the last call to the next method".
                var it = queue.iterator();
                check("iterator.remove", "IllegalStateException before next()", true, removeThrows(it));
                var seen = new ArrayList<Integer>();
                var expectedRest = new ArrayList<Integer>();
                var removals = new StringBuilder();
                while (it.hasNext() && seen.size() <= capacity) {
                    var e = it.next();
                    seen.add(e);
                    if (random.nextBoolean()) {
                        it.remove();
                        removals.append(seen.size() - 1).append(' ');
                        check("iterator.remove", "IllegalStateException after remove()", true, removeThrows(it));
                    } else {
                        expectedRest.add(e);
                    }
                }
                var op = "iterator.remove at " + removals;
                check(op, "elements", model, seen);
                model.clear();
                model.addAll(expectedRest);
                return op;
            }
            case 16: {
                queue.clear();
                model.clear();
                return "clear()";
            }
            case 17: {
                // toArray(T[] a): "If the queue fits in the specified array, it is returned therein. Otherwise, a
                // new array is allocated". "If this queue fits in the specified array with room to spare (i.e., the
                // array has more elements than this queue), the element in the array immediately following the end
                // of the queue is set to null."
                var length = random.nextInt(capacity + 2);
                var op = "toArray(new Integer[" + length + "])";
                var array = new Integer[length];
                Arrays.fill(array, -1);
                var result = queue.toArray(array);
                var expected = model.toArray(new Integer[0]);
                if (length >= model.size()) {
                    check(op, "same array", true, result == array);
                    var expectedArray = new Integer[length];
                    Arrays.fill(expectedArray, -1);
                    System.arraycopy(expected, 0, expectedArray, 0, expected.length);
                    if (length > model.size()) {
                        expectedArray[model.size()] = null;
                    }
                    expected = expectedArray;
                }
                check(op, "elements", Arrays.asList(expected), Arrays.asList(result));
                return op;
            }
            case 18: {
                // "Retrieves, but does not remove, the head of this queue, or returns null if this queue is empty."
                check("peek()", "result", model.isEmpty() ? null : model.get(0), queue.peek());
                return "peek()";
            }
            default: {
                // element(): "throws an exception if this queue is empty"
                var thrown = false;
                Integer result = null;
                try {
                    result = queue.element();
                } catch (NoSuchElementException e) {
                    thrown = true;
                }
                check("element()", "NoSuchElementException", model.isEmpty(), thrown);
                if (!thrown) {
                    check("element()", "result", model.get(0), result);
                }
                return "element()";
            }
        }
    }

    private static List<Integer> randomValues(Random random) {
        var values = new ArrayList<Integer>();
        for (var i = 0; i < VALUES; ++i) {
            if (random.nextInt(3) == 0) {
                values.add(i);
            }
        }
        return values;
    }

    private static boolean removeThrows(Iterator<Integer> it) {
        try {
            it.remove();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // The state that every operation must keep: the elements in order (through toArray() and iteration), size(),
    // isEmpty() and remainingCapacity(), which is "the number of additional elements that this queue can ideally (in
    // the absence of memory or resource constraints) accept without blocking".
    private static void checkState(String description, ArrayBlockingQueue<Integer> queue, List<Integer> model,
            int capacity) {
        check(description, "size()", model.size(), queue.size());
        check(description, "isEmpty()", model.isEmpty(), queue.isEmpty());
        check(description, "remainingCapacity()", capacity - model.size(), queue.remainingCapacity());
        check(description, "toArray()", model, Arrays.asList(queue.toArray()));
        var iterated = new ArrayList<Integer>();
        for (var it = queue.iterator(); it.hasNext() && iterated.size() <= capacity;) {
            iterated.add(it.next());
        }
        check(description, "iteration", model, iterated);
        check(description, "peek()", model.isEmpty() ? null : model.get(0), queue.peek());
    }

    private static void check(String description, String what, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new Mismatch(description + ": " + what + " expected " + expected + ", got " + actual);
        }
    }

    // Names the exception with instanceof checks, because getClass().getName() can be null on JS and C for an
    // exception that the runtime throws.
    private static String describe(RuntimeException e) {
        if (e instanceof ArrayIndexOutOfBoundsException) {
            return "ArrayIndexOutOfBoundsException";
        } else if (e instanceof NullPointerException) {
            return "NullPointerException";
        } else if (e instanceof IllegalStateException) {
            return "IllegalStateException";
        } else if (e instanceof NoSuchElementException) {
            return "NoSuchElementException";
        } else {
            return "other RuntimeException";
        }
    }

    private static class Mismatch extends RuntimeException {
        Mismatch(String message) {
            super(message);
        }
    }
}
