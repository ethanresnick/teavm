/*
 *  Copyright 2015 Alexey Andreev.
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
package org.teavm.classlib.java.nio.charset.impl;

import org.teavm.classlib.java.nio.charset.TCharset;
import org.teavm.classlib.java.nio.charset.TCoderResult;

/**
 * Follows the JDK's rules for malformed input (sun.nio.cs.UTF_8.Decoder): the malformed length of each bad
 * sequence, and rejection of overlong forms and of code points above U+10FFFF.
 */
public class TUTF8Decoder extends TBufferedDecoder {
    public TUTF8Decoder(TCharset cs) {
        super(cs, 1f / 3, 0.5f);
    }

    @Override
    protected TCoderResult arrayDecode(byte[] inArray, int inPos, int inSize, char[] outArray, int outPos, int outSize,
            Controller controller) {
        TCoderResult result = null;
        // The position stays on the lead byte until a whole sequence is decoded, so that a result reports the
        // input from there, and the caller skips the reported length.
        while (inPos < inSize && outPos < outSize) {
            int b = inArray[inPos] & 0xFF;
            int avail = inSize - inPos - 1;
            if (b < 0x80) {
                outArray[outPos++] = (char) b;
                inPos++;
            } else if (b >= 0xC2 && b <= 0xDF) {
                if (avail < 1) {
                    result = controller.hasMoreInput() ? null : TCoderResult.UNDERFLOW;
                    break;
                }
                int b2 = inArray[inPos + 1];
                if (!isContinuation(b2)) {
                    result = TCoderResult.malformedForLength(1);
                    break;
                }
                outArray[outPos++] = (char) (((b & 0x1F) << 6) | (b2 & 0x3F));
                inPos += 2;
            } else if ((b & 0xF0) == 0xE0) {
                if (avail >= 1 && isMalformed3Prefix(b, inArray[inPos + 1])) {
                    result = TCoderResult.malformedForLength(1);
                    break;
                }
                if (avail < 2) {
                    result = controller.hasMoreInput() ? null : TCoderResult.UNDERFLOW;
                    break;
                }
                int b3 = inArray[inPos + 2];
                if (!isContinuation(b3)) {
                    result = TCoderResult.malformedForLength(2);
                    break;
                }
                char c = (char) (((b & 0x0F) << 12) | ((inArray[inPos + 1] & 0x3F) << 6) | (b3 & 0x3F));
                if (Character.isSurrogate(c)) {
                    result = TCoderResult.malformedForLength(3);
                    break;
                }
                outArray[outPos++] = c;
                inPos += 3;
            } else if (b >= 0xF0 && b <= 0xF4) {
                // With F0-F4 and a valid second byte, the code point is always in U+10000..U+10FFFF.
                if (avail >= 1 && isMalformed4Prefix(b, inArray[inPos + 1] & 0xFF)) {
                    result = TCoderResult.malformedForLength(1);
                    break;
                }
                if (avail >= 2 && !isContinuation(inArray[inPos + 2])) {
                    result = TCoderResult.malformedForLength(2);
                    break;
                }
                if (avail < 3) {
                    result = controller.hasMoreInput() ? null : TCoderResult.UNDERFLOW;
                    break;
                }
                int b4 = inArray[inPos + 3];
                if (!isContinuation(b4)) {
                    result = TCoderResult.malformedForLength(3);
                    break;
                }
                if (outPos + 2 > outSize) {
                    if (!controller.hasMoreOutput(2)) {
                        result = TCoderResult.OVERFLOW;
                    }
                    break;
                }
                int code = ((b & 0x07) << 18) | ((inArray[inPos + 1] & 0x3F) << 12)
                        | ((inArray[inPos + 2] & 0x3F) << 6) | (b4 & 0x3F);
                outArray[outPos++] = Character.highSurrogate(code);
                outArray[outPos++] = Character.lowSurrogate(code);
                inPos += 4;
            } else {
                // 80-C1 (a continuation byte, or the lead of an overlong 2-byte form) and F5-FF (no code point
                // above U+10FFFF has a valid form).
                result = TCoderResult.malformedForLength(1);
                break;
            }
        }

        controller.setInPosition(inPos);
        controller.setOutPosition(outPos);
        return result;
    }

    private static boolean isContinuation(int b) {
        return (b & 0xC0) == 0x80;
    }

    /** The JDK's isMalformed3_2: an overlong E0 form, or a second byte that is not a continuation byte. */
    private static boolean isMalformed3Prefix(int b1, int b2) {
        return (b1 == 0xE0 && (b2 & 0xE0) == 0x80) || !isContinuation(b2);
    }

    /** The JDK's isMalformed4_2: F0 needs 90-BF, F4 needs 80-8F, every second byte must be a continuation. */
    private static boolean isMalformed4Prefix(int b1, int b2) {
        return (b1 == 0xF0 && (b2 < 0x90 || b2 > 0xBF)) || (b1 == 0xF4 && (b2 & 0xF0) != 0x80) || !isContinuation(b2);
    }
}
