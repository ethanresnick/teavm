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

import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;

public abstract class TMessageDigest implements Cloneable {
    private final String algorithm;
    private DigestEngine engine;
    // True after an update and until the next digest or reset; only toString reads it.
    private boolean inProgress;

    TMessageDigest(String algorithm, DigestEngine engine) {
        this.algorithm = algorithm;
        this.engine = engine;
    }

    public static TMessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm == null) {
            throw new NullPointerException();
        }
        String canonical = canonicalName(algorithm);
        DigestEngine engine = canonical != null ? DigestEngines.create(canonical) : null;
        if (engine == null) {
            throw new NoSuchAlgorithmException();
        }
        return new Impl(algorithm, engine);
    }

    // Maps the standard names and their common aliases, ignoring case.
    private static String canonicalName(String algorithm) {
        switch (algorithm.toUpperCase()) {
            case "MD5":
                return "MD5";
            case "SHA":
            case "SHA1":
            case "SHA-1":
                return "SHA-1";
            case "SHA224":
            case "SHA-224":
                return "SHA-224";
            case "SHA256":
            case "SHA-256":
                return "SHA-256";
            case "SHA384":
            case "SHA-384":
                return "SHA-384";
            case "SHA512":
            case "SHA-512":
                return "SHA-512";
            case "SHA512/224":
            case "SHA-512/224":
                return "SHA-512/224";
            case "SHA512/256":
            case "SHA-512/256":
                return "SHA-512/256";
            default:
                return null;
        }
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public final int getDigestLength() {
        return engine.length();
    }

    public void update(byte input) {
        engine.update(input);
        inProgress = true;
    }

    public void update(byte[] input) {
        if (input == null) {
            throw new NullPointerException();
        }
        engine.update(input, 0, input.length);
        inProgress = true;
    }

    public void update(byte[] input, int offset, int len) {
        if (input == null || input.length - offset < len) {
            throw new IllegalArgumentException();
        }
        if (len == 0) {
            return;
        }
        if (offset < 0 || len < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        engine.update(input, offset, len);
        inProgress = true;
    }

    public final void update(ByteBuffer input) {
        if (input == null) {
            throw new NullPointerException();
        }
        int len = input.remaining();
        if (len == 0) {
            return;
        }
        if (input.hasArray()) {
            int position = input.position();
            engine.update(input.array(), input.arrayOffset() + position, len);
            input.position(position + len);
        } else {
            byte[] chunk = new byte[Math.min(len, 4096)];
            while (input.hasRemaining()) {
                int n = Math.min(chunk.length, input.remaining());
                input.get(chunk, 0, n);
                engine.update(chunk, 0, n);
            }
        }
        inProgress = true;
    }

    public byte[] digest() {
        byte[] result = new byte[engine.length()];
        engine.digest(result, 0);
        inProgress = false;
        return result;
    }

    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    public int digest(byte[] buf, int offset, int len) throws DigestException {
        if (buf == null || buf.length - offset < len) {
            throw new IllegalArgumentException();
        }
        int length = engine.length();
        if (len < length || offset < 0) {
            throw new DigestException();
        }
        engine.digest(buf, offset);
        inProgress = false;
        return length;
    }

    public void reset() {
        engine.reset();
        inProgress = false;
    }

    // Takes time that depends only on digesta.length.
    public static boolean isEqual(byte[] digesta, byte[] digestb) {
        if (digesta == digestb) {
            return true;
        }
        if (digesta == null || digestb == null) {
            return false;
        }
        int lengthB = digestb.length;
        int diff = digesta.length ^ lengthB;
        for (int i = 0; i < digesta.length; i++) {
            byte b = lengthB > 0 ? digestb[i < lengthB ? i : 0] : 0;
            diff |= digesta[i] ^ b;
        }
        return diff == 0;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        TMessageDigest copy = (TMessageDigest) super.clone();
        copy.engine = engine.copy();
        return copy;
    }

    @Override
    public String toString() {
        return algorithm + " Message Digest from TeaVM, " + (inProgress ? "<in progress>" : "<initialized>") + "\n";
    }

    private static final class Impl extends TMessageDigest {
        Impl(String algorithm, DigestEngine engine) {
            super(algorithm, engine);
        }
    }
}
