/*
 *  Copyright 2014 Alexey Andreev.
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

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/**
 * @author Elena Semukhina
 */

package org.teavm.classlib.java.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class BigIntegerHashCodeTest {
    /**
     * Test hash codes for the same object
     */
    @Test
    public void testSameObject() {
        String value1 = "12378246728727834290276457386374882976782849";
        String value2 = "-5634562095872038262928728727834290276457386374882976782849";
        BigInteger aNumber1 = new BigInteger(value1);
        BigInteger aNumber2 = new BigInteger(value2);
        int code1 = aNumber1.hashCode();
        aNumber1.add(aNumber2).shiftLeft(125);
        aNumber1.subtract(aNumber2).shiftRight(125);
        aNumber1.multiply(aNumber2).toByteArray();
        aNumber1.divide(aNumber2).bitLength();
        aNumber1.gcd(aNumber2).pow(7);
        int code2 = aNumber1.hashCode();
        assertTrue(code1 == code2, "hash codes for the same object differ");
    }

    /**
     * Test hash codes for equal objects.
     */
    @Test
    public void testEqualObjects() {
        String value1 = "12378246728727834290276457386374882976782849";
        String value2 = "12378246728727834290276457386374882976782849";
        BigInteger aNumber1 = new BigInteger(value1);
        BigInteger aNumber2 = new BigInteger(value2);
        int code1 = aNumber1.hashCode();
        int code2 = aNumber2.hashCode();
        if (aNumber1.equals(aNumber2)) {
            assertTrue(code1 == code2, "hash codes for equal objects are unequal");
        }
    }

    /**
     * Test hash codes for unequal objects.
     * The codes are unequal.
     */
    @Test
    public void testUnequalObjectsUnequal() {
        String value1 = "12378246728727834290276457386374882976782849";
        String value2 = "-5634562095872038262928728727834290276457386374882976782849";
        BigInteger aNumber1 = new BigInteger(value1);
        BigInteger aNumber2 = new BigInteger(value2);
        int code1 = aNumber1.hashCode();
        int code2 = aNumber2.hashCode();
        if (!aNumber1.equals(aNumber2)) {
            assertTrue(code1 != code2, "hash codes for unequal objects are equal");
        }
    }

    @Test
    public void equalValuesBuiltDifferentlyHashAlike() {
        BigInteger p = new BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951");
        BigInteger[] equal = {
                new BigInteger(p.toString(16), 16),
                new BigInteger(1, p.toByteArray()),
                BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE.shiftLeft(224)).add(BigInteger.ONE.shiftLeft(192))
                        .add(BigInteger.ONE.shiftLeft(96)).subtract(BigInteger.ONE),
                p.add(BigInteger.TEN).subtract(BigInteger.TEN)
        };
        for (BigInteger x : equal) {
            assertEquals(p, x);
            assertEquals(p.hashCode(), x.hashCode());
        }
        BigInteger twoTo100 = BigInteger.ONE.shiftLeft(100);
        assertEquals(3, twoTo100.add(BigInteger.valueOf(3)).subtract(twoTo100).hashCode());
        assertEquals(0, twoTo100.subtract(twoTo100).hashCode());
    }

    @Test
    public void hashCodeIsTheJdkOne() {
        assertEquals(-1, BigInteger.ONE.negate().hashCode());
        assertEquals(16 * 31 * 31 * 31, BigInteger.ONE.shiftLeft(100).hashCode());
        assertEquals(31 * 7 + 5, new BigInteger("30064771077").hashCode());
    }
}
