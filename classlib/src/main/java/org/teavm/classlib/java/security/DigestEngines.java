/*
 *  Copyright 2026 Ethan Resnick.
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
package org.teavm.classlib.java.security;

import java.util.Arrays;

final class DigestEngines {
    private DigestEngines() {
    }

    // Returns a new engine for a canonical name ("MD5", "SHA-1", "SHA-224", "SHA-256",
    // "SHA-384", "SHA-512", "SHA-512/224", "SHA-512/256"), or null if no engine exists.
    static DigestEngine create(String canonicalName) {
        switch (canonicalName) {
            case "MD5":
                return new Md5();
            case "SHA-1":
                return new Sha1();
            case "SHA-224":
                return new Sha256(true);
            case "SHA-256":
                return new Sha256(false);
            case "SHA-384":
                return new Sha512(Sha512.IV_384, 48);
            case "SHA-512":
                return new Sha512(Sha512.IV_512, 64);
            case "SHA-512/224":
                return new Sha512(Sha512.IV_512_224, 28);
            case "SHA-512/256":
                return new Sha512(Sha512.IV_512_256, 32);
            default:
                return null;
        }
    }

    /** Shared 64-byte-block buffering and Merkle-Damgard padding. */
    private abstract static class BlockDigest implements DigestEngine {
        private final byte[] block = new byte[64];
        private int blockLen;
        private long totalBytes;
        private final boolean bigEndianLength;

        BlockDigest(boolean bigEndianLength) {
            this.bigEndianLength = bigEndianLength;
            // Subclasses call initState() in their own constructors: their state arrays do not exist yet here.
        }

        abstract void processBlock(byte[] b, int off);

        abstract void initState();

        abstract byte[] output();

        @Override
        public void update(byte[] input, int offset, int len) {
            totalBytes += len;
            while (len > 0) {
                int n = Math.min(64 - blockLen, len);
                System.arraycopy(input, offset, block, blockLen, n);
                blockLen += n;
                offset += n;
                len -= n;
                if (blockLen == 64) {
                    processBlock(block, 0);
                    blockLen = 0;
                }
            }
        }

        private byte[] finish() {
            long bits = totalBytes * 8;
            byte[] pad = new byte[((blockLen < 56) ? 56 : 120) - blockLen + 8];
            pad[0] = (byte) 0x80;
            for (int i = 0; i < 8; i++) {
                int shift = bigEndianLength ? 56 - 8 * i : 8 * i;
                pad[pad.length - 8 + i] = (byte) (bits >>> shift);
            }
            long savedTotal = totalBytes;
            update(pad, 0, pad.length);
            totalBytes = savedTotal;
            return output();
        }

        @Override
        public void update(byte input) {
            update(new byte[] { input }, 0, 1);
        }

        @Override
        public void digest(byte[] output, int offset) {
            byte[] out = finish();
            System.arraycopy(out, 0, output, offset, out.length);
            reset();
        }

        // Copies the buffered input and the length into other, which has the same algorithm.
        void copyStateTo(BlockDigest other) {
            System.arraycopy(block, 0, other.block, 0, block.length);
            other.blockLen = blockLen;
            other.totalBytes = totalBytes;
        }

        @Override
        public void reset() {
            blockLen = 0;
            totalBytes = 0;
            initState();
        }
    }

    private static final class Md5 extends BlockDigest {
        private static final int[] S = { 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5,
            9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10,
            15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21 };
        private static final int[] K = new int[64];

        static {
            for (int i = 0; i < 64; i++) {
                K[i] = (int) (long) Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296.0);
            }
        }

        private final int[] h = new int[4];
        private final int[] m = new int[16];

        Md5() {
            super(false);
            initState();
        }

        @Override
        void initState() {
            h[0] = 0x67452301;
            h[1] = 0xefcdab89;
            h[2] = 0x98badcfe;
            h[3] = 0x10325476;
        }

        @Override
        void processBlock(byte[] b, int off) {
            for (int i = 0; i < 16; i++) {
                int j = off + 4 * i;
                m[i] = (b[j] & 0xff) | (b[j + 1] & 0xff) << 8 | (b[j + 2] & 0xff) << 16 | (b[j + 3] & 0xff) << 24;
            }
            int a = h[0];
            int bb = h[1];
            int c = h[2];
            int d = h[3];
            for (int i = 0; i < 64; i++) {
                int f;
                int g;
                if (i < 16) {
                    f = (bb & c) | (~bb & d);
                    g = i;
                } else if (i < 32) {
                    f = (d & bb) | (~d & c);
                    g = (5 * i + 1) & 15;
                } else if (i < 48) {
                    f = bb ^ c ^ d;
                    g = (3 * i + 5) & 15;
                } else {
                    f = c ^ (bb | ~d);
                    g = (7 * i) & 15;
                }
                int t = d;
                d = c;
                c = bb;
                bb = bb + Integer.rotateLeft(a + f + K[i] + m[g], S[i]);
                a = t;
            }
            h[0] += a;
            h[1] += bb;
            h[2] += c;
            h[3] += d;
        }

        @Override
        byte[] output() {
            byte[] out = new byte[16];
            for (int i = 0; i < 16; i++) {
                out[i] = (byte) (h[i / 4] >>> (8 * (i % 4)));
            }
            return out;
        }

        @Override
        public DigestEngine copy() {
            Md5 copy = new Md5();
            copyStateTo(copy);
            System.arraycopy(h, 0, copy.h, 0, h.length);
            return copy;
        }

        @Override
        public int length() {
            return 16;
        }
    }

    private static final class Sha1 extends BlockDigest {
        private final int[] h = new int[5];
        private final int[] w = new int[80];

        Sha1() {
            super(true);
            initState();
        }

        @Override
        void initState() {
            h[0] = 0x67452301;
            h[1] = 0xEFCDAB89;
            h[2] = 0x98BADCFE;
            h[3] = 0x10325476;
            h[4] = 0xC3D2E1F0;
        }

        @Override
        void processBlock(byte[] b, int off) {
            for (int i = 0; i < 16; i++) {
                int j = off + 4 * i;
                w[i] = (b[j] & 0xff) << 24 | (b[j + 1] & 0xff) << 16 | (b[j + 2] & 0xff) << 8 | (b[j + 3] & 0xff);
            }
            for (int i = 16; i < 80; i++) {
                w[i] = Integer.rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
            }
            int a = h[0];
            int bb = h[1];
            int c = h[2];
            int d = h[3];
            int e = h[4];
            for (int i = 0; i < 80; i++) {
                int f;
                int k;
                if (i < 20) {
                    f = (bb & c) | (~bb & d);
                    k = 0x5A827999;
                } else if (i < 40) {
                    f = bb ^ c ^ d;
                    k = 0x6ED9EBA1;
                } else if (i < 60) {
                    f = (bb & c) | (bb & d) | (c & d);
                    k = 0x8F1BBCDC;
                } else {
                    f = bb ^ c ^ d;
                    k = 0xCA62C1D6;
                }
                int t = Integer.rotateLeft(a, 5) + f + e + k + w[i];
                e = d;
                d = c;
                c = Integer.rotateLeft(bb, 30);
                bb = a;
                a = t;
            }
            h[0] += a;
            h[1] += bb;
            h[2] += c;
            h[3] += d;
            h[4] += e;
        }

        @Override
        byte[] output() {
            return bigEndian(h);
        }

        @Override
        public DigestEngine copy() {
            Sha1 copy = new Sha1();
            copyStateTo(copy);
            System.arraycopy(h, 0, copy.h, 0, h.length);
            return copy;
        }

        @Override
        public int length() {
            return 20;
        }
    }

    private static final class Sha256 extends BlockDigest {
        private static final int[] K = { 0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
            0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe,
            0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa,
            0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
            0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb,
            0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624,
            0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
            0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb,
            0xbef9a3f7, 0xc67178f2 };
        private final int[] h = new int[8];
        private final int[] w = new int[64];
        private final boolean sha224;

        Sha256(boolean sha224) {
            super(true);
            this.sha224 = sha224;
            initState();
        }

        @Override
        void initState() {
            if (sha224) {
                h[0] = 0xc1059ed8;
                h[1] = 0x367cd507;
                h[2] = 0x3070dd17;
                h[3] = 0xf70e5939;
                h[4] = 0xffc00b31;
                h[5] = 0x68581511;
                h[6] = 0x64f98fa7;
                h[7] = 0xbefa4fa4;
                return;
            }
            h[0] = 0x6a09e667;
            h[1] = 0xbb67ae85;
            h[2] = 0x3c6ef372;
            h[3] = 0xa54ff53a;
            h[4] = 0x510e527f;
            h[5] = 0x9b05688c;
            h[6] = 0x1f83d9ab;
            h[7] = 0x5be0cd19;
        }

        @Override
        void processBlock(byte[] b, int off) {
            for (int i = 0; i < 16; i++) {
                int j = off + 4 * i;
                w[i] = (b[j] & 0xff) << 24 | (b[j + 1] & 0xff) << 16 | (b[j + 2] & 0xff) << 8 | (b[j + 3] & 0xff);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            int a = h[0];
            int bb = h[1];
            int c = h[2];
            int d = h[3];
            int e = h[4];
            int f = h[5];
            int g = h[6];
            int hh = h[7];
            for (int i = 0; i < 64; i++) {
                int s1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
                int ch = (e & f) ^ (~e & g);
                int t1 = hh + s1 + ch + K[i] + w[i];
                int s0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
                int maj = (a & bb) ^ (a & c) ^ (bb & c);
                int t2 = s0 + maj;
                hh = g;
                g = f;
                f = e;
                e = d + t1;
                d = c;
                c = bb;
                bb = a;
                a = t1 + t2;
            }
            h[0] += a;
            h[1] += bb;
            h[2] += c;
            h[3] += d;
            h[4] += e;
            h[5] += f;
            h[6] += g;
            h[7] += hh;
        }

        @Override
        byte[] output() {
            byte[] out = bigEndian(h);
            return sha224 ? Arrays.copyOf(out, 28) : out;
        }

        @Override
        public DigestEngine copy() {
            Sha256 copy = new Sha256(sha224);
            copyStateTo(copy);
            System.arraycopy(h, 0, copy.h, 0, h.length);
            return copy;
        }

        @Override
        public int length() {
            return sha224 ? 28 : 32;
        }
    }

    /** SHA-512 and its truncated variants (FIPS 180-4): 128-byte blocks, 64-bit words, 128-bit length. */
    private static final class Sha512 implements DigestEngine {
        static final long[] IV_512 = { 0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL,
            0xa54ff53a5f1d36f1L, 0x510e527fade682d1L, 0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL,
            0x5be0cd19137e2179L };
        static final long[] IV_384 = { 0xcbbb9d5dc1059ed8L, 0x629a292a367cd507L, 0x9159015a3070dd17L,
            0x152fecd8f70e5939L, 0x67332667ffc00b31L, 0x8eb44a8768581511L, 0xdb0c2e0d64f98fa7L,
            0x47b5481dbefa4fa4L };
        static final long[] IV_512_224 = { 0x8C3D37C819544DA2L, 0x73E1996689DCD4D6L, 0x1DFAB7AE32FF9C82L,
            0x679DD514582F9FCFL, 0x0F6D2B697BD44DA8L, 0x77E36F7304C48942L, 0x3F9D85A86A1D36C8L,
            0x1112E6AD91D692A1L };
        static final long[] IV_512_256 = { 0x22312194FC2BF72CL, 0x9F555FA3C84C64C2L, 0x2393B86B6F53B151L,
            0x963877195940EABDL, 0x96283EE2A88EFFE3L, 0xBE5E1E2553863992L, 0x2B0199FC2C85B8AAL,
            0x0EB72DDC81C52CA2L };
        private static final long[] K = { 0x428a2f98d728ae22L, 0x7137449123ef65cdL, 0xb5c0fbcfec4d3b2fL,
            0xe9b5dba58189dbbcL, 0x3956c25bf348b538L, 0x59f111f1b605d019L, 0x923f82a4af194f9bL,
            0xab1c5ed5da6d8118L, 0xd807aa98a3030242L, 0x12835b0145706fbeL, 0x243185be4ee4b28cL,
            0x550c7dc3d5ffb4e2L, 0x72be5d74f27b896fL, 0x80deb1fe3b1696b1L, 0x9bdc06a725c71235L,
            0xc19bf174cf692694L, 0xe49b69c19ef14ad2L, 0xefbe4786384f25e3L, 0x0fc19dc68b8cd5b5L,
            0x240ca1cc77ac9c65L, 0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L, 0x5cb0a9dcbd41fbd4L,
            0x76f988da831153b5L, 0x983e5152ee66dfabL, 0xa831c66d2db43210L, 0xb00327c898fb213fL,
            0xbf597fc7beef0ee4L, 0xc6e00bf33da88fc2L, 0xd5a79147930aa725L, 0x06ca6351e003826fL,
            0x142929670a0e6e70L, 0x27b70a8546d22ffcL, 0x2e1b21385c26c926L, 0x4d2c6dfc5ac42aedL,
            0x53380d139d95b3dfL, 0x650a73548baf63deL, 0x766a0abb3c77b2a8L, 0x81c2c92e47edaee6L,
            0x92722c851482353bL, 0xa2bfe8a14cf10364L, 0xa81a664bbc423001L, 0xc24b8b70d0f89791L,
            0xc76c51a30654be30L, 0xd192e819d6ef5218L, 0xd69906245565a910L, 0xf40e35855771202aL,
            0x106aa07032bbd1b8L, 0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L, 0x2748774cdf8eeb99L,
            0x34b0bcb5e19b48a8L, 0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL, 0x5b9cca4f7763e373L,
            0x682e6ff3d6b2b8a3L, 0x748f82ee5defb2fcL, 0x78a5636f43172f60L, 0x84c87814a1f0ab72L,
            0x8cc702081a6439ecL, 0x90befffa23631e28L, 0xa4506cebde82bde9L, 0xbef9a3f7b2c67915L,
            0xc67178f2e372532bL, 0xca273eceea26619cL, 0xd186b8c721c0c207L, 0xeada7dd6cde0eb1eL,
            0xf57d4f7fee6ed178L, 0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L, 0x113f9804bef90daeL,
            0x1b710b35131c471bL, 0x28db77f523047d84L, 0x32caab7b40c72493L, 0x3c9ebe0a15c9bebcL,
            0x431d67c49c100d4cL, 0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL, 0x5fcb6fab3ad6faecL,
            0x6c44198c4a475817L };

        private final long[] iv;
        private final int length;
        private final long[] h = new long[8];
        private final long[] w = new long[80];
        private final byte[] block = new byte[128];
        private int blockLen;
        private long totalBytes;

        Sha512(long[] iv, int length) {
            this.iv = iv;
            this.length = length;
            reset();
        }

        @Override
        public void update(byte[] input, int offset, int len) {
            totalBytes += len;
            while (len > 0) {
                int n = Math.min(128 - blockLen, len);
                System.arraycopy(input, offset, block, blockLen, n);
                blockLen += n;
                offset += n;
                len -= n;
                if (blockLen == 128) {
                    processBlock();
                    blockLen = 0;
                }
            }
        }

        private byte[] finish() {
            long bits = totalBytes * 8;
            // The length field is 128 bits. Messages here are shorter than 2^61 bytes, so the high 64 bits
            // stay zero and the high bits of totalBytes go into the low byte of that half.
            byte[] pad = new byte[((blockLen < 112) ? 112 : 240) - blockLen + 16];
            pad[0] = (byte) 0x80;
            pad[pad.length - 9] = (byte) (totalBytes >>> 61);
            for (int i = 0; i < 8; i++) {
                pad[pad.length - 8 + i] = (byte) (bits >>> (56 - 8 * i));
            }
            update(pad, 0, pad.length);
            byte[] out = new byte[length];
            for (int i = 0; i < length; i++) {
                out[i] = (byte) (h[i / 8] >>> (56 - 8 * (i % 8)));
            }
            return out;
        }

        @Override
        public void update(byte input) {
            update(new byte[] { input }, 0, 1);
        }

        @Override
        public void digest(byte[] output, int offset) {
            byte[] out = finish();
            System.arraycopy(out, 0, output, offset, out.length);
            reset();
        }

        @Override
        public DigestEngine copy() {
            Sha512 copy = new Sha512(iv, length);
            System.arraycopy(block, 0, copy.block, 0, block.length);
            copy.blockLen = blockLen;
            copy.totalBytes = totalBytes;
            System.arraycopy(h, 0, copy.h, 0, h.length);
            return copy;
        }

        @Override
        public void reset() {
            blockLen = 0;
            totalBytes = 0;
            System.arraycopy(iv, 0, h, 0, 8);
        }

        private void processBlock() {
            for (int i = 0; i < 16; i++) {
                long v = 0;
                for (int j = 0; j < 8; j++) {
                    v = (v << 8) | (block[8 * i + j] & 0xff);
                }
                w[i] = v;
            }
            for (int i = 16; i < 80; i++) {
                long s0 = Long.rotateRight(w[i - 15], 1) ^ Long.rotateRight(w[i - 15], 8) ^ (w[i - 15] >>> 7);
                long s1 = Long.rotateRight(w[i - 2], 19) ^ Long.rotateRight(w[i - 2], 61) ^ (w[i - 2] >>> 6);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            long a = h[0];
            long b = h[1];
            long c = h[2];
            long d = h[3];
            long e = h[4];
            long f = h[5];
            long g = h[6];
            long hh = h[7];
            for (int i = 0; i < 80; i++) {
                long s1 = Long.rotateRight(e, 14) ^ Long.rotateRight(e, 18) ^ Long.rotateRight(e, 41);
                long ch = (e & f) ^ (~e & g);
                long t1 = hh + s1 + ch + K[i] + w[i];
                long s0 = Long.rotateRight(a, 28) ^ Long.rotateRight(a, 34) ^ Long.rotateRight(a, 39);
                long maj = (a & b) ^ (a & c) ^ (b & c);
                long t2 = s0 + maj;
                hh = g;
                g = f;
                f = e;
                e = d + t1;
                d = c;
                c = b;
                b = a;
                a = t1 + t2;
            }
            h[0] += a;
            h[1] += b;
            h[2] += c;
            h[3] += d;
            h[4] += e;
            h[5] += f;
            h[6] += g;
            h[7] += hh;
        }

        @Override
        public int length() {
            return length;
        }
    }

    private static byte[] bigEndian(int[] words) {
        byte[] out = new byte[words.length * 4];
        for (int i = 0; i < words.length; i++) {
            out[4 * i] = (byte) (words[i] >>> 24);
            out[4 * i + 1] = (byte) (words[i] >>> 16);
            out[4 * i + 2] = (byte) (words[i] >>> 8);
            out[4 * i + 3] = (byte) words[i];
        }
        return out;
    }
}
