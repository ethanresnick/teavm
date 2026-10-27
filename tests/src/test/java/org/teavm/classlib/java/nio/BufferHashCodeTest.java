/*
 *  Copyright 2026 TeaVM contributors.
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
package org.teavm.classlib.java.nio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * ByteBuffer.hashCode Javadoc (the other buffer types say the same): "The hash code of a byte buffer
 * depends only upon its remaining elements; that is, upon the elements from position() up to, and
 * including, the element at limit() - 1."
 *
 * <p>The Javadoc does not give the exact formula. The JDK computes {@code h = 1}, then
 * {@code h = 31 * h + (int) get(i)} for {@code i} from {@code limit() - 1} down to {@code position()}.
 * {@link #expectedHash} is that formula. This class also runs on the JVM, which checks the formula
 * against the JDK.
 */
@TeaVMTest
public class BufferHashCodeTest {
    private static final ByteOrder[] ORDERS = { ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN };

    @Test
    public void matchesJdkValues() {
        // Computed on the JVM (JDK 25).
        assertEquals(1, ByteBuffer.allocate(0).hashCode());
        assertEquals(1024, ByteBuffer.wrap(new byte[] { 1, 2 }).hashCode());
        assertEquals(28893, ByteBuffer.wrap(new byte[] { 1, 2, -1 }).hashCode());
        assertEquals(4096, CharBuffer.wrap("ab").hashCode());
        assertEquals(1024, ShortBuffer.wrap(new short[] { 1, 2 }).hashCode());
        assertEquals(1024, IntBuffer.wrap(new int[] { 1, 2 }).hashCode());
        assertEquals(36, LongBuffer.wrap(new long[] { (1L << 40) | 5 }).hashCode());
        assertEquals(1023, FloatBuffer.wrap(new float[] { 0.5f, 2.7f }).hashCode());
        assertEquals(31, FloatBuffer.wrap(new float[] { -0.0f }).hashCode());
        assertEquals(31, FloatBuffer.wrap(new float[] { Float.NaN }).hashCode());
        assertEquals(-2147483618, FloatBuffer.wrap(new float[] { 1e20f }).hashCode());
        assertEquals(-2147482715, DoubleBuffer.wrap(new double[] { 3.9, 1e30 }).hashCode());
    }

    @Test
    public void dependsOnlyOnRemainingElements() {
        ByteBuffer a = ByteBuffer.wrap(new byte[] { 9, 1, 2, 3, 8 });
        a.position(1).limit(4);
        ByteBuffer b = ByteBuffer.wrap(new byte[] { 1, 2, 3 });
        assertEquals(a, b);
        assertEquals(b.hashCode(), a.hashCode());
        assertNotEquals(ByteBuffer.wrap(new byte[] { 1, 2, 4 }).hashCode(), b.hashCode());
        assertNotEquals(ByteBuffer.wrap(new byte[] { 1, 3, 3 }).hashCode(), b.hashCode());
        assertNotEquals(ByteBuffer.wrap(new byte[] { 2, 2, 3 }).hashCode(), b.hashCode());

        // hashCode must not move the position
        assertEquals(1, a.position());
        assertEquals(4, a.limit());
    }

    @Test
    public void equalFloatBuffersHaveEqualHashCodes() {
        // FloatBuffer.equals: "Two float elements are considered to be equal if
        // (a == b) || (Float.isNaN(a) && Float.isNaN(b))", so 0.0 and -0.0 are equal elements.
        FloatBuffer pos = FloatBuffer.wrap(new float[] { 0.0f, 1.0f });
        FloatBuffer neg = FloatBuffer.wrap(new float[] { -0.0f, 1.0f });
        assertEquals(pos, neg);
        assertEquals(pos.hashCode(), neg.hashCode());
        DoubleBuffer dpos = DoubleBuffer.wrap(new double[] { 0.0, 1.0 });
        DoubleBuffer dneg = DoubleBuffer.wrap(new double[] { -0.0, 1.0 });
        assertEquals(dpos, dneg);
        assertEquals(dpos.hashCode(), dneg.hashCode());
    }

    @Test
    public void byteBuffer() {
        check(new Kind<ByteBuffer>() {
            @Override
            long random(Random random) {
                return (byte) random.nextInt();
            }

            @Override
            List<ByteBuffer> variants(long[] raw) {
                byte[] data = new byte[raw.length];
                for (int i = 0; i < raw.length; ++i) {
                    data[i] = (byte) raw[i];
                }
                byte[] padded = new byte[data.length + 5];
                padded[0] = 55;
                padded[padded.length - 1] = 66;
                System.arraycopy(data, 0, padded, 3, data.length);
                List<ByteBuffer> result = new ArrayList<>();
                result.add(ByteBuffer.wrap(data.clone()));
                result.add(ByteBuffer.wrap(padded).position(3).slice());
                result.add(ByteBuffer.wrap(data.clone()).asReadOnlyBuffer());
                result.add(ByteBuffer.wrap(data.clone()).duplicate());
                ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
                direct.put(data).clear();
                result.add(direct);
                result.add(direct.asReadOnlyBuffer());
                return result;
            }

            @Override
            int elementAsInt(ByteBuffer buffer, int index) {
                return buffer.get(index);
            }
        });
    }

    @Test
    public void charBuffer() {
        check(new Kind<CharBuffer>() {
            @Override
            long random(Random random) {
                return (char) random.nextInt();
            }

            @Override
            List<CharBuffer> variants(long[] raw) {
                char[] data = new char[raw.length];
                for (int i = 0; i < raw.length; ++i) {
                    data[i] = (char) raw[i];
                }
                List<CharBuffer> result = new ArrayList<>();
                result.add(CharBuffer.wrap(data.clone()));
                result.add(CharBuffer.wrap(padded(data)).position(3).slice());
                result.add(CharBuffer.wrap(data.clone()).asReadOnlyBuffer());
                result.add(CharBuffer.wrap(new String(data)));
                addViews(result, data.length * 2, ByteBuffer::asCharBuffer, b -> b.put(data));
                return result;
            }

            private char[] padded(char[] data) {
                char[] padded = new char[data.length + 5];
                padded[0] = 'x';
                padded[padded.length - 1] = 'y';
                System.arraycopy(data, 0, padded, 3, data.length);
                return padded;
            }

            @Override
            int elementAsInt(CharBuffer buffer, int index) {
                return buffer.get(index);
            }
        });
    }

    @Test
    public void shortBuffer() {
        check(new Kind<ShortBuffer>() {
            @Override
            long random(Random random) {
                return (short) random.nextInt();
            }

            @Override
            List<ShortBuffer> variants(long[] raw) {
                short[] data = new short[raw.length];
                short[] padded = new short[raw.length + 5];
                padded[0] = 77;
                padded[padded.length - 1] = 88;
                for (int i = 0; i < raw.length; ++i) {
                    data[i] = (short) raw[i];
                    padded[i + 3] = data[i];
                }
                List<ShortBuffer> result = new ArrayList<>();
                result.add(ShortBuffer.wrap(data.clone()));
                result.add(ShortBuffer.wrap(padded).position(3).slice());
                result.add(ShortBuffer.wrap(data.clone()).asReadOnlyBuffer());
                addViews(result, data.length * 2, ByteBuffer::asShortBuffer, b -> b.put(data));
                return result;
            }

            @Override
            int elementAsInt(ShortBuffer buffer, int index) {
                return buffer.get(index);
            }
        });
    }

    @Test
    public void intBuffer() {
        check(new Kind<IntBuffer>() {
            @Override
            long random(Random random) {
                return random.nextInt();
            }

            @Override
            List<IntBuffer> variants(long[] raw) {
                int[] data = new int[raw.length];
                int[] padded = new int[raw.length + 5];
                padded[0] = 77;
                padded[padded.length - 1] = 88;
                for (int i = 0; i < raw.length; ++i) {
                    data[i] = (int) raw[i];
                    padded[i + 3] = data[i];
                }
                List<IntBuffer> result = new ArrayList<>();
                result.add(IntBuffer.wrap(data.clone()));
                result.add(IntBuffer.wrap(padded).position(3).slice());
                result.add(IntBuffer.wrap(data.clone()).asReadOnlyBuffer());
                addViews(result, data.length * 4, ByteBuffer::asIntBuffer, b -> b.put(data));
                return result;
            }

            @Override
            int elementAsInt(IntBuffer buffer, int index) {
                return buffer.get(index);
            }
        });
    }

    @Test
    public void longBuffer() {
        check(new Kind<LongBuffer>() {
            @Override
            long random(Random random) {
                return random.nextLong();
            }

            @Override
            List<LongBuffer> variants(long[] raw) {
                long[] data = raw.clone();
                long[] padded = new long[raw.length + 5];
                padded[0] = 77;
                padded[padded.length - 1] = 88;
                System.arraycopy(data, 0, padded, 3, data.length);
                List<LongBuffer> result = new ArrayList<>();
                result.add(LongBuffer.wrap(data.clone()));
                result.add(LongBuffer.wrap(padded).position(3).slice());
                result.add(LongBuffer.wrap(data.clone()).asReadOnlyBuffer());
                addViews(result, data.length * 8, ByteBuffer::asLongBuffer, b -> b.put(data));
                return result;
            }

            @Override
            int elementAsInt(LongBuffer buffer, int index) {
                return (int) buffer.get(index);
            }
        });
    }

    @Test
    public void floatBuffer() {
        check(new Kind<FloatBuffer>() {
            @Override
            long random(Random random) {
                return Float.floatToIntBits((float) (random.nextGaussian() * 10000));
            }

            @Override
            long bump(long raw) {
                return Float.floatToIntBits(Float.intBitsToFloat((int) raw) + 2);
            }

            @Override
            List<FloatBuffer> variants(long[] raw) {
                float[] data = new float[raw.length];
                float[] padded = new float[raw.length + 5];
                padded[0] = 77;
                padded[padded.length - 1] = 88;
                for (int i = 0; i < raw.length; ++i) {
                    data[i] = Float.intBitsToFloat((int) raw[i]);
                    padded[i + 3] = data[i];
                }
                List<FloatBuffer> result = new ArrayList<>();
                result.add(FloatBuffer.wrap(data.clone()));
                result.add(FloatBuffer.wrap(padded).position(3).slice());
                result.add(FloatBuffer.wrap(data.clone()).asReadOnlyBuffer());
                addViews(result, data.length * 4, ByteBuffer::asFloatBuffer, b -> b.put(data));
                return result;
            }

            @Override
            int elementAsInt(FloatBuffer buffer, int index) {
                return (int) buffer.get(index);
            }
        });
    }

    @Test
    public void doubleBuffer() {
        check(new Kind<DoubleBuffer>() {
            @Override
            long random(Random random) {
                return Double.doubleToLongBits(random.nextGaussian() * 10000);
            }

            @Override
            long bump(long raw) {
                return Double.doubleToLongBits(Double.longBitsToDouble(raw) + 2);
            }

            @Override
            List<DoubleBuffer> variants(long[] raw) {
                double[] data = new double[raw.length];
                double[] padded = new double[raw.length + 5];
                padded[0] = 77;
                padded[padded.length - 1] = 88;
                for (int i = 0; i < raw.length; ++i) {
                    data[i] = Double.longBitsToDouble(raw[i]);
                    padded[i + 3] = data[i];
                }
                List<DoubleBuffer> result = new ArrayList<>();
                result.add(DoubleBuffer.wrap(data.clone()));
                result.add(DoubleBuffer.wrap(padded).position(3).slice());
                result.add(DoubleBuffer.wrap(data.clone()).asReadOnlyBuffer());
                addViews(result, data.length * 8, ByteBuffer::asDoubleBuffer, b -> b.put(data));
                return result;
            }

            @Override
            int elementAsInt(DoubleBuffer buffer, int index) {
                return (int) buffer.get(index);
            }
        });
    }

    /**
     * Adds views of heap and direct byte buffers in both byte orders, plus read-only views.
     */
    private static <B extends Buffer> void addViews(List<B> result, int byteSize,
            Function<ByteBuffer, B> view, Function<B, ?> fill) {
        for (ByteOrder order : ORDERS) {
            for (boolean direct : new boolean[] { false, true }) {
                ByteBuffer bytes = direct ? ByteBuffer.allocateDirect(byteSize) : ByteBuffer.allocate(byteSize);
                bytes.order(order);
                B buffer = view.apply(bytes);
                fill.apply(buffer);
                buffer.clear();
                result.add(buffer);
                B readOnly = view.apply(bytes.asReadOnlyBuffer().order(order));
                result.add(readOnly);
            }
        }
    }

    private static int expectedHash(int[] elements, int position, int limit) {
        int h = 1;
        for (int i = limit - 1; i >= position; --i) {
            h = 31 * h + elements[i];
        }
        return h;
    }

    private abstract static class Kind<B extends Buffer> {
        abstract long random(Random random);

        /** Returns an element whose {@code (int)} cast differs from the given one. */
        long bump(long raw) {
            return raw + 2;
        }

        /** Buffers of every implementation, each holding {@code raw} with position 0 and limit raw.length. */
        abstract List<B> variants(long[] raw);

        abstract int elementAsInt(B buffer, int index);
    }

    private static <B extends Buffer> void check(Kind<B> kind) {
        Random random = new Random(1234);
        for (int iteration = 0; iteration < 40; ++iteration) {
            int size = iteration < 4 ? iteration : 4 + random.nextInt(30);
            long[] raw = new long[size];
            for (int i = 0; i < size; ++i) {
                raw[i] = kind.random(random);
            }
            int position = random.nextInt(size + 1);
            int limit = position + random.nextInt(size - position + 1);

            List<B> variants = kind.variants(raw);
            int[] elements = new int[size];
            for (int i = 0; i < size; ++i) {
                elements[i] = kind.elementAsInt(variants.get(0), i);
            }
            int expected = expectedHash(elements, position, limit);
            for (int v = 0; v < variants.size(); ++v) {
                B buffer = variants.get(v);
                buffer.limit(limit).position(position);
                assertEquals(expected, buffer.hashCode(), "variant " + v + ", size " + size
                        + ", position " + position + ", limit " + limit);
                assertEquals(position, buffer.position());
            }

            // Changing any one remaining element must change the hash code. The (int) values differ by
            // a small non-zero amount d, so the hash codes differ by 31^k * d, which is never 0 mod 2^32.
            for (int k = position; k < limit; ++k) {
                long[] changed = raw.clone();
                changed[k] = kind.bump(raw[k]);
                List<B> changedVariants = kind.variants(changed);
                for (int v = 0; v < changedVariants.size(); ++v) {
                    B buffer = changedVariants.get(v);
                    buffer.limit(limit).position(position);
                    assertNotEquals(expected, buffer.hashCode(), "variant " + v + ", changed index " + k
                            + ", position " + position + ", limit " + limit);
                }
            }

            // Elements outside [position, limit) must not affect the hash code.
            for (int k = 0; k < size; ++k) {
                if (k >= position && k < limit) {
                    continue;
                }
                long[] changed = raw.clone();
                changed[k] = kind.bump(raw[k]);
                for (B buffer : kind.variants(changed)) {
                    buffer.limit(limit).position(position);
                    assertEquals(expected, buffer.hashCode());
                }
            }
        }

        // Random buffers of the same length should practically never collide.
        Set<Integer> sameLength = new HashSet<>();
        int count = 200;
        for (int i = 0; i < count; ++i) {
            long[] raw = new long[8];
            for (int j = 0; j < raw.length; ++j) {
                raw[j] = kind.random(random);
            }
            sameLength.add(kind.variants(raw).get(0).hashCode());
        }
        assertTrue(sameLength.size() >= count - 2, "distinct hash codes: " + sameLength.size());
    }
}
