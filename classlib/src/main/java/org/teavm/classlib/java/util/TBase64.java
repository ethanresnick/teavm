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

    public static class Encoder {
        private final byte[] mapping;
        private final boolean padding;

        private Encoder(byte[] mapping, boolean padding) {
            this.mapping = mapping;
            this.padding = padding;
        }

        public byte[] encode(byte[] src) {
            return Base64Impl.encode(src, mapping, padding);
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
            return new Encoder(mapping, false);
        }
    }

    public static Decoder getDecoder() {
        return new Decoder(Base64Impl.reverse);
    }

    public static Decoder getUrlDecoder() {
        return new Decoder(Base64Impl.urlReverse);
    }

    public static class Decoder {
        private final int[] mapping;

        private Decoder(int[] mapping) {
            this.mapping = mapping;
        }

        public byte[] decode(byte[] src) {
            return Base64Impl.decode(src, mapping);
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
                    throw new IllegalArgumentException();
                }
                bytes[i] = (byte) c;
            }
            return decode(bytes);
        }
    }
}
