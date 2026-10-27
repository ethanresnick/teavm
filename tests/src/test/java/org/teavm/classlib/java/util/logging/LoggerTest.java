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
package org.teavm.classlib.java.util.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.ListResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class LoggerTest {
    // Logger.getResourceBundleName(): "Returns: localization bundle name (may be null)". getResourceBundle():
    // "Returns: localization bundle (may be null)". getLogger(String) creates a logger without a bundle.
    @Test
    public void resourceBundleOfNamedLoggerIsNull() {
        var logger = Logger.getLogger("teavm.test.logger");
        assertNull(logger.getResourceBundleName());
        assertNull(logger.getResourceBundle());
        var anonymous = Logger.getAnonymousLogger();
        assertNull(anonymous.getResourceBundleName());
        assertNull(anonymous.getResourceBundle());
    }

    // LogRecord.setResourceBundleName(String): "Set the localization resource bundle name."
    // LogRecord.setResourceBundle(ResourceBundle): "Set the localization resource bundle."
    @Test
    public void logRecordResourceBundleName() {
        var record = new LogRecord(Level.INFO, "message");
        assertNull(record.getResourceBundleName());
        record.setResourceBundleName("bundle");
        assertEquals("bundle", record.getResourceBundleName());
        record.setResourceBundleName(null);
        assertNull(record.getResourceBundleName());
    }

    @Test
    public void logRecordResourceBundle() {
        var record = new LogRecord(Level.INFO, "message");
        assertNull(record.getResourceBundle());
        var bundle = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] { { "message", "localized" } };
            }
        };
        record.setResourceBundle(bundle);
        assertSame(bundle, record.getResourceBundle());
        // The bundle and its name are separate properties.
        assertNull(record.getResourceBundleName());
        record.setResourceBundle(null);
        assertNull(record.getResourceBundle());
    }
}
