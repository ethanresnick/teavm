/*
 *  Copyright 2026 Alexey Andreev.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.memory.InMemoryVirtualFileSystem;
import org.teavm.runtime.fs.memory.VirtualFileImpl;

@TeaVMTest
@SkipJVM
public class FileAccessorLifecycleTest {
    @Test
    public void randomAccessCloseReleasesAccessorExactlyOnce() throws IOException {
        verifyRandomAccessClose(false);
    }

    @Test
    public void failedRandomAccessCloseRemainsClosed() throws IOException {
        verifyRandomAccessClose(true);
    }

    private void verifyRandomAccessClose(boolean fail) throws IOException {
        var accessor = new TestAccessor();
        accessor.failClose = fail;
        var file = new TRandomAccessFile(new TestFile(accessor), "rw");
        if (fail) {
            assertThrows(IOException.class, file::close);
        } else {
            file.close();
        }
        file.close();
        assertEquals(1, accessor.closes);
        assertThrows(IOException.class, file::read);
        assertThrows(IOException.class, () -> file.write(1));
        assertEquals(0, accessor.reads);
        assertEquals(0, accessor.writes);
    }

    @Test
    public void inputCloseDetachesAccessorEvenOnFailure() throws IOException {
        var inputAccessor = new TestAccessor();
        inputAccessor.failClose = true;
        var input = new TFileInputStream(inputAccessor);
        assertThrows(IOException.class, input::close);
        input.close();
        assertThrows(IOException.class, input::read);
        assertEquals(1, inputAccessor.closes);
        assertEquals(0, inputAccessor.reads);
    }

    @Test
    public void outputCloseDetachesAccessorEvenOnFailure() throws IOException {
        var outputAccessor = new TestAccessor();
        outputAccessor.failClose = true;
        var output = new TFileOutputStream(outputAccessor);
        assertThrows(IOException.class, output::close);
        output.close();
        assertThrows(IOException.class, () -> output.write(1));
        assertThrows(IOException.class, output::flush);
        assertEquals(1, outputAccessor.closes);
        assertEquals(0, outputAccessor.writes);
        assertEquals(0, outputAccessor.flushes);
    }

    @Test
    public void synchronousModesSynchronizeWritesAndLengthChanges() throws IOException {
        // RandomAccessFile mode rwd/rws: every content update is synchronous with storage.
        for (String mode : new String[] { "rwd", "rws", "rw" }) {
            var accessor = new SyncAccessor();
            try (var file = new TRandomAccessFile(new TestFile(accessor), mode)) {
                file.write(1);
                file.write(new byte[] { 2, 3 });
                file.setLength(2);
                assertEquals(mode.equals("rw") ? 0 : 3, accessor.syncs);
                assertEquals(0, accessor.flushes);
                assertEquals(2, accessor.writes);
                assertEquals(1, accessor.resizes);
                assertEquals(2, file.getFilePointer());
                if (!mode.equals("rw")) {
                    accessor.failSync = true;
                    assertThrows(IOException.class, () -> file.write(4));
                    assertThrows(IOException.class, () -> file.write(new byte[] { 4 }));
                    assertThrows(IOException.class, () -> file.setLength(1));
                    assertEquals(6, accessor.syncs);
                }
            }
        }
    }

    @Test
    public void unsupportedSynchronizationFailsLoudly() throws IOException {
        var accessor = new TestAccessor();
        try (var file = new TRandomAccessFile(new TestFile(accessor), "rwd")) {
            assertThrows(IOException.class, () -> file.write(1));
            assertEquals(1, accessor.writes);
            assertEquals(0, accessor.flushes);
        }
    }

    static class TestFile extends TFile {
        private final VirtualFile file;

        TestFile(VirtualFileAccessor accessor) {
            super("tracked");
            file = new VirtualFileImpl(new InMemoryVirtualFileSystem(), "/tracked") {
                @Override
                public boolean isFile() {
                    return true;
                }

                @Override
                public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
                    return accessor;
                }
            };
        }

        @Override
        VirtualFile findVirtualFile() {
            return file;
        }
    }

    static class TestAccessor implements VirtualFileAccessor {
        int reads;
        int writes;
        int closes;
        int flushes;
        int resizes;
        int position;
        int length;
        boolean failClose;

        @Override
        public int read(byte[] buffer, int offset, int limit) {
            ++reads;
            return 0;
        }

        @Override
        public void write(byte[] buffer, int offset, int limit) {
            ++writes;
            position += limit;
            length = Math.max(length, position);
        }

        @Override
        public int tell() {
            return position;
        }

        @Override
        public void seek(int target) {
            position = target;
        }

        @Override
        public void skip(int amount) {
            position += amount;
        }

        @Override
        public int size() {
            return length;
        }

        @Override
        public void resize(int size) {
            ++resizes;
            position = length;
            length = size;
        }

        @Override
        public void close() throws IOException {
            ++closes;
            if (failClose) {
                throw new IOException();
            }
        }

        @Override
        public void flush() {
            ++flushes;
        }
    }

    static class SyncAccessor extends TestAccessor {
        int syncs;
        boolean failSync;

        @Override
        public void sync() throws IOException {
            ++syncs;
            if (failSync) {
                throw new IOException();
            }
        }
    }
}
