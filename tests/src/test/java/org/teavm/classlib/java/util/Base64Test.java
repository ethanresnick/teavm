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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class Base64Test {
    @Test
    public void decoderWorks() {
        assertEquals("q", decode("cQ=="));
        assertEquals("qw", decode("cXc="));
        assertEquals("qwe", decode("cXdl"));
        assertEquals("qwer", decode("cXdlcg=="));
        assertEquals("qwert", decode("cXdlcnQ="));
        assertEquals("qwerty", decode("cXdlcnR5"));
        assertEquals("qwertyu", decode("cXdlcnR5dQ=="));
        assertEquals("юзернейм:пароль", decode("0Y7Qt9C10YDQvdC10LnQvDrQv9Cw0YDQvtC70Yw="));
    }

    @Test
    public void encoderWorks() {
        assertEquals("cQ==", encode("q"));
        assertEquals("cXc=", encode("qw"));
        assertEquals("cXdl", encode("qwe"));
        assertEquals("cXdlcg==", encode("qwer"));
        assertEquals("cXdlcnQ=", encode("qwert"));
        assertEquals("cXdlcnR5", encode("qwerty"));
        assertEquals("cXdlcnR5dQ==", encode("qwertyu"));
        assertEquals("0Y7Qt9C10YDQvdC10LnQvDrQv9Cw0YDQvtC70Yw=", encode("юзернейм:пароль"));
    }

    @Test
    public void urlDecoderWorks() {
        assertEquals("q", decode("cQ"));
        assertEquals("qw", decode("cXc"));
        assertEquals("qwe", decode("cXdl"));
        assertEquals("qwer", decode("cXdlcg"));
        assertEquals("qwerty", decode("cXdlcnR5"));
        assertEquals("qwertyu", decode("cXdlcnR5dQ"));
        assertEquals("юзернейм:пароль", decode("0Y7Qt9C10YDQvdC10LnQvDrQv9Cw0YDQvtC70Yw"));
    }

    @Test
    public void urlEncoderWorks() {
        assertEquals("cQ", encodeNoPad("q"));
        assertEquals("cXc", encodeNoPad("qw"));
        assertEquals("cXdl", encodeNoPad("qwe"));
        assertEquals("cXdlcg", encodeNoPad("qwer"));
        assertEquals("cXdlcnQ", encodeNoPad("qwert"));
        assertEquals("cXdlcnR5", encodeNoPad("qwerty"));
        assertEquals("cXdlcnR5dQ", encodeNoPad("qwertyu"));
        assertEquals("0Y7Qt9C10YDQvdC10LnQvDrQv9Cw0YDQvtC70Yw", encodeNoPad("юзернейм:пароль"));
    }

    @Test
    public void decoderRejectsNonAsciiInput() {
        var bytes = new byte[][] { { 'Q', 'U', (byte) 0x80, 'A' }, { 'Q', 'U', (byte) 0xFF, 'A' } };
        for (var text : bytes) {
            assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(text));
            assertThrows(IllegalArgumentException.class, () -> Base64.getUrlDecoder().decode(text));
        }
        for (var text : new String[] { "QU\u00E9A", "QU\u0142B" }) {
            assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(text), text);
        }
    }

    @Test
    public void decoderRejectsIllegalCharacters() {
        for (var text : new String[] { "QU*A", "QU A", "QU=A", "QU_A", "QU-A", "QQ==QQ==" }) {
            assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(text), text);
        }
        for (var text : new String[] { "QU+A", "QU/A" }) {
            assertThrows(IllegalArgumentException.class, () -> Base64.getUrlDecoder().decode(text), text);
        }
    }

    @Test
    public void decoderRejectsWrongPadding() {
        for (var text : new String[] { "QUJDR", "QUJDR===", "QQ=", "QQ===", "QUI==", "QUJD=", "QUJD====", "==" }) {
            assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(text), text);
        }
    }

    private String decode(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeNoPad(String text) {
        return Base64.getEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    // Javadoc of Base64.Encoder.withoutPadding(): "Returns an encoder instance that encodes equivalently
    // to this one, but without adding any padding character at the end of the encoded byte data."
    // So every encoder returned by a factory, getUrlEncoder() included, pads.
    @Test
    public void urlEncoderPads() {
        var bytes = new byte[] { (byte) 0xFB, (byte) 0xFF };
        assertEquals("-_8=", Base64.getUrlEncoder().encodeToString(bytes));
        assertEquals("-w==", Base64.getUrlEncoder().encodeToString(new byte[] { (byte) 0xFB }));
        assertEquals("-_8", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        assertEquals("cXdlcnQ=", Base64.getUrlEncoder().encodeToString("qwert".getBytes(StandardCharsets.UTF_8)));
    }

    // Differential test: the encoders and decoders against a bit-by-bit reference implementation of
    // RFC 4648 (Table 1 for getEncoder(), Table 2 for getUrlEncoder(), padding per section 3.2). On the JVM
    // run of this test, the reference itself is checked against the JDK.
    @Test
    public void encodersAndDecodersMatchReference() {
        var random = new Random(66);
        for (var length = 0; length <= 100; ++length) {
            for (var attempt = 0; attempt < 3; ++attempt) {
                var data = new byte[length];
                random.nextBytes(data);
                checkAgainstReference(data, false, Base64.getEncoder(), Base64.getDecoder());
                checkAgainstReference(data, true, Base64.getUrlEncoder(), Base64.getUrlDecoder());
            }
        }
    }

    private static void checkAgainstReference(byte[] data, boolean url, Base64.Encoder encoder,
            Base64.Decoder decoder) {
        var message = (url ? "url " : "basic ") + Arrays.toString(data);
        var padded = referenceEncode(data, url, true);
        var unpadded = referenceEncode(data, url, false);
        assertEquals(padded, encoder.encodeToString(data), message);
        assertArrayEquals(padded.getBytes(StandardCharsets.ISO_8859_1), encoder.encode(data), message);
        assertEquals(unpadded, encoder.withoutPadding().encodeToString(data), message);
        assertArrayEquals(data, decoder.decode(padded), message);
        assertArrayEquals(data, decoder.decode(unpadded), message);
        assertArrayEquals(data, decoder.decode(padded.getBytes(StandardCharsets.ISO_8859_1)), message);
        checkOverloads(data, padded.getBytes(StandardCharsets.ISO_8859_1), encoder, decoder, message);
        checkOverloads(data, unpadded.getBytes(StandardCharsets.ISO_8859_1), encoder.withoutPadding(), decoder,
                message);
    }

    // Javadoc of Encoder.encode(byte[], byte[]) and Decoder.decode(byte[], byte[]): "No bytes will be written to
    // the output byte array if the output byte array is not big enough", and they throw IllegalArgumentException
    // "if dst does not have enough space". Javadoc of encode(ByteBuffer) and decode(ByteBuffer): "Upon return,
    // the source buffer's position will be updated to its limit; its limit will not have been changed. The
    // returned output buffer's position will be zero and its limit will be the number of resulting encoded bytes."
    private static void checkOverloads(byte[] data, byte[] encoded, Base64.Encoder encoder, Base64.Decoder decoder,
            String message) {
        // Array overloads: exact size, larger destination (the tail stays as it was), one byte too small.
        var dst = new byte[encoded.length + 2];
        Arrays.fill(dst, (byte) 7);
        assertEquals(encoded.length, encoder.encode(data, dst), message);
        assertArrayEquals(encoded, Arrays.copyOf(dst, encoded.length), message);
        assertEquals(7, dst[encoded.length], message);
        assertEquals(encoded.length, encoder.encode(data, new byte[encoded.length]), message);
        var decoded = new byte[data.length + 2];
        Arrays.fill(decoded, (byte) 7);
        assertEquals(data.length, decoder.decode(encoded, decoded), message);
        assertArrayEquals(data, Arrays.copyOf(decoded, data.length), message);
        assertEquals(7, decoded[data.length], message);
        if (encoded.length > 0) {
            var small = new byte[encoded.length - 1];
            assertThrows(IllegalArgumentException.class, () -> encoder.encode(data, small), message);
            assertArrayEquals(new byte[small.length], small, message);
        }
        if (data.length > 0) {
            var small = new byte[data.length - 1];
            assertThrows(IllegalArgumentException.class, () -> decoder.decode(encoded, small), message);
        }

        // Buffer overloads, on a source with a non-zero position and a limit before its capacity.
        var source = ByteBuffer.allocate(data.length + 5);
        source.position(3);
        source.put(data);
        source.limit(3 + data.length);
        source.position(3);
        var result = encoder.encode(source);
        assertEquals(3 + data.length, source.position(), message);
        assertEquals(3 + data.length, source.limit(), message);
        assertEquals(0, result.position(), message);
        assertEquals(encoded.length, result.limit(), message);
        assertArrayEquals(encoded, remaining(result), message);
        // A read-only source works too.
        assertArrayEquals(encoded, remaining(encoder.encode(ByteBuffer.wrap(data).asReadOnlyBuffer())), message);

        var encodedSource = ByteBuffer.allocate(encoded.length + 4);
        encodedSource.position(2);
        encodedSource.put(encoded);
        encodedSource.limit(2 + encoded.length);
        encodedSource.position(2);
        result = decoder.decode(encodedSource);
        assertEquals(2 + encoded.length, encodedSource.position(), message);
        assertEquals(2 + encoded.length, encodedSource.limit(), message);
        assertEquals(0, result.position(), message);
        assertEquals(data.length, result.limit(), message);
        assertArrayEquals(data, remaining(result), message);
    }

    // Javadoc of Decoder.decode(ByteBuffer): "IllegalArgumentException is thrown if the input buffer is not in
    // valid Base64 encoding scheme. The position of the input buffer will not be advanced in this case."
    @Test
    public void decodeBufferKeepsPositionOnInvalidInput() {
        var random = new Random(67);
        for (var length = 1; length <= 100; ++length) {
            var data = new byte[length];
            random.nextBytes(data);
            var encoded = Base64.getEncoder().encode(data);
            var index = random.nextInt(encoded.length);
            encoded[index] = '*';
            var buffer = ByteBuffer.allocate(encoded.length + 2);
            buffer.position(1);
            buffer.put(encoded);
            buffer.limit(1 + encoded.length);
            buffer.position(1);
            assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(buffer), "at " + index);
            assertEquals(1, buffer.position());
            assertEquals(1 + encoded.length, buffer.limit());
            var dst = new byte[length];
            assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(encoded, dst));
        }
    }

    private static byte[] remaining(ByteBuffer buffer) {
        var bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    private static String referenceEncode(byte[] data, boolean url, boolean pad) {
        var alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" + (url ? "-_" : "+/");
        var sb = new StringBuilder();
        var bits = 0;
        var bitCount = 0;
        for (var b : data) {
            bits = (bits << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 6) {
                bitCount -= 6;
                sb.append(alphabet.charAt((bits >>> bitCount) & 63));
            }
            bits &= (1 << bitCount) - 1;
        }
        if (bitCount > 0) {
            sb.append(alphabet.charAt((bits << (6 - bitCount)) & 63));
        }
        while (pad && sb.length() % 4 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }

    @Test
    public void arrayAndBufferOverloads() {
        var abcd = new byte[] { 'A', 'B', 'C', 'D' };
        var encoded = new byte[10];
        assertEquals(8, Base64.getEncoder().encode(abcd, encoded));
        assertEquals("QUJDRA==", new String(encoded, 0, 8, StandardCharsets.ISO_8859_1));
        assertThrows(IllegalArgumentException.class, () -> Base64.getEncoder().encode(abcd, new byte[7]));

        var decoded = new byte[5];
        assertEquals(4, Base64.getDecoder().decode("QUJDRA==".getBytes(StandardCharsets.ISO_8859_1), decoded));
        assertArrayEquals(new byte[] { 'A', 'B', 'C', 'D', 0 }, decoded);
        assertThrows(IllegalArgumentException.class,
                () -> Base64.getDecoder().decode("QUJDRA==".getBytes(StandardCharsets.ISO_8859_1), new byte[3]));

        var encodedBuffer = Base64.getEncoder().encode(ByteBuffer.wrap(abcd));
        assertEquals(0, encodedBuffer.position());
        assertEquals(8, encodedBuffer.remaining());
        var source = ByteBuffer.wrap("xQUJD".getBytes(StandardCharsets.ISO_8859_1));
        source.get();
        var decodedBuffer = Base64.getDecoder().decode(source);
        assertEquals(5, source.position());
        assertEquals(0, decodedBuffer.position());
        assertEquals(3, decodedBuffer.remaining());
        assertEquals('A', decodedBuffer.get());

        // On an error, the source buffer's position does not move.
        var bad = ByteBuffer.wrap("QU*D".getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode(bad));
        assertEquals(0, bad.position());
    }
}
