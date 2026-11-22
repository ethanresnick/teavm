/*
 *  Copyright 2026 Alexey Andreev.
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
import org.teavm.junit.AsyncEnabled;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;


/**
 * Floating-point addition and multiplication are not associative: the grouping written in the source decides
 * the result, and has to survive compilation.
 */
@TeaVMTest
public class FloatingPointGroupingTest {
    @Test
    public void doubleAdditionKeepsGrouping() {
        assertEquals(0.0, doubleBig() + (-doubleBig() + doubleOne()), 0.0);
        assertEquals(1.0, (doubleBig() + -doubleBig()) + doubleOne(), 0.0);
    }

    @Test
    public void doubleMultiplicationKeepsGrouping() {
        assertEquals(1e308, doubleHuge() * (doubleTen() * doubleTenth()), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, (doubleHuge() * doubleTen()) * doubleTenth(), 0.0);
    }

    @Test
    public void integerAdditionIsUnchanged() {
        assertEquals(6, intOne() + (intTwo() + intThree()));
        assertEquals(6, intOne() * (intTwo() * intThree()));
    }

    private double doubleBig() {
        return 1e16;
    }

    private double doubleOne() {
        return 1;
    }

    private double doubleHuge() {
        return 1e308;
    }

    private double doubleTen() {
        return 10;
    }

    private double doubleTenth() {
        return 0.1;
    }

    private int intOne() {
        return 1;
    }

    private int intTwo() {
        return 2;
    }

    private int intThree() {
        return 3;
    }
    private static float[] floats = { 1, 1e8f, -1e8f, 3e38f };

    @Test
    @AsyncEnabled
    public void floatArithmeticKeepsGrouping() {
        float[] f = floats;
        assertEquals(1f, f[0] + (f[1] + f[2]), 0);
        assertEquals(3e38f, f[3] * (f[3] / f[3]), 0);
    }

    @Test
    @AsyncEnabled
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
