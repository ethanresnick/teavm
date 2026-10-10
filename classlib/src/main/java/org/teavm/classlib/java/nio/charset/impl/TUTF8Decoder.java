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
        while (inPos < inSize && outPos < outSize) {
            int b = inArray[inPos++] & 0xFF;
            if (b < 0x80) {
                outArray[outPos++] = (char) b;
            } else if (b >= 0xC2 && b <= 0xDF) {
                if (inPos >= inSize) {
                    --inPos;
                    if (!controller.hasMoreInput(2)) {
                        result = TCoderResult.UNDERFLOW;
                    }
                    break;
                }
                int b2 = inArray[inPos];
                if (!isContinuation(b2)) {
                    --inPos;
                    result = TCoderResult.malformedForLength(1);
                    break;
                }
                inPos++;
                outArray[outPos++] = (char) (((b & 0x1F) << 6) | (b2 & 0x3F));
            } else if ((b & 0xF0) == 0xE0) {
                int avail = inSize - inPos;
                if (avail < 2) {
                    if (avail == 1 && isMalformed3Prefix(b, inArray[inPos])) {
                        --inPos;
                        result = TCoderResult.malformedForLength(1);
                        break;
                    }
                    --inPos;
                    if (!controller.hasMoreInput(3)) {
                        result = TCoderResult.UNDERFLOW;
                    }
                    break;
                }
                int b2 = inArray[inPos];
                int b3 = inArray[inPos + 1];
                if (isMalformed3Prefix(b, b2) || !isContinuation(b3)) {
                    --inPos;
                    result = TCoderResult.malformedForLength(isMalformed3Prefix(b, b2) ? 1 : 2);
                    break;
                }
                char c = (char) (((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
                if (Character.isSurrogate(c)) {
                    --inPos;
                    result = TCoderResult.malformedForLength(3);
                    break;
                }
                inPos += 2;
                outArray[outPos++] = c;
            } else if ((b & 0xF8) == 0xF0) {
                int avail = inSize - inPos;
                if (avail < 3) {
                    if (avail >= 1 && isMalformed4Prefix(b, inArray[inPos] & 0xFF)) {
                        --inPos;
                        result = TCoderResult.malformedForLength(1);
                        break;
                    }
                    if (avail >= 2 && !isContinuation(inArray[inPos + 1])) {
                        --inPos;
                        result = TCoderResult.malformedForLength(2);
                        break;
                    }
                    --inPos;
                    if (!controller.hasMoreInput(4)) {
                        result = TCoderResult.UNDERFLOW;
                    }
                    break;
                }
                int b2 = inArray[inPos] & 0xFF;
                int b3 = inArray[inPos + 1];
                int b4 = inArray[inPos + 2];
                int code = ((b & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
                if (!isContinuation(b2) || !isContinuation(b3) || !isContinuation(b4)
                        || !Character.isSupplementaryCodePoint(code)) {
                    --inPos;
                    int length;
                    if (b > 0xF4 || isMalformed4Prefix(b, b2)) {
                        length = 1;
                    } else if (!isContinuation(b3)) {
                        length = 2;
                    } else {
                        length = 3;
                    }
                    result = TCoderResult.malformedForLength(length);
                    break;
                }
                if (outPos + 2 > outSize) {
                    --inPos;
                    if (!controller.hasMoreOutput(2)) {
                        result = TCoderResult.OVERFLOW;
                    }
                    break;
                }
                inPos += 3;
                outArray[outPos++] = Character.highSurrogate(code);
                outArray[outPos++] = Character.lowSurrogate(code);
            } else {
                // 80-C1 (a continuation byte, or the lead of an overlong 2-byte form) and F8-FF.
                --inPos;
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
