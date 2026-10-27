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
package org.teavm.classlib.java.nio.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;
import org.teavm.junit.AttachC;
import org.teavm.junit.IncludeC;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

// Real symbolic links and permission bits on the C backend's POSIX file system.
@TeaVMTest
@IncludeC("org/teavm/c/test/file-links.h")
@AttachC("org/teavm/c/test/file-links.c")
@SkipJVM
@OnlyPlatform(TestPlatform.C)
public class CreateNewFileCTest {
    // StandardOpenOption.CREATE_NEW: "Create a new file, failing if the file already exists. The check for the
    // existence of the file and the creation of the file if it does not exist is atomic". A symbolic link is an
    // existing entry even when its target is missing, so creating through it fails, and the target is not created:
    // otherwise whoever placed the link could choose the file that gets written.
    @Test
    public void createNewThroughDanglingLinkFailsAndDoesNotCreateTarget() throws IOException {
        var dir = Files.createDirectories(Path.of("test-create-new-dangling"));
        var source = dir.resolve("source");
        var target = dir.resolve("target");
        var link = dir.resolve("link");
        try {
            Files.write(source, new byte[] { 7 });
            assertTrue(symlink(target.getFileName().toString(), link.toString()));
            for (var creation : FilesTest.createNewCalls(source)) {
                assertThrows(FileAlreadyExistsException.class, () -> creation.run(link), creation.name);
                assertEquals(-1, fileMode(target.toString()), creation.name + " created the link's target");
                assertTrue(fileMode(link.toString()) >= 0, creation.name);
            }
        } finally {
            unlink(link.toString());
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.delete(dir);
        }
    }

    @Test
    public void createNewThroughLinkToExistingFileFailsAndLeavesTarget() throws IOException {
        var dir = Files.createDirectories(Path.of("test-create-new-link"));
        var source = dir.resolve("source");
        var target = dir.resolve("target");
        var link = dir.resolve("link");
        try {
            Files.write(source, new byte[] { 7 });
            assertTrue(symlink(target.getFileName().toString(), link.toString()));
            for (var creation : FilesTest.createNewCalls(source)) {
                Files.write(target, new byte[] { 1, 2, 3 });
                assertThrows(FileAlreadyExistsException.class, () -> creation.run(link), creation.name);
                assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(target), creation.name);
            }
        } finally {
            unlink(link.toString());
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.delete(dir);
        }
    }

    // Files.createTempFile: "The details as to how the name of the file is constructed is implementation dependent
    // and therefore not specified"; with no attributes the file system chooses them. As the JDK does on POSIX,
    // temporary files are owner-only (0600) and temporary directories 0700, while createFile and createDirectory
    // keep the usual modes (0644 and 0755, less the umask).
    @Test
    public void temporaryEntriesAreOwnerOnly() throws IOException {
        var umask = umask();
        var dir = Files.createDirectories(Path.of("test-temp-modes"));
        var file = dir.resolve("file");
        var subdir = dir.resolve("subdir");
        Path tempFile = null;
        Path tempDir = null;
        Path defaultTempFile = null;
        Path defaultTempDir = null;
        try {
            tempFile = Files.createTempFile(dir, "abc", null);
            tempDir = Files.createTempDirectory(dir, "abc");
            defaultTempFile = Files.createTempFile("abc", null);
            defaultTempDir = Files.createTempDirectory("abc");
            Files.createFile(file);
            Files.createDirectory(subdir);

            assertEquals(0600 & ~umask, fileMode(tempFile.toString()));
            assertEquals(0700 & ~umask, fileMode(tempDir.toString()));
            assertEquals(0600 & ~umask, fileMode(defaultTempFile.toString()));
            assertEquals(0700 & ~umask, fileMode(defaultTempDir.toString()));
            assertEquals(0644 & ~umask, fileMode(file.toString()));
            assertEquals(0755 & ~umask, fileMode(subdir.toString()));
        } finally {
            for (var path : new Path[] { tempFile, tempDir, defaultTempFile, defaultTempDir, file, subdir }) {
                if (path != null) {
                    Files.deleteIfExists(path);
                }
            }
            Files.delete(dir);
        }
    }

    private static ByteBuffer cString(String s) {
        var bytes = s.getBytes(StandardCharsets.UTF_8);
        var buffer = ByteBuffer.allocate(bytes.length + 1);
        buffer.put(bytes);
        buffer.put((byte) 0);
        return buffer;
    }

    private static boolean symlink(String target, String link) {
        return teavmTestSymlink(cString(target), cString(link)) != 0;
    }

    private static int fileMode(String path) {
        return teavmTestFileMode(cString(path));
    }

    private static void unlink(String path) {
        teavmTestUnlink(cString(path));
    }

    private static int umask() {
        return teavmTestUmask();
    }

    @Import(name = "teavmTestSymlink")
    @Include("file-links.h")
    private static native int teavmTestSymlink(ByteBuffer target, ByteBuffer link);

    @Import(name = "teavmTestFileMode")
    @Include("file-links.h")
    private static native int teavmTestFileMode(ByteBuffer path);

    @Import(name = "teavmTestUnlink")
    @Include("file-links.h")
    private static native int teavmTestUnlink(ByteBuffer path);

    @Import(name = "teavmTestUmask")
    @Include("file-links.h")
    private static native int teavmTestUmask();
}
