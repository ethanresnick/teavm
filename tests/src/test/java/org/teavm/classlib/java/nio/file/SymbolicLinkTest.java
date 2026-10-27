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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystem;
import org.teavm.runtime.fs.VirtualFileSystemProvider;
import org.teavm.runtime.fs.memory.InMemoryVirtualFileSystem;

@TeaVMTest
@SkipJVM
public class SymbolicLinkTest {
    // The default file system keeps the VirtualFileSystem that it finds first, so this class installs one with
    // symbolic links before any file system use, in its only test.
    @Test
    public void linksAreReportedAndNotFollowedByWalkOrDelete() throws IOException {
        var memory = new InMemoryVirtualFileSystem();
        VirtualFileSystemProvider.setInstance(new LinkFileSystem(memory, Map.of("/links/link-to-dir",
                "/links/target-dir", "/links/dangling", "/links/missing")));
        Files.createDirectories(Path.of("/links/target-dir"));
        Files.write(Path.of("/links/target-dir/a.txt"), new byte[] { 1 });
        // The links' directory entries, made in the in-memory file system: LinkFileSystem reports them as links.
        memory.getFile("/links").createFile("link-to-dir");
        memory.getFile("/links").createFile("dangling");

        var link = Path.of("/links/link-to-dir");
        assertTrue(Files.isSymbolicLink(link));
        assertFalse(Files.isSymbolicLink(Path.of("/links/target-dir")));
        assertTrue(Files.isDirectory(link));
        assertFalse(Files.isDirectory(link, LinkOption.NOFOLLOW_LINKS));
        var attributes = Files.readAttributes(link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertTrue(attributes.isSymbolicLink());
        assertFalse(attributes.isDirectory());
        assertFalse(Files.readAttributes(link, BasicFileAttributes.class).isSymbolicLink());

        Set<String> visited = new TreeSet<>();
        Files.walkFileTree(Path.of("/links"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                visited.add("dir " + dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                visited.add((attrs.isSymbolicLink() ? "link " : "file ") + file);
                return FileVisitResult.CONTINUE;
            }
        });
        assertEquals(Set.of("dir /links", "dir /links/target-dir", "file /links/target-dir/a.txt",
                "link /links/dangling", "link /links/link-to-dir"), visited);

        // A link whose target does not exist: only a NOFOLLOW_LINKS query sees it, and delete removes it.
        var dangling = Path.of("/links/dangling");
        assertFalse(Files.exists(dangling));
        assertTrue(Files.exists(dangling, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.notExists(dangling, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isSymbolicLink(dangling));

        Files.delete(dangling);
        assertFalse(Files.exists(dangling, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(Path.of("/links/target-dir")));
    }

    // Delegates to an in-memory file system and reports some paths as symbolic links to others.
    static class LinkFileSystem implements VirtualFileSystem {
        private final VirtualFileSystem delegate;
        private final Map<String, String> links;

        LinkFileSystem(VirtualFileSystem delegate, Map<String, String> links) {
            this.delegate = delegate;
            this.links = links;
        }

        @Override
        public String getUserDir() {
            return delegate.getUserDir();
        }

        @Override
        public VirtualFile getFile(String path) {
            var target = links.get(path);
            if (target != null) {
                return new LinkFile(delegate.getFile(path), delegate.getFile(target));
            }
            return delegate.getFile(path);
        }

        @Override
        public boolean isWindows() {
            return delegate.isWindows();
        }

        @Override
        public String canonicalize(String path) {
            return delegate.canonicalize(path);
        }

        @Override
        public String[] getRoots() {
            return delegate.getRoots();
        }
    }

    // The link's own entry answers getName, delete and isSymbolicLink; everything else describes the target.
    static class LinkFile implements VirtualFile {
        private final VirtualFile entry;
        private final VirtualFile target;

        LinkFile(VirtualFile entry, VirtualFile target) {
            this.entry = entry;
            this.target = target;
        }

        public boolean isSymbolicLink() {
            return entry.exists();
        }

        @Override
        public String getName() {
            return entry.getName();
        }

        @Override
        public boolean isDirectory() {
            return entry.exists() ? target.isDirectory() : entry.isDirectory();
        }

        @Override
        public boolean isFile() {
            return entry.exists() ? target.isFile() : entry.isFile();
        }

        @Override
        public String[] listFiles() {
            return entry.exists() ? target.listFiles() : entry.listFiles();
        }

        @Override
        public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
            return entry.exists() ? target.createAccessor(readable, writable, append)
                    : entry.createAccessor(readable, writable, append);
        }

        @Override
        public boolean createFile(String fileName) throws IOException {
            return target.createFile(fileName);
        }

        @Override
        public boolean createDirectory(String fileName) {
            return target.createDirectory(fileName);
        }

        @Override
        public boolean delete() {
            return entry.delete();
        }

        @Override
        public boolean adopt(VirtualFile file, String fileName) {
            return target.adopt(file, fileName);
        }

        @Override
        public boolean canRead() {
            return target.canRead();
        }

        @Override
        public boolean canWrite() {
            return target.canWrite();
        }

        @Override
        public long lastModified() {
            return target.lastModified();
        }

        @Override
        public boolean setLastModified(long lastModified) {
            return target.setLastModified(lastModified);
        }

        @Override
        public boolean setReadOnly(boolean readOnly) {
            return target.setReadOnly(readOnly);
        }

        @Override
        public int length() {
            return target.length();
        }
    }
}
