/*
 *  Copyright 2017 Alexey Andreev.
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
import java.util.Collection;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public class TSpliterators {

    private TSpliterators() {
    }

    public static <T> TSpliterator<T> spliterator(Object[] array, int additionalCharacteristics) {
        return new TSpliterator<T>() {
            private int index;

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (index >= array.length) {
                    return false;
                }

                @SuppressWarnings("unchecked")
                T e = (T) array[index++];
                action.accept(e);
                return true;
            }

            @Override
            public TSpliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return array.length - index;
            }

            @Override
            public int characteristics() {
                return additionalCharacteristics | TSpliterator.SIZED;
            }
        };
    }
    
    public static <T> TSpliterator<T> spliterator(Collection<? extends T> c, int characteristics) {
        return spliterator(c.iterator(), c.size(), characteristics);
    }

    public static TSpliterator.OfInt spliterator(int[] array, int additionalCharacteristics) {
        return spliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static TSpliterator.OfInt spliterator(int[] array, int fromIndex, int toIndex,
            int additionalCharacteristics) {
        return new TSpliterator.OfInt() {
            int index = fromIndex;

            @Override
            public TSpliterator.OfInt trySplit() {
                return null;
            }

            @Override
            public boolean tryAdvance(IntConsumer action) {
                if (index < toIndex) {
                    action.accept(array[index++]);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public long estimateSize() {
                return toIndex - index;
            }

            @Override
            public int characteristics() {
                return TSpliterator.SIZED;
            }
        };
    }

    public static <T> TSpliterator<T> spliterator(Iterator<? extends T> iterator, long size, int characteristics) {
        return new TSpliterator<T>() {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public TSpliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static <T> TSpliterator<T> spliteratorUnknownSize(Iterator<? extends T> iterator, int characteristics) {
        return new TSpliterator<T>() {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public TSpliterator<T> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfInt spliterator(PrimitiveIterator.OfInt iterator, long size, int characteristics) {
        return new TSpliterator.OfInt() {
            @Override
            public boolean tryAdvance(IntConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfInt trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfInt spliteratorUnknownSize(PrimitiveIterator.OfInt iterator, int characteristics) {
        return new TSpliterator.OfInt() {
            @Override
            public boolean tryAdvance(IntConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfInt trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfLong spliterator(PrimitiveIterator.OfLong iterator, long size, int characteristics) {
        return new TSpliterator.OfLong() {
            @Override
            public boolean tryAdvance(LongConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfLong trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfLong spliteratorUnknownSize(PrimitiveIterator.OfLong iterator, int characteristics) {
        return new TSpliterator.OfLong() {
            @Override
            public boolean tryAdvance(LongConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfLong trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfDouble spliterator(PrimitiveIterator.OfDouble iterator, long size,
            int characteristics) {
        return new TSpliterator.OfDouble() {
            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfDouble trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return size;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public static TSpliterator.OfDouble spliteratorUnknownSize(PrimitiveIterator.OfDouble iterator,
            int characteristics) {
        return new TSpliterator.OfDouble() {
            @Override
            public boolean tryAdvance(DoubleConsumer action) {
                if (iterator.hasNext()) {
                    action.accept(iterator.next());
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public OfDouble trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    /**
     * kafka-wasm: the JDK's base class for a spliterator that defines only tryAdvance (log4j's ServiceLoaderUtil
     * extends it). As in the JDK, trySplit copies a batch of elements into an array; each batch is 1024 elements
     * larger than the last, up to 2^25.
     */
    public abstract static class AbstractSpliterator<T> implements TSpliterator<T> {
        static final int BATCH_UNIT = 1 << 10;
        static final int MAX_BATCH = 1 << 25;
        private final int characteristics;
        private long est;
        private int batch;

        protected AbstractSpliterator(long est, int additionalCharacteristics) {
            this.est = est;
            this.characteristics = (additionalCharacteristics & TSpliterator.SIZED) != 0
                    ? additionalCharacteristics | TSpliterator.SUBSIZED
                    : additionalCharacteristics;
        }

        @Override
        public TSpliterator<T> trySplit() {
            Object[] holder = new Object[1];
            long s = est;
            if (s > 1 && tryAdvance(value -> holder[0] = value)) {
                int n = batch + BATCH_UNIT;
                if (n > s) {
                    n = (int) s;
                }
                if (n > MAX_BATCH) {
                    n = MAX_BATCH;
                }
                Object[] a = new Object[n];
                int j = 0;
                do {
                    a[j] = holder[0];
                } while (++j < n && tryAdvance(value -> holder[0] = value));
                batch = j;
                if (est != Long.MAX_VALUE) {
                    est -= j;
                }
                return spliterator(Arrays.copyOf(a, j), characteristics() | TSpliterator.SUBSIZED);
            }
            return null;
        }

        @Override
        public long estimateSize() {
            return est;
        }

        @Override
        public int characteristics() {
            return characteristics;
        }
    }
}
