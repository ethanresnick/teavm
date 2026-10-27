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

@TeaVMTest
@EachTestCompiledSeparately
public class ExceptionClassNameTest {
    private static int index = 4;

    // An exception that the runtime throws for an array store out of bounds. The program never names the class.
    @Test
    public void runtimeThrownArrayIndexOutOfBoundsHasName() {
        var array = new Object[4];
        String name;
        try {
            array[index] = null;
            name = "no exception";
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException", name);
    }

    // The same, but the program also names the class in a catch clause.
    @Test
    public void caughtArrayIndexOutOfBoundsHasName() {
        var array = new Object[4];
        String name;
        try {
            array[index] = null;
            name = "no exception";
        } catch (ArrayIndexOutOfBoundsException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.ArrayIndexOutOfBoundsException", name);
    }

    // An exception that the program creates and throws itself.
    @Test
    public void explicitlyThrownExceptionHasName() {
        String name;
        try {
            throwIt();
            name = "no exception";
        } catch (RuntimeException e) {
            name = e.getClass().getName();
        }
        assertEquals("java.lang.IllegalStateException", name);
    }

    private static void throwIt() {
        throw new IllegalStateException();
    }
}
