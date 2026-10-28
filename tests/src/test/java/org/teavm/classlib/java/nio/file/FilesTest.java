/*
 *  Copyright 2025 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class FilesTest {
    @Test
    public void inputStream() throws IOException {
        var file = new File("test-file");
        var dir = new File("test-dir");
        try {
            try (var output = new FileOutputStream(file)) {
                output.write(1);
                output.write(2);
                output.write(3);
            }

            dir.mkdirs();
            
            try (var input = Files.newInputStream(Path.of("test-file"))) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }

            assertThrows(NoSuchFileException.class, () -> Files.newInputStream(Path.of("no-such-file")));
            assertThrows(UnsupportedOperationException.class, () -> Files.newInputStream(Path.of("test-file"),
                    StandardOpenOption.APPEND));
        } finally {
            file.delete();
            dir.delete();
            new File("test-file-2").delete();
        }
    }

    @Test
    public void outputStream() throws IOException {
        try {
            try (var output = Files.newOutputStream(Path.of("test-file-1"))) {
                output.write(1);
                output.write(2);
                output.write(3);
            }

            try (var input = new FileInputStream("test-file-1")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }

            try (var output = Files.newOutputStream(Path.of("test-file-1"), StandardOpenOption.APPEND)) {
                output.write(4);
            }
            try (var input = new FileInputStream("test-file-1")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3, 4 }, bytes);
            }

            try (var output = Files.newOutputStream(Path.of("test-file-1"), StandardOpenOption.WRITE)) {
                output.write(5);
            }
            try (var input = new FileInputStream("test-file-1")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 5, 2, 3, 4 }, bytes);
            }

            try (var output = Files.newOutputStream(Path.of("test-file-1"), StandardOpenOption.TRUNCATE_EXISTING)) {
                output.write(6);
                output.write(7);
            }
            try (var input = new FileInputStream("test-file-1")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 6, 7 }, bytes);
            }

            assertThrows(FileAlreadyExistsException.class, () -> {
                Files.newOutputStream(Path.of("test-file-1"), StandardOpenOption.CREATE_NEW);
            });

            assertThrows(NoSuchFileException.class, () -> {
                Files.newOutputStream(Path.of("test-file-2"), StandardOpenOption.WRITE);
            });

            try (var output = Files.newOutputStream(Path.of("test-file-2"), StandardOpenOption.CREATE_NEW)) {
                output.write(1);
                output.write(2);
            }
            try (var input = new FileInputStream("test-file-2")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2 }, bytes);
            }
        } finally {
            new File("test-file-1").delete();
            new File("test-file-2").delete();
        }
    }
    
    @Test
    public void directoryStream() throws IOException {
        var dir = new File("test-dir1");
        var file2 = new File(dir, "test-file2");
        var file1 = new File(dir, "test-file1");
        try {
            dir.mkdirs();
            try (var out = new FileOutputStream(file1)) {
                out.write(1);
            }
            file2.mkdirs();

            var dirPath = Path.of("test-dir1");
            var entries = new LinkedHashSet<String>();
            try (var dirStream = Files.newDirectoryStream(dirPath)) {
                for (var entry : dirStream) {
                    entries.add(dirPath.relativize(entry).toString());
                }
            }
            assertEquals(Set.of("test-file1", "test-file2"), entries);

            entries.clear();
            try (var dirStream = Files.newDirectoryStream(Path.of("test-dir1"), f -> f.toString().endsWith("1"))) {
                for (var entry : dirStream) {
                    entries.add(dirPath.relativize(entry).toString());
                }
            }
            assertEquals(Set.of("test-file1"), entries);

            assertThrows(NoSuchFileException.class, () -> Files.newDirectoryStream(Path.of("test-dir-not-exists")));
            assertThrows(NotDirectoryException.class, () -> Files.newDirectoryStream(dirPath.resolve("test-file1")));
            assertThrows(DirectoryIteratorException.class, () -> {
                try (var dirStream = Files.newDirectoryStream(dirPath, f -> {
                    throw new IOException("test exception");
                })) {
                    dirStream.iterator().next();
                }
            });
        } finally {
            file2.delete();
            file1.delete();
            dir.delete();
        }
    }

    @Test
    public void createDir() throws IOException {
        var file = new File("test-dir");
        try {
            assertFalse(file.exists());
            var createdDir = Files.createDirectory(Path.of("test-dir"));
            assertEquals("test-dir", createdDir.toString());
            assertTrue(file.isDirectory());

            Files.createDirectory(Path.of("test-dir", "new-subdir"));
            assertTrue(new File(file, "new-subdir").isDirectory());
            
            new File(file, "subdir").mkdirs();
            assertThrows(FileAlreadyExistsException.class, () -> {
                Files.createDirectory(Path.of("test-dir", "subdir"));
            });
            
            new File(file, "subfile").createNewFile();
            assertThrows(FileAlreadyExistsException.class, () -> {
                Files.createDirectory(Path.of("test-dir", "subfile"));
            });
            
            assertThrows(FileSystemException.class, () -> {
                Files.createDirectory(Path.of("test-dir", "subfile", "subdir"));
            });

            assertThrows(FileSystemException.class, () -> {
                Files.createDirectory(Path.of("test-dir", "no-such-dir", "subdir"));
            });
        } finally {
            new File(file, "subfile").delete();
            new File(file, "subdir").delete();
            new File(file, "new-subdir").delete();
            file.delete();
        }
    }

    @Test
    public void createDirs() throws IOException {
        try {
            Files.createDirectories(Path.of("test-dir", "subdir1", "subdir2"));
            assertTrue(new File("test-dir/subdir1/subdir2").isDirectory());

            Files.createDirectories(Path.of("test-dir", "subdir1", "subdir2"));
            
            Files.createDirectories(Path.of("test-dir", "subdir1", "subdir3"));
            assertTrue(new File("test-dir/subdir1/subdir3").isDirectory());
            
            Files.createFile(Path.of("test-dir", "file"));
            Assertions.assertThrows(FileSystemException.class, () -> {
                Files.createDirectories(Path.of("test-dir", "file"));
            });
            Assertions.assertThrows(FileSystemException.class, () -> {
                Files.createDirectories(Path.of("test-dir", "file", "dir"));
            });
        } finally {
            new File("test-dir/subdir1/subdir2").delete();
            new File("test-dir/subdir1/subdir3").delete();
            new File("test-dir/subdir1").delete();
            new File("test-dir/file").delete();
            new File("test-dir").delete();
        }
    }

    @Test
    public void delete() throws IOException {
        try {
            new File("test-dir").mkdirs();

            Files.delete(Path.of("test-dir"));
            assertFalse(new File("test-dir").exists());

            Assertions.assertThrows(NoSuchFileException.class, () -> Files.delete(Path.of("test-dir")));

            new File("test-dir/subdir").mkdirs();
            Assertions.assertThrows(DirectoryNotEmptyException.class, () -> Files.delete(Path.of("test-dir")));

            new File("test-file").createNewFile();
            Files.delete(Path.of("test-file"));
            assertFalse(new File("test-file").exists());

            Assertions.assertThrows(NoSuchFileException.class, () -> Files.delete(Path.of("test-file")));
        } finally {
            new File("test-dir/subdir").delete();
            new File("test-dir").delete();
            new File("test-file").delete();
        }
    }

    @Test
    public void copy() throws IOException {
        try {
            assertThrows(NoSuchFileException.class, () -> {
                Files.copy(Path.of("no-such-file"), Path.of("no-such-file-copy"));
            });
            
            try (var output = new FileOutputStream("test-file-1")) {
                output.write(new byte[] { 1, 2, 3 });
            }
            
            Files.copy(Path.of("test-file-1"), Path.of("test-file-2"));
            assertTrue(new File("test-file-2").exists());
            try (var input = new FileInputStream("test-file-2")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }

            assertThrows(FileAlreadyExistsException.class, () -> {
                Files.copy(Path.of("test-file-1"), Path.of("test-file-2"));
            });
            
            new File("test-file-3").createNewFile();
            Files.copy(Path.of("test-file-1"), Path.of("test-file-3"), StandardCopyOption.REPLACE_EXISTING);
            assertTrue(new File("test-file-3").exists());
            try (var input = new FileInputStream("test-file-3")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }
        } finally {
            new File("test-file-1").delete();
            new File("test-file-2").delete();
            new File("test-file-3").delete();
        }
    }
    
    @Test
    public void move() throws IOException {
        try {
            assertThrows(NoSuchFileException.class, () -> {
                Files.move(Path.of("no-such-file"), Path.of("no-such-file-copy"));
            });
            
            try (var output = new FileOutputStream("test-file-1")) {
                output.write(new byte[] { 1, 2, 3 });
            }

            Files.move(Path.of("test-file-1"), Path.of("test-file-2"));
            assertTrue(new File("test-file-2").exists());
            assertFalse(new File("test-file-1").exists());
            try (var input = new FileInputStream("test-file-2")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }
            
            new File("test-file-1").createNewFile();

            assertThrows(FileAlreadyExistsException.class, () -> {
                Files.move(Path.of("test-file-1"), Path.of("test-file-2"));
            });

            new File("test-file-3").createNewFile();
            Files.move(Path.of("test-file-2"), Path.of("test-file-3"), StandardCopyOption.REPLACE_EXISTING);
            assertTrue(new File("test-file-3").exists());
            assertFalse(new File("test-file-2").exists());
            try (var input = new FileInputStream("test-file-3")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }
        } finally {
            new File("test-file-1").delete();
            new File("test-file-2").delete();
            new File("test-file-3").delete();
        }
    }

    @Test
    public void isSameFile() throws IOException {
        new File("test-dir").mkdirs();
        new File("test-dir/a").mkdirs();
        new File("test-dir/a/b").createNewFile();
        new File("test-dir/a/c").createNewFile();
        try {
            assertTrue(Files.isSameFile(Path.of("test-dir", "a", "b"), Path.of("test-dir", "a/b")));
            assertFalse(Files.isSameFile(Path.of("test-dir", "a", "b"), Path.of("test-dir", "a", "c")));
        } finally {
            new File("test-dir/a/b").delete();
            new File("test-dir/a/c").delete();
            new File("test-dir/a").delete();
            new File("test-dir").delete();
        }
    }

    @Test
    public void mismatch() throws IOException {
        try {
            try (var output = new FileOutputStream("test-file-1")) {
                output.write(new byte[] { 1, 2, 3 });
            }
            try (var output = new FileOutputStream("test-file-2")) {
                output.write(new byte[] { 1, 2, 3, 4 });
            }
            try (var output = new FileOutputStream("test-file-3")) {
                output.write(new byte[] { 1, 3, 3 });
            }
            
            assertEquals(-1L, Files.mismatch(Path.of("test-file-1"), Path.of("test-file-1")));
            assertEquals(3, Files.mismatch(Path.of("test-file-1"), Path.of("test-file-2")));
            assertEquals(1, Files.mismatch(Path.of("test-file-1"), Path.of("test-file-3")));
            
            assertThrows(IOException.class, () -> Files.mismatch(Path.of("test-file-1"), Path.of("no-such-file")));
        } finally {
            new File("test-file-1").delete();
            new File("test-file-2").delete();
            new File("test-file-3").delete();
        }
    }

    @Test
    public void readAttributes() throws IOException {
        new File("test-dir").mkdir();
        try (var out = new FileOutputStream("test-file")) {
            out.write(new byte[] { 1, 2, 3 });
        }
        try {
            var attrs = Files.readAttributes(Path.of("test-dir"), BasicFileAttributes.class);
            assertTrue(attrs.isDirectory());
            assertFalse(attrs.isRegularFile());
            assertFalse(attrs.isSymbolicLink());

            attrs = Files.readAttributes(Path.of("test-file"), BasicFileAttributes.class);
            assertFalse(attrs.isDirectory());
            assertTrue(attrs.isRegularFile());
            assertFalse(attrs.isSymbolicLink());
            assertEquals(3, attrs.size());

            assertThrows(NoSuchFileException.class, () -> Files.readAttributes(Path.of("no-such-file"),
                    BasicFileAttributes.class));
        } finally {
            new File("test-dir").delete();
            new File("test-file").delete();
        }
    }

    @Test
    public void isDirectory() throws IOException {
        new File("test-dir").mkdir();
        new File("test-file").createNewFile();
        try {
            assertTrue(Files.isDirectory(Path.of("test-dir")));
            assertFalse(Files.isDirectory(Path.of("test-file")));
            assertFalse(Files.isDirectory(Path.of("no-such-file")));
        } finally {
            new File("test-dir").delete();
            new File("test-file").delete();
        }
    }

    @Test
    public void isRegularFile() throws IOException {
        new File("test-dir").mkdir();
        new File("test-file").createNewFile();
        try {
            assertFalse(Files.isRegularFile(Path.of("test-dir")));
            assertTrue(Files.isRegularFile(Path.of("test-file")));
            assertFalse(Files.isRegularFile(Path.of("no-such-file")));
        } finally {
            new File("test-dir").delete();
            new File("test-file").delete();
        }
    }

    @Test
    public void size() throws IOException {
        try (var out = new FileOutputStream("test-file")) {
            out.write(new byte[] { 1, 2, 3 });
        }
        try {
            assertEquals(3, Files.size(Path.of("test-file")));
            assertThrows(NoSuchFileException.class, () -> Files.size(Path.of("no-such-file")));
        } finally {
            new File("test-file").delete();
        }
    }

    @Test
    public void walkFileTree() throws IOException {
        new File("test-dir").mkdir();
        new File("test-dir/a").createNewFile();
        new File("test-dir/b").mkdir();
        new File("test-dir/b/1").createNewFile();
        new File("test-dir/b/2").createNewFile();
        new File("test-dir/b/3").mkdir();
        new File("test-dir/b/3/x").createNewFile();
        try {
            var visitedFiles = new HashSet<Path>();
            var visitedDirs = new HashSet<Path>();
            var preVisitedDirs = new HashSet<Path>();
            var visitFailed = new HashSet<Path>();
            var visitor = new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    preVisitedDirs.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    visitedFiles.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    visitFailed.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    visitedDirs.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(Path.of("test-dir"), visitor);
            assertEquals(Set.of(Path.of("test-dir"), Path.of("test-dir/b"), Path.of("test-dir/b/3")), visitedDirs);
            assertEquals(Set.of(Path.of("test-dir"), Path.of("test-dir/b"), Path.of("test-dir/b/3")), preVisitedDirs);
            assertEquals(Set.of(Path.of("test-dir/a"), Path.of("test-dir/b/1"), Path.of("test-dir/b/2"),
                    Path.of("test-dir/b/3/x")), visitedFiles);
            assertEquals(Set.of(), visitFailed);
            
            visitedDirs.clear();
            visitedFiles.clear();
            preVisitedDirs.clear();
            Files.walkFileTree(Path.of("test-dir"), Set.of(), 1, visitor);
            assertEquals(Set.of(Path.of("test-dir")), visitedDirs);
            assertEquals(Set.of(Path.of("test-dir")), preVisitedDirs);
            assertEquals(Set.of(Path.of("test-dir/a"), Path.of("test-dir/b")), visitedFiles);
            assertEquals(Set.of(), visitFailed);

            visitedDirs.clear();
            visitedFiles.clear();
            Files.walkFileTree(Path.of("test-dir"), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)  {
                    preVisitedDirs.add(dir);
                    if (dir.equals(Path.of("test-dir/b/3"))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)  {
                    visitedFiles.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    visitedDirs.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            assertEquals(Set.of(Path.of("test-dir"), Path.of("test-dir/b")), visitedDirs);
            assertEquals(Set.of(Path.of("test-dir/a"), Path.of("test-dir/b/1"), Path.of("test-dir/b/2")), 
                    visitedFiles);
        } finally {
            new File("test-dir/a").delete();
            new File("test-dir/b/1").delete();
            new File("test-dir/b/2").delete();
            new File("test-dir/b/3/x").delete();
            new File("test-dir/b/3").delete();
            new File("test-dir/b").delete();
            new File("test-dir").delete();
        }
    }

    @Test
    public void walk() throws IOException {
        new File("test-dir").mkdir();
        new File("test-dir/a").createNewFile();
        new File("test-dir/b").mkdir();
        new File("test-dir/b/1").createNewFile();
        new File("test-dir/b/2").createNewFile();
        new File("test-dir/b/3").mkdir();
        new File("test-dir/b/3/x").createNewFile();
        try {
            var visitedFiles = new HashSet<Path>();
            try (var stream = Files.walk(Path.of("test-dir"))) {
                stream.forEach(visitedFiles::add);
            }
            assertEquals(Set.of(Path.of("test-dir"), Path.of("test-dir/a"), Path.of("test-dir/b"),
                    Path.of("test-dir/b/1"), Path.of("test-dir/b/2"), Path.of("test-dir/b/3"),
                    Path.of("test-dir/b/3/x")), visitedFiles);
        } finally {
            new File("test-dir/a").delete();
            new File("test-dir/b/1").delete();
            new File("test-dir/b/2").delete();
            new File("test-dir/b/3/x").delete();
            new File("test-dir/b/3").delete();
            new File("test-dir/b").delete();
            new File("test-dir").delete();
        }
    }

    @Test
    public void copyFromInputStream() throws IOException {
        try {
            Files.copy(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), Path.of("test-file-2"));
            assertTrue(new File("test-file-2").exists());
            try (var input = new FileInputStream("test-file-2")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }

            assertThrows(FileAlreadyExistsException.class, () -> {
                Files.copy(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), Path.of("test-file-2"));
            });

            new File("test-file-3").createNewFile();
            Files.copy(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), Path.of("test-file-3"),
                    StandardCopyOption.REPLACE_EXISTING);
            assertTrue(new File("test-file-3").exists());
            try (var input = new FileInputStream("test-file-3")) {
                var bytes = input.readAllBytes();
                assertArrayEquals(new byte[] { 1, 2, 3 }, bytes);
            }
        } finally {
            new File("test-file-1").delete();
            new File("test-file-2").delete();
            new File("test-file-3").delete();
        }
    }

    // StandardOpenOption.CREATE_NEW: "Create a new file, failing if the file already exists. The check for the
    // existence of the file and the creation of the file if it does not exist is atomic". Files.createFile: "The
    // check for the existence of the file and the creation of the new file if it does not exist are a single
    // operation"; it throws "FileAlreadyExistsException - If a file of that name already exists (optional specific
    // exception)". Files.copy without REPLACE_EXISTING throws FileAlreadyExistsException "if the target file
    // exists". Every way to create a new file fails on an existing file or directory and leaves it unchanged.
    @Test
    public void createNewFailsOnExistingEntryAndLeavesItUnchanged() throws IOException {
        var file = Path.of("test-create-new-file");
        var dir = Path.of("test-create-new-dir");
        var source = Path.of("test-create-new-source");
        try {
            Files.write(source, new byte[] { 7 });
            Files.createDirectory(dir);
            Files.createFile(dir.resolve("child"));
            for (var target : new Path[] { file, dir }) {
                for (var creation : createNewCalls(source)) {
                    var message = creation.name + " on " + target;
                    Files.write(file, new byte[] { 1, 2, 3 });
                    assertThrows(FileAlreadyExistsException.class, () -> creation.run(target), message);
                    assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(file), message);
                    assertTrue(Files.isDirectory(dir), message);
                    assertTrue(Files.isRegularFile(dir.resolve("child")), message);
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir.resolve("child"));
            Files.deleteIfExists(dir);
            Files.deleteIfExists(source);
        }
    }

    interface Creation {
        void create(Path path) throws IOException;
    }

    static final class NamedCreation {
        final String name;
        final Creation creation;

        NamedCreation(String name, Creation creation) {
            this.name = name;
            this.creation = creation;
        }

        void run(Path path) throws IOException {
            creation.create(path);
        }
    }

    // Every call that must create a new file, failing with FileAlreadyExistsException if anything is at the path.
    static NamedCreation[] createNewCalls(Path source) {
        return new NamedCreation[] {
            new NamedCreation("newOutputStream(CREATE_NEW)",
                    p -> Files.newOutputStream(p, StandardOpenOption.CREATE_NEW).close()),
            new NamedCreation("newOutputStream(CREATE_NEW, WRITE)",
                    p -> Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close()),
            new NamedCreation("newOutputStream(CREATE_NEW, WRITE, TRUNCATE_EXISTING)",
                    p -> Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING).close()),
            // "If the CREATE_NEW option is present then CREATE is ignored"
            new NamedCreation("newOutputStream(CREATE_NEW, CREATE)",
                    p -> Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.CREATE).close()),
            new NamedCreation("newOutputStream(CREATE_NEW, APPEND)",
                    p -> Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.APPEND).close()),
            new NamedCreation("write(bytes, CREATE_NEW)",
                    p -> Files.write(p, new byte[] { 9 }, StandardOpenOption.CREATE_NEW)),
            new NamedCreation("write(lines, CREATE_NEW)",
                    p -> Files.write(p, Arrays.asList("x"), StandardOpenOption.CREATE_NEW)),
            new NamedCreation("writeString(CREATE_NEW)",
                    p -> Files.writeString(p, "x", StandardOpenOption.CREATE_NEW)),
            new NamedCreation("newBufferedWriter(CREATE_NEW)",
                    p -> Files.newBufferedWriter(p, StandardOpenOption.CREATE_NEW).close()),
            new NamedCreation("createFile", p -> Files.createFile(p)),
            new NamedCreation("copy(Path, Path)", p -> Files.copy(source, p)),
            new NamedCreation("copy(InputStream, Path)",
                    p -> Files.copy(new ByteArrayInputStream(new byte[] { 9 }), p)),
        };
    }

    // Files.createFile, createDirectory, createDirectories, createTempFile and createTempDirectory throw
    // "UnsupportedOperationException - if the array contains an attribute that cannot be set atomically when
    // creating the file". An attribute is never silently dropped, and nothing is created.
    @Test
    public void creationRejectsUnsupportedAttribute() throws IOException {
        var attribute = new FileAttribute<String>() {
            @Override
            public String name() {
                return "teavm-test:unsupported";
            }

            @Override
            public String value() {
                return "value";
            }
        };
        var dir = Files.createDirectories(Path.of("test-attribute-dir"));
        try {
            assertThrows(UnsupportedOperationException.class, () -> Files.createFile(dir.resolve("f"), attribute));
            assertThrows(UnsupportedOperationException.class,
                    () -> Files.createDirectory(dir.resolve("d"), attribute));
            assertThrows(UnsupportedOperationException.class,
                    () -> Files.createDirectories(dir.resolve("e"), attribute));
            assertThrows(UnsupportedOperationException.class,
                    () -> Files.createDirectories(dir.resolve("g").resolve("h"), attribute));
            assertThrows(UnsupportedOperationException.class,
                    () -> Files.createTempFile(dir, "abc", null, attribute));
            assertThrows(UnsupportedOperationException.class,
                    () -> Files.createTempDirectory(dir, "abc", attribute));
            try (var entries = Files.list(dir)) {
                assertEquals(0, entries.count());
            }
        } finally {
            Files.delete(dir);
        }
    }

    // Files.createTempFile(dir, prefix, suffix, attrs): "prefix - the prefix string to be used in generating the
    // file's name; may be null"; "suffix - the suffix string to be used in generating the file's name; may be null,
    // in which case ".tmp" is used". Unlike File.createTempFile, no minimum prefix length is specified. It "Creates
    // a new empty file in the specified directory".
    @Test
    public void createTempFileTakesAnyPrefix() throws IOException {
        var dir = Files.createDirectories(Path.of("test-temp-file-dir"));
        var created = new ArrayList<Path>();
        try {
            String[] prefixes = { null, "", "a", "ab", "abc", "prefix-" };
            String[] suffixes = { null, "", ".x", ".suffix" };
            var names = new HashSet<String>();
            for (var prefix : prefixes) {
                for (var suffix : suffixes) {
                    var file = Files.createTempFile(dir, prefix, suffix);
                    created.add(file);
                    var name = file.getFileName().toString();
                    var expectedPrefix = prefix != null ? prefix : "";
                    var expectedSuffix = suffix != null ? suffix : ".tmp";
                    var message = "prefix " + prefix + ", suffix " + suffix + ": " + name;
                    assertTrue(name.startsWith(expectedPrefix), message);
                    assertTrue(name.endsWith(expectedSuffix), message);
                    assertTrue(name.length() > expectedPrefix.length() + expectedSuffix.length(), message);
                    assertEquals(dir, file.getParent(), message);
                    assertTrue(Files.isRegularFile(file), message);
                    assertEquals(0, Files.size(file), message);
                    assertTrue(names.add(name), "Name repeated: " + message);
                }
            }
        } finally {
            for (var file : created) {
                Files.deleteIfExists(file);
            }
            Files.delete(dir);
        }
    }

    // Files.createTempFile(prefix, suffix, attrs) "Creates an empty file in the default temporary-file directory";
    // its prefix "may be null" too.
    @Test
    public void createTempFileInDefaultDirectoryTakesShortOrNullPrefix() throws IOException {
        var tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        var shortPrefix = Files.createTempFile("a", null);
        var noPrefix = Files.createTempFile(null, ".x");
        try {
            assertTrue(shortPrefix.getFileName().toString().startsWith("a"));
            assertTrue(shortPrefix.getFileName().toString().endsWith(".tmp"));
            assertTrue(noPrefix.getFileName().toString().endsWith(".x"));
            assertTrue(Files.isRegularFile(shortPrefix));
            assertTrue(Files.isRegularFile(noPrefix));
            assertTrue(Files.isSameFile(tmpDir, shortPrefix.getParent()));
            assertTrue(Files.isSameFile(tmpDir, noPrefix.getParent()));
        } finally {
            Files.deleteIfExists(shortPrefix);
            Files.deleteIfExists(noPrefix);
        }
    }

    // Files.createTempDirectory(prefix, attrs) "Creates a new directory in the default temporary-file directory";
    // "prefix - the prefix string to be used in generating the directory's name; may be null".
    @Test
    public void createTempDirectoryInDefaultDirectoryTakesShortOrNullPrefix() throws IOException {
        var tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        var shortPrefix = Files.createTempDirectory("a");
        var noPrefix = Files.createTempDirectory((String) null);
        try {
            assertTrue(shortPrefix.getFileName().toString().startsWith("a"));
            assertTrue(Files.isDirectory(shortPrefix));
            assertTrue(Files.isDirectory(noPrefix));
            assertTrue(Files.isSameFile(tmpDir, shortPrefix.getParent()));
            assertTrue(Files.isSameFile(tmpDir, noPrefix.getParent()));
        } finally {
            Files.deleteIfExists(shortPrefix);
            Files.deleteIfExists(noPrefix);
        }
    }

    // Files.isReadable/isWritable/isExecutable: "true if the file exists and is readable/writable/executable".
    @Test
    public void accessChecksFollowFilePermissions() throws IOException {
        var file = new File("test-file-permissions");
        var path = Path.of("test-file-permissions");
        assertTrue(file.createNewFile());
        try {
            assertTrue(Files.isReadable(path));
            assertTrue(Files.isWritable(path));
            assertFalse(Files.isExecutable(path));

            assertTrue(file.setExecutable(true));
            assertTrue(Files.isExecutable(path));
            assertTrue(file.setReadable(false));
            assertFalse(Files.isReadable(path));
            assertTrue(file.setWritable(false));
            assertFalse(Files.isWritable(path));
        } finally {
            file.setReadable(true);
            file.setWritable(true);
            file.delete();
        }
        assertFalse(Files.isExecutable(path));
    }

    // Files.createDirectories: "Creates a directory by creating all nonexistent parent directories first. Unlike
    // the createDirectory method, an exception is not thrown if the directory could not be created because it
    // already exists." It returns "the directory".
    // Every depth of an existing prefix (none, some, all), for a relative and an absolute path.
    @Test
    public void createDirectoriesWithEveryExistingPrefix() throws IOException {
        var relative = Path.of("test-dirs-prefix", "a", "b");
        for (var leaf : new Path[] { relative, relative.toAbsolutePath() }) {
            var levels = new Path[] { leaf.getParent().getParent(), leaf.getParent(), leaf };
            for (var existing = 0; existing <= levels.length; ++existing) {
                try {
                    for (var i = 0; i < existing; ++i) {
                        Files.createDirectory(levels[i]);
                    }
                    assertEquals(leaf, Files.createDirectories(leaf));
                    for (var level : levels) {
                        assertTrue(Files.isDirectory(level), level + ", " + existing + " existing");
                    }
                } finally {
                    for (var i = levels.length - 1; i >= 0; --i) {
                        Files.deleteIfExists(levels[i]);
                    }
                }
            }
        }
    }

    // The same, for a path with a single name, relative and absolute.
    @Test
    public void createDirectoriesAcceptsExistingDirectory() throws IOException {
        var dir = Path.of("test-existing-dir");
        try {
            Files.createDirectory(dir);
            assertEquals(dir, Files.createDirectories(dir));
            assertEquals(dir.toAbsolutePath(), Files.createDirectories(dir.toAbsolutePath()));
            assertTrue(Files.isDirectory(dir));
        } finally {
            Files.deleteIfExists(dir);
        }
        var root = Path.of("/");
        assertEquals(root, Files.createDirectories(root));
        // On macOS, /tmp is a symbolic link to a directory. The in-memory file system of JS and Wasm GC has no /tmp.
        var tmp = Path.of("/tmp");
        if (Files.isDirectory(tmp)) {
            assertEquals(tmp, Files.createDirectories(tmp));
        }
    }

    // "FileAlreadyExistsException - if dir exists but is not a directory (optional specific exception)".
    @Test
    public void createDirectoriesRejectsExistingFile() throws IOException {
        var file = Path.of("test-dirs-file");
        var nested = Path.of("test-dirs-parent", "file");
        try {
            Files.createFile(file);
            Files.createDirectory(nested.getParent());
            Files.createFile(nested);
            for (var path : new Path[] { file, file.toAbsolutePath(), nested, nested.toAbsolutePath() }) {
                assertThrows(FileAlreadyExistsException.class, () -> Files.createDirectories(path), path.toString());
                assertTrue(Files.isRegularFile(path));
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(nested.getParent());
        }
    }

    // createDirectory and createFile with an absolute path create the entry at that path.
    @Test
    public void createDirectoryAndFileWithAbsolutePath() throws IOException {
        var dir = Path.of("test-abs-dir").toAbsolutePath();
        var file = dir.resolve("file");
        try {
            assertEquals(dir, Files.createDirectory(dir));
            assertTrue(Files.isDirectory(dir));
            assertTrue(Files.isDirectory(Path.of("test-abs-dir")));
            assertEquals(file, Files.createFile(file));
            assertTrue(Files.isRegularFile(Path.of("test-abs-dir", "file")));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    // Files.newInputStream: "If no options are present then it is equivalent to opening the file with the READ
    // option." Every combination of READ and NOFOLLOW_LINKS opens the file for reading.
    @Test
    public void newInputStreamAcceptsReadOptions() throws IOException {
        var path = Path.of("test-file-read-option");
        var optionSets = new OpenOption[][] {
            {},
            { StandardOpenOption.READ },
            { LinkOption.NOFOLLOW_LINKS },
            { StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS },
            { LinkOption.NOFOLLOW_LINKS, StandardOpenOption.READ },
            { StandardOpenOption.READ, StandardOpenOption.READ },
        };
        try {
            Files.write(path, new byte[] { 1, 2 });
            for (var options : optionSets) {
                try (var input = Files.newInputStream(path, options)) {
                    assertArrayEquals(new byte[] { 1, 2 }, input.readAllBytes(), Arrays.toString(options));
                }
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    // Files.newInputStream throws "IllegalArgumentException - if an invalid combination of options is specified"
    // or "UnsupportedOperationException - if an unsupported option is specified". A stream for reading cannot
    // write, so WRITE and APPEND are one or the other, with or without READ.
    @Test
    public void newInputStreamRejectsWriteOptions() throws IOException {
        var path = Path.of("test-file-read-write-option");
        try {
            Files.write(path, new byte[] { 1, 2 });
            for (var option : new OpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.APPEND }) {
                for (var options : new OpenOption[][] { { option }, { StandardOpenOption.READ, option } }) {
                    try {
                        Files.newInputStream(path, options).close();
                        fail("No exception for " + Arrays.toString(options));
                    } catch (IllegalArgumentException | UnsupportedOperationException e) {
                        // expected
                    }
                }
            }
            assertArrayEquals(new byte[] { 1, 2 }, Files.readAllBytes(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    // Files.newOutputStream: "This method opens or creates a file in exactly the manner specified by the
    // newByteChannel method with the exception that the READ option may not be present in the array of options."
    // It throws "IllegalArgumentException - if options contains an invalid combination of options".
    // StandardOpenOption.APPEND: "This option may not be used in conjunction with the READ or TRUNCATE_EXISTING
    // options."
    @Test
    public void newOutputStreamRejectsInvalidCombinations() throws IOException {
        var path = Path.of("test-file-write-invalid");
        var optionSets = new OpenOption[][] {
            { StandardOpenOption.READ },
            { StandardOpenOption.READ, StandardOpenOption.WRITE },
            { StandardOpenOption.WRITE, StandardOpenOption.READ },
            { StandardOpenOption.APPEND, StandardOpenOption.TRUNCATE_EXISTING },
            { StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.APPEND },
            { StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.TRUNCATE_EXISTING },
        };
        try {
            Files.write(path, new byte[] { 1, 2 });
            for (var options : optionSets) {
                assertThrows(IllegalArgumentException.class, () -> Files.newOutputStream(path, options).close(),
                        Arrays.toString(options));
                assertArrayEquals(new byte[] { 1, 2 }, Files.readAllBytes(path), Arrays.toString(options));
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    // Each option newOutputStream supports, on an existing file. Without TRUNCATE_EXISTING the file keeps its
    // length and writing starts at the beginning ("By default reading or writing commence at the beginning of the
    // file"); APPEND: "bytes will be written to the end of the file rather than the beginning";
    // TRUNCATE_EXISTING: "the existing file is truncated to a size of 0 bytes"; CREATE: "an existing file is opened
    // if it exists". SYNC, DSYNC, SPARSE and NOFOLLOW_LINKS do not change the content.
    @Test
    public void newOutputStreamOptionsOnExistingFile() throws IOException {
        var path = Path.of("test-file-write-options");
        var options = new OpenOption[] {
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE,
            StandardOpenOption.SYNC,
            StandardOpenOption.DSYNC,
            StandardOpenOption.SPARSE,
            LinkOption.NOFOLLOW_LINKS,
        };
        var expected = new byte[][] {
            { 9, 2, 3 },
            { 1, 2, 3, 9 },
            { 9 },
            { 9, 2, 3 },
            { 9, 2, 3 },
            { 9, 2, 3 },
            { 9, 2, 3 },
            { 9, 2, 3 },
        };
        try {
            for (var i = 0; i < options.length; ++i) {
                Files.write(path, new byte[] { 1, 2, 3 });
                try (var output = Files.newOutputStream(path, options[i])) {
                    output.write(9);
                }
                assertArrayEquals(expected[i], Files.readAllBytes(path), options[i].toString());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    // StandardOpenOption.DELETE_ON_CLOSE: "When this option is present then the implementation makes a best effort
    // attempt to delete the file when closed by the appropriate close method." TeaVM accepts the option but never
    // deletes the file.
    @Test
    @Disabled("newOutputStream accepts DELETE_ON_CLOSE but does not delete the file when the stream is closed")
    public void newOutputStreamDeleteOnClose() throws IOException {
        var path = Path.of("test-file-delete-on-close");
        try {
            try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.DELETE_ON_CLOSE)) {
                output.write(1);
            }
            assertFalse(Files.exists(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    // Files.setLastModifiedTime: "Updates a file's last modified time attribute." It throws IOException
    // "if an I/O error occurs", which for a missing file is NoSuchFileException.
    // BasicFileAttributeView.setTimes: "The values are converted to the epoch and precision supported by the
    // file system. Converting from finer to coarser granularities results in precision loss."
    // So a file system may keep whole seconds (the C backend's does), but must not keep any other value.
    @Test
    public void setLastModifiedTime() throws IOException {
        var path = Path.of("test-file-mtime");
        var dir = Path.of("test-dir-mtime");
        try {
            Files.write(path, new byte[] { 1 });
            Files.createDirectory(dir);
            long[] times = {
                    1_600_000_000_000L,
                    1_600_000_000_123L,
                    1_600_000_000_999L,
                    0L,
                    1L,
                    999L,
                    // Before the epoch, only whole seconds: the JDK on Linux stores -1500 ms as 0
                    -1_000L,
                    -86_400_000L * 365 * 30,
                    315_550_800_000L,
                    4_102_444_800_456L
            };
            for (var target : new Path[] { path, dir }) {
                for (var millis : times) {
                    var time = FileTime.fromMillis(millis);
                    assertEquals(target, Files.setLastModifiedTime(target, time));
                    assertStoredTime(millis, Files.getLastModifiedTime(target).toMillis());
                    assertStoredTime(millis, Files.readAttributes(target, BasicFileAttributes.class)
                            .lastModifiedTime().toMillis());
                }
            }
            Files.setLastModifiedTime(path, FileTime.from(1_600_000_123L, TimeUnit.SECONDS));
            assertStoredTime(1_600_000_123_000L, Files.getLastModifiedTime(path).toMillis());
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir);
        }
    }

    private static void assertStoredTime(long expected, long actual) {
        if (actual != expected) {
            assertEquals(expected / 1000 * 1000, actual, "time set: " + expected);
        }
    }

    @Test
    public void setLastModifiedTimeOfMissingFile() throws IOException {
        var time = FileTime.fromMillis(1_600_000_000_000L);
        assertThrows(NoSuchFileException.class, () -> Files.setLastModifiedTime(Path.of("no-such-file"), time));
        assertThrows(NoSuchFileException.class, () -> Files.setLastModifiedTime(
                Path.of("no-such-dir", "no-such-file"), time));
        assertFalse(Files.exists(Path.of("no-such-file")));
    }

    // java.nio.file package docs: "Unless otherwise noted, passing a null argument to a constructor or method of
    // any class or interface in this package will cause a NullPointerException to be thrown."
    @Test
    public void setLastModifiedTimeRejectsNull() throws IOException {
        var path = Path.of("test-file-mtime-null");
        try {
            Files.write(path, new byte[] { 1 });
            assertThrows(NullPointerException.class, () -> Files.setLastModifiedTime(path, null));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void readString() throws IOException {
        var path = Path.of("test-file-string");
        try {
            Files.write(path, "héllo".getBytes(StandardCharsets.UTF_8));
            assertEquals("héllo", Files.readString(path));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void createDirectoriesWithAbsolutePath() throws IOException {
        var dir = Path.of("test-abs-dirs").toAbsolutePath();
        var leaf = dir.resolve("a").resolve("b");
        try {
            Files.createDirectories(leaf);
            assertTrue(Files.isDirectory(leaf));
        } finally {
            Files.deleteIfExists(leaf);
            Files.deleteIfExists(dir.resolve("a"));
            Files.deleteIfExists(dir);
        }
    }
}
