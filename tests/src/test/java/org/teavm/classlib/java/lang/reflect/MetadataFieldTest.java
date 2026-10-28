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
package org.teavm.classlib.java.lang.reflect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.TypeVariable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.classlib.support.Reflectable;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class MetadataFieldTest {
    @Test
    public void annotatedNongenericFieldRetainsRawType() throws Exception {
        new Annotated();
        var field = Annotated.class.getDeclaredField("value");
        assertNotNull(field.getAnnotation(Marker.class));
        // Field.getGenericType represents the declared field type (Java SE 21 Field Javadoc).
        assertEquals(String.class, field.getGenericType());
        assertEquals(String.class, field.getGenericType());
        for (var name : new String[] { "primitive", "array", "raw", "plain" }) {
            var other = Annotated.class.getDeclaredField(name);
            assertEquals(other.getType(), other.getGenericType());
        }
    }

    @Test
    @Disabled("Enclosing-method type variables are not represented in reflection metadata")
    public void enclosingMethodVariableRetainsGenericIdentity() throws Exception {
        var value = makeLocal();
        var field = value.getClass().getDeclaredField("value");
        assertEquals(Number.class, field.getType());
        // Field/Method Javadoc requires the declared/formal generic type, not its erasure.
        var fieldType = assertInstanceOf(TypeVariable.class, field.getGenericType());
        assertEquals("T", fieldType.getName());
        assertEquals(Number.class, fieldType.getBounds()[0]);
        var method = value.getClass().getDeclaredMethod("read");
        assertEquals(Number.class, method.getReturnType());
        assertEquals(fieldType, method.getGenericReturnType());
    }

    private <T extends Number> Object makeLocal() {
        class Local {
            @Reflectable
            public T value;

            @Reflectable
            public T read() {
                return value;
            }
        }
        return new Local();
    }

    static class Annotated {
        @Reflectable
        @Marker
        public String value;

        @Reflectable
        @Marker
        public int primitive;

        @Reflectable
        @Marker
        public String[][] array;

        @Reflectable
        @Marker
        public java.util.List raw;

        @Reflectable
        public Object plain;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
    }
}
