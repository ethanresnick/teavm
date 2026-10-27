/*
 *  Copyright 2017 Alexey Andreev.
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

package org.teavm.classlib.java.util.zip;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TDeflaterOutputStream extends FilterOutputStream {
    static final int BUF_SIZE = 512;
    protected byte[] buf;
    protected TDeflater def;
    boolean done;
    // The JDK's syncFlush flag. flush() emits a sync-flush block only when it is true.
    private final boolean syncFlush;

    public TDeflaterOutputStream(OutputStream os, TDeflater def) {
        this(os, def, BUF_SIZE);
    }

    public TDeflaterOutputStream(OutputStream os) {
        this(os, new TDeflater());
    }

    public TDeflaterOutputStream(OutputStream os, TDeflater def, int bsize) {
        this(os, def, bsize, false);
    }

    public TDeflaterOutputStream(OutputStream os, TDeflater def, boolean syncFlush) {
        this(os, def, BUF_SIZE, syncFlush);
    }

    public TDeflaterOutputStream(OutputStream os, boolean syncFlush) {
        this(os, new TDeflater(), BUF_SIZE, syncFlush);
    }

    public TDeflaterOutputStream(OutputStream os, TDeflater def, int bsize, boolean syncFlush) {
        super(os);
        this.syncFlush = syncFlush;
        if (os == null || def == null) {
            throw new NullPointerException();
        }
        if (bsize <= 0) {
            throw new IllegalArgumentException();
        }
        this.def = def;
        buf = new byte[bsize];
    }

    protected void deflate() throws IOException {
        int x;
        do {
            x = def.deflate(buf);
            out.write(buf, 0, x);
        } while (!def.needsInput());
    }

    @Override
    public void flush() throws IOException {
        if (syncFlush && !def.finished()) {
            // A full buffer means more flushed output may be pending, so deflate again.
            int count;
            do {
                count = def.deflate(buf, 0, buf.length, TDeflater.SYNC_FLUSH);
                if (count > 0) {
                    out.write(buf, 0, count);
                }
            } while (count == buf.length);
        }
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!def.finished()) {
            finish();
        }
        def.end();
        out.close();
    }

    public void finish() throws IOException {
        if (done) {
            return;
        }
        def.finish();
        int x = 0;
        while (!def.finished()) {
            if (def.needsInput()) {
                def.setInput(buf, 0, 0);
            }
            x = def.deflate(buf);
            out.write(buf, 0, x);
        }
        done = true;
    }

    @Override
    public void write(int i) throws IOException {
        byte[] b = new byte[1];
        b[0] = (byte) i;
        write(b, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int off, int nbytes) throws IOException {
        if (done) {
            throw new IOException();
        }
        // avoid int overflow, check null buf
        if (off <= buffer.length && nbytes >= 0 && off >= 0 && buffer.length - off >= nbytes) {
            if (!def.needsInput()) {
                throw new IOException();
            }
            def.setInput(buffer, off, nbytes);
            deflate();
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }
}
