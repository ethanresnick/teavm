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
package org.teavm.classlib.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class OptionalTest {
    @Test
    public void toStringIsTheJdkOne() {
        assertEquals("Optional[42]", Optional.of(42).toString());
        assertEquals("Optional.empty", Optional.empty().toString());
        assertEquals("OptionalInt[5]", OptionalInt.of(5).toString());
        assertEquals("OptionalInt.empty", OptionalInt.empty().toString());
        assertEquals("OptionalLong[6]", OptionalLong.of(6).toString());
        assertEquals("OptionalLong.empty", OptionalLong.empty().toString());
        assertEquals("OptionalDouble[1.5]", OptionalDouble.of(1.5).toString());
        assertEquals("OptionalDouble.empty", OptionalDouble.empty().toString());
    }
}
