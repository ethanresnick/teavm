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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class MessageDigestTest {
    // The FIPS 180-4 example messages (NIST CSRC "Examples with intermediate values"): the empty message, "abc",
    // the 448-bit and 896-bit two-block messages, and one million 'a'. MD5 uses the same messages.
    private static final String MESSAGE_448 = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
    private static final String MESSAGE_896 = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmno"
            + "ijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu";

    private static final String[][] VECTORS = {
            { "MD5",
                "d41d8cd98f00b204e9800998ecf8427e",
                "900150983cd24fb0d6963f7d28e17f72",
                "8215ef0796a20bcaaae116d3876c664a",
                "03dd8807a93175fb062dfb55dc7d359c",
                "7707d6ae4e027c70eea2a935c2296f21" },
            { "SHA-1",
                "da39a3ee5e6b4b0d3255bfef95601890afd80709",
                "a9993e364706816aba3e25717850c26c9cd0d89d",
                "84983e441c3bd26ebaae4aa1f95129e5e54670f1",
                "a49b2446a02c645bf419f995b67091253a04a259",
                "34aa973cd4c4daa4f61eeb2bdbad27316534016f" },
            { "SHA-224",
                "d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f",
                "23097d223405d8228642a477bda255b32aadbce4bda0b3f7e36c9da7",
                "75388b16512776cc5dba5da1fd890150b0c6455cb4f58b1952522525",
                "c97ca9a559850ce97a04a96def6d99a9e0e0e2ab14e6b8df265fc0b3",
                "20794655980c91d8bbb4c1ea97618a4bf03f42581948b2ee4ee7ad67" },
            { "SHA-256",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
                "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
                "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0" },
            { "SHA-384",
                "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
                "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed8086072ba1e7cc2358baeca134c825a7",
                "3391fdddfc8dc7393707a65b1b4709397cf8b1d162af05abfe8f450de5f36bc6b0455a8520bc4e6f5fe95b1fe3c8452b",
                "09330c33f71147e83d192fc782cd1b4753111b173b3b05d22fa08086e3b0f712fcc7c71a557e2db966c3e9fa91746039",
                "9d0e1809716474cb086e834e310a4a1ced149e9c00f248527972cec5704c2a5b07b8b3dc38ecc4ebae97ddd87f3d8985" },
            { "SHA-512",
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce"
                        + "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                "204a8fc6dda82f0a0ced7beb8e08a41657c16ef468b228a8279be331a703c335"
                        + "96fd15c13b1b07f9aa1d3bea57789ca031ad85c7a71dd70354ec631238ca3445",
                "8e959b75dae313da8cf4f72814fc143f8f7779c6eb9f7fa17299aeadb6889018"
                        + "501d289e4900f7e4331b99dec4b5433ac7d329eeb6dd26545e96e55b874be909",
                "e718483d0ce769644e2e42c7bc15b4638e1f98b13b2044285632a803afa973eb"
                        + "de0ff244877ea60a4cb0432ce577c31beb009c5c2c49aa2e4eadb217ad8cc09b" },
            { "SHA-512/224",
                "6ed0dd02806fa89e25de060c19d3ac86cabb87d6a0ddd05c333b84f4",
                "4634270f707b6a54daae7530460842e20e37ed265ceee9a43e8924aa",
                "e5302d6d54bb242275d1e7622d68df6eb02dedd13f564c13dbda2174",
                "23fec5bb94d60b23308192640b0c453335d664734fe40e7268674af9",
                "37ab331d76f0d36de422bd0edeb22a28accd487b7a8453ae965dd287" },
            { "SHA-512/256",
                "c672b8d1ef56ed28ab87c3622c5114069bdd3ad7b8f9737498d0c01ecef0967a",
                "53048e2681941ef99b2e29b76b4c7dabe4c2d0c634fc6d46e0e2f13107e7af23",
                "bde8e1f9f19bb9fd3406c90ec6bc47bd36d8ada9f11880dbc8a22a7078b6a461",
                "3928e184fb8690f840da3988121d31be65cb9d3ef83ee6146feac861e19b563a",
                "9a59a052930187a97038cae692f30708aa6491923ef5194394dc68d56c74fb21" }
    };

    // RFC 1321, appendix A.5.
    private static final String[][] MD5_SUITE = {
            { "a", "0cc175b9c0f1b6a831c399e269772661" },
            { "message digest", "f96b697d7cb7938d525a2f31aaf161d0" },
            { "abcdefghijklmnopqrstuvwxyz", "c3fcd3d76192e4007dfb496cca67e13b" },
            { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", "d174ab98d277d9f5a5611c2c9f419d9f" },
            { "1234567890".repeat(8), "57edf4a22be3c955ac49da2e2107b67a" }
    };

    // Messages of every length from 0 to MAX_LENGTH cover every padding case of both block sizes (64 and 128).
    private static final int MAX_LENGTH = 300;

    // For each algorithm in VECTORS: the digest, computed by the JDK, of the concatenated digests of
    // XorShift(12345) messages of length 0 to MAX_LENGTH. See allLengthsMatchJdk.
    private static final String[] ALL_LENGTHS = {
            "2e038d2a179adafe15a3c39b6ec5f776",
            "f4b485c840ce32ebb37b7f241b884f268e538d3b",
            "80fb8a6686e55acda0c2b2ea74fb70ed31d86d51551402600f113b42",
            "49f8181e945e3c24caf271b50f7ff371a3d2285463469960f587fd4a6710b1c7",
            "3f93a127ac13466155113551d23a19b8645f29b052adf0ea47ba7719850d1ca3"
                    + "83f4b7af1b642ed70ad214d479b46e69",
            "7f7f7064e0f9a4bd56e81642e5d98b3f4636bb0ed3eaf82900e28d77fa9af4f9"
                    + "07061528fd0b883cad34fb31937b1e223ab03e2e6778adbbe97ddb9ed53e5683",
            "0570befd48d7e7e87c8cbb392bcd881fcaccb1631231652f22a77a0b",
            "54bf85e6bec6b49933332d76737fcfc57fe4b4f4290d666a32d77da52df2f464"
    };

    @Test
    public void standardVectors() throws NoSuchAlgorithmException {
        var million = new byte[1000000];
        Arrays.fill(million, (byte) 'a');
        for (var vector : VECTORS) {
            var md = MessageDigest.getInstance(vector[0]);
            assertEquals(vector[1], hex(md.digest(ascii(""))), vector[0]);
            assertEquals(vector[2], hex(md.digest(ascii("abc"))), vector[0]);
            assertEquals(vector[3], hex(md.digest(ascii(MESSAGE_448))), vector[0]);
            assertEquals(vector[4], hex(md.digest(ascii(MESSAGE_896))), vector[0]);
            assertEquals(vector[5], hex(md.digest(million)), vector[0]);
            assertEquals(vector[1].length() / 2, md.getDigestLength(), vector[0]);
        }
    }

    @Test
    public void md5Suite() throws NoSuchAlgorithmException {
        var md = MessageDigest.getInstance("MD5");
        for (var vector : MD5_SUITE) {
            assertEquals(vector[1], hex(md.digest(ascii(vector[0]))), vector[0]);
        }
    }

    // Differential test against the JDK: digests a pseudo-random message of every length from 0 to MAX_LENGTH and
    // folds the digests into one digest of the same algorithm. The expected values come from the JDK; this test
    // also runs on the JVM, which checks them.
    @Test
    public void allLengthsMatchJdk() throws NoSuchAlgorithmException {
        for (var i = 0; i < VECTORS.length; ++i) {
            var md = MessageDigest.getInstance(VECTORS[i][0]);
            var fold = MessageDigest.getInstance(VECTORS[i][0]);
            var random = new XorShift(12345);
            for (var length = 0; length <= MAX_LENGTH; ++length) {
                fold.update(md.digest(random.bytes(length)));
            }
            assertEquals(ALL_LENGTHS[i], hex(fold.digest()), VECTORS[i][0]);
        }
    }

    // Every way of feeding a message gives the one-shot digest: random split points, each piece fed through
    // update(byte), update(byte[], int, int) at a random offset in a larger array, or a heap, direct, read-only or
    // sliced ByteBuffer; the result read through digest() or digest(byte[], int, int) at a random offset.
    // Javadoc of update(ByteBuffer): "The digest is updated using the input.remaining() bytes starting at
    // input.position(). Upon return, the buffer's position will be equal to its limit; its limit will not have
    // changed."
    @Test
    public void randomSplitsMatchOneShot() throws Exception {
        var random = new XorShift(777);
        for (var vector : VECTORS) {
            var md = MessageDigest.getInstance(vector[0]);
            for (var length = 0; length <= MAX_LENGTH; ++length) {
                var data = random.bytes(length);
                var expected = md.digest(data);
                for (var round = 0; round < 3; ++round) {
                    feedInRandomPieces(md, data, random);
                    assertArrayEquals(expected, randomDigest(md, random), vector[0] + " length " + length);
                }
            }
        }
    }

    // Javadoc of clone: "Returns a clone if the implementation is cloneable." A clone taken in the middle of a
    // message continues independently of the original.
    @Test
    public void cloneMidMessage() throws Exception {
        var random = new XorShift(99);
        for (var vector : VECTORS) {
            var md = MessageDigest.getInstance(vector[0]);
            for (var length = 0; length <= MAX_LENGTH; length += 7) {
                var data = random.bytes(length);
                var expected = md.digest(data);
                var split = random.nextInt(length + 1);
                md.update(data, 0, split);
                var copy = (MessageDigest) md.clone();
                assertEquals(md.getAlgorithm(), copy.getAlgorithm());
                assertEquals(md.getDigestLength(), copy.getDigestLength());
                // Diverge the original; the copy must not see it.
                md.update(random.bytes(1 + random.nextInt(200)));
                copy.update(data, split, length - split);
                assertArrayEquals(expected, copy.digest(), vector[0] + " clone at " + split);
                md.reset();
                assertArrayEquals(expected, md.digest(data), vector[0] + " original after reset");
            }
        }
    }

    // Javadoc of reset: "Resets the digest for further use." Of digest(): "The digest is reset after this call is
    // made." Input from before a reset or a digest, at any buffer fill level, never affects later digests.
    @Test
    public void resetDiscardsInput() throws Exception {
        var random = new XorShift(4242);
        for (var vector : VECTORS) {
            var md = MessageDigest.getInstance(vector[0]);
            var message = random.bytes(100);
            var expected = MessageDigest.getInstance(vector[0]).digest(message);
            for (var junk = 0; junk <= 260; ++junk) {
                md.update(random.bytes(junk));
                if (junk % 2 == 0) {
                    md.reset();
                } else {
                    md.digest();
                }
                assertArrayEquals(expected, md.digest(message), vector[0] + " after " + junk + " bytes");
            }
            md.update(random.bytes(70));
            md.reset();
            assertEquals(vector[1], hex(md.digest()), vector[0]);
        }
    }

    // Javadoc of update(byte[], int, int): "offset - the offset to start from in the array of bytes. len - the
    // number of bytes to use, starting at offset." Exhaustively over small arrays: a valid range digests exactly
    // that slice; an invalid one with len != 0 throws (the type is unspecified) and leaves the state unchanged.
    @Test
    public void updateRangeChecks() throws NoSuchAlgorithmException {
        var md = MessageDigest.getInstance("SHA-256");
        var reference = MessageDigest.getInstance("SHA-256");
        int[] values = { Integer.MIN_VALUE, -5, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, Integer.MAX_VALUE };
        for (var n = 0; n <= 5; ++n) {
            var array = new byte[n];
            for (var i = 0; i < n; ++i) {
                array[i] = (byte) (i + 1);
            }
            for (var offset : values) {
                for (var len : values) {
                    var valid = offset >= 0 && len >= 0 && offset <= n - len;
                    md.update(ascii("prefix"));
                    reference.update(ascii("prefix"));
                    var what = "n=" + n + " offset=" + offset + " len=" + len;
                    if (valid) {
                        md.update(array, offset, len);
                        reference.update(Arrays.copyOfRange(array, offset, offset + len));
                    } else if (len != 0) {
                        int o = offset;
                        int l = len;
                        assertThrows(RuntimeException.class, () -> md.update(array, o, l), what);
                    } else {
                        try {
                            md.update(array, offset, len);
                        } catch (RuntimeException e) {
                            // Either outcome is fine for an empty range at a bad offset.
                        }
                    }
                    assertArrayEquals(reference.digest(), md.digest(), what);
                }
            }
        }
        assertThrows(RuntimeException.class, () -> md.update(null, 0, 1));
        assertThrows(NullPointerException.class, () -> md.update((byte[]) null));
        assertThrows(NullPointerException.class, () -> md.update((ByteBuffer) null));
    }

    // Javadoc of digest(byte[], int, int): "buf - output buffer for the computed digest; offset - offset into the
    // output buffer to begin storing the digest; len - number of bytes within buf allotted for the digest. Returns:
    // the number of bytes placed into buf. Throws: DigestException - if an error occurs." A failed call writes
    // nothing and keeps the state; a successful one writes the digest and nothing else.
    @Test
    public void digestIntoBufferChecks() throws Exception {
        for (var vector : VECTORS) {
            var md = MessageDigest.getInstance(vector[0]);
            var size = md.getDigestLength();
            var expected = md.digest(ascii("abc"));
            int[] values = { Integer.MIN_VALUE, -1, 0, 1, size - 1, size, size + 1, size + 2, Integer.MAX_VALUE };
            for (var n : new int[] { 0, size - 1, size, size + 1, size + 3 }) {
                for (var offset : values) {
                    for (var len : values) {
                        var buf = new byte[n];
                        Arrays.fill(buf, (byte) 0x5A);
                        md.update(ascii("abc"));
                        var valid = offset >= 0 && len >= size && offset <= n - len;
                        var what = vector[0] + " n=" + n + " offset=" + offset + " len=" + len;
                        if (valid) {
                            assertEquals(size, md.digest(buf, offset, len), what);
                            assertArrayEquals(expected, Arrays.copyOfRange(buf, offset, offset + size), what);
                            for (var i = 0; i < n; ++i) {
                                if (i < offset || i >= offset + size) {
                                    assertEquals((byte) 0x5A, buf[i], what);
                                }
                            }
                        } else {
                            int o = offset;
                            int l = len;
                            assertThrows(Exception.class, () -> md.digest(buf, o, l), what);
                            for (var b : buf) {
                                assertEquals((byte) 0x5A, b, what);
                            }
                            assertArrayEquals(expected, md.digest(), what + ": state after the failure");
                        }
                    }
                }
            }
            md.update(ascii("abc"));
            assertThrows(DigestException.class, () -> md.digest(new byte[size + 4], 0, size - 1));
            assertArrayEquals(expected, md.digest());
        }
    }

    @Test
    public void algorithmNames() throws NoSuchAlgorithmException {
        // Javadoc of getInstance: "algorithm - the name of the algorithm requested"; Standard Algorithm Names are
        // case-insensitive. getAlgorithm: "Returns a string that identifies the algorithm".
        String[][] aliases = {
                { "MD5", "md5", "Md5" },
                { "SHA-1", "SHA", "sha", "SHA1", "sha-1" },
                { "SHA-224", "SHA224", "sha-224" },
                { "SHA-256", "SHA256", "sha-256", "Sha-256" },
                { "SHA-384", "SHA384", "sha-384" },
                { "SHA-512", "SHA512", "sha-512" },
                { "SHA-512/224", "SHA512/224", "sha-512/224" },
                { "SHA-512/256", "SHA512/256", "sha-512/256" }
        };
        for (var i = 0; i < aliases.length; ++i) {
            assertEquals(VECTORS[i][0], aliases[i][0]);
            for (var name : aliases[i]) {
                var md = MessageDigest.getInstance(name);
                assertEquals(name, md.getAlgorithm());
                assertEquals(VECTORS[i][2], hex(md.digest(ascii("abc"))), name);
                assertEquals(VECTORS[i][2].length() / 2, md.getDigestLength(), name);
            }
        }
        for (var name : new String[] { "SHA-999", "", "SHA_256", "SHA-256 ", "MD-5", "SHA-512/384" }) {
            assertThrows(NoSuchAlgorithmException.class, () -> MessageDigest.getInstance(name), name);
        }
        assertThrows(NullPointerException.class, () -> MessageDigest.getInstance(null));
    }

    // Algorithms that the JDK's default provider has and TeaVM does not. The MessageDigest Javadoc only requires
    // "SHA-1" and "SHA-256" of every Java platform implementation.
    @Test
    @Disabled("TeaVM has no MD2, no SHA-3 and no OID aliases")
    public void unsupportedAlgorithms() throws NoSuchAlgorithmException {
        assertEquals("8350e5a3e24c153df2275c9f80692773", hex(MessageDigest.getInstance("MD2").digest()));
        assertEquals("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a",
                hex(MessageDigest.getInstance("SHA3-256").digest()));
        assertEquals(VECTORS[3][1], hex(MessageDigest.getInstance("2.16.840.1.101.3.4.2.1").digest()));
    }

    @Test
    public void isEqual() {
        assertTrue(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 2 }));
        assertFalse(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 3 }));
        assertFalse(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 2, 3 }));
        assertFalse(MessageDigest.isEqual(new byte[] { 1, 2, 1 }, new byte[] { 1, 2 }));
        assertFalse(MessageDigest.isEqual(new byte[] { 0 }, new byte[0]));
        assertFalse(MessageDigest.isEqual(new byte[0], new byte[] { 0 }));
        assertTrue(MessageDigest.isEqual(new byte[0], new byte[0]));
        assertTrue(MessageDigest.isEqual(null, null));
        assertFalse(MessageDigest.isEqual(new byte[0], null));
        assertFalse(MessageDigest.isEqual(null, new byte[0]));
    }

    // Javadoc of isEqual: "Two digests are equal if they have the same length and all bytes at corresponding
    // positions are equal." Exhaustive over all arrays of length 0 to 3 over four byte values.
    @Test
    public void isEqualMatchesArraysEquals() {
        byte[] alphabet = { 0, 1, -1, -128 };
        var arrays = new ArrayList<byte[]>();
        for (var length = 0; length <= 3; ++length) {
            var count = 1;
            for (var i = 0; i < length; ++i) {
                count *= alphabet.length;
            }
            for (var code = 0; code < count; ++code) {
                var array = new byte[length];
                var c = code;
                for (var i = 0; i < length; ++i) {
                    array[i] = alphabet[c % alphabet.length];
                    c /= alphabet.length;
                }
                arrays.add(array);
            }
        }
        for (var a : arrays) {
            for (var b : arrays) {
                assertEquals(Arrays.equals(a, b), MessageDigest.isEqual(a, b),
                        Arrays.toString(a) + " " + Arrays.toString(b));
            }
            assertTrue(MessageDigest.isEqual(a, a.clone()));
            assertFalse(MessageDigest.isEqual(a, null));
            assertFalse(MessageDigest.isEqual(null, a));
        }
    }

    private static void feedInRandomPieces(MessageDigest md, byte[] data, XorShift random) {
        var pos = 0;
        while (pos < data.length) {
            var n = random.nextInt(Math.min(data.length - pos, 140) + 1);
            switch (random.nextInt(7)) {
                case 0:
                    for (var i = 0; i < n; ++i) {
                        md.update(data[pos + i]);
                    }
                    break;
                case 1: {
                    var before = random.nextInt(5);
                    var array = random.bytes(before + n + random.nextInt(5));
                    System.arraycopy(data, pos, array, before, n);
                    md.update(array, before, n);
                    break;
                }
                case 2: {
                    var before = random.nextInt(5);
                    var array = random.bytes(before + n + 3);
                    System.arraycopy(data, pos, array, before, n);
                    var buffer = ByteBuffer.wrap(array, before, n);
                    md.update(buffer);
                    assertEquals(before + n, buffer.position());
                    assertEquals(before + n, buffer.limit());
                    break;
                }
                case 3: {
                    var buffer = ByteBuffer.allocateDirect(n + 2);
                    buffer.put((byte) 1).put(data, pos, n).put((byte) 2);
                    buffer.position(1);
                    buffer.limit(n + 1);
                    md.update(buffer);
                    assertEquals(n + 1, buffer.position());
                    assertEquals(n + 1, buffer.limit());
                    break;
                }
                case 4: {
                    var buffer = ByteBuffer.wrap(data, pos, n).asReadOnlyBuffer();
                    md.update(buffer);
                    assertFalse(buffer.hasRemaining());
                    break;
                }
                case 5: {
                    var buffer = ByteBuffer.wrap(data, pos, n).duplicate();
                    md.update(buffer);
                    assertEquals(pos + n, buffer.position());
                    break;
                }
                default: {
                    var array = random.bytes(n + 6);
                    System.arraycopy(data, pos, array, 3, n);
                    var buffer = ByteBuffer.wrap(array, 2, n + 2).slice();
                    buffer.position(1);
                    buffer.limit(n + 1);
                    md.update(buffer);
                    assertEquals(n + 1, buffer.position());
                    break;
                }
            }
            pos += n;
        }
    }

    private static byte[] randomDigest(MessageDigest md, XorShift random) throws DigestException {
        if (random.nextInt(2) == 0) {
            return md.digest();
        }
        var size = md.getDigestLength();
        var offset = random.nextInt(4);
        var buf = new byte[offset + size + random.nextInt(4)];
        assertEquals(size, md.digest(buf, offset, buf.length - offset));
        return Arrays.copyOfRange(buf, offset, offset + size);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static String hex(byte[] bytes) {
        var sb = new StringBuilder();
        for (var b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // A deterministic generator, so that every backend digests the same messages.
    private static final class XorShift {
        private int state;

        XorShift(int seed) {
            state = seed;
        }

        int next() {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            return state;
        }

        int nextInt(int bound) {
            return (next() >>> 1) % bound;
        }

        byte[] bytes(int length) {
            var result = new byte[length];
            for (var i = 0; i < length; ++i) {
                result[i] = (byte) next();
            }
            return result;
        }
    }
}
