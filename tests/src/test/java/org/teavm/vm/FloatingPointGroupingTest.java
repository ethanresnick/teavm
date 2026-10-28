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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSBody;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

/**
 * Floating-point + and * aren't associative (JLS 15.7.3: "Java programming language implementations must respect the
 * order of evaluation as indicated explicitly by parentheses"), so the parentheses of a right operand must be kept.
 * The operands come from an array, so that the compiler can't fold them.
 */
@TeaVMTest
public class FloatingPointGroupingTest {
    private static double[] doubles = { 1, 1e16, -1e16, 1e300, 7, 2, 3 };
    private static float[] floats = { 1, 1e8f, -1e8f, 3e38f, 7, 2, 3 };

    @Test
    public void doubleAdditionKeepsGrouping() {
        double[] d = doubles;
        // (1 + 1e16) - 1e16 would be 0 or 2
        assertEquals(1.0, d[0] + (d[1] + d[2]), 0);
        assertEquals(1.0, d[0] + (d[1] - d[1]), 0);
        assertEquals(1.0, d[0] - (d[1] + d[2]), 0);
    }

    @Test
    public void doubleMultiplicationKeepsGrouping() {
        double[] d = doubles;
        // (1e300 * 1e300) / 1e300 and (1e300 * 1e300) * (1 / 1e300) would be infinite
        assertEquals(1e300, d[3] * (d[3] / d[3]), 0);
        assertEquals(1e300, d[3] * (d[3] * (d[0] / d[3])), 1e285);
        assertEquals(1.0, d[4] % (d[5] * d[6]), 0);
        assertEquals(3.5, d[4] / (d[5] * d[0]), 0);
    }

    @Test
    public void floatArithmeticKeepsGrouping() {
        float[] f = floats;
        assertEquals(1f, f[0] + (f[1] + f[2]), 0);
        assertEquals(3e38f, f[3] * (f[3] / f[3]), 0);
        assertEquals(1f, f[4] % (f[5] * f[6]), 0);
    }

    @Test
    @SkipJVM
    @OnlyPlatform(TestPlatform.JAVASCRIPT)
    public void jsBodyKeepsGrouping() {
        assertEquals("x3", concat("x", 1, 2));
        assertEquals(1.0, sum(1, 1e16, -1e16), 0);
    }

    @JSBody(params = { "a", "b", "c" }, script = "return a + (b + c);")
    private static native String concat(String a, int b, int c);

    @JSBody(params = { "a", "b", "c" }, script = "return a + (b + c);")
    private static native double sum(double a, double b, double c);
}
