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
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.runtime.fs.VirtualFileSystemProvider;
import org.teavm.runtime.fs.memory.InMemoryVirtualFileSystem;

// A class of its own: the default file system keeps the first VirtualFileSystem that it finds.
@TeaVMTest
@SkipJVM
public class SymbolicLinkCycleTest {
    // walkFileTree: "If the options parameter contains the FOLLOW_LINKS option then this method keeps track of
    // directories visited so that cycles can be detected. [...] When a cycle is detected it is treated as an I/O
    // error, and the visitFileFailed method is invoked with an instance of FileSystemLoopException."
    @Test
    @Disabled("walkFileTree does not detect cycles with FOLLOW_LINKS: VirtualFile has no file keys, and isSameFile "
            + "compares paths")
    public void followLinksReportsCycle() throws IOException {
        var memory = new InMemoryVirtualFileSystem();
        VirtualFileSystemProvider.setInstance(new SymbolicLinkTest.LinkFileSystem(memory,
                Map.of("/loop/self", "/loop")));
        Files.createDirectories(Path.of("/loop"));
        memory.getFile("/loop").createFile("self");

        List<String> events = new ArrayList<>();
        Files.walkFileTree(Path.of("/loop"), Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        events.add("dir " + dir);
                        // Without cycle detection the walk would go on until the file system gives up.
                        return dir.getNameCount() > 4 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        events.add(exc.getClass().getSimpleName() + " " + file);
                        return FileVisitResult.CONTINUE;
                    }
                });
        assertEquals(List.of("dir /loop", "FileSystemLoopException /loop/self"), events);
    }
}
