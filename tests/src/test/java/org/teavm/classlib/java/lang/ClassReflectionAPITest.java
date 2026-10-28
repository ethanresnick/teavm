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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.Reflectable;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ClassReflectionAPITest {
    @Test
    public void packageNames() {
        // Class.getPackage specifies the package of the class, and null for arrays, primitives and void.
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getPackage()
        assertEquals("java.lang", String.class.getPackage().getName());
        assertEquals("org.teavm.classlib.java.lang", FoundLate.class.getPackage().getName());
        assertSame(getClass().getPackage(), FoundLate.class.getPackage());
    }

    @Test
    public void arraysPrimitivesAndVoidHaveNoPackage() {
        // Class.getPackage specifies null for array types, primitive types and void.
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getPackage()
        for (var type : new Class<?>[] { boolean.class, byte.class, short.class, char.class, int.class,
                long.class, float.class, double.class, void.class, String[].class, int[].class,
                String[][].class, int[][].class }) {
            assertNull(type.getPackage());
        }
    }

    @Test
    public void lateFoundClassHasNameAndReflectiveResultType() throws Exception {
        // Class.forName returns the class with the specified name; getDeclaredField exposes its declared field.
        // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#forName(java.lang.String)
        var object = new FoundLate();
        String name = new String("org.teavm.classlib.java.lang.ClassReflectionAPITest$FoundLate".toCharArray());
        var type = Class.forName(name);
        assertEquals(FoundLate.class, type);
        assertEquals(73, type.getDeclaredField("value").getInt(object));
    }

    @Test
    public void lateFoundClassRetainsName() throws Exception {
        // Avoid a class literal or getName call on FoundLate: those would retain its name independently.
        var object = new FoundLate();
        String name = new String("org.teavm.classlib.java.lang.ClassReflectionAPITest$FoundLate".toCharArray());
        var type = Class.forName(name);
        assertEquals(name, type.getName());
        assertEquals(73, type.getDeclaredField("value").getInt(object));
    }

    public static class FoundLate {
        @Reflectable
        public int value = 73;
    }
}
