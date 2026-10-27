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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;
import org.teavm.junit.AttachC;
import org.teavm.junit.IncludeC;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

// Real symbolic links on the C backend's POSIX file system. The expected values are what the JDK on macOS returns
// for the same tree.
@TeaVMTest
@IncludeC("org/teavm/c/test/file-links.h")
@AttachC("org/teavm/c/test/file-links.c")
@SkipJVM
@OnlyPlatform(TestPlatform.C)
public class SymbolicLinkCTest {
    private static final Path ROOT = Path.of("test-symbolic-links-c");

    @Test
    public void realLinks() throws IOException {
        var targetDir = ROOT.resolve("target-dir");
        var file = targetDir.resolve("a.txt");
        var linkToDir = ROOT.resolve("link-to-dir");
        var linkToFile = ROOT.resolve("link-to-file");
        var dangling = ROOT.resolve("dangling");
        try {
            Files.createDirectories(targetDir);
            Files.write(file, new byte[] { 1 });
            assertTrue(symlink("target-dir", linkToDir.toString()));
            assertTrue(symlink("target-dir/a.txt", linkToFile.toString()));
            assertTrue(symlink("missing", dangling.toString()));

            assertEquals("link=false dir=true dirNF=true file=false fileNF=false exists=true existsNF=true "
                    + "notExistsNF=false", describe(targetDir));
            assertEquals("link=true dir=true dirNF=false file=false fileNF=false exists=true existsNF=true "
                    + "notExistsNF=false", describe(linkToDir));
            assertEquals("link=true dir=false dirNF=false file=true fileNF=false exists=true existsNF=true "
                    + "notExistsNF=false", describe(linkToFile));
            assertEquals("link=true dir=false dirNF=false file=false fileNF=false exists=false existsNF=true "
                    + "notExistsNF=false", describe(dangling));

            // walkFileTree: "By default, symbolic links are not automatically followed by this method."
            assertEquals(Set.of("dir ", "link /dangling", "link /link-to-dir", "link /link-to-file",
                    "dir /target-dir", "file /target-dir/a.txt"), walk(Set.of()));
            // "If the options parameter contains the FOLLOW_LINKS option then symbolic links are followed. When
            // following links, and the attributes of the target cannot be read, then this method attempts to get
            // the BasicFileAttributes of the link."
            assertEquals(Set.of("dir ", "link /dangling", "dir /link-to-dir", "file /link-to-dir/a.txt",
                    "file /link-to-file", "dir /target-dir", "file /target-dir/a.txt"),
                    walk(Set.of(FileVisitOption.FOLLOW_LINKS)));

            // Files.delete: "If the file is a symbolic link then the symbolic link itself, not the final target of
            // the link, is deleted."
            Files.delete(dangling);
            Files.delete(linkToDir);
            Files.delete(linkToFile);
            assertFalse(Files.exists(dangling, LinkOption.NOFOLLOW_LINKS));
            assertFalse(Files.exists(linkToDir, LinkOption.NOFOLLOW_LINKS));
            assertFalse(Files.exists(linkToFile, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.isDirectory(targetDir));
            assertTrue(Files.isRegularFile(file));
        } finally {
            unlink(dangling.toString());
            unlink(linkToDir.toString());
            unlink(linkToFile.toString());
            Files.deleteIfExists(file);
            Files.deleteIfExists(targetDir);
            Files.deleteIfExists(ROOT);
        }
    }

    private static String describe(Path p) {
        return "link=" + Files.isSymbolicLink(p)
                + " dir=" + Files.isDirectory(p)
                + " dirNF=" + Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)
                + " file=" + Files.isRegularFile(p)
                + " fileNF=" + Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                + " exists=" + Files.exists(p)
                + " existsNF=" + Files.exists(p, LinkOption.NOFOLLOW_LINKS)
                + " notExistsNF=" + Files.notExists(p, LinkOption.NOFOLLOW_LINKS);
    }

    private static Set<String> walk(Set<FileVisitOption> options) throws IOException {
        Set<String> result = new TreeSet<>();
        var prefix = ROOT.toString().length();
        Files.walkFileTree(ROOT, options, Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                result.add("dir " + dir.toString().substring(prefix));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                result.add((attrs.isSymbolicLink() ? "link " : "file ") + file.toString().substring(prefix));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                result.add("failed " + file.toString().substring(prefix));
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
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

    private static void unlink(String path) {
        teavmTestUnlink(cString(path));
    }

    @Import(name = "teavmTestSymlink")
    @Include("file-links.h")
    private static native int teavmTestSymlink(ByteBuffer target, ByteBuffer link);

    @Import(name = "teavmTestUnlink")
    @Include("file-links.h")
    private static native int teavmTestUnlink(ByteBuffer path);
}
