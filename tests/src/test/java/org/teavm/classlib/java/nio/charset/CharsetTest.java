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
package org.teavm.classlib.java.nio.charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class CharsetTest {
    // Javadoc of Charset.isSupported: "Returns: true if, and only if, support for the named charset is available
    // in the current Java virtual machine". The class Javadoc makes charset names case-insensitive.
    @Test
    public void isSupported() {
        assertTrue(Charset.isSupported("UTF-8"));
        assertTrue(Charset.isSupported("utf-8"));
        assertTrue(Charset.isSupported("ISO-8859-1"));
        assertTrue(Charset.isSupported("US-ASCII"));
        assertTrue(Charset.isSupported("UTF-16"));
        assertFalse(Charset.isSupported("x-teavm-no-such-charset"));
        assertFalse(Charset.isSupported("UTF-9"));
    }

    // "Throws: IllegalCharsetNameException - If the given charset name is illegal;
    // IllegalArgumentException - If the given charsetName is null"
    @Test
    public void isSupportedRejectsIllegalNames() {
        assertThrows(IllegalArgumentException.class, () -> Charset.isSupported(null));
        for (var name : new String[] { "", "-utf8", "+x", ".x", ":x", "_x", "utf 8", "utf/8", "utf8\u00e9" }) {
            assertThrows(IllegalCharsetNameException.class, () -> Charset.isSupported(name), name);
        }
    }

    // isSupported(name) is true exactly when forName(name) returns a charset, and both treat an illegal name
    // the same way.
    @Test
    public void isSupportedAgreesWithForName() {
        for (var name : new String[] { "UTF-8", "utf-8", "Utf-8", "ISO-8859-1", "iso-8859-1", "US-ASCII",
                "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-32", "windows-1252", "x-unknown", "a", "0", "a.b:c_d+e-f",
                "", "-a", "a b" }) {
            boolean forName;
            try {
                forName = Charset.forName(name) != null;
            } catch (UnsupportedCharsetException e) {
                forName = false;
            } catch (IllegalCharsetNameException e) {
                assertThrows(IllegalCharsetNameException.class, () -> Charset.isSupported(name), name);
                continue;
            }
            assertEquals(forName, Charset.isSupported(name), name);
        }
    }
}
