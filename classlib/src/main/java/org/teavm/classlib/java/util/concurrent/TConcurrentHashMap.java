/*
 *  Copyright 2020 Alexey Andreev.
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

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TCloneNotSupportedException;
import org.teavm.classlib.java.lang.TCloneable;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.classlib.java.lang.TIllegalStateException;
import org.teavm.classlib.java.lang.TObject;
import org.teavm.classlib.java.util.TAbstractCollection;
import org.teavm.classlib.java.util.TAbstractMap;
import org.teavm.classlib.java.util.TAbstractSet;
import org.teavm.classlib.java.util.TCollection;
import org.teavm.classlib.java.util.TIterator;
import org.teavm.classlib.java.util.TMap;
import org.teavm.classlib.java.util.TNoSuchElementException;
import org.teavm.classlib.java.util.TSet;
import org.teavm.interop.Rename;

/**
 * Follows the JDK's ConcurrentHashMap contract:
 * <ul>
 * <li>A null key or value throws NullPointerException.</li>
 * <li>Iterators are weakly consistent: they never throw ConcurrentModificationException. An iterator returns the
 * entries that existed when it was created, and skips the ones removed since. So code can remove entries while
 * it iterates over them.</li>
 * <li>computeIfAbsent stores nothing when the function returns null, and computeIfPresent removes the entry when it
 * returns null.</li>
 * <li>A bucket index uses the JDK's spread hash {@code h ^ (h >>> 16)} ({@link #indexFor}), so keys whose hash codes
 * differ only in high bits do not share one bucket. The spread also gives the JDK's bucket, so keys that do not
 * share a bucket iterate in the JDK's order for the same table size.</li>
 * </ul>
 */
public class TConcurrentHashMap<K, V> extends TAbstractMap<K, V>
        implements TConcurrentMap<K, V>, TCloneable, TSerializable {
    private transient int elementCount;
    private transient HashEntry<K, V>[] elementData;
    private static final int DEFAULT_SIZE = 16;
    private final float loadFactor;
    private int threshold;
    private transient KeySetView<K, V> cachedKeySet;
    private transient TCollection<V> cachedValues;

    static class HashEntry<K, V> extends TMapEntry<K, V> {
        final int origKeyHash;
        HashEntry<K, V> next;
        boolean removed;

        HashEntry(K theKey, int hash) {
            super(theKey, null);
            this.origKeyHash = hash;
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
        // The entries when the iterator was created. A snapshot keeps iteration valid while the map changes.
        private final HashEntry<K, V>[] snapshot;
        private int position;
        HashEntry<K, V> currentEntry;

        final TConcurrentHashMap<K, V> associatedMap;

        AbstractMapIterator(TConcurrentHashMap<K, V> hm) {
            associatedMap = hm;
            var table = hm.elementData;
            int count = 0;
            for (var entry : table) {
                for (var e = entry; e != null; e = e.next) {
                    count++;
                }
            }
            snapshot = hm.newElementArray(count);
            int i = 0;
            for (var entry : table) {
                for (var e = entry; e != null; e = e.next) {
                    snapshot[i++] = e;
                }
            }
        }

        public boolean hasNext() {
            while (position < snapshot.length && snapshot[position].removed) {
                position++;
            }
            return position < snapshot.length;
        }

        final void makeNext() {
            if (!hasNext()) {
                throw new TNoSuchElementException();
            }
            currentEntry = snapshot[position++];
        }

        public final void remove() {
            if (currentEntry == null) {
                throw new TIllegalStateException();
            }
            associatedMap.remove(currentEntry.key);
            currentEntry = null;
        }
    }


    private static class EntryIterator<K, V> extends AbstractMapIterator<K, V>
            implements TIterator<Entry<K, V>> {
        EntryIterator(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public Entry<K, V> next() {
            makeNext();
            return currentEntry;
        }
    }

    private static class KeyIterator<K, V> extends AbstractMapIterator<K, V> implements TIterator<K> {
        KeyIterator(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public K next() {
            makeNext();
            return currentEntry.key;
        }
    }

    private static class ValueIterator<K, V> extends AbstractMapIterator<K, V> implements TIterator<V> {
        ValueIterator(TConcurrentHashMap<K, V> map) {
            super(map);
        }

        @Override
        public V next() {
            makeNext();
            return currentEntry.value;
        }
    }

    static class HashMapEntrySet<K, V> extends TAbstractSet<Entry<K, V>> {
        private final TConcurrentHashMap<K, V> associatedMap;

        HashMapEntrySet(TConcurrentHashMap<K, V> hm) {
            associatedMap = hm;
        }

        TConcurrentHashMap<K, V> hashMap() {
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
                var oEntry = (Entry<?, ?>) object;
                if (oEntry.getKey() == null || oEntry.getValue() == null) {
                    return false;
                }
                var entry = associatedMap.getEntryByKeyAndValue(oEntry.getKey(), oEntry.getValue());
                if (entry != null) {
                    associatedMap.removeEntry(entry);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean contains(Object object) {
            if (object instanceof TMap.Entry) {
                var oEntry = (Entry<?, ?>) object;
                return oEntry.getKey() != null && oEntry.getValue() != null
                        && associatedMap.getEntryByKeyAndValue(oEntry.getKey(), oEntry.getValue()) != null;
            }
            return false;
        }

        @Override
        public TIterator<Entry<K, V>> iterator() {
            return new EntryIterator<>(associatedMap);
        }
    }

    @SuppressWarnings("unchecked")
    HashEntry<K, V>[] newElementArray(int s) {
        return new HashEntry[s];
    }

    public TConcurrentHashMap() {
        this(DEFAULT_SIZE);
    }

    public TConcurrentHashMap(int capacity) {
        this(capacity, 0.75f);  // default load factor of 0.75
    }

    private static int calculateCapacity(int x) {
        if (x >= 1 << 30) {
            return 1 << 30;
        }
        if (x == 0) {
            return 16;
        }
        x = x - 1;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }

    public TConcurrentHashMap(int capacity, float loadFactor) {
        if (capacity >= 0 && loadFactor > 0) {
            capacity = calculateCapacity(capacity);
            elementCount = 0;
            elementData = newElementArray(capacity);
            this.loadFactor = loadFactor;
            computeThreshold();
        } else {
            throw new TIllegalArgumentException();
        }
    }

    public TConcurrentHashMap(TMap<? extends K, ? extends V> map) {
        this(calculateCapacity(map.size()));
        putAllImpl(map);
    }

    public TConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        this(initialCapacity, loadFactor);
    }

    @Override
    public void clear() {
        if (elementCount > 0) {
            // An open iterator skips removed entries.
            for (var entry : elementData) {
                for (var e = entry; e != null; e = e.next) {
                    e.removed = true;
                }
            }
            elementCount = 0;
            Arrays.fill(elementData, null);
        }
    }

    @Rename("clone")
    @SuppressWarnings("unchecked")
    public TObject clone0() {
        try {
            var map = (TConcurrentHashMap<K, V>) super.clone();
            map.elementCount = 0;
            map.elementData = newElementArray(elementData.length);
            map.putAll(this);

            return map;
        } catch (TCloneNotSupportedException e) {
            return null;
        }
    }

    private void computeThreshold() {
        threshold = (int) (elementData.length * loadFactor);
    }

    @Override
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        Objects.requireNonNull(value);
        repeatTable: do {
            var table = elementData;

            if (value != null) {
                for (var i = 0; i < table.length; i++) {
                    do {
                        var first = table[i];
                        if (first == null) {
                            break;
                        }
                        var entry = first;

                        while (entry != null) {
                            boolean equal = areEqualValues(value, entry.value);
                            if (table != elementData) {
                                continue repeatTable;
                            }
                            if (equal) {
                                return true;
                            }
                            entry = entry.next;
                        }

                        if (first == table[i]) {
                            break;
                        }
                    } while (true);
                }
            } else {
                for (var i = 0; i < elementData.length; i++) {
                    var entry = elementData[i];
                    while (entry != null) {
                        if (entry.value == null) {
                            return true;
                        }
                        entry = entry.next;
                    }
                }
            }
            return false;
        } while (true);
    }

    @Override
    public TSet<Entry<K, V>> entrySet() {
        return new HashMapEntrySet<>(this);
    }

    @Override
    public V get(Object key) {
        var m = getEntry(key);
        if (m != null) {
            return m.value;
        }
        return null;
    }

    @Override
    public boolean remove(Object key, Object value) {
        Objects.requireNonNull(key);
        if (value == null) {
            return false;
        }
        var entry = getEntryByKeyAndValue(key, value);
        if (entry != null) {
            removeEntry(entry);
            return true;
        }
        return false;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(oldValue);
        Objects.requireNonNull(newValue);
        var entry = getEntryByKeyAndValue(key, oldValue);
        if (entry != null) {
            entry.setValue(newValue);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        Objects.requireNonNull(value);
        var entry = getEntry(key);
        if (entry == null) {
            return null;
        }

        var result = entry.getValue();
        entry.setValue(value);
        return result;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        var m = getEntry(key);
        return m != null ? m.getValue() : defaultValue;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        var table = elementData;
        for (var entry : table) {
            while (entry != null) {
                if (!entry.removed) {
                    action.accept(entry.getKey(), entry.getValue());
                }
                entry = entry.next;
            }
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        var hash = key.hashCode();
        var entry = getEntry(key, hash);

        if (entry != null) {
            return entry.getValue();
        }
        var index = computeIndex(hash);
        entry = placeHashedEntry(key, index, hash);
        entry.setValue(value);
        return null;
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        var table = elementData;
        for (var entry : table) {
            while (entry != null) {
                if (!entry.removed) {
                    var newValue = function.apply(entry.getKey(), entry.getValue());
                    Objects.requireNonNull(newValue);
                    entry.setValue(newValue);
                }
                entry = entry.next;
            }
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(mappingFunction);

        var hash = key.hashCode();
        var entry = getEntry(key, hash);
        if (entry != null) {
            return entry.getValue();
        }

        var newValue = mappingFunction.apply(key);
        entry = getEntry(key, hash);
        if (entry != null) {
            return entry.getValue();
        }
        if (newValue == null) {
            return null;
        }
        var index = computeIndex(hash);
        entry = placeHashedEntry(key, index, hash);
        entry.setValue(newValue);
        return newValue;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(remappingFunction);
        int hash = key.hashCode();

        V newValue = null;
        var newValueComputed = false;
        while (true) {
            var entry = getEntry(key, hash);
            if (entry == null) {
                return null;
            }

            var oldValue = entry.getValue();
            if (!newValueComputed) {
                newValueComputed = true;
                newValue = remappingFunction.apply(key, oldValue);
            }
            entry = getEntry(key, hash);
            if (entry == null) {
                return null;
            } else if (entry.getValue() == oldValue) {
                if (newValue == null) {
                    removeEntry(entry);
                } else {
                    entry.setValue(newValue);
                }
                return newValue;
            }
        }
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(remappingFunction);
        var hash = key.hashCode();

        while (true) {
            var entry = getEntry(key, hash);
            if (entry == null) {
                var newValue = remappingFunction.apply(key, null);
                if (getEntry(key, hash) == null) {
                    if (newValue != null) {
                        var index = computeIndex(hash);
                        entry = placeHashedEntry(key, index, hash);
                        entry.setValue(newValue);
                    }
                    return newValue;
                }
            } else {
                var oldValue = entry.getValue();
                var newValue = remappingFunction.apply(key, oldValue);
                entry = getEntry(key, hash);
                if (entry != null && entry.getValue() == oldValue) {
                    if (newValue == null) {
                        removeEntry(entry);
                    } else {
                        entry.setValue(newValue);
                    }
                    return newValue;
                }
            }
        }
    }

    private HashEntry<K, V> getEntry(Object key) {
        Objects.requireNonNull(key);
        return getEntry(key, key.hashCode());
    }

    private HashEntry<K, V> getEntry(Object key, int hash) {
        if (key == null) {
            throw new NullPointerException();
        } else {
            repeatTable:
            do {
                var table = elementData;
                int index = indexFor(hash, table.length);

                repeatElement:
                do {
                    var first = table[index];
                    if (first == null) {
                        return null;
                    }
                    var m = first;

                    while (m != null) {
                        if (!m.removed && m.origKeyHash == hash) {
                            var equal = areEqualKeys(key, m.key);
                            if (table != elementData) {
                                continue repeatTable;
                            }
                            if (equal) {
                                if (m.removed) {
                                    continue repeatElement;
                                }
                                return m;
                            }
                        }
                        m = m.next;
                    }

                    if (first == table[index]) {
                        return null;
                    }
                } while (true);
            } while (true);
        }
    }

    private HashEntry<K, V> getEntryByKeyAndValue(Object key, Object value) {
        var hash = Objects.hashCode(key);
        repeatTable:
        do {
            var table = elementData;
            int index = indexFor(hash, table.length);

            repeatElement:
            do {
                var first = table[index];
                if (first == null) {
                    return null;
                }
                var m = first;

                while (m != null) {
                    if (m.origKeyHash == hash) {
                        boolean equal = key != null ? areEqualKeys(key, m.key) : m.key == null;
                        if (table != elementData) {
                            continue repeatTable;
                        }
                        if (m.removed) {
                            continue repeatElement;
                        }
                        if (equal) {
                            equal = areEqualValues(value, m.value);
                            if (table != elementData) {
                                continue repeatTable;
                            }
                            if (m.removed) {
                                continue repeatElement;
                            }
                            return equal ? m : null;
                        }
                    }
                    m = m.next;
                }

                if (first == table[index]) {
                    return null;
                }
            } while (true);
        } while (true);
    }

    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }

    // As in the JDK, keySet() returns ConcurrentHashMap.KeySetView. Code compiled against the JDK calls it with that
    // return type.
    @Override
    public KeySetView<K, V> keySet() {
        if (cachedKeySet == null) {
            cachedKeySet = new KeySetView<>(this, null);
        }
        return cachedKeySet;
    }

    public KeySetView<K, V> keySet(V mappedValue) {
        if (mappedValue == null) {
            throw new NullPointerException();
        }
        return new KeySetView<>(this, mappedValue);
    }

    public static <K> KeySetView<K, Boolean> newKeySet() {
        return new KeySetView<>(new TConcurrentHashMap<>(), Boolean.TRUE);
    }

    public static <K> KeySetView<K, Boolean> newKeySet(int initialCapacity) {
        return new KeySetView<>(new TConcurrentHashMap<>(initialCapacity), Boolean.TRUE);
    }

    public static class KeySetView<K, V> extends TAbstractSet<K> implements TSerializable {
        private final TConcurrentHashMap<K, V> map;
        private final V value;

        KeySetView(TConcurrentHashMap<K, V> map, V value) {
            this.map = map;
            this.value = value;
        }

        public TConcurrentHashMap<K, V> getMap() {
            return map;
        }

        public V getMappedValue() {
            return value;
        }

        @Override
        public boolean contains(Object o) {
            return map.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return map.remove(o) != null;
        }

        @Override
        public TIterator<K> iterator() {
            return new KeyIterator<>(map);
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public boolean add(K e) {
            if (value == null) {
                throw new UnsupportedOperationException();
            }
            return map.putIfAbsent(e, value) == null;
        }

        @Override
        public boolean addAll(TCollection<? extends K> c) {
            if (value == null) {
                throw new UnsupportedOperationException();
            }
            boolean added = false;
            for (TIterator<? extends K> it = c.iterator(); it.hasNext();) {
                if (map.putIfAbsent(it.next(), value) == null) {
                    added = true;
                }
            }
            return added;
        }
    }

    @Override
    public V put(K key, V value) {
        return putImpl(key, value);
    }

    private V putImpl(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        var hash = key.hashCode();
        var entry = getEntry(key, hash);
        var index = computeIndex(hash);

        if (entry == null) {
            entry = placeHashedEntry(key, index, hash);
        }

        V result = entry.value;
        entry.value = value;
        return result;
    }

    private HashEntry<K, V> placeHashedEntry(K key, int index, int hash) {
        var entry = createHashedEntry(key, index, hash);
        if (++elementCount > threshold) {
            rehash();
        }
        return entry;
    }

    private HashEntry<K, V> createHashedEntry(K key, int index, int hash) {
        var entry = new HashEntry<K, V>(key, hash);
        entry.next = elementData[index];
        elementData[index] = entry;
        return entry;
    }

    @Override
    public void putAll(TMap<? extends K, ? extends V> map) {
        if (!map.isEmpty()) {
            putAllImpl(map);
        }
    }

    private void putAllImpl(TMap<? extends K, ? extends V> map) {
        int capacity = elementCount + map.size();
        if (capacity > threshold) {
            rehash(capacity);
        }
        for (var iter = map.entrySet().iterator(); iter.hasNext();) {
            var entry = iter.next();
            putImpl(entry.getKey(), entry.getValue());
        }
    }

    private void rehash(int capacity) {
        int length = calculateCapacity(capacity == 0 ? 1 : capacity << 1);

        var newData = newElementArray(length);
        for (int i = 0; i < elementData.length; i++) {
            var entry = elementData[i];
            elementData[i] = null;
            while (entry != null) {
                int index = indexFor(entry.origKeyHash, length);
                var next = entry.next;
                entry.next = newData[index];
                newData[index] = entry;
                entry = next;
            }
        }
        elementData = newData;
        computeThreshold();
    }

    private void rehash() {
        rehash(elementData.length);
    }

    @Override
    public V remove(Object key) {
        var entry = getEntry(key);
        if (entry == null) {
            return null;
        }
        removeEntry(entry);
        return entry.value;
    }

    private void removeEntry(HashEntry<K, V> entry) {
        var index = indexFor(entry.origKeyHash, elementData.length);
        var m = elementData[index];
        if (m == entry) {
            elementData[index] = entry.next;
        } else {
            while (m.next != entry) {
                m = m.next;
            }
            m.next = entry.next;
        }
        elementCount--;
        entry.removed = true;
    }

    @Override
    public int size() {
        return elementCount;
    }

    @Override
    public TCollection<V> values() {
        if (cachedValues == null) {
            cachedValues = new TAbstractCollection<>() {
                @Override public boolean contains(Object object) {
                    return containsValue(object);
                }
                @Override public int size() {
                    return TConcurrentHashMap.this.size();
                }
                @Override public void clear() {
                    TConcurrentHashMap.this.clear();
                }
                @Override public TIterator<V> iterator() {
                    return new ValueIterator<>(TConcurrentHashMap.this);
                }
            };
        }
        return cachedValues;
    }

    private static boolean areEqualKeys(Object key1, Object key2) {
        return (key1 == key2) || key1.equals(key2);
    }

    private static boolean areEqualValues(Object value1, Object value2) {
        return (value1 == value2) || value1.equals(value2);
    }

    private int computeIndex(int hash) {
        return indexFor(hash, elementData.length);
    }

    /**
     * The bucket of a key's raw hash code in a table of {@code length} buckets (a power of two), as the JDK's
     * {@code (n - 1) & spread(h)}. The JDK's spread also clears the sign bit, which no index of a table of at most
     * 2^30 buckets uses.
     */
    private static int indexFor(int hash, int length) {
        return (hash ^ (hash >>> 16)) & (length - 1);
    }
}
