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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSBody;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

@TeaVMTest
public class StrictMathTest {
    // The C test build lets clang contract a * b + c into a fused multiply-add, which rounds once instead of twice.
    // fdlibm's steps then give a result 1 ulp off: for the input 0x3FF50D3B414EDA2E, C gave 0x3FD18FA30D0C6D53
    // where fdlibm gives 0x3FD18FA30D0C6D54. With -ffp-contract=off the C backend passes.
    @Test
    @SkipPlatform(TestPlatform.C)
    public void logGivesFdlibmBits() {
        // Inputs for which Node 24's Math.log gives another last bit than fdlibm.
        long[][] cases = {
                { 0x3FF50D3B414EDA2EL, 0x3FD18FA30D0C6D54L },
                { 0x3FD9CD0FFC328D70L, 0xBFED122D47E139FFL },
                { 0x400B0433CAEEBD13L, 0x3FF378D841B54204L },
                { 0x400599AEB56D9852L, 0x3FEFC8D736D54E56L }
        };
        for (long[] c : cases) {
            assertEquals(c[1], Double.doubleToRawLongBits(StrictMath.log(Double.longBitsToDouble(c[0]))));
        }
    }

    @Test
    public void logSpecialValues() {
        assertEquals(Double.NEGATIVE_INFINITY, StrictMath.log(0.0), 0);
        assertEquals(Double.NEGATIVE_INFINITY, StrictMath.log(-0.0), 0);
        assertEquals(0.0, StrictMath.log(1.0), 0);
        assertEquals(Double.POSITIVE_INFINITY, StrictMath.log(Double.POSITIVE_INFINITY), 0);
        assertEquals(Double.NaN, StrictMath.log(-1.0), 0);
        assertEquals(Double.NaN, StrictMath.log(Double.NaN), 0);
        assertEquals(-744.4400719213812, StrictMath.log(Double.MIN_VALUE), 0);
        assertEquals(709.782712893384, StrictMath.log(Double.MAX_VALUE), 0);
    }

    @Test
    @SkipJVM
    @OnlyPlatform(TestPlatform.JAVASCRIPT)
    public void logDoesNotUseHostMathLog() {
        // A host Math.log need not be fdlibm's: Node 24's differs in the last bit for the inputs above, Chromium
        // 138's does not. Replace it with a wrong function, so that a StrictMath.log that calls it fails.
        breakHostLog();
        try {
            assertEquals(0x3FD18FA30D0C6D54L,
                    Double.doubleToRawLongBits(StrictMath.log(Double.longBitsToDouble(0x3FF50D3B414EDA2EL))));
        } finally {
            restoreHostLog();
        }
    }

    @JSBody(script = "globalThis.teavmTestSavedLog = Math.log; Math.log = function() { return 0; };")
    private static native void breakHostLog();

    @JSBody(script = "Math.log = globalThis.teavmTestSavedLog;")
    private static native void restoreHostLog();
}
