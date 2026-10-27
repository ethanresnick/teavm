/*
 *  Copyright 2023 ihromant.
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

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.teavm.classlib.impl.Base64Impl;

public final class TBase64 {
    private TBase64() {
    }

    public static Encoder getEncoder() {
        return new Encoder(Base64Impl.alphabet, true);
    }

    public static Encoder getUrlEncoder() {
        return new Encoder(Base64Impl.urlAlphabet, true);
    }

    public static Encoder getMimeEncoder() {
        return new Encoder(Base64Impl.alphabet, true, new byte[] { '\r', '\n' }, 76);
    }

    public static Encoder getMimeEncoder(int lineLength, byte[] lineSeparator) {
        for (var b : lineSeparator) {
            if (Base64Impl.reverse[b & 0xFF] >= 0 || b == '=') {
                throw new IllegalArgumentException();
            }
        }
        // As in the JDK, the line length rounds down to a multiple of 4, and a length under 4 means one line.
        lineLength &= ~3;
        if (lineLength <= 0) {
            return getEncoder();
        }
        return new Encoder(Base64Impl.alphabet, true, lineSeparator.clone(), lineLength);
    }

    public static class Encoder {
        private final byte[] mapping;
        private final boolean padding;
        private final byte[] lineSeparator;
        private final int lineLength;

        private Encoder(byte[] mapping, boolean padding) {
            this(mapping, padding, null, 0);
        }

        private Encoder(byte[] mapping, boolean padding, byte[] lineSeparator, int lineLength) {
            this.mapping = mapping;
            this.padding = padding;
            this.lineSeparator = lineSeparator;
            this.lineLength = lineLength;
        }

        public byte[] encode(byte[] src) {
            var bytes = Base64Impl.encode(src, mapping, padding);
            if (lineSeparator == null || bytes.length <= lineLength) {
                return bytes;
            }
            // A separator after every lineLength characters, but none after the last line.
            var lines = (bytes.length - 1) / lineLength;
            var result = new byte[bytes.length + lines * lineSeparator.length];
            var j = 0;
            for (var i = 0; i < bytes.length; i += lineLength) {
                if (i > 0) {
                    System.arraycopy(lineSeparator, 0, result, j, lineSeparator.length);
                    j += lineSeparator.length;
                }
                var count = Math.min(lineLength, bytes.length - i);
                System.arraycopy(bytes, i, result, j, count);
                j += count;
            }
            return result;
        }

        public int encode(byte[] src, byte[] dst) {
            var bytes = encode(src);
            if (dst.length < bytes.length) {
                throw new IllegalArgumentException();
            }
            System.arraycopy(bytes, 0, dst, 0, bytes.length);
            return bytes.length;
        }

        public ByteBuffer encode(ByteBuffer buffer) {
            var src = new byte[buffer.remaining()];
            buffer.get(src);
            return ByteBuffer.wrap(encode(src));
        }

        public String encodeToString(byte[] src) {
            var bytes = encode(src);
            var chars = new char[bytes.length];
            for (var i = 0; i < bytes.length; ++i) {
                chars[i] = (char) (bytes[i] & 0xFF);
            }
            return new String(chars);
        }

        public Encoder withoutPadding() {
            return new Encoder(mapping, false, lineSeparator, lineLength);
        }
    }

    public static Decoder getDecoder() {
        return new Decoder(Base64Impl.reverse);
    }

    public static Decoder getUrlDecoder() {
        return new Decoder(Base64Impl.urlReverse);
    }

    public static Decoder getMimeDecoder() {
        return new Decoder(Base64Impl.reverse, true);
    }

    public static class Decoder {
        private final int[] mapping;
        private final boolean mime;

        private Decoder(int[] mapping) {
            this(mapping, false);
        }

        private Decoder(int[] mapping, boolean mime) {
            this.mapping = mapping;
            this.mime = mime;
        }

        public byte[] decode(byte[] src) {
            return Base64Impl.decode(mime ? skipOutsideAlphabet(src) : src, mapping);
        }

        // As in the JDK's MIME decoder: characters outside the alphabet are skipped, and the padding that
        // completes the last unit ends the data ("==" must be adjacent). Only characters outside the alphabet,
        // '=' among them, may follow it.
        private byte[] skipOutsideAlphabet(byte[] src) {
            var result = new byte[src.length];
            var j = 0;
            var i = 0;
            while (i < src.length && src[i] != '=') {
                if (mapping[src[i] & 0xFF] >= 0) {
                    result[j++] = src[i];
                }
                ++i;
            }
            if (i == src.length) {
                return Arrays.copyOf(result, j);
            }
            if (j % 4 == 3) {
                result[j++] = '=';
                ++i;
            } else if (j % 4 == 2 && i + 1 < src.length && src[i + 1] == '=') {
                result[j++] = '=';
                result[j++] = '=';
                i += 2;
            } else {
                throw new IllegalArgumentException();
            }
            while (i < src.length) {
                if (mapping[src[i++] & 0xFF] >= 0) {
                    throw new IllegalArgumentException();
                }
            }
            return Arrays.copyOf(result, j);
        }

        public int decode(byte[] src, byte[] dst) {
            var bytes = decode(src);
            if (dst.length < bytes.length) {
                throw new IllegalArgumentException();
            }
            System.arraycopy(bytes, 0, dst, 0, bytes.length);
            return bytes.length;
        }

        public ByteBuffer decode(ByteBuffer buffer) {
            // As in the JDK, the source buffer's position does not move when the input is invalid.
            var position = buffer.position();
            var src = new byte[buffer.remaining()];
            buffer.get(src);
            try {
                return ByteBuffer.wrap(decode(src));
            } catch (IllegalArgumentException e) {
                buffer.position(position);
                throw e;
            }
        }

        public byte[] decode(String src) {
            var bytes = new byte[src.length()];
            for (var i = 0; i < bytes.length; ++i) {
                var c = src.charAt(i);
                if (c > 0xFF) {
                    // As in the JDK, which decodes src.getBytes(ISO_8859_1): such a character becomes '?', which
                    // only the MIME decoder accepts (and skips).
                    if (!mime) {
                        throw new IllegalArgumentException();
                    }
                    c = '?';
                }
                bytes[i] = (byte) c;
            }
            return decode(bytes);
        }
    }
}
