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

    @Test
    public void mimeEncoderWrapsLines() {
        var bytes = new byte[60];
        for (var i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) (i * 7);
        }
        assertEquals("AAcOFRwjKjE4P0ZNVFtiaXB3foWMk5qhqK+2vcTL0tng5+71/AMKERgfJi00O0JJUFdeZWxzeoGI\r\nj5ad",
                Base64.getMimeEncoder().encodeToString(bytes));
        // 57 bytes fill one line of 76 characters exactly, and no separator follows the last line.
        assertEquals(76, Base64.getMimeEncoder().encodeToString(new byte[57]).length());
        var tenBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
        // The line length rounds down to a multiple of 4.
        assertEquals("AQIDBAUG\nBwgJCg==", Base64.getMimeEncoder(10, new byte[] { '\n' }).encodeToString(tenBytes));
        // A line length under 4 means no line breaks.
        assertEquals("AQIDBAUGBwgJCg==", Base64.getMimeEncoder(3, new byte[] { '\n' }).encodeToString(tenBytes));
        assertEquals("AQIDBAUG\nBwgJCg", Base64.getMimeEncoder(10, new byte[] { '\n' }).withoutPadding()
                .encodeToString(tenBytes));
        assertThrows(IllegalArgumentException.class, () -> Base64.getMimeEncoder(76, new byte[] { 'A' }));
    }

    @Test
    public void mimeDecoderSkipsCharactersOutsideAlphabet() {
        assertEquals("ABCDEF", new String(Base64.getMimeDecoder().decode("QU*J\r\nDR EVG"),
                StandardCharsets.ISO_8859_1));
        assertEquals("ABCDEFG", new String(Base64.getMimeDecoder().decode("QUJD\r\nREVG\r\nRw=="),
                StandardCharsets.ISO_8859_1));
        var bytes = new byte[100];
        for (var i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) (i * 13);
        }
        assertArrayEquals(bytes, Base64.getMimeDecoder().decode(Base64.getMimeEncoder().encode(bytes)));
    }

    // Javadoc of Base64 (MIME): "The encoded output must be represented in lines of no more than 76 characters
    // each and uses a carriage return '\r' followed immediately by a linefeed '\n' as the line separator. No line
    // separator is added to the end of the encoded output." Javadoc of getMimeEncoder(int, byte[]): lineLength is
    // "rounded down to nearest multiple of 4. If the rounded down line length is not a positive value, the output
    // will not be separated in lines".
    @Test
    public void mimeEncoderMatchesReference() {
        var random = new Random(69);
        var separators = new byte[][] { {}, { '\n' }, { '\r', '\n' }, { '*', '#', '!' } };
        for (var length = 0; length <= 100; ++length) {
            var data = new byte[length];
            random.nextBytes(data);
            var message = Arrays.toString(data);
            var padded = referenceEncode(data, false, true);
            var unpadded = referenceEncode(data, false, false);
            assertEquals(wrap(padded, 76, "\r\n"), Base64.getMimeEncoder().encodeToString(data), message);
            for (var lineLength : new int[] { -4, 0, 3, 4, 5, 8, 10, 76, 77 }) {
                for (var separator : separators) {
                    var separatorText = new String(separator, StandardCharsets.ISO_8859_1);
                    var encoder = Base64.getMimeEncoder(lineLength, separator);
                    var expected = wrap(padded, lineLength & ~3, separatorText);
                    var lineMessage = message + " " + lineLength + " " + separatorText;
                    assertEquals(expected, encoder.encodeToString(data), lineMessage);
                    assertEquals(wrap(unpadded, lineLength & ~3, separatorText),
                            encoder.withoutPadding().encodeToString(data), lineMessage);
                    assertArrayEquals(data, Base64.getMimeDecoder().decode(expected), lineMessage);
                    checkOverloads(data, expected.getBytes(StandardCharsets.ISO_8859_1), encoder,
                            Base64.getMimeDecoder(), lineMessage);
                }
            }
        }
    }

    private static String wrap(String text, int lineLength, String separator) {
        if (lineLength <= 0) {
            return text;
        }
        var sb = new StringBuilder();
        for (var i = 0; i < text.length(); i += lineLength) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(text, i, Math.min(text.length(), i + lineLength));
        }
        return sb.toString();
    }

    // Javadoc of getMimeEncoder(int, byte[]): throws IllegalArgumentException "if lineSeparator includes any
    // character of "The Base64 Alphabet" as specified in Table 1 of RFC 2045". The JDK rejects '=' too.
    @Test
    public void mimeEncoderRejectsSeparatorsInAlphabet() {
        var alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
        for (var b = 0; b < 256; ++b) {
            var separator = new byte[] { '\r', (byte) b };
            if (alphabet.indexOf(b) >= 0) {
                assertThrows(IllegalArgumentException.class, () -> Base64.getMimeEncoder(76, separator),
                        String.valueOf(b));
            } else {
                Base64.getMimeEncoder(76, separator);
            }
        }
    }

    // Javadoc of Base64 (MIME): "All line separators or other characters not found in the base64 alphabet table
    // are ignored in decoding operation." Javadoc of Base64.Decoder: "The Base64 padding character '=' is accepted
    // and interpreted as the end of the encoded byte data, but is not required. [...] If there is a padding
    // character present in the final unit, the correct number of padding character(s) must be present".
    // The expected values were checked against the JDK.
    @Test
    public void mimeDecoderPaddingAndTrailingCharacters() {
        var decoder = Base64.getMimeDecoder();
        assertArrayEquals(new byte[0], decoder.decode(""));
        assertArrayEquals(new byte[0], decoder.decode("*\r\n"));
        assertArrayEquals(new byte[] { 65, 4 }, decoder.decode("QQQ=="));
        assertArrayEquals(new byte[] { 65 }, decoder.decode("QQ==="));
        assertArrayEquals(new byte[] { 65 }, decoder.decode("QQ==\r\n=*"));
        assertArrayEquals(new byte[] { 65, 66 }, decoder.decode("QU\r\nI=\r\n"));
        assertArrayEquals(new byte[] { 65 }, decoder.decode("Q\nQ"));
        for (var text : new String[] { "=", "Q", "Q=", "QQ=", "QQ=\n=", "QQ=*=", "QUJD=", "QQ==QQ==", "QQ==A",
                "QUI=A" }) {
            assertThrows(IllegalArgumentException.class, () -> decoder.decode(text), text);
        }
    }

    // Exhaustive differential test: every input of up to 6 characters over two small alphabets. The expected
    // digests were computed with the JDK's MIME decoder, and the JVM run of this test checks them.
    @Test
    public void mimeDecoderMatchesJdkOnAllShortInputs() {
        assertEquals(-5105170174715733430L, mimeDecoderOutcomeDigest("QA=*\n", 6));
        assertEquals(-8695801745419786358L, mimeDecoderOutcomeDigest("g/=é ", 6));
    }

    private static long mimeDecoderOutcomeDigest(String alphabet, int maxLength) {
        var decoder = Base64.getMimeDecoder();
        var hash = 0L;
        var count = 0;
        for (var length = 0; length <= maxLength; ++length) {
            var indexes = new int[length];
            var input = new byte[length];
            while (true) {
                for (var k = 0; k < length; ++k) {
                    input[k] = (byte) alphabet.charAt(indexes[k]);
                }
                try {
                    var output = decoder.decode(input);
                    hash = hash * 31 + output.length + 1;
                    for (var b : output) {
                        hash = hash * 31 + b;
                    }
                } catch (IllegalArgumentException e) {
                    hash = hash * 31 - 1;
                }
                ++count;
                var k = length - 1;
                while (k >= 0 && ++indexes[k] == alphabet.length()) {
                    indexes[k--] = 0;
                }
                if (k < 0) {
                    break;
                }
            }
        }
        return hash * 31 + count;
    }

    // Javadoc of Base64.Decoder.decode(String): "An invocation of this method has exactly the same effect as
    // invoking decode(src.getBytes(StandardCharsets.ISO_8859_1))". A character above 0xFF becomes '?', which the
    // MIME decoder skips and the other decoders reject.
    @Test
    public void decodeStringMapsWideCharactersAsIso88591() {
        assertArrayEquals(new byte[] { 65, 66, 67 }, Base64.getMimeDecoder().decode("QUłJD"));
        assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode("QUłJD"));
        assertThrows(IllegalArgumentException.class, () -> Base64.getUrlDecoder().decode("QUłJD"));
    }
}
