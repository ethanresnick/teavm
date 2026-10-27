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
package org.teavm.classlib.javax.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;

// HMAC (RFC 2104) over java.security.MessageDigest.
public class TMac implements Cloneable {
    private static final byte IPAD = 0x36;
    private static final byte OPAD = 0x5C;

    private final String algorithm;
    private final int blockSize;
    // Computes the inner hash, then (after digest() resets it) the outer one.
    private MessageDigest inner;
    // Padded key XOR ipad / opad; null until init succeeds.
    private byte[] innerPad;
    private byte[] outerPad;

    private TMac(String algorithm, MessageDigest inner, int blockSize) {
        this.algorithm = algorithm;
        this.inner = inner;
        this.blockSize = blockSize;
    }

    public static final TMac getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm == null) {
            throw new NullPointerException();
        }
        String digestName;
        int blockSize;
        switch (algorithm.toUpperCase()) {
            case "HMACMD5":
                digestName = "MD5";
                blockSize = 64;
                break;
            case "HMACSHA1":
                digestName = "SHA-1";
                blockSize = 64;
                break;
            case "HMACSHA224":
                digestName = "SHA-224";
                blockSize = 64;
                break;
            case "HMACSHA256":
                digestName = "SHA-256";
                blockSize = 64;
                break;
            case "HMACSHA384":
                digestName = "SHA-384";
                blockSize = 128;
                break;
            case "HMACSHA512":
                digestName = "SHA-512";
                blockSize = 128;
                break;
            case "HMACSHA512/224":
                digestName = "SHA-512/224";
                blockSize = 128;
                break;
            case "HMACSHA512/256":
                digestName = "SHA-512/256";
                blockSize = 128;
                break;
            default:
                throw new NoSuchAlgorithmException();
        }
        return new TMac(algorithm, MessageDigest.getInstance(digestName), blockSize);
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public final int getMacLength() {
        return inner.getDigestLength();
    }

    public final void init(Key key) throws InvalidKeyException {
        // HMAC takes a secret key; null and non-secret keys (such as public keys) are rejected.
        if (!(key instanceof SecretKey)) {
            throw new InvalidKeyException();
        }
        byte[] keyBytes = key.getEncoded();
        if (keyBytes == null) {
            throw new InvalidKeyException();
        }
        // A key longer than the block is replaced by its hash; a shorter one is zero-padded.
        boolean hashed = keyBytes.length > blockSize;
        if (hashed) {
            inner.reset();
            keyBytes = inner.digest(keyBytes);
        }
        byte[] ipad = new byte[blockSize];
        byte[] opad = new byte[blockSize];
        for (int i = 0; i < blockSize; i++) {
            byte k = i < keyBytes.length ? keyBytes[i] : 0;
            ipad[i] = (byte) (k ^ IPAD);
            opad[i] = (byte) (k ^ OPAD);
        }
        // Wipe the key material this Mac owns: the hashed key and the pads of the previous key. The array from
        // getEncoded() may belong to the key, so it is left alone.
        if (hashed) {
            Arrays.fill(keyBytes, (byte) 0);
        }
        if (innerPad != null) {
            Arrays.fill(innerPad, (byte) 0);
            Arrays.fill(outerPad, (byte) 0);
        }
        innerPad = ipad;
        outerPad = opad;
        restart();
    }

    public final void update(byte input) {
        checkInitialized();
        inner.update(input);
    }

    public final void update(byte[] input) {
        checkInitialized();
        if (input != null) {
            inner.update(input);
        }
    }

    public final void update(byte[] input, int offset, int len) {
        checkInitialized();
        if (input != null) {
            if (offset < 0 || len < 0 || len > input.length - offset) {
                throw new IllegalArgumentException();
            }
            inner.update(input, offset, len);
        }
    }

    public final void update(ByteBuffer input) {
        checkInitialized();
        if (input == null) {
            throw new IllegalArgumentException();
        }
        // MessageDigest.update(ByteBuffer) reads remaining() bytes and moves position to limit.
        inner.update(input);
    }

    public final byte[] doFinal() {
        checkInitialized();
        byte[] innerHash = inner.digest();
        inner.update(outerPad);
        byte[] result = inner.digest(innerHash);
        restart();
        return result;
    }

    public final byte[] doFinal(byte[] input) {
        update(input);
        return doFinal();
    }

    public final void doFinal(byte[] output, int outOffset) throws ShortBufferException {
        checkInitialized();
        if (output == null || output.length - outOffset < getMacLength()) {
            throw new ShortBufferException();
        }
        if (outOffset < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        byte[] result = doFinal();
        System.arraycopy(result, 0, output, outOffset, result.length);
    }

    public final void reset() {
        if (innerPad != null) {
            restart();
        }
    }

    @Override
    public final Object clone() throws CloneNotSupportedException {
        TMac copy = (TMac) super.clone();
        copy.inner = (MessageDigest) inner.clone();
        // init wipes the old pads, so the copy needs its own.
        if (innerPad != null) {
            copy.innerPad = innerPad.clone();
            copy.outerPad = outerPad.clone();
        }
        return copy;
    }

    // Puts the Mac back in the state right after init.
    private void restart() {
        inner.reset();
        inner.update(innerPad);
    }

    private void checkInitialized() {
        if (innerPad == null) {
            throw new IllegalStateException();
        }
    }
}
