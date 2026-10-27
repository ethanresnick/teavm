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
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

/**
 * Phis that merge objects which do not escape, so that scalar replacement takes them apart. Most shapes
 * only need to compile and run: with optimizations on, the compiler used to fail on some of them with
 * "Variable used before definition". Where the merged object is read, the result is checked too.
 */
@TeaVMTest
@EachTestCompiledSeparately
public class ScalarReplacementPhiTest {
    @Test
    public void ternaryDifferentTypes() {
        for (var flag : new boolean[] { false, true }) {
            new DifferentTypes(flag);
        }
    }

    @Test
    public void ternarySameType() {
        for (var flag : new boolean[] { false, true }) {
            new SameType(flag);
        }
    }

    @Test
    public void ifElseDifferentTypes() {
        for (var flag : new boolean[] { false, true }) {
            new IfElse(flag);
        }
    }

    @Test
    public void ternaryInt() {
        for (var flag : new boolean[] { false, true }) {
            new IntField(flag);
        }
    }

    @Test
    public void ternaryNullOrObject() {
        for (var flag : new boolean[] { false, true }) {
            new NullOrObject(flag);
        }
    }

    @Test
    public void nestedTernaryDisjointFields() {
        for (var i = 0; i < 4; ++i) {
            new Holder(i == 0 ? new IntBox(1) : i == 1 ? new LongBox(2) : i == 2 ? new Pair(3, 4) : new Object());
        }
    }

    @Test
    public void loopDifferentTypes() {
        assertEquals(0, loopDifferentTypes(0));
        assertEquals(45, loopDifferentTypes(10));
    }

    private static int loopDifferentTypes(int n) {
        Object o = new IntBox(0);
        var sum = 0;
        for (var i = 0; i < n; ++i) {
            o = (i & 1) == 0 ? new LongBox(i) : new IntBox(i);
            sum += i;
        }
        new Holder(o);
        return sum;
    }

    @Test
    public void loopSwapDifferentTypes() {
        assertEquals(10, loopSwap(5));
        assertEquals(0, loopSwap(0));
    }

    private static int loopSwap(int n) {
        Object a = new IntBox(1);
        Object b = new LongBox(2);
        var sum = 0;
        for (var i = 0; i < n; ++i) {
            var t = a;
            a = b;
            b = t;
            sum += i;
        }
        new Holder(a);
        new Holder(b);
        return sum;
    }

    @Test
    public void loopSameTypeRead() {
        assertEquals(0, loopSameType(0));
        assertEquals(45, loopSameType(10));
        assertEquals(4950, loopSameType(100));
    }

    private static int loopSameType(int n) {
        var box = new IntBox(0);
        for (var i = 0; i < n; ++i) {
            box = new IntBox(box.value + i);
        }
        return box.value;
    }

    @Test
    public void nestedLoopSameTypeRead() {
        assertEquals(0, nestedLoops(0));
        assertEquals(3 * (0 + 1 + 2), nestedLoops(3));
        assertEquals(4 * (0 + 1 + 2 + 3), nestedLoops(4));
    }

    private static int nestedLoops(int n) {
        var outer = new IntBox(0);
        for (var i = 0; i < n; ++i) {
            var inner = new IntBox(outer.value);
            for (var j = 0; j < n; ++j) {
                inner = new IntBox(inner.value + j);
            }
            outer = (i & 1) == 0 ? inner : new IntBox(inner.value);
        }
        return outer.value;
    }

    @Test
    public void ternaryDisjointFieldsReadCommonField() {
        assertEquals(3, readCommonField(true));
        assertEquals(4, readCommonField(false));
    }

    private static int readCommonField(boolean flag) {
        Base p = flag ? new SubA(3) : new SubB(4);
        return p.x;
    }

    @Test
    public void ternarySameTypeRead() {
        assertEquals(7, sameTypeRead(true));
        assertEquals(12, sameTypeRead(false));
    }

    private static int sameTypeRead(boolean flag) {
        var pair = flag ? new Pair(3, 4) : new Pair(5, 7);
        return pair.a + pair.b;
    }

    @Test
    public void ternaryNullOrObjectRead() {
        assertEquals(-1, nullOrBox(true));
        assertEquals(7, nullOrBox(false));
    }

    private static int nullOrBox(boolean flag) {
        var box = flag ? null : new IntBox(7);
        return box == null ? -1 : box.value;
    }

    @Test
    public void incomingUsedThroughMethod() {
        assertEquals(5, viaMethod(true));
        assertEquals(5, viaMethod(false));
    }

    private static int viaMethod(boolean flag) {
        var getter = new Getter();
        getter.set(5);
        var result = getter.get();
        new Holder(flag ? getter : new Object());
        return result;
    }

    @Test
    public void incomingWithFieldsOnlyThroughMethod() {
        assertEquals(0, viaMethodReadOnly(true));
        assertEquals(0, viaMethodReadOnly(false));
    }

    private static int viaMethodReadOnly(boolean flag) {
        var getter = new Getter();
        var result = getter.get();
        new Holder(flag ? new Object() : getter);
        return result;
    }

    static final class DifferentTypes {
        final Object value;

        DifferentTypes(boolean flag) {
            value = flag ? new Object() : new StringBuilder();
        }
    }

    static final class SameType {
        final Object value;

        SameType(boolean flag) {
            value = flag ? new Object() : new Object();
        }
    }

    static final class IfElse {
        final Object value;

        IfElse(boolean flag) {
            if (flag) {
                value = new Object();
            } else {
                value = new StringBuilder();
            }
        }
    }

    static final class IntField {
        final int value;

        IntField(boolean flag) {
            value = flag ? 1 : 2;
        }
    }

    static final class NullOrObject {
        final Object value;

        NullOrObject(boolean flag) {
            value = flag ? null : new Object();
        }
    }

    static final class Holder {
        final Object value;

        Holder(Object value) {
            this.value = value;
        }
    }

    static final class IntBox {
        final int value;

        IntBox(int value) {
            this.value = value;
        }
    }

    static final class LongBox {
        final long value;

        LongBox(long value) {
            this.value = value;
        }
    }

    static final class Pair {
        final int a;
        final int b;

        Pair(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    abstract static class Base {
        int x;
    }

    static final class SubA extends Base {
        int a;

        SubA(int x) {
            this.x = x;
            a = x * 2;
        }
    }

    static final class SubB extends Base {
        long b;

        SubB(int x) {
            this.x = x;
            b = x * 3L;
        }
    }

    static final class Getter {
        int value;

        void set(int value) {
            this.value = value;
        }

        int get() {
            return value;
        }
    }
}
