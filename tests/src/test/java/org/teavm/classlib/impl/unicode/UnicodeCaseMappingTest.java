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
package org.teavm.classlib.impl.unicode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.teavm.classlib.java.lang.TCharacter;
import org.teavm.parsing.resource.ResourceProvider;

public class UnicodeCaseMappingTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = { 0, 0xffff, 0x10000, 0x1000f, 0x10010, 0x10011, 0x10012, 0x10013,
            0x10014, 0x10015, 0x10017, 0x10018, 0x10020, 0x10021, 0x10025, 0x10ffff })
    public void omittedAndRangeCodePointsHaveIdentityMappings(int codePoint) throws Exception {
        // UnicodeData omits default-property code points and uses First/Last rows for identity ranges.
        String data = row(0x10010, "MAPPED", true) + row(0x10013, "MAPPED", true)
                + row(0x10014, "<Synthetic, First>", false) + row(0x10017, "<Synthetic, Last>", false)
                + row(0x10020, "MAPPED", true);
        int delta = codePoint == 0x10010 || codePoint == 0x10013 || codePoint == 0x10020 ? 1 : 0;
        assertMappings(data, codePoint, new int[] { codePoint + delta, codePoint + 2 * delta,
                codePoint + 3 * delta });
    }

    @Test
    public void emptyDataHasOnlyIdentityMappings() throws Exception {
        assertMappings("", 0x10000, new int[] { 0x10000, 0x10000, 0x10000 });
    }

    private void assertMappings(String data, int codePoint, int[] expected) throws Exception {
        var file = directory.resolve("org/teavm/classlib/impl/unicode/UnicodeData.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, data);
        try (var provider = ResourceProvider.ofClassPath(List.of(directory.toFile()))) {
            int[][] tables = { UnicodeSupport.getUpperCaseMapping(provider),
                UnicodeSupport.getLowerCaseMapping(provider), UnicodeSupport.getTitleCaseMapping(provider) };
            var mapChar = TCharacter.class.getDeclaredMethod("mapChar", CharMapping.class, int.class);
            mapChar.setAccessible(true);
            int[] actual = new int[3];
            for (int i = 0; i < tables.length; i++) {
                assertTrue(tables[i].length <= 14, "Keep sparse change-point encoding");
                actual[i] = (int) mapChar.invoke(null, UnicodeHelper.createCharMapping(tables[i]), codePoint);
            }
            assertArrayEquals(expected, actual, Integer.toHexString(codePoint));
        }
    }

    private static String row(int codePoint, String name, boolean mapped) {
        String[] fields = new String[15];
        Arrays.fill(fields, "");
        fields[0] = Integer.toHexString(codePoint);
        fields[1] = name;
        fields[2] = "Lo";
        if (mapped) {
            fields[12] = Integer.toHexString(codePoint + 1);
            fields[13] = Integer.toHexString(codePoint + 2);
            fields[14] = Integer.toHexString(codePoint + 3);
        }
        return String.join(";", fields) + "\n";
    }
}
