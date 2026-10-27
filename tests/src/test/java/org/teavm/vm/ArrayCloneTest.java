/*
 *  Copyright 2026 TeaVM contributors.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

/**
 * JLS 10.7: "The return type of the clone method of an array type T[] is T[]", and cloning a
 * multidimensional array is shallow (it creates a single new array; subarrays are shared).
 * JLS 15.10.2 / 10.7: the clone is a new array of the same type and length, with the same elements.
 *
 * <p>Each test is compiled separately on purpose: points-to information is global per array type,
 * so a single escaping clone of {@code int[]} anywhere in the program would hide a missing
 * dependency on a clone whose source and result never escape. Tests that use a local array in a
 * non-escaping way must therefore not share a compilation with any other clone of the same type.
 */
@TeaVMTest
@EachTestCompiledSeparately
public class ArrayCloneTest {
    static int sink;
    static Object objectSink;

    @Test
    public void localIntArrayCloneUsedOnlyForLength() {
        int[] a = new int[2];
        sink = a.clone().length;
        assertEquals(2, sink);
    }

    @Test
    public void localCloneUsedOnlyForLength() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        assertEquals(3, aZ.clone().length);
        assertEquals(3, aB.clone().length);
        assertEquals(3, aS.clone().length);
        assertEquals(3, aC.clone().length);
        assertEquals(3, aI.clone().length);
        assertEquals(3, aJ.clone().length);
        assertEquals(3, aF.clone().length);
        assertEquals(3, aD.clone().length);
        assertEquals(3, aL.clone().length);
        assertEquals(3, aO.clone().length);
        assertEquals(3, aII.clone().length);
        assertEquals(3, aLL.clone().length);
        assertEquals(3, aIII.clone().length);
    }

    @Test
    public void localCloneUsedOnlyForElementRead() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        assertTrue(aZ.clone()[1]);
        assertTrue(aB.clone()[1] == 2);
        assertTrue(aS.clone()[1] == 2);
        assertTrue(aC.clone()[1] == 'b');
        assertTrue(aI.clone()[1] == 2);
        assertTrue(aJ.clone()[1] == 2L);
        assertTrue(aF.clone()[1] == 2.5f);
        assertTrue(aD.clone()[1] == 2.5);
        assertTrue(aL.clone()[1] != null);
        assertTrue(aO.clone()[1] != null);
        assertTrue(aII.clone()[1].length == 2);
        assertTrue(aLL.clone()[1].length == 2);
        assertTrue(aIII.clone()[1].length == 2);
    }

    @Test
    public void localClonePassedToMethod() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        assertTrue(identity(aZ.clone()) instanceof boolean[]);
        assertTrue(identity(aB.clone()) instanceof byte[]);
        assertTrue(identity(aS.clone()) instanceof short[]);
        assertTrue(identity(aC.clone()) instanceof char[]);
        assertTrue(identity(aI.clone()) instanceof int[]);
        assertTrue(identity(aJ.clone()) instanceof long[]);
        assertTrue(identity(aF.clone()) instanceof float[]);
        assertTrue(identity(aD.clone()) instanceof double[]);
        assertTrue(identity(aL.clone()) instanceof String[]);
        assertTrue(identity(aO.clone()) instanceof Object[]);
        assertTrue(identity(aII.clone()) instanceof int[][]);
        assertTrue(identity(aLL.clone()) instanceof String[][]);
        assertTrue(identity(aIII.clone()) instanceof int[][][]);
    }

    @Test
    public void localCloneStoredInField() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        objectSink = aZ.clone();
        assertEquals(3, ((boolean[]) objectSink).length);
        objectSink = aB.clone();
        assertEquals(3, ((byte[]) objectSink).length);
        objectSink = aS.clone();
        assertEquals(3, ((short[]) objectSink).length);
        objectSink = aC.clone();
        assertEquals(3, ((char[]) objectSink).length);
        objectSink = aI.clone();
        assertEquals(3, ((int[]) objectSink).length);
        objectSink = aJ.clone();
        assertEquals(3, ((long[]) objectSink).length);
        objectSink = aF.clone();
        assertEquals(3, ((float[]) objectSink).length);
        objectSink = aD.clone();
        assertEquals(3, ((double[]) objectSink).length);
        objectSink = aL.clone();
        assertEquals(3, ((String[]) objectSink).length);
        objectSink = aO.clone();
        assertEquals(3, ((Object[]) objectSink).length);
        objectSink = aII.clone();
        assertEquals(3, ((int[][]) objectSink).length);
        objectSink = aLL.clone();
        assertEquals(3, ((String[][]) objectSink).length);
        objectSink = aIII.clone();
        assertEquals(3, ((int[][][]) objectSink).length);
    }

    @Test
    public void localCloneUnused() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        aZ.clone();
        assertEquals(3, aZ.length);
        aB.clone();
        assertEquals(3, aB.length);
        aS.clone();
        assertEquals(3, aS.length);
        aC.clone();
        assertEquals(3, aC.length);
        aI.clone();
        assertEquals(3, aI.length);
        aJ.clone();
        assertEquals(3, aJ.length);
        aF.clone();
        assertEquals(3, aF.length);
        aD.clone();
        assertEquals(3, aD.length);
        aL.clone();
        assertEquals(3, aL.length);
        aO.clone();
        assertEquals(3, aO.length);
        aII.clone();
        assertEquals(3, aII.length);
        aLL.clone();
        assertEquals(3, aLL.length);
        aIII.clone();
        assertEquals(3, aIII.length);
    }

    @Test
    public void localCloneWrittenAndCompared() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        boolean[] cZ = aZ.clone();
        cZ[0] = cZ[2];
        assertTrue(cZ[0] == cZ[2] && aZ[0] != aZ[2] && cZ[1] == aZ[1]);
        byte[] cB = aB.clone();
        cB[0] = cB[2];
        assertTrue(cB[0] == cB[2] && aB[0] != aB[2] && cB[1] == aB[1]);
        short[] cS = aS.clone();
        cS[0] = cS[2];
        assertTrue(cS[0] == cS[2] && aS[0] != aS[2] && cS[1] == aS[1]);
        char[] cC = aC.clone();
        cC[0] = cC[2];
        assertTrue(cC[0] == cC[2] && aC[0] != aC[2] && cC[1] == aC[1]);
        int[] cI = aI.clone();
        cI[0] = cI[2];
        assertTrue(cI[0] == cI[2] && aI[0] != aI[2] && cI[1] == aI[1]);
        long[] cJ = aJ.clone();
        cJ[0] = cJ[2];
        assertTrue(cJ[0] == cJ[2] && aJ[0] != aJ[2] && cJ[1] == aJ[1]);
        float[] cF = aF.clone();
        cF[0] = cF[2];
        assertTrue(cF[0] == cF[2] && aF[0] != aF[2] && cF[1] == aF[1]);
        double[] cD = aD.clone();
        cD[0] = cD[2];
        assertTrue(cD[0] == cD[2] && aD[0] != aD[2] && cD[1] == aD[1]);
        String[] cL = aL.clone();
        cL[0] = cL[2];
        assertTrue(cL[0] == cL[2] && aL[0] != aL[2] && cL[1] == aL[1]);
        Object[] cO = aO.clone();
        cO[0] = cO[2];
        assertTrue(cO[0] == cO[2] && aO[0] != aO[2] && cO[1] == aO[1]);
        int[][] cII = aII.clone();
        cII[0] = cII[2];
        assertTrue(cII[0] == cII[2] && aII[0] != aII[2] && cII[1] == aII[1]);
        String[][] cLL = aLL.clone();
        cLL[0] = cLL[2];
        assertTrue(cLL[0] == cLL[2] && aLL[0] != aLL[2] && cLL[1] == aLL[1]);
        int[][][] cIII = aIII.clone();
        cIII[0] = cIII[2];
        assertTrue(cIII[0] == cIII[2] && aIII[0] != aIII[2] && cIII[1] == aIII[1]);
    }

    @Test
    public void parameterClone() {
        checkParameters(
                new boolean[] { false, true, true },
                new byte[] { 1, 2, 3 },
                new short[] { 1, 2, 3 },
                new char[] { 'a', 'b', 'c' },
                new int[] { 1, 2, 3 },
                new long[] { 1L, 2L, 3L },
                new float[] { 1f, 2.5f, 3f },
                new double[] { 1.0, 2.5, 3.0 },
                new String[] { "a", "b", "c" },
                new Object[] { new Object(), new Object(), new Object() },
                new int[][] { { 1 }, { 2, 3 }, { 4, 5, 6 } },
                new String[][] { { "a" }, { "b", "c" }, { } },
                new int[][][] { { { 1 } }, { { 2 }, { 3 } }, { } });
    }

    private static void checkParameters(
            boolean[] aZ,
            byte[] aB,
            short[] aS,
            char[] aC,
            int[] aI,
            long[] aJ,
            float[] aF,
            double[] aD,
            String[] aL,
            Object[] aO,
            int[][] aII,
            String[][] aLL,
            int[][][] aIII) {
        assertEquals(3, aZ.clone().length);
        assertEquals(3, aB.clone().length);
        assertEquals(3, aS.clone().length);
        assertEquals(3, aC.clone().length);
        assertEquals(3, aI.clone().length);
        assertEquals(3, aJ.clone().length);
        assertEquals(3, aF.clone().length);
        assertEquals(3, aD.clone().length);
        assertEquals(3, aL.clone().length);
        assertEquals(3, aO.clone().length);
        assertEquals(3, aII.clone().length);
        assertEquals(3, aLL.clone().length);
        assertEquals(3, aIII.clone().length);
        assertTrue(aZ.clone()[1]);
        assertTrue(aB.clone()[1] == 2);
        assertTrue(aS.clone()[1] == 2);
        assertTrue(aC.clone()[1] == 'b');
        assertTrue(aI.clone()[1] == 2);
        assertTrue(aJ.clone()[1] == 2L);
        assertTrue(aF.clone()[1] == 2.5f);
        assertTrue(aD.clone()[1] == 2.5);
        assertTrue(aL.clone()[1] != null);
        assertTrue(aO.clone()[1] != null);
        assertTrue(aII.clone()[1].length == 2);
        assertTrue(aLL.clone()[1].length == 2);
        assertTrue(aIII.clone()[1].length == 2);
        assertTrue(identity(aZ.clone()) instanceof boolean[]);
        assertTrue(identity(aB.clone()) instanceof byte[]);
        assertTrue(identity(aS.clone()) instanceof short[]);
        assertTrue(identity(aC.clone()) instanceof char[]);
        assertTrue(identity(aI.clone()) instanceof int[]);
        assertTrue(identity(aJ.clone()) instanceof long[]);
        assertTrue(identity(aF.clone()) instanceof float[]);
        assertTrue(identity(aD.clone()) instanceof double[]);
        assertTrue(identity(aL.clone()) instanceof String[]);
        assertTrue(identity(aO.clone()) instanceof Object[]);
        assertTrue(identity(aII.clone()) instanceof int[][]);
        assertTrue(identity(aLL.clone()) instanceof String[][]);
        assertTrue(identity(aIII.clone()) instanceof int[][][]);
        objectSink = aZ.clone();
        assertEquals(3, ((boolean[]) objectSink).length);
        objectSink = aB.clone();
        assertEquals(3, ((byte[]) objectSink).length);
        objectSink = aS.clone();
        assertEquals(3, ((short[]) objectSink).length);
        objectSink = aC.clone();
        assertEquals(3, ((char[]) objectSink).length);
        objectSink = aI.clone();
        assertEquals(3, ((int[]) objectSink).length);
        objectSink = aJ.clone();
        assertEquals(3, ((long[]) objectSink).length);
        objectSink = aF.clone();
        assertEquals(3, ((float[]) objectSink).length);
        objectSink = aD.clone();
        assertEquals(3, ((double[]) objectSink).length);
        objectSink = aL.clone();
        assertEquals(3, ((String[]) objectSink).length);
        objectSink = aO.clone();
        assertEquals(3, ((Object[]) objectSink).length);
        objectSink = aII.clone();
        assertEquals(3, ((int[][]) objectSink).length);
        objectSink = aLL.clone();
        assertEquals(3, ((String[][]) objectSink).length);
        objectSink = aIII.clone();
        assertEquals(3, ((int[][][]) objectSink).length);
        aZ.clone();
        assertEquals(3, aZ.length);
        aB.clone();
        assertEquals(3, aB.length);
        aS.clone();
        assertEquals(3, aS.length);
        aC.clone();
        assertEquals(3, aC.length);
        aI.clone();
        assertEquals(3, aI.length);
        aJ.clone();
        assertEquals(3, aJ.length);
        aF.clone();
        assertEquals(3, aF.length);
        aD.clone();
        assertEquals(3, aD.length);
        aL.clone();
        assertEquals(3, aL.length);
        aO.clone();
        assertEquals(3, aO.length);
        aII.clone();
        assertEquals(3, aII.length);
        aLL.clone();
        assertEquals(3, aLL.length);
        aIII.clone();
        assertEquals(3, aIII.length);
        boolean[] cZ = aZ.clone();
        cZ[0] = cZ[2];
        assertTrue(cZ[0] == cZ[2] && aZ[0] != aZ[2] && cZ[1] == aZ[1]);
        byte[] cB = aB.clone();
        cB[0] = cB[2];
        assertTrue(cB[0] == cB[2] && aB[0] != aB[2] && cB[1] == aB[1]);
        short[] cS = aS.clone();
        cS[0] = cS[2];
        assertTrue(cS[0] == cS[2] && aS[0] != aS[2] && cS[1] == aS[1]);
        char[] cC = aC.clone();
        cC[0] = cC[2];
        assertTrue(cC[0] == cC[2] && aC[0] != aC[2] && cC[1] == aC[1]);
        int[] cI = aI.clone();
        cI[0] = cI[2];
        assertTrue(cI[0] == cI[2] && aI[0] != aI[2] && cI[1] == aI[1]);
        long[] cJ = aJ.clone();
        cJ[0] = cJ[2];
        assertTrue(cJ[0] == cJ[2] && aJ[0] != aJ[2] && cJ[1] == aJ[1]);
        float[] cF = aF.clone();
        cF[0] = cF[2];
        assertTrue(cF[0] == cF[2] && aF[0] != aF[2] && cF[1] == aF[1]);
        double[] cD = aD.clone();
        cD[0] = cD[2];
        assertTrue(cD[0] == cD[2] && aD[0] != aD[2] && cD[1] == aD[1]);
        String[] cL = aL.clone();
        cL[0] = cL[2];
        assertTrue(cL[0] == cL[2] && aL[0] != aL[2] && cL[1] == aL[1]);
        Object[] cO = aO.clone();
        cO[0] = cO[2];
        assertTrue(cO[0] == cO[2] && aO[0] != aO[2] && cO[1] == aO[1]);
        int[][] cII = aII.clone();
        cII[0] = cII[2];
        assertTrue(cII[0] == cII[2] && aII[0] != aII[2] && cII[1] == aII[1]);
        String[][] cLL = aLL.clone();
        cLL[0] = cLL[2];
        assertTrue(cLL[0] == cLL[2] && aLL[0] != aLL[2] && cLL[1] == aLL[1]);
        int[][][] cIII = aIII.clone();
        cIII[0] = cIII[2];
        assertTrue(cIII[0] == cIII[2] && aIII[0] != aIII[2] && cIII[1] == aIII[1]);
    }

    private static boolean[] fieldZ = { false, true, true };
    private static byte[] fieldB = { 1, 2, 3 };
    private static short[] fieldS = { 1, 2, 3 };
    private static char[] fieldC = { 'a', 'b', 'c' };
    private static int[] fieldI = { 1, 2, 3 };
    private static long[] fieldJ = { 1L, 2L, 3L };
    private static float[] fieldF = { 1f, 2.5f, 3f };
    private static double[] fieldD = { 1.0, 2.5, 3.0 };
    private static String[] fieldL = { "a", "b", "c" };
    private static Object[] fieldO = { new Object(), new Object(), new Object() };
    private static int[][] fieldII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
    private static String[][] fieldLL = { { "a" }, { "b", "c" }, { } };
    private static int[][][] fieldIII = { { { 1 } }, { { 2 }, { 3 } }, { } };

    @Test
    public void fieldClone() {
        assertEquals(3, fieldZ.clone().length);
        assertEquals(3, fieldB.clone().length);
        assertEquals(3, fieldS.clone().length);
        assertEquals(3, fieldC.clone().length);
        assertEquals(3, fieldI.clone().length);
        assertEquals(3, fieldJ.clone().length);
        assertEquals(3, fieldF.clone().length);
        assertEquals(3, fieldD.clone().length);
        assertEquals(3, fieldL.clone().length);
        assertEquals(3, fieldO.clone().length);
        assertEquals(3, fieldII.clone().length);
        assertEquals(3, fieldLL.clone().length);
        assertEquals(3, fieldIII.clone().length);
        assertTrue(fieldZ.clone()[1]);
        assertTrue(fieldB.clone()[1] == 2);
        assertTrue(fieldS.clone()[1] == 2);
        assertTrue(fieldC.clone()[1] == 'b');
        assertTrue(fieldI.clone()[1] == 2);
        assertTrue(fieldJ.clone()[1] == 2L);
        assertTrue(fieldF.clone()[1] == 2.5f);
        assertTrue(fieldD.clone()[1] == 2.5);
        assertTrue(fieldL.clone()[1] != null);
        assertTrue(fieldO.clone()[1] != null);
        assertTrue(fieldII.clone()[1].length == 2);
        assertTrue(fieldLL.clone()[1].length == 2);
        assertTrue(fieldIII.clone()[1].length == 2);
        assertTrue(identity(fieldZ.clone()) instanceof boolean[]);
        assertTrue(identity(fieldB.clone()) instanceof byte[]);
        assertTrue(identity(fieldS.clone()) instanceof short[]);
        assertTrue(identity(fieldC.clone()) instanceof char[]);
        assertTrue(identity(fieldI.clone()) instanceof int[]);
        assertTrue(identity(fieldJ.clone()) instanceof long[]);
        assertTrue(identity(fieldF.clone()) instanceof float[]);
        assertTrue(identity(fieldD.clone()) instanceof double[]);
        assertTrue(identity(fieldL.clone()) instanceof String[]);
        assertTrue(identity(fieldO.clone()) instanceof Object[]);
        assertTrue(identity(fieldII.clone()) instanceof int[][]);
        assertTrue(identity(fieldLL.clone()) instanceof String[][]);
        assertTrue(identity(fieldIII.clone()) instanceof int[][][]);
        objectSink = fieldZ.clone();
        assertEquals(3, ((boolean[]) objectSink).length);
        objectSink = fieldB.clone();
        assertEquals(3, ((byte[]) objectSink).length);
        objectSink = fieldS.clone();
        assertEquals(3, ((short[]) objectSink).length);
        objectSink = fieldC.clone();
        assertEquals(3, ((char[]) objectSink).length);
        objectSink = fieldI.clone();
        assertEquals(3, ((int[]) objectSink).length);
        objectSink = fieldJ.clone();
        assertEquals(3, ((long[]) objectSink).length);
        objectSink = fieldF.clone();
        assertEquals(3, ((float[]) objectSink).length);
        objectSink = fieldD.clone();
        assertEquals(3, ((double[]) objectSink).length);
        objectSink = fieldL.clone();
        assertEquals(3, ((String[]) objectSink).length);
        objectSink = fieldO.clone();
        assertEquals(3, ((Object[]) objectSink).length);
        objectSink = fieldII.clone();
        assertEquals(3, ((int[][]) objectSink).length);
        objectSink = fieldLL.clone();
        assertEquals(3, ((String[][]) objectSink).length);
        objectSink = fieldIII.clone();
        assertEquals(3, ((int[][][]) objectSink).length);
        fieldZ.clone();
        assertEquals(3, fieldZ.length);
        fieldB.clone();
        assertEquals(3, fieldB.length);
        fieldS.clone();
        assertEquals(3, fieldS.length);
        fieldC.clone();
        assertEquals(3, fieldC.length);
        fieldI.clone();
        assertEquals(3, fieldI.length);
        fieldJ.clone();
        assertEquals(3, fieldJ.length);
        fieldF.clone();
        assertEquals(3, fieldF.length);
        fieldD.clone();
        assertEquals(3, fieldD.length);
        fieldL.clone();
        assertEquals(3, fieldL.length);
        fieldO.clone();
        assertEquals(3, fieldO.length);
        fieldII.clone();
        assertEquals(3, fieldII.length);
        fieldLL.clone();
        assertEquals(3, fieldLL.length);
        fieldIII.clone();
        assertEquals(3, fieldIII.length);
        boolean[] cZ = fieldZ.clone();
        cZ[0] = cZ[2];
        assertTrue(cZ[0] == cZ[2] && fieldZ[0] != fieldZ[2] && cZ[1] == fieldZ[1]);
        byte[] cB = fieldB.clone();
        cB[0] = cB[2];
        assertTrue(cB[0] == cB[2] && fieldB[0] != fieldB[2] && cB[1] == fieldB[1]);
        short[] cS = fieldS.clone();
        cS[0] = cS[2];
        assertTrue(cS[0] == cS[2] && fieldS[0] != fieldS[2] && cS[1] == fieldS[1]);
        char[] cC = fieldC.clone();
        cC[0] = cC[2];
        assertTrue(cC[0] == cC[2] && fieldC[0] != fieldC[2] && cC[1] == fieldC[1]);
        int[] cI = fieldI.clone();
        cI[0] = cI[2];
        assertTrue(cI[0] == cI[2] && fieldI[0] != fieldI[2] && cI[1] == fieldI[1]);
        long[] cJ = fieldJ.clone();
        cJ[0] = cJ[2];
        assertTrue(cJ[0] == cJ[2] && fieldJ[0] != fieldJ[2] && cJ[1] == fieldJ[1]);
        float[] cF = fieldF.clone();
        cF[0] = cF[2];
        assertTrue(cF[0] == cF[2] && fieldF[0] != fieldF[2] && cF[1] == fieldF[1]);
        double[] cD = fieldD.clone();
        cD[0] = cD[2];
        assertTrue(cD[0] == cD[2] && fieldD[0] != fieldD[2] && cD[1] == fieldD[1]);
        String[] cL = fieldL.clone();
        cL[0] = cL[2];
        assertTrue(cL[0] == cL[2] && fieldL[0] != fieldL[2] && cL[1] == fieldL[1]);
        Object[] cO = fieldO.clone();
        cO[0] = cO[2];
        assertTrue(cO[0] == cO[2] && fieldO[0] != fieldO[2] && cO[1] == fieldO[1]);
        int[][] cII = fieldII.clone();
        cII[0] = cII[2];
        assertTrue(cII[0] == cII[2] && fieldII[0] != fieldII[2] && cII[1] == fieldII[1]);
        String[][] cLL = fieldLL.clone();
        cLL[0] = cLL[2];
        assertTrue(cLL[0] == cLL[2] && fieldLL[0] != fieldLL[2] && cLL[1] == fieldLL[1]);
        int[][][] cIII = fieldIII.clone();
        cIII[0] = cIII[2];
        assertTrue(cIII[0] == cIII[2] && fieldIII[0] != fieldIII[2] && cIII[1] == fieldIII[1]);
    }

    private static boolean[] makeZ() {
        return new boolean[] { false, true, true };
    }

    private static byte[] makeB() {
        return new byte[] { 1, 2, 3 };
    }

    private static short[] makeS() {
        return new short[] { 1, 2, 3 };
    }

    private static char[] makeC() {
        return new char[] { 'a', 'b', 'c' };
    }

    private static int[] makeI() {
        return new int[] { 1, 2, 3 };
    }

    private static long[] makeJ() {
        return new long[] { 1L, 2L, 3L };
    }

    private static float[] makeF() {
        return new float[] { 1f, 2.5f, 3f };
    }

    private static double[] makeD() {
        return new double[] { 1.0, 2.5, 3.0 };
    }

    private static String[] makeL() {
        return new String[] { "a", "b", "c" };
    }

    private static Object[] makeO() {
        return new Object[] { new Object(), new Object(), new Object() };
    }

    private static int[][] makeII() {
        return new int[][] { { 1 }, { 2, 3 }, { 4, 5, 6 } };
    }

    private static String[][] makeLL() {
        return new String[][] { { "a" }, { "b", "c" }, { } };
    }

    private static int[][][] makeIII() {
        return new int[][][] { { { 1 } }, { { 2 }, { 3 } }, { } };
    }

    @Test
    public void returnValueClone() {
        assertEquals(3, makeZ().clone().length);
        assertEquals(3, makeB().clone().length);
        assertEquals(3, makeS().clone().length);
        assertEquals(3, makeC().clone().length);
        assertEquals(3, makeI().clone().length);
        assertEquals(3, makeJ().clone().length);
        assertEquals(3, makeF().clone().length);
        assertEquals(3, makeD().clone().length);
        assertEquals(3, makeL().clone().length);
        assertEquals(3, makeO().clone().length);
        assertEquals(3, makeII().clone().length);
        assertEquals(3, makeLL().clone().length);
        assertEquals(3, makeIII().clone().length);
        assertTrue(makeZ().clone()[1]);
        assertTrue(makeB().clone()[1] == 2);
        assertTrue(makeS().clone()[1] == 2);
        assertTrue(makeC().clone()[1] == 'b');
        assertTrue(makeI().clone()[1] == 2);
        assertTrue(makeJ().clone()[1] == 2L);
        assertTrue(makeF().clone()[1] == 2.5f);
        assertTrue(makeD().clone()[1] == 2.5);
        assertTrue(makeL().clone()[1] != null);
        assertTrue(makeO().clone()[1] != null);
        assertTrue(makeII().clone()[1].length == 2);
        assertTrue(makeLL().clone()[1].length == 2);
        assertTrue(makeIII().clone()[1].length == 2);
        assertTrue(identity(makeZ().clone()) instanceof boolean[]);
        assertTrue(identity(makeB().clone()) instanceof byte[]);
        assertTrue(identity(makeS().clone()) instanceof short[]);
        assertTrue(identity(makeC().clone()) instanceof char[]);
        assertTrue(identity(makeI().clone()) instanceof int[]);
        assertTrue(identity(makeJ().clone()) instanceof long[]);
        assertTrue(identity(makeF().clone()) instanceof float[]);
        assertTrue(identity(makeD().clone()) instanceof double[]);
        assertTrue(identity(makeL().clone()) instanceof String[]);
        assertTrue(identity(makeO().clone()) instanceof Object[]);
        assertTrue(identity(makeII().clone()) instanceof int[][]);
        assertTrue(identity(makeLL().clone()) instanceof String[][]);
        assertTrue(identity(makeIII().clone()) instanceof int[][][]);
        objectSink = makeZ().clone();
        assertEquals(3, ((boolean[]) objectSink).length);
        objectSink = makeB().clone();
        assertEquals(3, ((byte[]) objectSink).length);
        objectSink = makeS().clone();
        assertEquals(3, ((short[]) objectSink).length);
        objectSink = makeC().clone();
        assertEquals(3, ((char[]) objectSink).length);
        objectSink = makeI().clone();
        assertEquals(3, ((int[]) objectSink).length);
        objectSink = makeJ().clone();
        assertEquals(3, ((long[]) objectSink).length);
        objectSink = makeF().clone();
        assertEquals(3, ((float[]) objectSink).length);
        objectSink = makeD().clone();
        assertEquals(3, ((double[]) objectSink).length);
        objectSink = makeL().clone();
        assertEquals(3, ((String[]) objectSink).length);
        objectSink = makeO().clone();
        assertEquals(3, ((Object[]) objectSink).length);
        objectSink = makeII().clone();
        assertEquals(3, ((int[][]) objectSink).length);
        objectSink = makeLL().clone();
        assertEquals(3, ((String[][]) objectSink).length);
        objectSink = makeIII().clone();
        assertEquals(3, ((int[][][]) objectSink).length);
        boolean[] runusedZ = makeZ();
        byte[] runusedB = makeB();
        short[] runusedS = makeS();
        char[] runusedC = makeC();
        int[] runusedI = makeI();
        long[] runusedJ = makeJ();
        float[] runusedF = makeF();
        double[] runusedD = makeD();
        String[] runusedL = makeL();
        Object[] runusedO = makeO();
        int[][] runusedII = makeII();
        String[][] runusedLL = makeLL();
        int[][][] runusedIII = makeIII();
        runusedZ.clone();
        assertEquals(3, runusedZ.length);
        runusedB.clone();
        assertEquals(3, runusedB.length);
        runusedS.clone();
        assertEquals(3, runusedS.length);
        runusedC.clone();
        assertEquals(3, runusedC.length);
        runusedI.clone();
        assertEquals(3, runusedI.length);
        runusedJ.clone();
        assertEquals(3, runusedJ.length);
        runusedF.clone();
        assertEquals(3, runusedF.length);
        runusedD.clone();
        assertEquals(3, runusedD.length);
        runusedL.clone();
        assertEquals(3, runusedL.length);
        runusedO.clone();
        assertEquals(3, runusedO.length);
        runusedII.clone();
        assertEquals(3, runusedII.length);
        runusedLL.clone();
        assertEquals(3, runusedLL.length);
        runusedIII.clone();
        assertEquals(3, runusedIII.length);
        boolean[] rwriteZ = makeZ();
        byte[] rwriteB = makeB();
        short[] rwriteS = makeS();
        char[] rwriteC = makeC();
        int[] rwriteI = makeI();
        long[] rwriteJ = makeJ();
        float[] rwriteF = makeF();
        double[] rwriteD = makeD();
        String[] rwriteL = makeL();
        Object[] rwriteO = makeO();
        int[][] rwriteII = makeII();
        String[][] rwriteLL = makeLL();
        int[][][] rwriteIII = makeIII();
        boolean[] cZ = rwriteZ.clone();
        cZ[0] = cZ[2];
        assertTrue(cZ[0] == cZ[2] && rwriteZ[0] != rwriteZ[2] && cZ[1] == rwriteZ[1]);
        byte[] cB = rwriteB.clone();
        cB[0] = cB[2];
        assertTrue(cB[0] == cB[2] && rwriteB[0] != rwriteB[2] && cB[1] == rwriteB[1]);
        short[] cS = rwriteS.clone();
        cS[0] = cS[2];
        assertTrue(cS[0] == cS[2] && rwriteS[0] != rwriteS[2] && cS[1] == rwriteS[1]);
        char[] cC = rwriteC.clone();
        cC[0] = cC[2];
        assertTrue(cC[0] == cC[2] && rwriteC[0] != rwriteC[2] && cC[1] == rwriteC[1]);
        int[] cI = rwriteI.clone();
        cI[0] = cI[2];
        assertTrue(cI[0] == cI[2] && rwriteI[0] != rwriteI[2] && cI[1] == rwriteI[1]);
        long[] cJ = rwriteJ.clone();
        cJ[0] = cJ[2];
        assertTrue(cJ[0] == cJ[2] && rwriteJ[0] != rwriteJ[2] && cJ[1] == rwriteJ[1]);
        float[] cF = rwriteF.clone();
        cF[0] = cF[2];
        assertTrue(cF[0] == cF[2] && rwriteF[0] != rwriteF[2] && cF[1] == rwriteF[1]);
        double[] cD = rwriteD.clone();
        cD[0] = cD[2];
        assertTrue(cD[0] == cD[2] && rwriteD[0] != rwriteD[2] && cD[1] == rwriteD[1]);
        String[] cL = rwriteL.clone();
        cL[0] = cL[2];
        assertTrue(cL[0] == cL[2] && rwriteL[0] != rwriteL[2] && cL[1] == rwriteL[1]);
        Object[] cO = rwriteO.clone();
        cO[0] = cO[2];
        assertTrue(cO[0] == cO[2] && rwriteO[0] != rwriteO[2] && cO[1] == rwriteO[1]);
        int[][] cII = rwriteII.clone();
        cII[0] = cII[2];
        assertTrue(cII[0] == cII[2] && rwriteII[0] != rwriteII[2] && cII[1] == rwriteII[1]);
        String[][] cLL = rwriteLL.clone();
        cLL[0] = cLL[2];
        assertTrue(cLL[0] == cLL[2] && rwriteLL[0] != rwriteLL[2] && cLL[1] == rwriteLL[1]);
        int[][][] cIII = rwriteIII.clone();
        cIII[0] = cIII[2];
        assertTrue(cIII[0] == cIII[2] && rwriteIII[0] != rwriteIII[2] && cIII[1] == rwriteIII[1]);
    }

    @Test
    public void cloneOfLocalInsideLambda() {
        IntSupplier f = () -> {
            boolean[] aZ = { false, true, true };
            byte[] aB = { 1, 2, 3 };
            short[] aS = { 1, 2, 3 };
            char[] aC = { 'a', 'b', 'c' };
            int[] aI = { 1, 2, 3 };
            long[] aJ = { 1L, 2L, 3L };
            float[] aF = { 1f, 2.5f, 3f };
            double[] aD = { 1.0, 2.5, 3.0 };
            String[] aL = { "a", "b", "c" };
            Object[] aO = { new Object(), new Object(), new Object() };
            int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
            String[][] aLL = { { "a" }, { "b", "c" }, { } };
            int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
            int n = 0;
            n += aZ.clone().length;
            n += aZ.clone()[1] ? 1 : 0;
            n += aB.clone().length;
            n += aB.clone()[1] == 2 ? 1 : 0;
            n += aS.clone().length;
            n += aS.clone()[1] == 2 ? 1 : 0;
            n += aC.clone().length;
            n += aC.clone()[1] == 'b' ? 1 : 0;
            n += aI.clone().length;
            n += aI.clone()[1] == 2 ? 1 : 0;
            n += aJ.clone().length;
            n += aJ.clone()[1] == 2L ? 1 : 0;
            n += aF.clone().length;
            n += aF.clone()[1] == 2.5f ? 1 : 0;
            n += aD.clone().length;
            n += aD.clone()[1] == 2.5 ? 1 : 0;
            n += aL.clone().length;
            n += aL.clone()[1] != null ? 1 : 0;
            n += aO.clone().length;
            n += aO.clone()[1] != null ? 1 : 0;
            n += aII.clone().length;
            n += aII.clone()[1].length == 2 ? 1 : 0;
            n += aLL.clone().length;
            n += aLL.clone()[1].length == 2 ? 1 : 0;
            n += aIII.clone().length;
            n += aIII.clone()[1].length == 2 ? 1 : 0;
            return n;
        };
        assertEquals(52, f.getAsInt());
    }

    @Test
    public void cloneOfCapturedArrayInsideLambda() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        IntSupplier f = () -> {
            int n = 0;
            n += aZ.clone().length;
            n += aZ.clone()[1] ? 1 : 0;
            n += aB.clone().length;
            n += aB.clone()[1] == 2 ? 1 : 0;
            n += aS.clone().length;
            n += aS.clone()[1] == 2 ? 1 : 0;
            n += aC.clone().length;
            n += aC.clone()[1] == 'b' ? 1 : 0;
            n += aI.clone().length;
            n += aI.clone()[1] == 2 ? 1 : 0;
            n += aJ.clone().length;
            n += aJ.clone()[1] == 2L ? 1 : 0;
            n += aF.clone().length;
            n += aF.clone()[1] == 2.5f ? 1 : 0;
            n += aD.clone().length;
            n += aD.clone()[1] == 2.5 ? 1 : 0;
            n += aL.clone().length;
            n += aL.clone()[1] != null ? 1 : 0;
            n += aO.clone().length;
            n += aO.clone()[1] != null ? 1 : 0;
            n += aII.clone().length;
            n += aII.clone()[1].length == 2 ? 1 : 0;
            n += aLL.clone().length;
            n += aLL.clone()[1].length == 2 ? 1 : 0;
            n += aIII.clone().length;
            n += aIII.clone()[1].length == 2 ? 1 : 0;
            return n;
        };
        assertEquals(52, f.getAsInt());
    }

    @Test
    public void cloneHasSameTypeAndIsNewArray() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        assertSame(boolean[].class, aZ.clone().getClass());
        assertNotSame(aZ, aZ.clone());
        assertSame(byte[].class, aB.clone().getClass());
        assertNotSame(aB, aB.clone());
        assertSame(short[].class, aS.clone().getClass());
        assertNotSame(aS, aS.clone());
        assertSame(char[].class, aC.clone().getClass());
        assertNotSame(aC, aC.clone());
        assertSame(int[].class, aI.clone().getClass());
        assertNotSame(aI, aI.clone());
        assertSame(long[].class, aJ.clone().getClass());
        assertNotSame(aJ, aJ.clone());
        assertSame(float[].class, aF.clone().getClass());
        assertNotSame(aF, aF.clone());
        assertSame(double[].class, aD.clone().getClass());
        assertNotSame(aD, aD.clone());
        assertSame(String[].class, aL.clone().getClass());
        assertNotSame(aL, aL.clone());
        assertSame(Object[].class, aO.clone().getClass());
        assertNotSame(aO, aO.clone());
        assertSame(int[][].class, aII.clone().getClass());
        assertNotSame(aII, aII.clone());
        assertSame(String[][].class, aLL.clone().getClass());
        assertNotSame(aLL, aLL.clone());
        assertSame(int[][][].class, aIII.clone().getClass());
        assertNotSame(aIII, aIII.clone());
        assertSame(aII[1], aII.clone()[1]);
        assertSame(aLL[1], aLL.clone()[1]);
    }

    @Test
    public void localArrayGetClassUsedOnlyForName() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        assertEquals("[Z", aZ.getClass().getName());
        assertEquals("[B", aB.getClass().getName());
        assertEquals("[S", aS.getClass().getName());
        assertEquals("[C", aC.getClass().getName());
        assertEquals("[I", aI.getClass().getName());
        assertEquals("[J", aJ.getClass().getName());
        assertEquals("[F", aF.getClass().getName());
        assertEquals("[D", aD.getClass().getName());
        assertEquals("[Ljava.lang.String;", aL.getClass().getName());
        assertEquals("[Ljava.lang.Object;", aO.getClass().getName());
        assertEquals("[[I", aII.getClass().getName());
        assertEquals("[[Ljava.lang.String;", aLL.getClass().getName());
        assertEquals("[[[I", aIII.getClass().getName());
    }

    @Test
    public void localArrayHashCodeAndEquals() {
        boolean[] aZ = { false, true, true };
        byte[] aB = { 1, 2, 3 };
        short[] aS = { 1, 2, 3 };
        char[] aC = { 'a', 'b', 'c' };
        int[] aI = { 1, 2, 3 };
        long[] aJ = { 1L, 2L, 3L };
        float[] aF = { 1f, 2.5f, 3f };
        double[] aD = { 1.0, 2.5, 3.0 };
        String[] aL = { "a", "b", "c" };
        Object[] aO = { new Object(), new Object(), new Object() };
        int[][] aII = { { 1 }, { 2, 3 }, { 4, 5, 6 } };
        String[][] aLL = { { "a" }, { "b", "c" }, { } };
        int[][][] aIII = { { { 1 } }, { { 2 }, { 3 } }, { } };
        assertEquals(aZ.hashCode(), aZ.hashCode());
        assertTrue(aZ.equals(aZ));
        assertFalse(aZ.equals(aZ.clone()));
        assertEquals(aB.hashCode(), aB.hashCode());
        assertTrue(aB.equals(aB));
        assertFalse(aB.equals(aB.clone()));
        assertEquals(aS.hashCode(), aS.hashCode());
        assertTrue(aS.equals(aS));
        assertFalse(aS.equals(aS.clone()));
        assertEquals(aC.hashCode(), aC.hashCode());
        assertTrue(aC.equals(aC));
        assertFalse(aC.equals(aC.clone()));
        assertEquals(aI.hashCode(), aI.hashCode());
        assertTrue(aI.equals(aI));
        assertFalse(aI.equals(aI.clone()));
        assertEquals(aJ.hashCode(), aJ.hashCode());
        assertTrue(aJ.equals(aJ));
        assertFalse(aJ.equals(aJ.clone()));
        assertEquals(aF.hashCode(), aF.hashCode());
        assertTrue(aF.equals(aF));
        assertFalse(aF.equals(aF.clone()));
        assertEquals(aD.hashCode(), aD.hashCode());
        assertTrue(aD.equals(aD));
        assertFalse(aD.equals(aD.clone()));
        assertEquals(aL.hashCode(), aL.hashCode());
        assertTrue(aL.equals(aL));
        assertFalse(aL.equals(aL.clone()));
        assertEquals(aO.hashCode(), aO.hashCode());
        assertTrue(aO.equals(aO));
        assertFalse(aO.equals(aO.clone()));
        assertEquals(aII.hashCode(), aII.hashCode());
        assertTrue(aII.equals(aII));
        assertFalse(aII.equals(aII.clone()));
        assertEquals(aLL.hashCode(), aLL.hashCode());
        assertTrue(aLL.equals(aLL));
        assertFalse(aLL.equals(aLL.clone()));
        assertEquals(aIII.hashCode(), aIII.hashCode());
        assertTrue(aIII.equals(aIII));
        assertFalse(aIII.equals(aIII.clone()));
    }

    private static Object identity(Object o) {
        return o;
    }
}
