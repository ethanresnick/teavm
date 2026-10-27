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

import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ScalarReplacementConstructOnlyTest {
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
}
