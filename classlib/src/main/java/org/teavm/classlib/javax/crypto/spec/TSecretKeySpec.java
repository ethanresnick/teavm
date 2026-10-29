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
package org.teavm.classlib.javax.crypto.spec;

import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKey;

public class TSecretKeySpec implements KeySpec, SecretKey {
    private final byte[] key;
    private final String algorithm;

    public TSecretKeySpec(byte[] key, String algorithm) {
        this(key, 0, key != null ? key.length : 0, algorithm);
    }

    public TSecretKeySpec(byte[] key, int offset, int length, String algorithm) {
        if (key == null || algorithm == null || key.length == 0) {
            throw new IllegalArgumentException();
        }
        if (length < 0 || offset < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (offset > key.length || length > key.length - offset) {
            throw new IllegalArgumentException();
        }
        this.key = Arrays.copyOfRange(key, offset, offset + length);
        this.algorithm = algorithm;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public String getFormat() {
        return "RAW";
    }

    @Override
    public byte[] getEncoded() {
        return key.clone();
    }

    private static int foldCase(int codePoint) {
        if (codePoint < 128) {
            return codePoint >= 'A' && codePoint <= 'Z' ? codePoint + ('a' - 'A') : codePoint;
        }
        return Character.toLowerCase(Character.toUpperCase(codePoint));
    }

    private static String canonicalAlgorithm(String name) {
        return "TripleDES".equalsIgnoreCase(name) ? "DESede" : name;
    }

    @Override
    public int hashCode() {
        String name = canonicalAlgorithm(algorithm);
        int hash = 0;
        for (int i = 0; i < name.length();) {
            int codePoint = name.charAt(i);
            if (Character.isHighSurrogate((char) codePoint)) {
                codePoint = name.codePointAt(i);
            }
            hash = 31 * hash + foldCase(codePoint);
            i += Character.charCount(codePoint);
        }
        return Arrays.hashCode(key) ^ hash;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof SecretKey)) {
            return false;
        }
        var secret = (SecretKey) other;
        if (!canonicalAlgorithm(algorithm).equalsIgnoreCase(canonicalAlgorithm(secret.getAlgorithm()))) {
            return false;
        }
        byte[] otherEncoding = secret.getEncoded();
        try {
            return MessageDigest.isEqual(key, otherEncoding);
        } finally {
            if (otherEncoding != null) {
                Arrays.fill(otherEncoding, (byte) 0);
            }
        }
    }
}
