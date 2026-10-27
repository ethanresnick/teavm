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
import org.teavm.junit.TeaVMTest;

/**
 * Loop nests of depth 2 and 3 with suspension points at different positions. Each body uses the loop
 * variables and values derived from them ({@code k - 1}, {@code k * 2}, ...) after the suspension point
 * and after the inner loop. On C, a suspension point gives the loop a second entry (the resume entry),
 * so the coroutine transformation splits the irreducible loop by copying blocks.
 *
 * <p>Methods named {@code d<N><Shape>At<positions>} share the shape of the loops and differ only in
 * where {@link #pause()} is called, so all of them must produce the same string. Positions for depth 2:
 * 0 = start of the outer body, 1/2/3 = start/middle/end of the inner body, 4 = after the inner loop.
 * Positions for depth 3: 0 = start of the outer body, 1 = start of the middle body, 2/3 = start/end of
 * the innermost body, 4 = after the innermost loop, 5 = after the middle loop.
 *
 * <p>Expected strings were produced by running the same code on the JVM.
 */
@TeaVMTest
public class AsyncLoopNestTest {
    private static int counter;

    // Suspends on every other call, so that both the path that suspends and the one that does not are taken.
    private static void pause() throws InterruptedException {
        if (counter++ % 2 == 0) {
            Thread.sleep(1);
        }
    }

    @Test
    public void depth2Down() throws InterruptedException {
        var expected = "0:0,2,2,0 0/2/2; 1:1,5,4,2 0:1,5,4,1 1/4/5; 2:2,8,6,4 1:2,8,6,3 0:2,8,6,2 2/6/8;"
                + " ";
        counter = 0;
        assertEquals(expected, d2DownAt0(), "d2DownAt0");
        counter = 0;
        assertEquals(expected, d2DownAt1(), "d2DownAt1");
        counter = 0;
        assertEquals(expected, d2DownAt2(), "d2DownAt2");
        counter = 0;
        assertEquals(expected, d2DownAt3(), "d2DownAt3");
        counter = 0;
        assertEquals(expected, d2DownAt4(), "d2DownAt4");
        counter = 0;
        assertEquals(expected, d2DownAt14(), "d2DownAt14");
        counter = 0;
        assertEquals(expected, d2DownAt03(), "d2DownAt03");
    }

    @Test
    public void depth2Up() throws InterruptedException {
        var expected = "0:0,2,2,0 1:0,2,2,1 0/2/2; 0:1,5,4,1 1:1,5,4,2 2:1,5,4,3 3:1,5,4,4 1/4/5; 0:2,8,"
                + "6,2 1:2,8,6,3 2:2,8,6,4 3:2,8,6,5 4:2,8,6,6 5:2,8,6,7 2/6/8; ";
        counter = 0;
        assertEquals(expected, d2UpAt0(), "d2UpAt0");
        counter = 0;
        assertEquals(expected, d2UpAt1(), "d2UpAt1");
        counter = 0;
        assertEquals(expected, d2UpAt2(), "d2UpAt2");
        counter = 0;
        assertEquals(expected, d2UpAt3(), "d2UpAt3");
        counter = 0;
        assertEquals(expected, d2UpAt4(), "d2UpAt4");
        counter = 0;
        assertEquals(expected, d2UpAt14(), "d2UpAt14");
        counter = 0;
        assertEquals(expected, d2UpAt03(), "d2UpAt03");
    }

    @Test
    public void depth2While() throws InterruptedException {
        var expected = "2:0,2,2,2 0:0,2,2,0 0/2/2; 4:1,5,4,5 2:1,5,4,3 1/4/5; 6:2,8,6,8 4:2,8,6,6 2:2,8,"
                + "6,4 2/6/8; ";
        counter = 0;
        assertEquals(expected, d2WhileAt0(), "d2WhileAt0");
        counter = 0;
        assertEquals(expected, d2WhileAt1(), "d2WhileAt1");
        counter = 0;
        assertEquals(expected, d2WhileAt2(), "d2WhileAt2");
        counter = 0;
        assertEquals(expected, d2WhileAt3(), "d2WhileAt3");
        counter = 0;
        assertEquals(expected, d2WhileAt4(), "d2WhileAt4");
        counter = 0;
        assertEquals(expected, d2WhileAt14(), "d2WhileAt14");
        counter = 0;
        assertEquals(expected, d2WhileAt03(), "d2WhileAt03");
    }

    @Test
    public void depth2DoWhile() throws InterruptedException {
        var expected = "0:0,2,2,0 1:0,2,2,1 0/2/2; 1:1,5,4,2 2:1,5,4,3 1/4/5; 2:2,8,6,4 3:2,8,6,5 2/6/8;"
                + " ";
        counter = 0;
        assertEquals(expected, d2DoWhileAt0(), "d2DoWhileAt0");
        counter = 0;
        assertEquals(expected, d2DoWhileAt1(), "d2DoWhileAt1");
        counter = 0;
        assertEquals(expected, d2DoWhileAt2(), "d2DoWhileAt2");
        counter = 0;
        assertEquals(expected, d2DoWhileAt3(), "d2DoWhileAt3");
        counter = 0;
        assertEquals(expected, d2DoWhileAt4(), "d2DoWhileAt4");
        counter = 0;
        assertEquals(expected, d2DoWhileAt14(), "d2DoWhileAt14");
        counter = 0;
        assertEquals(expected, d2DoWhileAt03(), "d2DoWhileAt03");
    }

    @Test
    public void depth3Up() throws InterruptedException {
        var expected = "0:0:0,1,-1 -1/2; 0|0:1:1,4,-2 1:1:1,4,-1 0/4; 0:0:1,2,-2 -1/4; 1|0:2:2,7,-3 1:2:"
                + "2,7,-2 2:2:2,7,-1 1/6; 0:1:2,5,-3 1:1:2,5,-2 0/6; 0:0:2,3,-3 -1/6; 2|";
        counter = 0;
        assertEquals(expected, d3UpAt0(), "d3UpAt0");
        counter = 0;
        assertEquals(expected, d3UpAt1(), "d3UpAt1");
        counter = 0;
        assertEquals(expected, d3UpAt2(), "d3UpAt2");
        counter = 0;
        assertEquals(expected, d3UpAt3(), "d3UpAt3");
        counter = 0;
        assertEquals(expected, d3UpAt4(), "d3UpAt4");
        counter = 0;
        assertEquals(expected, d3UpAt5(), "d3UpAt5");
        counter = 0;
        assertEquals(expected, d3UpAt25(), "d3UpAt25");
    }

    @Test
    public void depth3Down() throws InterruptedException {
        var expected = "0:0:0,1,-1 -1/2; 0|2:1:1,4,0 0:1:1,4,-2 0/4; 0:0:1,2,-2 -2:0:1,2,-4 -1/4; 1|4:2:"
                + "2,7,1 2:2:2,7,-1 0:2:2,7,-3 1/6; 2:1:2,5,-1 0:1:2,5,-3 -2:1:2,5,-5 0/6; 0:0:2,3,"
                + "-3 -2:0:2,3,-5 -1/6; 2|";
        counter = 0;
        assertEquals(expected, d3DownAt0(), "d3DownAt0");
        counter = 0;
        assertEquals(expected, d3DownAt1(), "d3DownAt1");
        counter = 0;
        assertEquals(expected, d3DownAt2(), "d3DownAt2");
        counter = 0;
        assertEquals(expected, d3DownAt3(), "d3DownAt3");
        counter = 0;
        assertEquals(expected, d3DownAt4(), "d3DownAt4");
        counter = 0;
        assertEquals(expected, d3DownAt5(), "d3DownAt5");
        counter = 0;
        assertEquals(expected, d3DownAt25(), "d3DownAt25");
    }

    @Test
    public void labeledContinue() throws InterruptedException {
        counter = 0;
        assertEquals("0:0 2; c1 2:2 c2 3:3 2:3 c3 ", computeLabeledContinue());
    }

    @Test
    public void suspensionInTryCatch() throws InterruptedException {
        counter = 0;
        assertEquals("0:0 2; 1:1 !1:1 4; 2:2 !2:2 0:2 6; ", computeSuspensionInTryCatch());
    }

    @Test
    public void longAndDoubleVariables() throws InterruptedException {
        counter = 0;
        assertEquals("0,0.5,0 1.0; 1,1.0,3 1,1.0,0 2.0; 2,1.5,6 2,1.5,3 2,1.5,0 3.0; ",
                computeLongAndDoubleVariables());
    }

    @Test
    public void innerCounterUsedAfterLoop() throws InterruptedException {
        counter = 0;
        assertEquals("0:0 -1/0; 1:1 1:0 -1/1; 2:2 2:1 0/2; ", computeInnerCounterUsedAfterLoop());
    }

    @Test
    public void suspensionInLoopCondition() throws InterruptedException {
        counter = 0;
        assertEquals("0:0 0; 1:1 0:1 1; 2:2 1:2 2; ", computeSuspensionInLoopCondition());
    }

    private static String computeLabeledContinue() throws InterruptedException {
        var sb = new StringBuilder();
        outer:
        for (var k = 1; k < 5; k++) {
            for (var i = k - 1; i >= 0; i--) {
                pause();
                if (i == 1) {
                    sb.append('c').append(k - 1).append(' ');
                    continue outer;
                }
                sb.append(i).append(':').append(k - 1).append(' ');
            }
            sb.append(k * 2).append("; ");
        }
        return sb.toString();
    }

    private static String computeSuspensionInTryCatch() {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                try {
                    pause();
                    if (i == k - 2) {
                        throw new IllegalStateException();
                    }
                    sb.append(i).append(':');
                } catch (IllegalStateException | InterruptedException e) {
                    sb.append('!').append(k - 1).append(':');
                }
                sb.append(k - 1).append(' ');
            }
            sb.append(k * 2).append("; ");
        }
        return sb.toString();
    }

    private static String computeLongAndDoubleVariables() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1L; k < 4; k++) {
            var d = k * 0.5;
            for (var i = k - 1; i >= 0; i--) {
                pause();
                sb.append(k - 1).append(',').append(d).append(',').append(i * 3).append(' ');
            }
            sb.append(d * 2).append("; ");
        }
        return sb.toString();
    }

    private static String computeInnerCounterUsedAfterLoop() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            int i;
            for (i = k - 1; i > k - 3 && i >= 0; i--) {
                pause();
                sb.append(k - 1).append(':').append(i).append(' ');
            }
            sb.append(i).append('/').append(k - 1).append("; ");
        }
        return sb.toString();
    }

    private static String computeSuspensionInLoopCondition() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; check(i, k); i--) {
                sb.append(i).append(':').append(k - 1).append(' ');
            }
            sb.append(k - 1).append("; ");
        }
        return sb.toString();
    }

    private static boolean check(int i, int k) throws InterruptedException {
        pause();
        return i >= 0 && i > k - 3;
    }

    private static String d2DownAt0() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DownAt1() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DownAt2() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                sb.append(i).append(':');
                pause();
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DownAt3() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DownAt4() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DownAt14() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DownAt03() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            for (var i = k - 1; i >= 0; i--) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt0() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt1() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt2() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                sb.append(i).append(':');
                pause();
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt3() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt4() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt14() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2UpAt03() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            for (var i = 0; i < k * 2; i++) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt0() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt1() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt2() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                sb.append(i).append(':');
                pause();
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt3() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt4() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt14() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2WhileAt03() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            var i = k * 2;
            while (i > k - 2) {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
                i -= 2;
            }
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt0() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt1() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt2() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                sb.append(i).append(':');
                pause();
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt3() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt4() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt14() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                pause();
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            pause();
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d2DoWhileAt03() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            var m = k * 3 - 1;
            var i = k - 1;
            do {
                sb.append(i).append(':');
                sb.append(k - 1).append(',').append(m).append(',');
                pause();
                sb.append(k * 2).append(',').append(i + k - 1).append(' ');
            } while (++i < k + 1);
            sb.append(k - 1).append('/').append(k * 2).append('/').append(m).append("; ");
        }
        return sb.toString();
    }

    private static String d3UpAt0() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            for (var i = k - 1; i >= 0; i--) {
                for (var j = 0; j <= i; j++) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3UpAt1() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                pause();
                for (var j = 0; j <= i; j++) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3UpAt2() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = 0; j <= i; j++) {
                    pause();
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3UpAt3() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = 0; j <= i; j++) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    pause();
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3UpAt4() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = 0; j <= i; j++) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                pause();
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3UpAt5() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = 0; j <= i; j++) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            pause();
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3UpAt25() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = 0; j <= i; j++) {
                    pause();
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            pause();
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt0() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            pause();
            for (var i = k - 1; i >= 0; i--) {
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt1() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                pause();
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt2() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    pause();
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt3() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    pause();
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt4() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                pause();
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt5() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            pause();
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }

    private static String d3DownAt25() throws InterruptedException {
        var sb = new StringBuilder();
        for (var k = 1; k < 4; k++) {
            for (var i = k - 1; i >= 0; i--) {
                for (var j = i * 2; j > i - 1 - k; j -= 2) {
                    pause();
                    sb.append(j).append(':').append(i).append(':').append(k - 1).append(',');
                    sb.append(i * 2 + k).append(',').append(j - k).append(' ');
                }
                sb.append(i - 1).append('/').append(k * 2).append("; ");
            }
            pause();
            sb.append(k - 1).append('|');
        }
        return sb.toString();
    }
}
