/*
 *  Copyright 2023 Alexey Andreev.
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
package org.teavm.buildutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.junit.jupiter.api.Test;

class TestShardFilterTest {
    @Test
    void classesHaveExactlyOneShard() {
        for (int count : new int[] { 1, 2, 3, 7 }) {
            int[] totals = new int[count];
            for (int i = 0; i < 2000; ++i) {
                String name = "org/example/Suite" + i;
                int owners = 0;
                for (int shard = 0; shard < count; ++shard) {
                    var filter = new TestShardFilter(count, shard);
                    boolean included = filter.isSatisfiedBy(file(name + ".class", false));
                    assertEquals(included, filter.isSatisfiedBy(file(name + "$Nested.class", false)));
                    assertEquals(included, filter.isSatisfiedBy(file(name + "$Nested$Deep.class", false)));
                    if (included) {
                        ++owners;
                        ++totals[shard];
                    }
                }
                assertEquals(1, owners, name);
            }
            for (int total : totals) {
                assertTrue(total > 0);
            }
        }
    }

    @Test
    void directoriesRemainTraversable() {
        for (int shard = 0; shard < 3; ++shard) {
            assertTrue(new TestShardFilter(3, shard).isSatisfiedBy(file("org/example", true)));
        }
    }

    @Test
    void rejectsInvalidShards() {
        assertThrows(IllegalArgumentException.class, () -> new TestShardFilter(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TestShardFilter(3, -1));
        assertThrows(IllegalArgumentException.class, () -> new TestShardFilter(3, 3));
    }

    private static FileTreeElement file(String path, boolean directory) {
        return (FileTreeElement) Proxy.newProxyInstance(TestShardFilterTest.class.getClassLoader(),
                new Class<?>[] { FileTreeElement.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isDirectory":
                            return directory;
                        case "getRelativePath":
                            return new RelativePath(!directory, path.split("/"));
                        default:
                            throw new UnsupportedOperationException();
                    }
                });
    }
}
