/*
 *  Copyright 2025 Ashera Cordova.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class PropertiesTest {

    @Test
    public void testSetAndGetProperty() {
        Properties props = new Properties();
        props.setProperty("username", "admin");
        props.setProperty("timeout", "30");

        assertEquals("admin", props.getProperty("username"));
        assertEquals("30", props.getProperty("timeout"));
        assertNull(props.getProperty("missingKey"));
        assertEquals("default", props.getProperty("missingKey", "default"));
    }
    
    @Test
    public void testLoadFromReader() throws IOException {
        String data = "# Sample config\n"
                + "host=localhost\n"
                + "port=8080\n";

        Properties props = new Properties();
        Reader reader = new StringReader(data);
        props.load(reader);
        reader.close();

        assertEquals("localhost", props.getProperty("host"));
        assertEquals("8080", props.getProperty("port"));

        data = "# Sample configuration\n"
                + "host=localhost\n"
                + "port=8080\n"
                + "description=This is a long description \\\n"
                + "that continues on the next line \\\n"
                + "and ends here.\n"
                + "path=C:\\\\Program Files\\\\MyApp\n";
        reader = new StringReader(data);
        props = new Properties();
        props.load(reader);
        reader.close();

        assertEquals("localhost", props.getProperty("host"));
        assertEquals("8080", props.getProperty("port"));
        assertEquals("This is a long description that continues on the next line and ends here.",
                     props.getProperty("description"));
        assertEquals("C:\\Program Files\\MyApp", props.getProperty("path"));
    }
    
    
    @Test
    public void testLoadFromInputStream() throws IOException {
        String data = "db.user=root\n"
                       + "db.pass=secret\n";

        Properties props = new Properties();
        InputStream in = new ByteArrayInputStream(data.getBytes("ISO-8859-1"));
        props.load(in);
        in.close();

        assertEquals("root", props.getProperty("db.user"));
        assertEquals("secret", props.getProperty("db.pass"));
    }

    @Test
    public void stringPropertyNamesSkipsNonStringKeysAndValues() {
        Properties props = new Properties();
        props.setProperty("a", "1");
        props.put(1, "x");
        props.put("n", 5);
        assertEquals(Set.of("a"), props.stringPropertyNames());

        Properties withDefaults = new Properties(props);
        withDefaults.put("a", 6);
        assertEquals(Set.of("a"), withDefaults.stringPropertyNames());
    }

    @Test
    public void propertyNamesRejectsNonStringKey() {
        Properties props = new Properties();
        props.setProperty("a", "1");
        props.put(1, "x");
        try {
            props.propertyNames();
            fail();
        } catch (ClassCastException e) {
            // ok
        }
        Properties withDefaults = new Properties(props);
        withDefaults.setProperty("b", "2");
        try {
            withDefaults.propertyNames();
            fail();
        } catch (ClassCastException e) {
            // ok
        }
        try {
            props.list(new PrintStream(new ByteArrayOutputStream()));
            fail();
        } catch (ClassCastException e) {
            // ok
        }

        Properties ok = new Properties();
        ok.setProperty("a", "1");
        ok.setProperty("b", "2");
        List<String> names = new ArrayList<>();
        for (Enumeration<?> e = ok.propertyNames(); e.hasMoreElements();) {
            names.add((String) e.nextElement());
        }
        Collections.sort(names);
        assertEquals(List.of("a", "b"), names);
    }
}
