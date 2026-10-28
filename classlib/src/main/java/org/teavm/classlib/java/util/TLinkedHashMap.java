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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TLinkedHashMap<K, V> extends THashMap<K, V> implements TSequencedMap<K, V> {
    private boolean accessOrder;

    transient LinkedHashMapEntry<K, V> head;
    transient LinkedHashMapEntry<K, V> tail;

    public TLinkedHashMap() {
        accessOrder = false;
        head = null;
    }

    public TLinkedHashMap(int s) {
        super(s);
        accessOrder = false;
        head = null;
    }

    public TLinkedHashMap(int s, float lf) {
        super(s, lf);
        accessOrder = false;
        head = null;
        tail = null;
    }

    public TLinkedHashMap(int s, float lf, boolean order) {
        super(s, lf);
        accessOrder = order;
        head = null;
        tail = null;
    }

    public TLinkedHashMap(TMap<? extends K, ? extends V> m) {
        accessOrder = false;
        head = null;
        tail = null;
        putAll(m);
    }

    @Override
    void putAllImpl(TMap<? extends K, ? extends V> map) {
        presize(map.size());
        for (var it = map.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            putImpl(entry.getKey(), entry.getValue(), false, accessOrder);
        }
    }

    static final class LinkedHashMapEntry<K, V> extends HashEntry<K, V> {
        LinkedHashMapEntry<K, V> chainForward;
        LinkedHashMapEntry<K, V> chainBackward;

        LinkedHashMapEntry(K theKey, V theValue) {
            super(theKey, theValue);
            chainForward = null;
            chainBackward = null;
        }

        LinkedHashMapEntry(K theKey, int hash) {
            super(theKey, hash);
            chainForward = null;
            chainBackward = null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object clone() {
            LinkedHashMapEntry<K, V> entry = (LinkedHashMapEntry<K, V>) super.clone();
            entry.chainBackward = chainBackward;
            entry.chainForward = chainForward;
            LinkedHashMapEntry<K, V> lnext = (LinkedHashMapEntry<K, V>) entry.next;
            if (lnext != null) {
                entry.next = (LinkedHashMapEntry<K, V>) lnext.clone();
            }
            return entry;
        }
    }

    @Override
    public boolean containsValue(Object value) {
        LinkedHashMapEntry<K, V> entry = head;
        if (null == value) {
            while (null != entry) {
                if (null == entry.value) {
                    return true;
                }
                entry = entry.chainForward;
            }
        } else {
            while (null != entry) {
                if (value.equals(entry.value)) {
                    return true;
                }
                entry = entry.chainForward;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    HashEntry<K, V>[] newElementArray(int s) {
        return new LinkedHashMapEntry[s];
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        LinkedHashMapEntry<K, V> entry;
        if (key == null) {
            entry = (LinkedHashMapEntry<K, V>) findNullKeyEntry();
        } else {
            int hash = key.hashCode();
            entry = (LinkedHashMapEntry<K, V>) findNonNullKeyEntry(key, indexFor(hash), hash);
        }
        if (entry == null) {
            return defaultValue;
        }
        if (accessOrder) {
            linkEntry(entry, false);
        }
        return entry.value;
    }

    @Override
    public V get(Object key) {
        return getOrDefault(key, null);
    }

    private HashEntry<K, V> createHashedEntry(K key, int index, int hash, boolean first) {
        var entry = new LinkedHashMapEntry<K, V>(key, hash);
        entry.next = elementData[index];
        elementData[index] = entry;
        if (first) {
            if (head != null) {
                head.chainBackward = entry;
            } else {
                tail = entry;
            }
            entry.chainForward = head;
            head = entry;
        } else {
            if (tail != null) {
                tail.chainForward = entry;
            } else {
                head = entry;
            }
            entry.chainBackward = tail;
            tail = entry;
        }
        return entry;
    }

    // kafka-wasm: THashMap's computeIfAbsent, compute, and merge insert into buckets directly, and do not link the
    // entry. Here they use get, put, and remove, as Map's default methods do.

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        TObjects.requireNonNull(mappingFunction);
        V v = get(key);
        if (v != null) {
            return v;
        }
        int mc = modCount;
        V newValue = mappingFunction.apply(key);
        checkModCount(mc);
        if (newValue != null) {
            put(key, newValue);
        }
        return newValue;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        TObjects.requireNonNull(remappingFunction);
        V oldValue = get(key);
        int mc = modCount;
        V newValue = remappingFunction.apply(key, oldValue);
        checkModCount(mc);
        if (newValue != null) {
            put(key, newValue);
        } else {
            // "If the remapping function returns null, the mapping is removed", also a mapping to null.
            remove(key);
        }
        return newValue;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        TObjects.requireNonNull(value);
        TObjects.requireNonNull(remappingFunction);
        V oldValue = get(key);
        V newValue;
        if (oldValue == null) {
            newValue = value;
        } else {
            int mc = modCount;
            newValue = remappingFunction.apply(oldValue, value);
            checkModCount(mc);
        }
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
    public V put(K key, V value) {
        var oldSize = size();
        var existing = putImpl(key, value, false, accessOrder);
        if (size() != oldSize) {
            if (removeEldestEntry(head)) {
                removeLinkedEntry(head);
            }
        }
        return existing;
    }

    V putImpl(K key, V value, boolean first, boolean forceMotion) {
        LinkedHashMapEntry<K, V> entry;
        if (elementCount == 0) {
            head = null;
            tail = null;
        }
        int hash = Objects.hashCode(key);
        // kafka-wasm: THashMap's bucket index and resize (the JDK's). The iteration order is the linked order, so
        // the bucket order does not matter here, but lookups must use THashMap's index.
        if (tableUnused) {
            resize();
        }
        int index = indexFor(hash);
        entry = (LinkedHashMapEntry<K, V>) (key != null ? findNonNullKeyEntry(key, index, hash) : findNullKeyEntry());
        if (entry == null) {
            modCount++;
            if (++elementCount > threshold) {
                resize();
                index = indexFor(hash);
            }
            entry = (LinkedHashMapEntry<K, V>) createHashedEntry(key, index, hash, first);
        } else if (forceMotion) {
            linkEntry(entry, first);
        }

        var existing = entry.value;
        entry.value = value;
        return existing;
    }

    private void linkEntry(LinkedHashMapEntry<K, V> entry, boolean first) {
        if (first) {
            var p = entry.chainBackward;
            if (p == null) {
                return;
            }
            var n = entry.chainForward;
            if (n != null) {
                n.chainBackward = p;
            } else {
                tail = p;
            }
            p.chainForward = n;
            if (head != null) {
                head.chainBackward = entry;
            }
            entry.chainForward = head;
            entry.chainBackward = null;
            head = entry;
        } else {
            var n = entry.chainForward;
            if (n == null) {
                return;
            }
            var p = entry.chainBackward;
            if (p != null) {
                p.chainForward = n;
            } else {
                head = n;
            }
            n.chainBackward = p;
            if (tail != null) {
                tail.chainForward = entry;
            }
            entry.chainBackward = tail;
            entry.chainForward = null;
            tail = entry;
        }
    }

    @Override
    public TSet<Entry<K, V>> entrySet() {
        return new TLinkedHashMapEntrySet<>(this, false);
    }

    @Override
    public TSet<K> keySet() {
        return sequencedKeySet();
    }

    @Override
    public TSequencedSet<K> sequencedKeySet() {
        if (cachedKeySet == null) {
            cachedKeySet = new TLinkedHashMapKeySet<>(this, false);
        }
        return (TSequencedSet<K>) cachedKeySet;
    }

    @Override
    public TCollection<V> values() {
        return sequencedValues();
    }

    @Override
    public TSequencedCollection<V> sequencedValues() {
        if (cachedValues == null) {
            cachedValues = new TLinkedHashMapValues<>(this, false);
        }
        return (TSequencedCollection<V>) cachedValues;
    }

    @Override
    public TSequencedSet<Entry<K, V>> sequencedEntrySet() {
        return new TLinkedHashMapEntrySet<>(this, false);
    }

    @Override
    public V remove(Object key) {
        var m = (LinkedHashMapEntry<K, V>) removeByKey(key);
        if (m == null) {
            return null;
        }
        unlinkEntry(m);

        return m.value;
    }

    void removeLinkedEntry(LinkedHashMapEntry<K, V> entry) {
        removeEntry(entry);
        unlinkEntry(entry);
    }

    private void unlinkEntry(LinkedHashMapEntry<K, V> entry) {
        var p = entry.chainBackward;
        var n = entry.chainForward;
        if (p != null) {
            p.chainForward = n;
            if (n != null) {
                n.chainBackward = p;
            } else {
                tail = p;
            }
        } else {
            head = n;
            if (n != null) {
                n.chainBackward = null;
            } else {
                tail = null;
            }
        }
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (elementCount > 0) {
            int prevModCount = modCount;
            LinkedHashMapEntry<K, V> entry = head;
            do {
                action.accept(entry.key, entry.value);
                entry = entry.chainForward;
                if (modCount != prevModCount) {
                    throw new TConcurrentModificationException();
                }
            } while (entry != null);
        }
    }

    protected boolean removeEldestEntry(@SuppressWarnings("unused") Entry<K, V> eldest) {
        return false;
    }

    @Override
    public void clear() {
        super.clear();
        head = null;
        tail = null;
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (elementCount > 0) {
            int prevModCount = modCount;
            LinkedHashMapEntry<K, V> entry = head;
            do {
                entry.value = function.apply(entry.key, entry.value);
                entry = entry.chainForward;
                if (modCount != prevModCount) {
                    throw new TConcurrentModificationException();
                }
            } while (entry != null);
        }
    }

    @Override
    public V putFirst(K k, V v) {
        return putImpl(k, v, true, true);
    }

    @Override
    public V putLast(K k, V v) {
        return putImpl(k, v, false, true);
    }

    @Override
    public TSequencedMap<K, V> reversed() {
        return new TReversedLinkedHashMap<>(this);
    }

    public static <K, V> TLinkedHashMap<K, V> newLinkedHashMap(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return new TLinkedHashMap<>(THashMap.capacity(size));
    }

    static <T> T checkNotNull(T node) {
        if (node == null) {
            throw new TNoSuchElementException();
        }
        return node;
    }
}
