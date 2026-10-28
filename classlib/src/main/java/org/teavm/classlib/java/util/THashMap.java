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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TCloneNotSupportedException;
import org.teavm.classlib.java.lang.TCloneable;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TIllegalStateException;
import org.teavm.classlib.java.lang.TNullPointerException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.interop.Rename;

/**
 * kafka-wasm: the JDK 21 HashMap's iteration order. That order is unspecified, but Kafka's assignors iterate
 * HashMaps and HashSets to break ties, and their tests expect the JDK's results. This class follows the JDK's rules:
 * <ul>
 * <li>A bucket index uses the spread hash {@code h ^ (h >>> 16)}.</li>
 * <li>put appends a new entry at the tail of its bucket. computeIfAbsent, compute, and merge insert at the head.</li>
 * <li>A resize doubles the table and splits each bucket into a low and a high bucket, in order.</li>
 * <li>The table is sized at the first insertion, from the constructor's capacity, or from putAll's pre-size.</li>
 * <li>A bucket of 8 or more entries in a table of less than 64 buckets resizes the table.</li>
 * </ul>
 * One difference stays: the JDK turns a bucket of 8 or more entries in a larger table into a tree, which iterates in
 * another order. This class keeps the list. Keys with well-spread hashes do not reach 8 entries in one bucket.
 */
public class THashMap<K, V> extends TAbstractMap<K, V> implements TCloneable, TSerializable {
    transient int elementCount;
    transient HashEntry<K, V>[] elementData;
    transient int modCount;
    private static final int DEFAULT_SIZE = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    // The JDK's TREEIFY_THRESHOLD and MIN_TREEIFY_CAPACITY.
    private static final int LONG_BUCKET = 8;
    private static final int MIN_TREE_CAPACITY = 64;
    final float loadFactor;
    /**
     * As in the JDK: the size at which the next insertion resizes. Before the first insertion
     * ({@link #tableUnused}), it holds the initial capacity instead, or 0 for the default capacity.
     */
    int threshold;
    /**
     * The JDK allocates the table at the first insertion, and putAll can still change its size until then. True
     * until that insertion: elementData is then an empty placeholder of one bucket, and {@link #resize} allocates the
     * real table.
     */
    transient boolean tableUnused;

    static class HashEntry<K, V> extends TMapEntry<K, V> {
        final int origKeyHash;

        HashEntry<K, V> next;

        HashEntry(K theKey, int hash) {
            super(theKey, null);
            this.origKeyHash = hash;
        }

        HashEntry(K theKey, V theValue) {
            super(theKey, theValue);
            origKeyHash = TObjects.hashCode(theKey);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clone() {
            HashEntry<K, V> entry = (HashEntry<K, V>) super.clone();
            if (next != null) {
                entry.next = (HashEntry<K, V>) next.clone();
            }
            return entry;
        }
    }

    private static class AbstractMapIterator<K, V>  {
        private int position;
        int expectedModCount;
        HashEntry<K, V> futureEntry;
        HashEntry<K, V> currentEntry;
        HashEntry<K, V> prevEntry;

        final THashMap<K, V> associatedMap;

        AbstractMapIterator(THashMap<K, V> hm) {
            associatedMap = hm;
            expectedModCount = hm.modCount;
            futureEntry = null;
        }

        public boolean hasNext() {
            if (futureEntry != null) {
                return true;
            }
            while (position < associatedMap.elementData.length) {
                if (associatedMap.elementData[position] == null) {
                    position++;
                } else {
                    return true;
                }
            }
            return false;
        }

        final void checkConcurrentMod() throws TConcurrentModificationException {
            if (expectedModCount != associatedMap.modCount) {
                throw new TConcurrentModificationException();
            }
        }

        final void makeNext() {
            checkConcurrentMod();
            if (!hasNext()) {
                throw new TNoSuchElementException();
            }
            if (futureEntry == null) {
                currentEntry = associatedMap.elementData[position++];
                futureEntry = currentEntry.next;
                prevEntry = null;
            } else {
                if (currentEntry != null) {
                    prevEntry = currentEntry;
                }
                currentEntry = futureEntry;
                futureEntry = futureEntry.next;
            }
        }

        public final void remove() {
            checkConcurrentMod();
            if (currentEntry == null) {
                throw new TIllegalStateException();
            }
            if (prevEntry == null) {
                int index = associatedMap.indexFor(currentEntry.origKeyHash);
                associatedMap.elementData[index] = associatedMap.elementData[index].next;
            } else {
                prevEntry.next = currentEntry.next;
            }
            currentEntry = null;
            expectedModCount++;
            associatedMap.modCount++;
            associatedMap.elementCount--;

        }
    }


    private static class EntryIterator<K, V> extends AbstractMapIterator<K, V>
            implements TIterator<TMap.Entry<K, V>> {
        EntryIterator(THashMap<K, V> map) {
            super(map);
        }

        @Override
        public TMap.Entry<K, V> next() {
            makeNext();
            return currentEntry;
        }
    }

    private static class KeyIterator<K, V> extends AbstractMapIterator<K, V> implements TIterator<K> {
        KeyIterator(THashMap<K, V> map) {
            super(map);
        }

        @Override
        public K next() {
            makeNext();
            return currentEntry.key;
        }
    }

    private static class ValueIterator<K, V> extends AbstractMapIterator<K, V> implements TIterator<V> {
        ValueIterator(THashMap<K, V> map) {
            super(map);
        }

        @Override
        public V next() {
            makeNext();
            return currentEntry.value;
        }
    }

    static class HashMapEntrySet<K, V> extends TAbstractSet<TMap.Entry<K, V>> {
        private final THashMap<K, V> associatedMap;

        public HashMapEntrySet(THashMap<K, V> hm) {
            associatedMap = hm;
        }

        THashMap<K, V> hashMap() {
            return associatedMap;
        }

        @Override
        public int size() {
            return associatedMap.elementCount;
        }

        @Override
        public void clear() {
            associatedMap.clear();
        }

        @Override
        public boolean remove(Object object) {
            if (object instanceof TMap.Entry) {
                var oEntry = (TMap.Entry<?, ?>) object;
                var entry = associatedMap.entryByKey(oEntry.getKey());
                if (entry != null && TObjects.equals(entry.getValue(), oEntry.getValue())) {
                    associatedMap.removeEntry(entry);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof TMap.Entry) {
                TMap.Entry<?, ?> oEntry = (TMap.Entry<?, ?>) object;
                TMap.Entry<K, V> entry = associatedMap.entryByKey(oEntry.getKey());
                return entry != null && TObjects.equals(entry.getValue(), oEntry.getValue());
            }
            return false;
        }

        @Override
        public TIterator<TMap.Entry<K, V>> iterator() {
            return new EntryIterator<>(associatedMap);
        }

        @Override
        public void forEach(Consumer<? super Entry<K, V>> action) {
            if (associatedMap.elementCount > 0) {
                int prevModCount = associatedMap.modCount;
                for (int i = 0; i < associatedMap.elementData.length; i++) {
                    HashEntry<K, V> entry = associatedMap.elementData[i];
                    while (entry != null) {
                        action.accept(entry);
                        entry = entry.next;
                        if (prevModCount != associatedMap.modCount) {
                            throw new TConcurrentModificationException();
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    HashEntry<K, V>[] newElementArray(int s) {
        return new HashEntry[s];
    }

    public THashMap() {
        loadFactor = DEFAULT_LOAD_FACTOR;
        clearTable();
    }

    public THashMap(int capacity) {
        this(capacity, DEFAULT_LOAD_FACTOR);
    }

    /** The JDK's tableSizeFor: the smallest power of two at least cap, from 1 to 2^30. */
    static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return n < 0 ? 1 : n >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : n + 1;
    }

    /**
     * Constructs a new {@code HashMap} instance with the specified capacity and
     * load factor.
     *
     * @param capacity
     *            the initial capacity of this hash map.
     * @param loadFactor
     *            the initial load factor.
     * @throws IllegalArgumentException
     *                when the capacity is less than zero or the load factor is
     *                less or equal to zero.
     */
    public THashMap(int capacity, float loadFactor) {
        if (capacity < 0 || loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new TIllegalArgumentException();
        }
        this.loadFactor = loadFactor;
        clearTable();
        threshold = tableSizeFor(Math.min(capacity, MAXIMUM_CAPACITY));
    }

    /** The JDK's state before the first insertion: no table, and the default capacity. */
    private void clearTable() {
        elementData = newElementArray(1);
        tableUnused = true;
        threshold = 0;
    }

    /** The bucket of a key's raw hash code, as the JDK's {@code (n - 1) & (h ^ (h >>> 16))}. */
    final int indexFor(int hash) {
        return (hash ^ (hash >>> 16)) & (elementData.length - 1);
    }

    public THashMap(TMap<? extends K, ? extends V> map) {
        this();
        putAllImpl(map);
    }

    @Override
    public void clear() {
        if (elementCount > 0) {
            elementCount = 0;
            Arrays.fill(elementData, null);
            modCount++;
        }
    }

    @Rename("clone")
    @SuppressWarnings("unchecked")
    public TObject clone0() {
        try {
            // As the JDK: a new table, sized for this map's entries, filled in this map's order.
            THashMap<K, V> map = (THashMap<K, V>) super.clone();
            map.elementCount = 0;
            map.modCount = 0;
            map.clearTable();
            map.putAllImpl(this);

            return map;
        } catch (TCloneNotSupportedException e) {
            return null;
        }
    }

    @Override
    public boolean containsKey(Object key) {
        HashEntry<K, V> m = entryByKey(key);
        return m != null;
    }

    @Override
    public boolean containsValue(Object value) {
        if (value != null) {
            for (int i = 0; i < elementData.length; i++) {
                HashEntry<K, V> entry = elementData[i];
                while (entry != null) {
                    if (value.equals(entry.value)) {
                        return true;
                    }
                    entry = entry.next;
                }
            }
        } else {
            for (int i = 0; i < elementData.length; i++) {
                HashEntry<K, V> entry = elementData[i];
                while (entry != null) {
                    if (entry.value == null) {
                        return true;
                    }
                    entry = entry.next;
                }
            }
        }
        return false;
    }

    @Override
    public TSet<TMap.Entry<K, V>> entrySet() {
        return new HashMapEntrySet<>(this);
    }

    @Override
    public V get(Object key) {
        HashEntry<K, V> m = entryByKey(key);
        if (m != null) {
            return m.value;
        }
        return null;
    }

    final HashEntry<K, V> entryByKey(Object key) {
        HashEntry<K, V> m;
        if (key == null) {
            m = findNullKeyEntry();
        } else {
            int hash = key.hashCode();
            m = findNonNullKeyEntry(key, indexFor(hash), hash);
        }
        return m;
    }

    final HashEntry<K, V> findNonNullKeyEntry(Object key, int index, int keyHash) {
        HashEntry<K, V> m = elementData[index];
        while (m != null && (m.origKeyHash != keyHash || !areEqualKeys(key, m.key))) {
            m = m.next;
        }
        return m;
    }

    final HashEntry<K, V> findNullKeyEntry() {
        HashEntry<K, V> m = elementData[0];
        while (m != null && m.key != null) {
            m = m.next;
        }
        return m;
    }

    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }

    @Override
    public TSet<K> keySet() {
        if (cachedKeySet == null) {
            cachedKeySet = new TAbstractSet<K>() {
                @Override public boolean contains(Object object) {
                    return containsKey(object);
                }
                @Override public int size() {
                    return THashMap.this.size();
                }
                @Override public void clear() {
                    THashMap.this.clear();
                }
                @Override public boolean remove(Object key) {
                    HashEntry<K, V> entry = THashMap.this.removeByKey(key);
                    return entry != null;
                }
                @Override public TIterator<K> iterator() {
                    return new KeyIterator<>(THashMap.this);
                }
                @Override public void forEach(Consumer<? super K> action) {
                    if (elementCount > 0) {
                        int prevModCount = modCount;
                        for (int i = 0; i < elementData.length; i++) {
                            HashEntry<K, V> entry = elementData[i];
                            while (entry != null) {
                                action.accept(entry.key);
                                entry = entry.next;
                                if (prevModCount != modCount) {
                                    throw new TConcurrentModificationException();
                                }
                            }
                        }
                    }
                }
            };
        }
        return cachedKeySet;
    }

    @Override
    public V put(K key, V value) {
        return putImpl(key, value);
    }

    private V putImpl(K key, V value) {
        // kafka-wasm: the JDK's putVal. A new entry goes at the tail of its bucket.
        int hash = TObjects.hashCode(key);
        if (tableUnused) {
            resize();
        }
        int index = indexFor(hash);
        HashEntry<K, V> entry = elementData[index];
        HashEntry<K, V> last = null;
        int bucketSize = 0;
        while (entry != null && !(entry.origKeyHash == hash && (entry.key == key || key != null
                && key.equals(entry.key)))) {
            last = entry;
            entry = entry.next;
            bucketSize++;
        }
        if (entry == null) {
            modCount++;
            entry = new HashEntry<>(key, hash);
            if (last == null) {
                elementData[index] = entry;
            } else {
                last.next = entry;
            }
            if (bucketSize >= LONG_BUCKET) {
                longBucket();
            }
            if (++elementCount > threshold) {
                resize();
            }
        }

        V result = entry.value;
        entry.value = value;
        return result;
    }

    /** The JDK's treeifyBin, without the tree: a small table grows instead. */
    final void longBucket() {
        if (elementData.length < MIN_TREE_CAPACITY) {
            resize();
        }
    }

    @Override
    public void putAll(TMap<? extends K, ? extends V> map) {
        if (!map.isEmpty()) {
            putAllImpl(map);
        }
    }

    void putAllImpl(TMap<? extends K, ? extends V> map) {
        presize(map.size());
        for (var it = map.entrySet().iterator(); it.hasNext();) {
            TMap.Entry<? extends K, ? extends V> entry = it.next();
            putImpl(entry.getKey(), entry.getValue());
        }
    }

    /** The first half of the JDK's putMapEntries: size the table for s more entries before adding them. */
    final void presize(int s) {
        if (s <= 0) {
            return;
        }
        if (tableUnused) {
            double dt = Math.ceil(s / (double) loadFactor);
            int t = dt < (double) MAXIMUM_CAPACITY ? (int) dt : MAXIMUM_CAPACITY;
            if (t > threshold) {
                threshold = tableSizeFor(t);
            }
        } else {
            while (s > threshold && elementData.length < MAXIMUM_CAPACITY) {
                resize();
            }
        }
    }

    /**
     * The JDK's resize. Before the first insertion, it allocates the table. After it, it doubles the table, and
     * splits each bucket into the same index and the index plus the old length, keeping the entries' order.
     */
    final void resize() {
        int oldCap = tableUnused ? 0 : elementData.length;
        int oldThr = threshold;
        int newCap;
        int newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return;
            }
            newCap = oldCap << 1;
            if (newCap < MAXIMUM_CAPACITY && oldCap >= DEFAULT_SIZE) {
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            newCap = oldThr;
        } else {
            newCap = DEFAULT_SIZE;
            newThr = (int) (DEFAULT_LOAD_FACTOR * DEFAULT_SIZE);
        }
        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;
            newThr = newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE;
        }
        threshold = newThr;
        HashEntry<K, V>[] newData = newElementArray(newCap);
        if (!tableUnused) {
            for (int j = 0; j < oldCap; j++) {
                HashEntry<K, V> entry = elementData[j];
                HashEntry<K, V> loHead = null;
                HashEntry<K, V> loTail = null;
                HashEntry<K, V> hiHead = null;
                HashEntry<K, V> hiTail = null;
                while (entry != null) {
                    HashEntry<K, V> next = entry.next;
                    int h = entry.origKeyHash;
                    if (((h ^ (h >>> 16)) & oldCap) == 0) {
                        if (loTail == null) {
                            loHead = entry;
                        } else {
                            loTail.next = entry;
                        }
                        loTail = entry;
                    } else {
                        if (hiTail == null) {
                            hiHead = entry;
                        } else {
                            hiTail.next = entry;
                        }
                        hiTail = entry;
                    }
                    entry = next;
                }
                if (loTail != null) {
                    loTail.next = null;
                    newData[j] = loHead;
                }
                if (hiTail != null) {
                    hiTail.next = null;
                    newData[j + oldCap] = hiHead;
                }
            }
        }
        tableUnused = false;
        elementData = newData;
    }

    /**
     * kafka-wasm: computeIfAbsent, compute, and merge resize before they insert, if the map is over its threshold.
     * Their insertion does not check the threshold, so the map can stay over it until the next insertion.
     */
    private void resizeBeforeCompute() {
        if (elementCount > threshold || tableUnused) {
            resize();
        }
    }

    private HashEntry<K, V> findInBucket(Object key, int hash, int index) {
        HashEntry<K, V> e = elementData[index];
        while (e != null && !(e.origKeyHash == hash && (e.key == key || key != null && key.equals(e.key)))) {
            e = e.next;
        }
        return e;
    }

    private int bucketSize(int index) {
        int n = 0;
        for (HashEntry<K, V> e = elementData[index]; e != null; e = e.next) {
            n++;
        }
        return n;
    }

    /** The JDK's insertion in computeIfAbsent, compute, and merge: at the head of the bucket. */
    private void insertAtHead(K key, int hash, int index, V value, int sizeBefore) {
        HashEntry<K, V> entry = new HashEntry<>(key, hash);
        entry.value = value;
        entry.next = elementData[index];
        elementData[index] = entry;
        if (sizeBefore >= LONG_BUCKET - 1) {
            longBucket();
        }
        elementCount++;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null) {
            throw new TNullPointerException();
        }
        int hash = TObjects.hashCode(key);
        resizeBeforeCompute();
        int index = indexFor(hash);
        HashEntry<K, V> old = findInBucket(key, hash, index);
        if (old != null && old.value != null) {
            return old.value;
        }
        int sizeBefore = old == null ? bucketSize(index) : 0;
        int mc = modCount;
        V v = mappingFunction.apply(key);
        if (mc != modCount) {
            throw new TConcurrentModificationException();
        }
        if (v == null) {
            return null;
        } else if (old != null) {
            old.value = v;
            return v;
        }
        insertAtHead(key, hash, index, v, sizeBefore);
        modCount = mc + 1;
        return v;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        TObjects.requireNonNull(remappingFunction);
        V v = get(key);
        if (v == null) {
            return null;
        }
        int mc = modCount;
        V newValue = remappingFunction.apply(key, v);
        checkModCount(mc);
        if (newValue != null) {
            put(key, newValue);
        } else {
            remove(key);
        }
        return newValue;
    }

    private void checkModCount(int expected) {
        if (modCount != expected) {
            throw new TConcurrentModificationException();
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null) {
            throw new TNullPointerException();
        }
        int hash = TObjects.hashCode(key);
        resizeBeforeCompute();
        int index = indexFor(hash);
        HashEntry<K, V> old = findInBucket(key, hash, index);
        int sizeBefore = old == null ? bucketSize(index) : 0;
        V oldValue = old == null ? null : old.value;
        int mc = modCount;
        V v = remappingFunction.apply(key, oldValue);
        if (mc != modCount) {
            throw new TConcurrentModificationException();
        }
        if (old != null) {
            if (v != null) {
                old.value = v;
            } else {
                removeEntry(old);
            }
        } else if (v != null) {
            insertAtHead(key, hash, index, v, sizeBefore);
            modCount = mc + 1;
        }
        return v;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null || remappingFunction == null) {
            throw new TNullPointerException();
        }
        int hash = TObjects.hashCode(key);
        resizeBeforeCompute();
        int index = indexFor(hash);
        HashEntry<K, V> old = findInBucket(key, hash, index);
        if (old != null) {
            V v;
            if (old.value != null) {
                int mc = modCount;
                v = remappingFunction.apply(old.value, value);
                if (mc != modCount) {
                    throw new TConcurrentModificationException();
                }
            } else {
                v = value;
            }
            if (v != null) {
                old.value = v;
            } else {
                removeEntry(old);
            }
            return v;
        }
        insertAtHead(key, hash, index, value, bucketSize(index));
        modCount++;
        return value;
    }

    @Override
    public V remove(Object key) {
        HashEntry<K, V> entry = removeByKey(key);
        if (entry != null) {
            return entry.value;
        }
        return null;
    }

    final void removeEntry(HashEntry<K, V> entry) {
        int index = indexFor(entry.origKeyHash);
        var m = elementData[index];
        if (m == entry) {
            elementData[index] = entry.next;
        } else {
            while (m.next != entry) {
                m = m.next;
            }
            m.next = entry.next;
        }
        modCount++;
        elementCount--;
    }

    final HashEntry<K, V> removeByKey(Object key) {
        int index = 0;
        HashEntry<K, V> entry;
        HashEntry<K, V> last = null;
        if (key != null) {
            int hash = key.hashCode();
            index = indexFor(hash);
            entry = elementData[index];
            while (entry != null && !(entry.origKeyHash == hash && areEqualKeys(key, entry.key))) {
                last = entry;
                entry = entry.next;
            }
        } else {
            entry = elementData[0];
            while (entry != null && entry.key != null) {
                last = entry;
                entry = entry.next;
            }
        }
        if (entry == null) {
            return null;
        }
        if (last == null) {
            elementData[index] = entry.next;
        } else {
            last.next = entry.next;
        }
        modCount++;
        elementCount--;
        return entry;
    }

    @Override
    public int size() {
        return elementCount;
    }

    @Override
    public TCollection<V> values() {
        if (cachedValues == null) {
            cachedValues = new TAbstractCollection<V>() {
                @Override public boolean contains(Object object) {
                    return containsValue(object);
                }
                @Override public int size() {
                    return THashMap.this.size();
                }
                @Override public void clear() {
                    THashMap.this.clear();
                }
                @Override public TIterator<V> iterator() {
                    return new ValueIterator<>(THashMap.this);
                }
                @Override public void forEach(Consumer<? super V> action) {
                    if (elementCount > 0) {
                        int prevModCount = modCount;
                        for (int i = 0; i < elementData.length; i++) {
                            HashEntry<K, V> entry = elementData[i];
                            while (entry != null) {
                                action.accept(entry.value);
                                entry = entry.next;
                                if (prevModCount != modCount) {
                                    throw new TConcurrentModificationException();
                                }
                            }
                        }
                    }
                }
            };
        }
        return cachedValues;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (elementCount > 0) {
            int prevModCount = modCount;
            for (int i = 0; i < elementData.length; i++) {
                HashEntry<K, V> entry = elementData[i];
                while (entry != null) {
                    action.accept(entry.key, entry.value);
                    entry = entry.next;
                    if (prevModCount != modCount) {
                        throw new TConcurrentModificationException();
                    }
                }
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (elementCount > 0) {
            int prevModCount = modCount;
            for (int i = 0; i < elementData.length; i++) {
                HashEntry<K, V> entry = elementData[i];
                while (entry != null) {
                    entry.value = function.apply(entry.key, entry.value);
                    entry = entry.next;
                    if (prevModCount != modCount) {
                        throw new TConcurrentModificationException();
                    }
                }
            }
        }
    }

    static boolean areEqualKeys(Object key1, Object key2) {
        return (key1 == key2) || key1.equals(key2);
    }

    // The JDK's calculateHashMapCapacity.
    static int capacity(int size) {
        return (int) Math.ceil(size / (double) DEFAULT_LOAD_FACTOR);
    }

    public static <K, V> THashMap<K, V> newHashMap(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return new THashMap<>(capacity(size));
    }
}
