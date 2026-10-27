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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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

    @Test
    public void incrementalUpdatesMatchOneShot() throws NoSuchAlgorithmException {
        // Split points around the 64-byte and 128-byte block boundaries.
        var data = new byte[300];
        for (var i = 0; i < data.length; ++i) {
            data[i] = (byte) (i * 31 + 7);
        }
        for (var vector : VECTORS) {
            var md = MessageDigest.getInstance(vector[0]);
            var expected = md.digest(data);
            for (var split : new int[] { 0, 1, 55, 56, 63, 64, 65, 111, 112, 127, 128, 129, 300 }) {
                md.update(data, 0, split);
                md.update(data, split, data.length - split);
                assertArrayEquals(expected, md.digest(), vector[0] + " split " + split);
            }
            for (var b : data) {
                md.update(b);
            }
            assertArrayEquals(expected, md.digest(), vector[0] + " byte by byte");
            var buffer = ByteBuffer.allocate(data.length + 10);
            buffer.position(5);
            buffer.put(data);
            buffer.flip();
            buffer.position(5);
            md.update(buffer);
            assertEquals(buffer.limit(), buffer.position());
            assertArrayEquals(expected, md.digest(), vector[0] + " ByteBuffer");
        }
    }

    @Test
    public void resetDiscardsInput() throws NoSuchAlgorithmException {
        var md = MessageDigest.getInstance("SHA-256");
        md.update(ascii("ignored"));
        md.reset();
        assertEquals(VECTORS[3][2], hex(md.digest(ascii("abc"))));
        // digest() also resets.
        assertEquals(VECTORS[3][1], hex(md.digest()));
    }

    @Test
    public void algorithmNames() throws NoSuchAlgorithmException {
        assertEquals("SHA-256", MessageDigest.getInstance("SHA-256").getAlgorithm());
        assertEquals(VECTORS[1][2], hex(MessageDigest.getInstance("SHA").digest(ascii("abc"))));
        assertEquals(VECTORS[1][2], hex(MessageDigest.getInstance("SHA1").digest(ascii("abc"))));
        assertEquals(VECTORS[3][2], hex(MessageDigest.getInstance("SHA256").digest(ascii("abc"))));
        assertEquals(VECTORS[3][2], hex(MessageDigest.getInstance("sha-256").digest(ascii("abc"))));
        assertThrows(NoSuchAlgorithmException.class, () -> MessageDigest.getInstance("SHA-999"));
        assertThrows(NullPointerException.class, () -> MessageDigest.getInstance(null));
    }

    @Test
    public void isEqual() {
        assertTrue(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 2 }));
        assertFalse(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 3 }));
        assertFalse(MessageDigest.isEqual(new byte[] { 1, 2 }, new byte[] { 1, 2, 3 }));
        assertTrue(MessageDigest.isEqual(null, null));
        assertFalse(MessageDigest.isEqual(new byte[0], null));
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
}
