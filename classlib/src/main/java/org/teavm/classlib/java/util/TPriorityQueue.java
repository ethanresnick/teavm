/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.util;

import java.util.Arrays;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TIllegalStateException;
import org.teavm.classlib.java.lang.TNullPointerException;

/**
 *
 * @author Alexey Andreev
 * @param <E>
 */
public class TPriorityQueue<E> extends TAbstractQueue<E> implements TSerializable {
    private static final int REMOVE_MOVED_UP = -2;
    private Object[] data;
    private TComparator<Object> comparator;
    private TComparator<? super E> originalComparator;
    private int size;
    private int version;

    public TPriorityQueue() {
        this(1);
    }

    public TPriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    public TPriorityQueue(TCollection<? extends E> c) {
        if (c instanceof TPriorityQueue) {
            initFromPriorityQueue((TPriorityQueue<? extends E>) c);
        } else if (c instanceof TSortedSet) {
            initFromSortedSet((TSortedSet<? extends E>) c);
        } else {
            data = new Object[c.size()];
            setComparator(null);
            fillFromCollection(c);
        }
    }

    public TPriorityQueue(TPriorityQueue<? extends E> c) {
        initFromPriorityQueue(c);
    }

    public TPriorityQueue(TSortedSet<? extends E> c) {
        initFromSortedSet(c);
    }

    @SuppressWarnings("unchecked")
    private void initFromSortedSet(TSortedSet<? extends E> sortedSet) {
        data = new Object[sortedSet.size()];
        setComparator((TComparator<? super E>) sortedSet.comparator());
        fillFromCollection(sortedSet);
    }

    @SuppressWarnings("unchecked")
    private void initFromPriorityQueue(TPriorityQueue<? extends E> prirityQueue) {
        data = Arrays.copyOf(prirityQueue.data, prirityQueue.size);
        size = prirityQueue.size;
        setComparator((TComparator<? super E>) prirityQueue.comparator());
    }

    private void fillFromCollection(TCollection<? extends E> c) {
        for (TIterator<? extends E> iter = c.iterator(); iter.hasNext();) {
            offer(iter.next());
        }
        version = 0;
    }

    public TPriorityQueue(TComparator<? super E> comparator) {
        this(16, comparator);
    }

    public TPriorityQueue(int initialCapacity, TComparator<? super E> comparator) {
        if (initialCapacity < 1) {
            throw new TIllegalArgumentException();
        }
        data = new Object[initialCapacity];
        setComparator(comparator);
    }

    @SuppressWarnings("unchecked")
    private void setComparator(TComparator<? super E> comparator) {
        this.originalComparator = comparator;
        if (comparator == null) {
            comparator = TComparator.NaturalOrder.instance();
        }
        this.comparator = (TComparator<Object>) comparator;
    }

    public TComparator<? super E> comparator() {
        return originalComparator;
    }

    @Override
    public boolean offer(E e) {
        if (e == null) {
            throw new TNullPointerException();
        }
        ensureCapacity(size + 1);
        siftUp(size, e);
        ++size;
        ++version;
        return true;
    }

    @Override
    public E poll() {
        if (size == 0) {
            return null;
        }
        @SuppressWarnings("unchecked")
        E elem = (E) data[0];
        removeAt(0);
        return elem;
    }

    @SuppressWarnings("unchecked")
    @Override
    public E peek() {
        if (size == 0) {
            return null;
        }
        return (E) data[0];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public TIterator<E> iterator() {
        return new TIterator<E>() {
            private int index;
            private int knownVersion = version;
            // Array index of the last returned element, REMOVE_MOVED_UP if it is removeElement, which was returned
            // from movedUp, or -1 if there is nothing to remove
            private int removeIndex = -1;
            private Object removeElement;
            // Elements that a removal moved from beyond the cursor to before it; returned after the array scan
            private Object[] movedUp;
            private int movedUpCount;
            private int movedUpIndex;

            @Override public boolean hasNext() {
                checkVersion();
                return index < size || movedUpIndex < movedUpCount;
            }

            @SuppressWarnings("unchecked")
            @Override public E next() {
                checkVersion();
                if (index < size) {
                    removeIndex = index;
                    return (E) data[index++];
                }
                if (movedUpIndex < movedUpCount) {
                    removeIndex = REMOVE_MOVED_UP;
                    removeElement = movedUp[movedUpIndex];
                    movedUp[movedUpIndex++] = null;
                    return (E) removeElement;
                }
                throw new TNoSuchElementException();
            }

            @Override public void remove() {
                checkVersion();
                if (removeIndex >= 0) {
                    Object moved = removeAt(removeIndex);
                    if (moved == null) {
                        // The slot now holds an element that has not been returned yet (or is past the end)
                        index = removeIndex;
                    } else {
                        // The slot now holds an already returned element, while an element that has not been
                        // returned yet moved before the cursor
                        if (movedUp == null) {
                            movedUp = new Object[4];
                        } else if (movedUpCount == movedUp.length) {
                            movedUp = Arrays.copyOf(movedUp, movedUpCount * 2);
                        }
                        movedUp[movedUpCount++] = moved;
                    }
                } else if (removeIndex == REMOVE_MOVED_UP) {
                    for (int i = 0; i < size; ++i) {
                        if (data[i] == removeElement) {
                            removeAt(i);
                            break;
                        }
                    }
                    removeElement = null;
                } else {
                    throw new TIllegalStateException();
                }
                removeIndex = -1;
                knownVersion = version;
            }

            private void checkVersion() {
                if (version != knownVersion) {
                    throw new TConcurrentModificationException();
                }
            }
        };
    }

    @Override
    public void clear() {
        for (int i = 0; i < size; ++i) {
            data[i] = null;
        }
        size = 0;
        ++version;
    }

    /**
     * Removes the element at the given index and fills the gap with the last element, which may have to move
     * either down or up the heap.
     *
     * @return the last element if it moved to an index lower than the given one, otherwise {@code null}.
     */
    private Object removeAt(int index) {
        ++version;
        int last = --size;
        Object item = data[last];
        data[last] = null;
        if (index == last) {
            return null;
        }
        if (siftDown(index, item) == index && siftUp(index, item) != index) {
            return item;
        }
        return null;
    }

    // Puts item into the hole at index, moving the hole up until item is not less than its parent
    private int siftUp(int index, Object item) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (comparator.compare(item, data[parent]) >= 0) {
                break;
            }
            data[index] = data[parent];
            index = parent;
        }
        data[index] = item;
        return index;
    }

    // Puts item into the hole at index, moving the hole down until item is not greater than its children
    private int siftDown(int index, Object item) {
        while (true) {
            int next = index * 2 + 1;
            if (next >= size) {
                break;
            }
            int right = next + 1;
            if (right < size && comparator.compare(data[right], data[next]) < 0) {
                next = right;
            }
            if (comparator.compare(item, data[next]) <= 0) {
                break;
            }
            data[index] = data[next];
            index = next;
        }
        data[index] = item;
        return index;
    }

    private void ensureCapacity(int capacity) {
        if (data.length >= capacity) {
            return;
        }
        capacity = Math.max(capacity, data.length * 3 / 2);
        data = Arrays.copyOf(data, capacity);
    }
}
