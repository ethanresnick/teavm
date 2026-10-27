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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class MacTest {
    // RFC 2202 test cases 1-7: the expected HMAC-MD5 and HMAC-SHA1 values. rfc2202Vectors() builds the inputs.
    private static final String[][] RFC_2202 = {
            { "9294727a3638bb1c13f48ef8158bfc9d", "b617318655057264e28bc0b6fb378c8ef146be00" },
            { "750c783e6ab0b503eaa86e310a5db738", "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79" },
            { "56be34521d144c88dbb8c733f0e8b3f6", "125d7342b9ac11cd91a39af48aa17b4f63f175d3" },
            { "697eaf0aca3a3aea3a75164746ffaa79", "4c9007f4026250c6bc8414f9bf50c86c2d7235da" },
            { "56461ef2342edc00f9bab995690efd4c", "4c1a03424b55e07fe7f27be1d58bb9324a9a5a04" },
            { "6b1ab7fe4bd7bf8f0b62e6ce61b9d0cd", "aa4ae5e15272d00e95705637ce8a3b55ed402112" },
            { "6f630fad67cda0ee1fb1f562db3aa53e", "e8e99d0f45237d786d6bbaa7965c7808bbff1a91" }
    };

    private static final String[] RFC_4231_ALGORITHMS = { "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512",
            "HmacSHA512/224", "HmacSHA512/256" };

    // RFC 4231 test cases 1-7, for RFC_4231_ALGORITHMS in order. Test case 5 lists the full output; the RFC lists
    // its first 128 bits. HmacSHA512/224 and HmacSHA512/256 have no RFC vectors; their values come from JDK 21.
    private static final String[][] RFC_4231 = {
            {
                "896fb1128abbdf196832107cd49df33f47b4b1169912ba4f53684b22",
                "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
                "afd03944d84895626b0825f4ab46907f15f9dadbe4101ec682aa034c7cebc59cfaea9ea9076ede7f"
                    + "4af152e8b2fa9cb6",
                "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cdedaa833b7d6b8a702"
                    + "038b274eaea3f4e4be9d914eeb61f1702e696c203a126854",
                "b244ba01307c0e7a8ccaad13b1067a4cf6b961fe0c6a20bda3d92039",
                "9f9126c3d9c3c330d760425ca8a217e31feae31bfe70196ff81642b868402eab" },
            {
                "a30e01098bc6dbbf45690f3a7e9e6d0f8bbea2a39e6148008fd05e44",
                "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                "af45d2e376484031617f78d2b58a6b1b9c7ef464f5a01b47e42ec3736322445e8e2240ca5e69e2c7"
                    + "8b3239ecfab21649",
                "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a"
                    + "6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
                "4a530b31a79ebcce36916546317c45f247d83241dfb818fd37254bde",
                "6df7b24630d5ccb2ee335407081a87188c221489768fa2020513b2d593359456" },
            {
                "7fb3cb3588c6c1f6ffa9694d7d6ad2649365b0c1f65d69d1ec8333ea",
                "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
                "88062608d3e6ad8a0aa2ace014c8a86f0aa635d947ac9febe83ef4e55966144b2a5ab39dc13814b9"
                    + "4e3ab6e101a34f27",
                "fa73b0089d56a284efb0f0756c890be9b1b5dbdd8ee81a3655f83e33b2279d39bf3e848279a722c8"
                    + "06b485a47e67c807b946a337bee8942674278859e13292fb",
                "db34ea525c2c216ee5a6ccb6608bea870bbef12fd9b96a5109e2b6fc",
                "229006391d66c8ecddf43ba5cf8f83530ef221a4e9401840d1bead5137c8a2ea" },
            {
                "6c11506874013cac6a2abc1bb382627cec6a90d86efc012de7afec5a",
                "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b",
                "3e8a69b7783c25851933ab6290af6ca77a9981480850009cc5577c6e1f573b4e6801dd23c4a7d679"
                    + "ccf8a386c674cffb",
                "b0ba465637458c6990e5a8c5f61d4af7e576d97ff94b872de76f8050361ee3dba91ca5c11aa25eb4"
                    + "d679275cc5788063a5f19741120c4f2de2adebeb10a298dd",
                "c2391863cda465c6828af06ac5d4b72d0b792109952da530e11a0d26",
                "36d60c8aa1d0be856e10804cf836e821e8733cbafeae87630589fd0b9b0a2f4c" },
            {
                "0e2aea68a90c8d37c988bcdb9fca6fa8099cd857c7ec4a1815cac54c",
                "a3b6167473100ee06e0c796c2955552bfa6f7c0a6a8aef8b93f860aab0cd20c5",
                "3abf34c3503b2a23a46efc619baef897f4c8e42c934ce55ccbae9740fcbc1af4ca62269e2a37cd88"
                    + "ba926341efe4aeea",
                "415fad6271580a531d4179bc891d87a650188707922a4fbb36663a1eb16da008711c5b50ddd0fc23"
                    + "5084eb9d3364a1454fb2ef67cd1d29fe6773068ea266e96b",
                "1df8eae8baeedd4eddfb555ec0ba768f4b5ba29e9e3d55f08303120f",
                "337f526924766971bf72b82ad19c2c825301791e3ae2d8bb4ec03817dd821f46" },
            {
                "95e9a0db962095adaebe9b2d6f0dbce2d499f112f2d2b7273fa6870e",
                "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
                "4ece084485813e9088d2c63a041bc5b44f9ef1012a2b588f3cd11f05033ac4c60c2ef6ab4030fe82"
                    + "96248df163f44952",
                "80b24263c7c1a3ebb71493c1dd7be8b49b46d1f41b4aeec1121b013783f8f3526b56d037e05f2598"
                    + "bd0fd2215d6a1e5295e64f73f63f0aec8b915a985d786598",
                "29bef8ce88b54d4226c3c7718ea9e32ace2429026f089e38cea9aeda",
                "87123c45f7c537a404f8f47cdbedda1fc9bec60eeb971982ce7ef10e774e6539" },
            {
                "3a854166ac5d9f023f54d517d0b39dbd946770db9c2b95c9f6f565d1",
                "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
                "6617178e941f020d351e2f254e8fd32c602420feb0b8fb9adccebb82461e99c5a678cc31e799176d"
                    + "3860e6110c46523e",
                "e37b6a775dc87dbaa4dfa9f96e5e3ffddebd71f8867289865df5a32d20cdc944b6022cac3c4982b1"
                    + "0d5eeb55c3e4de15134676fb6de0446065c97440fa8c6a58",
                "82a9619b47af0cea73a8b9741355ce902d807ad87ee9078522a246e1",
                "6ea83f8e7315072c0bdaa33b93a26fc1659974637a9db8a887d06c05a7f35a66" }
    };

    @Test
    public void rfc2202Vectors() throws Exception {
        var data = new String[] { "4869205468657265", "7768617420646f2079612077616e7420666f72206e6f7468696e673f",
                "dd".repeat(50), "cd".repeat(50), hex(ascii("Test With Truncation")),
                hex(ascii("Test Using Larger Than Block-Size Key - Hash Key First")),
                hex(ascii("Test Using Larger Than Block-Size Key and Larger Than One Block-Size Data")) };
        var md5Keys = new String[] { "0b".repeat(16), "4a656665", "aa".repeat(16),
                "0102030405060708090a0b0c0d0e0f10111213141516171819", "0c".repeat(16), "aa".repeat(80),
                "aa".repeat(80) };
        var sha1Keys = new String[] { "0b".repeat(20), "4a656665", "aa".repeat(20),
                "0102030405060708090a0b0c0d0e0f10111213141516171819", "0c".repeat(20), "aa".repeat(80),
                "aa".repeat(80) };
        for (var i = 0; i < data.length; ++i) {
            assertEquals(RFC_2202[i][0], hmac("HmacMD5", md5Keys[i], data[i]), "HmacMD5 case " + (i + 1));
            assertEquals(RFC_2202[i][1], hmac("HmacSHA1", sha1Keys[i], data[i]), "HmacSHA1 case " + (i + 1));
        }
    }

    @Test
    public void rfc4231Vectors() throws Exception {
        var keys = new String[] { "0b".repeat(20), "4a656665", "aa".repeat(20),
                "0102030405060708090a0b0c0d0e0f10111213141516171819", "0c".repeat(20), "aa".repeat(131),
                "aa".repeat(131) };
        var data = new String[] { "4869205468657265", "7768617420646f2079612077616e7420666f72206e6f7468696e673f",
                "dd".repeat(50), "cd".repeat(50), hex(ascii("Test With Truncation")),
                hex(ascii("Test Using Larger Than Block-Size Key - Hash Key First")),
                hex(ascii("This is a test using a larger than block-size key and a larger than block-size data. "
                        + "The key needs to be hashed before being used by the HMAC algorithm.")) };
        for (var i = 0; i < keys.length; ++i) {
            for (var j = 0; j < RFC_4231_ALGORITHMS.length; ++j) {
                assertEquals(RFC_4231[i][j], hmac(RFC_4231_ALGORITHMS[j], keys[i], data[i]),
                        RFC_4231_ALGORITHMS[j] + " case " + (i + 1));
            }
        }
    }

    @Test
    public void incrementalUpdatesAndReuse() throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(ascii("key"), "HmacSHA256"));
        var expected = mac.doFinal(ascii("The quick brown fox"));
        // doFinal resets the MAC, so it can be used again with the same key.
        mac.update(ascii("The quick brown "));
        mac.update((byte) 'f');
        mac.update(ascii("xoxx"), 1, 2);
        assertArrayEquals(expected, mac.doFinal());
        var buffer = ByteBuffer.wrap(ascii("The quick brown fox"));
        mac.update(buffer);
        assertFalse(buffer.hasRemaining());
        assertArrayEquals(expected, mac.doFinal());
        mac.update(ascii("discarded"));
        mac.reset();
        assertArrayEquals(expected, mac.doFinal(ascii("The quick brown fox")));
        var output = new byte[40];
        mac.update(ascii("The quick brown fox"));
        mac.doFinal(output, 8);
        for (var i = 0; i < 32; ++i) {
            assertEquals(expected[i], output[8 + i]);
        }
        assertThrows(ShortBufferException.class, () -> mac.doFinal(new byte[40], 9));
    }

    @Test
    public void algorithmNamesAndErrors() throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        assertEquals("HmacSHA256", mac.getAlgorithm());
        assertEquals(32, mac.getMacLength());
        assertEquals("hmacsha256", Mac.getInstance("hmacsha256").getAlgorithm());
        assertEquals(16, Mac.getInstance("HmacMD5").getMacLength());
        assertEquals(20, Mac.getInstance("HmacSHA1").getMacLength());
        assertEquals(48, Mac.getInstance("HmacSHA384").getMacLength());
        assertThrows(NoSuchAlgorithmException.class, () -> Mac.getInstance("HmacNope"));
        assertThrows(IllegalStateException.class, () -> mac.update((byte) 1));
        assertThrows(IllegalStateException.class, () -> mac.doFinal());
        InvalidKeyException e = assertThrows(InvalidKeyException.class, () -> mac.init(null));
        assertTrue((Object) e instanceof KeyException);
    }

    @Test
    public void secretKeySpec() {
        var a = new SecretKeySpec(new byte[] { 1, 2, 3 }, "HmacSHA256");
        var b = new SecretKeySpec(new byte[] { 9, 1, 2, 3, 9 }, 1, 3, "hmacsha256");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        // Equal specs have equal hash codes. The value itself differs between JDK versions, so the test does not
        // fix it. A different key or algorithm gives a different hash code for these inputs.
        assertNotEquals(a.hashCode(), new SecretKeySpec(new byte[] { 1, 2, 4 }, "HmacSHA256").hashCode());
        assertNotEquals(a.hashCode(), new SecretKeySpec(new byte[] { 1, 2, 3 }, "HmacSHA1").hashCode());
        assertEquals("RAW", a.getFormat());
        assertEquals("hmacsha256", b.getAlgorithm());
        assertFalse(a.equals(new SecretKeySpec(new byte[] { 1, 2, 4 }, "HmacSHA256")));
        assertFalse(a.equals(new SecretKeySpec(new byte[] { 1, 2, 3 }, "HmacSHA1")));

        // getEncoded returns a copy, and the constructor copies its argument.
        var bytes = new byte[] { 5, 6 };
        var c = new SecretKeySpec(bytes, "HmacSHA1");
        bytes[0] = 0;
        c.getEncoded()[1] = 0;
        assertArrayEquals(new byte[] { 5, 6 }, c.getEncoded());

        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[0], "x"));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[] { 1 }, null));
        assertThrows(IllegalArgumentException.class, () -> new SecretKeySpec(new byte[] { 1, 2 }, 1, 2, "x"));
    }

    @Test
    public void secretKeyDestroyableDefaults() {
        SecretKey key = new SecretKeySpec(new byte[] { 1 }, "HmacSHA1");
        Key asKey = key;
        assertEquals("HmacSHA1", asKey.getAlgorithm());
        assertFalse(key.isDestroyed());
        assertThrows(DestroyFailedException.class, key::destroy);
    }

    private static String hmac(String algorithm, String keyHex, String dataHex) throws Exception {
        var mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(unhex(keyHex), algorithm));
        return hex(mac.doFinal(unhex(dataHex)));
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] unhex(String s) {
        var result = new byte[s.length() / 2];
        for (var i = 0; i < result.length; ++i) {
            result[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    private static String hex(byte[] bytes) {
        var sb = new StringBuilder();
        for (var b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
