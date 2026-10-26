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
package org.teavm.classlib.java.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;
import org.teavm.runtime.fs.VirtualFileSystemProvider;
import org.teavm.runtime.fs.memory.InMemoryVirtualFileSystem;

@TeaVMTest
@SkipJVM
@EachTestCompiledSeparately
public class TempDirectoryTest {
    // The default file system keeps the VirtualFileSystem that it finds first, and the system properties take
    // their defaults once. So this test installs its file system before any use of either, in its own program.
    // On C, the default temporary directory comes from the C file system (CFileSystem.tempDirectory), not from
    // VirtualFileSystem, so an in-memory file system cannot set it there.
    @Test
    @SkipPlatform(TestPlatform.C)
    public void tempFilesGoToJavaIoTmpdir() throws IOException {
        VirtualFileSystemProvider.setInstance(new InMemoryVirtualFileSystem() {
            // Overrides VirtualFileSystem.tempDirectory(); no @Override, so that the test also compiles without it.
            public String tempDirectory() {
                return "/fake-temp";
            }
        });
        Files.createDirectories(Path.of("/fake-temp"));
        Files.createDirectories(Path.of("/other-temp"));

        assertEquals("/fake-temp", System.getProperty("java.io.tmpdir"));
        assertEquals("/fake-temp", File.createTempFile("abc", ".txt").getParent());
        assertEquals(Path.of("/fake-temp"), Files.createTempFile("abc", ".txt").getParent());
        assertEquals(Path.of("/fake-temp"), Files.createTempDirectory("abc").getParent());

        System.setProperty("java.io.tmpdir", "/other-temp");
        try {
            assertEquals("/other-temp", File.createTempFile("abc", null).getParent());
            assertEquals(Path.of("/other-temp"), Files.createTempFile("abc", null).getParent());
            assertEquals(Path.of("/other-temp"), Files.createTempDirectory("abc").getParent());
        } finally {
            System.setProperty("java.io.tmpdir", "/fake-temp");
        }
    }

    @Test
    public void tempFilesFollowJavaIoTmpdir() throws IOException {
        var defaultDir = System.getProperty("java.io.tmpdir");
        // The in-memory file system of JS and Wasm GC starts empty. On C the directory exists, and on macOS /tmp is
        // a symbolic link, which createDirectories does not accept there.
        if (!Files.isDirectory(Path.of(defaultDir))) {
            Files.createDirectories(Path.of(defaultDir));
        }
        var other = Files.createTempDirectory("teavm-tmpdir-test");
        System.setProperty("java.io.tmpdir", other.toString());
        try {
            var file = File.createTempFile("abc", null);
            var path = Files.createTempFile("abc", null);
            var dir = Files.createTempDirectory("abc");
            assertEquals(other.toString(), file.getParent());
            assertEquals(other, path.getParent());
            assertEquals(other, dir.getParent());
            file.delete();
            Files.delete(path);
            Files.delete(dir);
        } finally {
            System.setProperty("java.io.tmpdir", defaultDir);
            Files.delete(other);
        }
    }
}
