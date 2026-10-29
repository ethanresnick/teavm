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

import java.io.Serializable;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;

/** Keeps a test class and all its nested classes on the same deterministic shard. */
public final class TestShardFilter implements Spec<FileTreeElement>, Serializable {
    private final int count;
    private final int index;

    public TestShardFilter(int count, int index) {
        if (count < 1 || index < 0 || index >= count) {
            throw new IllegalArgumentException();
        }
        this.count = count;
        this.index = index;
    }

    @Override
    public boolean isSatisfiedBy(FileTreeElement element) {
        if (element.isDirectory()) {
            return true;
        }
        String path = element.getRelativePath().getPathString();
        int nested = path.indexOf('$');
        if (nested >= 0) {
            path = path.substring(0, nested) + ".class";
        }
        return Math.floorMod(path.hashCode(), count) == index;
    }
}
