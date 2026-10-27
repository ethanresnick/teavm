/*
 *  Copyright 2026 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

/**
 * Tests array(), arrayOffset() and hasArray() of every buffer type, on buffers derived by duplicate(), slice()
 * and asReadOnlyBuffer(), and bulk put(XBuffer) between buffers at different positions.
 */
@TeaVMTest
public class BufferArrayTest {
    private static final int SIZE = 12;
    private static final int[] POSITIONS = { 0, 1, 3 };
    private static final int[] WRAP_OFFSETS = { 0, 2 };
    private static final int MAX_DEPTH = 3;

    // Buffer.arrayOffset(): "If this buffer is backed by an array then buffer position p corresponds to array
    // index p + arrayOffset()."
    // ByteBuffer.duplicate(): "The new buffer's capacity, limit, position, and mark values will be identical to
    // those of this buffer"; ByteBuffer.slice(): "The new buffer's content will start at this buffer's current
    // position". So a duplicate keeps its parent's arrayOffset(), and a slice's arrayOffset() is its parent's
    // arrayOffset() plus its parent's position. The same text appears for every buffer type.
    @Test
    public void arrayOffsetMatchesContentAcrossDerivedBuffers() {
        for (Kind kind : kinds()) {
            for (int wrapOffset : WRAP_OFFSETS) {
                // wrap(array, offset, length): "Its position will be offset ... and its array offset will be
                // zero."
                Buffer base = kind.wrap(SIZE, wrapOffset, SIZE - wrapOffset);
                assertEquals(wrapOffset, base.position(), kind.name);
                assertEquals(0, base.arrayOffset(), kind.name);
                explore(kind, base, 0, kind.name + " wrap(" + wrapOffset + ")", 0);
            }
            Buffer allocated = kind.allocateFilled(SIZE);
            explore(kind, allocated, 0, kind.name + " allocate", 0);
        }
    }

    private static void explore(Kind kind, Buffer buffer, int expectedOffset, String path, int depth) {
        checkArrayMatchesContent(kind, buffer, expectedOffset, path);
        if (depth == MAX_DEPTH) {
            return;
        }
        for (int p : POSITIONS) {
            if (p > buffer.limit()) {
                continue;
            }
            int oldPosition = buffer.position();
            buffer.position(p);

            Buffer duplicate = kind.duplicate(buffer);
            assertEquals(p, duplicate.position(), path);
            explore(kind, duplicate, expectedOffset, path + ".position(" + p + ").duplicate()", depth + 1);

            Buffer slice = kind.slice(buffer);
            assertEquals(0, slice.position(), path);
            assertEquals(buffer.limit() - p, slice.capacity(), path);
            explore(kind, slice, expectedOffset + p, path + ".position(" + p + ").slice()", depth + 1);

            buffer.position(oldPosition);
        }
    }

    private static void checkArrayMatchesContent(Kind kind, Buffer buffer, int expectedOffset, String path) {
        assertTrue(buffer.hasArray(), path);
        assertEquals(expectedOffset, buffer.arrayOffset(), path);
        for (int i = 0; i < buffer.capacity(); ++i) {
            assertEquals(kind.get(buffer, i), kind.arrayElement(buffer, buffer.arrayOffset() + i),
                    path + " at " + i);
        }
    }

    // Buffer.hasArray(): "If this method returns true then the array and arrayOffset methods may safely be
    // invoked." Buffer.array() and Buffer.arrayOffset() throw "ReadOnlyBufferException - If this buffer is backed
    // by an array but is read-only". So a read-only buffer, even one backed by an array, reports false.
    // ByteBuffer.asReadOnlyBuffer(): "The new buffer's capacity, limit, position, and mark values will be
    // identical to those of this buffer", and a duplicate or slice of a read-only buffer is read-only too.
    @Test
    public void readOnlyBufferHasNoAccessibleArray() {
        for (Kind kind : kinds()) {
            checkNoAccessibleArray(kind.asReadOnly(kind.allocateFilled(SIZE)), kind.name + " allocate");
            for (int p : POSITIONS) {
                Buffer base = kind.wrap(SIZE, 0, SIZE);
                base.position(p);
                List<Buffer> readOnly = new ArrayList<>();
                readOnly.add(kind.asReadOnly(base));
                readOnly.add(kind.asReadOnly(kind.duplicate(base)));
                readOnly.add(kind.asReadOnly(kind.slice(base)));
                readOnly.add(kind.duplicate(kind.asReadOnly(base)));
                readOnly.add(kind.slice(kind.asReadOnly(base)));
                for (Buffer buffer : readOnly) {
                    checkNoAccessibleArray(buffer, kind.name + " at " + p);
                }
                // The writable buffer the views came from still exposes its array.
                assertTrue(base.hasArray(), kind.name);
                assertEquals(0, base.arrayOffset(), kind.name);
            }
        }
    }

    private static void checkNoAccessibleArray(Buffer buffer, String name) {
        assertTrue(buffer.isReadOnly(), name);
        assertFalse(buffer.hasArray(), name);
        assertThrows(ReadOnlyBufferException.class, buffer::array, name);
        assertThrows(ReadOnlyBufferException.class, buffer::arrayOffset, name);
    }

    // XBuffer.put(XBuffer src): "This method transfers the elements remaining in the given source buffer into
    // this buffer." Destination and source start at their own positions, and the source must not change.
    @Test
    public void bulkPutCopiesFromSourcePosition() {
        for (Kind kind : kinds()) {
            for (int sourceType = 0; sourceType < 4; ++sourceType) {
                for (int srcPos : POSITIONS) {
                    for (int dstPos : POSITIONS) {
                        Buffer src;
                        String srcName;
                        switch (sourceType) {
                            case 0:
                                src = kind.wrap(SIZE, 0, SIZE);
                                srcName = "heap";
                                break;
                            case 1:
                                src = kind.asReadOnly(kind.wrap(SIZE, 0, SIZE));
                                srcName = "read-only";
                                break;
                            case 2:
                                src = kind.slice(kind.wrap(SIZE + 2, 2, SIZE));
                                srcName = "slice";
                                break;
                            default:
                                src = kind.overBytes(SIZE);
                                srcName = "view over bytes";
                                break;
                        }
                        String name = kind.name + " from " + srcName + " at " + srcPos + " to " + dstPos;
                        long[] srcBefore = new long[src.capacity()];
                        for (int i = 0; i < srcBefore.length; ++i) {
                            srcBefore[i] = kind.get(src, i);
                        }
                        src.position(srcPos);
                        Buffer dst = kind.allocate(SIZE + dstPos);
                        dst.position(dstPos);
                        int count = src.remaining();

                        kind.put(dst, src);

                        assertEquals(dstPos + count, dst.position(), name);
                        assertEquals(src.limit(), src.position(), name);
                        for (int i = 0; i < dstPos; ++i) {
                            assertEquals(0, kind.get(dst, i), name + " before " + i);
                        }
                        for (int i = 0; i < count; ++i) {
                            assertEquals(srcBefore[srcPos + i], kind.get(dst, dstPos + i), name + " at " + i);
                        }
                        for (int i = 0; i < srcBefore.length; ++i) {
                            assertEquals(srcBefore[i], kind.get(src, i), name + " source at " + i);
                        }
                    }
                }
            }
        }
    }

    private static long value(int index) {
        return index + 1;
    }

    private static List<Kind> kinds() {
        List<Kind> kinds = new ArrayList<>();
        kinds.add(new Kind("ByteBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                byte[] array = new byte[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = (byte) value(i);
                }
                return ByteBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return ByteBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                // A ByteBuffer has no view over bytes; a direct buffer stands in for a buffer with no array.
                ByteBuffer result = ByteBuffer.allocateDirect(size);
                for (int i = 0; i < size; ++i) {
                    result.put(i, (byte) value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((ByteBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((ByteBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((ByteBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return ((ByteBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return ((ByteBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((ByteBuffer) dst).put((ByteBuffer) src);
            }
        });
        kinds.add(new Kind("CharBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                char[] array = new char[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = (char) value(i);
                }
                return CharBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return CharBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                CharBuffer result = ByteBuffer.allocate(size * 2).asCharBuffer();
                for (int i = 0; i < size; ++i) {
                    result.put(i, (char) value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((CharBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((CharBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((CharBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return ((CharBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return ((CharBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((CharBuffer) dst).put((CharBuffer) src);
            }
        });
        kinds.add(new Kind("ShortBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                short[] array = new short[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = (short) value(i);
                }
                return ShortBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return ShortBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                ShortBuffer result = ByteBuffer.allocate(size * 2).asShortBuffer();
                for (int i = 0; i < size; ++i) {
                    result.put(i, (short) value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((ShortBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((ShortBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((ShortBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return ((ShortBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return ((ShortBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((ShortBuffer) dst).put((ShortBuffer) src);
            }
        });
        kinds.add(new Kind("IntBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                int[] array = new int[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = (int) value(i);
                }
                return IntBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return IntBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                IntBuffer result = ByteBuffer.allocate(size * 4).asIntBuffer();
                for (int i = 0; i < size; ++i) {
                    result.put(i, (int) value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((IntBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((IntBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((IntBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return ((IntBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return ((IntBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((IntBuffer) dst).put((IntBuffer) src);
            }
        });
        kinds.add(new Kind("LongBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                long[] array = new long[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = value(i);
                }
                return LongBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return LongBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                LongBuffer result = ByteBuffer.allocate(size * 8).asLongBuffer();
                for (int i = 0; i < size; ++i) {
                    result.put(i, value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((LongBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((LongBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((LongBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return ((LongBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return ((LongBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((LongBuffer) dst).put((LongBuffer) src);
            }
        });
        kinds.add(new Kind("FloatBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                float[] array = new float[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = value(i);
                }
                return FloatBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return FloatBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                FloatBuffer result = ByteBuffer.allocate(size * 4).asFloatBuffer();
                for (int i = 0; i < size; ++i) {
                    result.put(i, value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((FloatBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((FloatBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((FloatBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return (long) ((FloatBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return (long) ((FloatBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((FloatBuffer) dst).put((FloatBuffer) src);
            }
        });
        kinds.add(new Kind("DoubleBuffer") {
            @Override
            Buffer wrap(int size, int offset, int length) {
                double[] array = new double[size];
                for (int i = 0; i < size; ++i) {
                    array[i] = value(i);
                }
                return DoubleBuffer.wrap(array, offset, length);
            }

            @Override
            Buffer allocate(int size) {
                return DoubleBuffer.allocate(size);
            }

            @Override
            Buffer overBytes(int size) {
                DoubleBuffer result = ByteBuffer.allocate(size * 8).asDoubleBuffer();
                for (int i = 0; i < size; ++i) {
                    result.put(i, value(i));
                }
                return result;
            }

            @Override
            Buffer duplicate(Buffer buffer) {
                return ((DoubleBuffer) buffer).duplicate();
            }

            @Override
            Buffer slice(Buffer buffer) {
                return ((DoubleBuffer) buffer).slice();
            }

            @Override
            Buffer asReadOnly(Buffer buffer) {
                return ((DoubleBuffer) buffer).asReadOnlyBuffer();
            }

            @Override
            long get(Buffer buffer, int index) {
                return (long) ((DoubleBuffer) buffer).get(index);
            }

            @Override
            long arrayElement(Buffer buffer, int index) {
                return (long) ((DoubleBuffer) buffer).array()[index];
            }

            @Override
            void put(Buffer dst, Buffer src) {
                ((DoubleBuffer) dst).put((DoubleBuffer) src);
            }
        });
        return kinds;
    }

    private abstract static class Kind {
        final String name;

        Kind(String name) {
            this.name = name;
        }

        // Wraps an array of the given size whose element i holds value(i).
        abstract Buffer wrap(int size, int offset, int length);

        abstract Buffer allocate(int size);

        Buffer allocateFilled(int size) {
            Buffer result = allocate(size);
            put(result, wrap(size, 0, size));
            result.clear();
            return result;
        }

        // A buffer with no accessible array whose element i holds value(i).
        abstract Buffer overBytes(int size);

        abstract Buffer duplicate(Buffer buffer);

        abstract Buffer slice(Buffer buffer);

        abstract Buffer asReadOnly(Buffer buffer);

        abstract long get(Buffer buffer, int index);

        abstract long arrayElement(Buffer buffer, int index);

        abstract void put(Buffer dst, Buffer src);
    }
}
