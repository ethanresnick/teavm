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

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.GZIPException;
import com.jcraft.jzlib.JZlib;
import java.util.Arrays;
import org.teavm.classlib.java.lang.TAutoCloseable;

public class TDeflater implements TAutoCloseable {
    public static final int BEST_COMPRESSION = 9;
    public static final int BEST_SPEED = 1;
    public static final int DEFAULT_COMPRESSION = -1;
    public static final int DEFAULT_STRATEGY = 0;
    public static final int DEFLATED = 8;
    public static final int FILTERED = 1;
    public static final int HUFFMAN_ONLY = 2;
    public static final int NO_COMPRESSION = 0;
    // The JDK's public flush modes, for deflate(byte[], int, int, int).
    public static final int NO_FLUSH = 0;
    public static final int SYNC_FLUSH = 2;
    public static final int FULL_FLUSH = 3;
    static final int Z_NO_FLUSH = 0;
    static final int Z_SYNC_FLUSH = 2;
    static final int Z_FINISH = 4;
    private int flushParm = Z_NO_FLUSH;
    private boolean finished;
    private int compressLevel = DEFAULT_COMPRESSION;
    private int strategy = DEFAULT_STRATEGY;
    private Deflater impl;
    private int inRead;
    private int inLength;
    private boolean nowrap;
    // As in the JDK, setLevel and setStrategy take effect at the next deflate call.
    private boolean paramsChanged;
    // The level that the stream compresses with now (compressLevel may be a change not applied yet).
    private int appliedLevel;
    // Output of a parameter-change flush that the caller's buffers have not taken yet (see
    // flushAndSetParams). getTotalOut and getBytesWritten do not count it, as the JDK counts only delivered bytes.
    private byte[] held;
    private int heldPos;
    private int heldEnd;

    public TDeflater() {
        this(DEFAULT_COMPRESSION, false);
    }

    public TDeflater(int level) {
        this(level, false);
    }

    public TDeflater(int level, boolean noHeader) {
        super();
        if (level < DEFAULT_COMPRESSION || level > BEST_COMPRESSION) {
            throw new IllegalArgumentException();
        }
        compressLevel = level;
        appliedLevel = level;
        try {
            impl = new Deflater(compressLevel, noHeader);
        } catch (GZIPException e) {
            // do nothing
        }
        nowrap = noHeader;
    }

    public int deflate(byte[] buf) {
        return deflate(buf, 0, buf.length);
    }

    public int deflate(byte[] buf, int off, int nbytes) {
        return deflateImpl(buf, off, nbytes, flushParm);
    }

    // Public, as in the JDK since Java 7. The JDK accepts only the three flush modes, and once
    // finish() was called it finishes the stream whatever mode the caller passes.
    public int deflate(byte[] buf, int off, int nbytes, int flush) {
        if (flush != NO_FLUSH && flush != SYNC_FLUSH && flush != FULL_FLUSH) {
            throw new IllegalArgumentException();
        }
        return deflateImpl(buf, off, nbytes, flushParm == Z_FINISH ? Z_FINISH : flush);
    }

    private int deflateImpl(byte[] buf, int off, int nbytes, int flushParam) {
        if (impl == null) {
            throw new IllegalStateException();
        }
        // avoid int overflow, check null buf
        if (off > buf.length || nbytes < 0 || off < 0 || buf.length - off < nbytes) {
            throw new ArrayIndexOutOfBoundsException();
        }

        long sin = impl.total_in;
        if (paramsChanged) {
            paramsChanged = false;
            if (impl.total_in != 0 && function(appliedLevel) != function(compressLevel)) {
                flushAndSetParams();
            } else {
                setParams();
            }
        }
        int count = 0;
        if (heldPos < heldEnd) {
            count = Math.min(nbytes, heldEnd - heldPos);
            System.arraycopy(held, heldPos, buf, off, count);
            heldPos += count;
            if (heldPos < heldEnd) {
                inRead += impl.total_in - sin;
                return count;
            }
            off += count;
            nbytes -= count;
        }
        long sout = impl.total_out;
        impl.setOutput(buf, off, nbytes);
        int err = impl.deflate(flushParam);
        switch (err) {
            case JZlib.Z_OK:
                break;
            case JZlib.Z_STREAM_END:
                finished = true;
                break;
            case JZlib.Z_BUF_ERROR:
                // Zlib reports "no progress possible" this way. The JDK's Deflater returns 0 bytes
                // for it, and callers loop until deflate returns 0.
                break;
            default:
                throw new RuntimeException("Error: " + err);
        }

        inRead += impl.total_in - sin;
        return count + (int) (impl.total_out - sout);
    }

    // Zlib's deflateParams. When the compression function changes on a stream that has read input,
    // jzlib (zlib 1.1) runs a Z_PARTIAL_FLUSH and switches the function even when that flush did not fit the
    // output. The lazy "slow" function (levels 4-9) holds one pending literal, so switching early lost a byte.
    // So the flush must complete before params() runs. It goes to an internal buffer, which grows until the flush
    // fits: a flush left half done in a small caller buffer cannot be resumed, because each new Z_PARTIAL_FLUSH
    // after a full output buffer emits another empty block, and with a 1-byte buffer the stream never ended.
    // The flush and params() see no new input, so the buffer holds only data that the stream has already read
    // (at most one block), and params() cannot compress new input with the old function.
    private void flushAndSetParams() {
        int availIn = impl.avail_in;
        impl.avail_in = 0;
        int length = heldEnd - heldPos;
        byte[] out = new byte[length + 4096];
        if (length > 0) {
            System.arraycopy(held, heldPos, out, 0, length);
        }
        while (true) {
            impl.setOutput(out, length, out.length - length);
            int err = impl.deflate(JZlib.Z_PARTIAL_FLUSH);
            if (err != JZlib.Z_OK && err != JZlib.Z_BUF_ERROR) {
                throw new RuntimeException("Error: " + err);
            }
            length = out.length - impl.avail_out;
            if (impl.avail_out > 0) {
                break;
            }
            out = Arrays.copyOf(out, out.length * 2);
        }
        // params() repeats the flush, which now finds nothing to do; any output still goes to out.
        setParams();
        impl.avail_in = availIn;
        held = out;
        heldPos = 0;
        heldEnd = out.length - impl.avail_out;
    }

    private void setParams() {
        int err = impl.params(compressLevel, strategy);
        if (err != JZlib.Z_OK && err != JZlib.Z_BUF_ERROR) {
            throw new RuntimeException("Error: " + err);
        }
        appliedLevel = compressLevel;
    }

    // Zlib 1.1's compression function for a level (its config_table): 0 stores, 1-3 use the fast
    // function, 4-9 the lazy one. DEFAULT_COMPRESSION is level 6.
    private static int function(int level) {
        if (level == DEFAULT_COMPRESSION) {
            level = 6;
        }
        return level == 0 ? 0 : level <= 3 ? 1 : 2;
    }

    public void end() {
        impl = null;
        dropHeld();
    }

    private void dropHeld() {
        held = null;
        heldPos = 0;
        heldEnd = 0;
    }

    public void finish() {
        flushParm = Z_FINISH;
    }

    public boolean finished() {
        return finished;
    }

    public int getAdler() {
        if (impl == null) {
            throw new IllegalStateException();
        }

        return (int) impl.getAdler();
    }

    public int getTotalIn() {
        if (impl == null) {
            throw new IllegalStateException();
        }

        return (int) impl.getTotalIn();
    }

    public int getTotalOut() {
        if (impl == null) {
            throw new IllegalStateException();
        }

        return (int) (impl.getTotalOut() - (heldEnd - heldPos));
    }

    public boolean needsInput() {
        return inRead == inLength;
    }

    public void reset() {
        if (impl == null) {
            throw new NullPointerException();
        }

        flushParm = Z_NO_FLUSH;
        finished = false;
        impl.init(compressLevel, 15, nowrap);
        impl.params(compressLevel, strategy);
        paramsChanged = false;
        appliedLevel = compressLevel;
        dropHeld();
    }

    public void setDictionary(byte[] buf) {
        setDictionary(buf, 0, buf.length);
    }

    public void setDictionary(byte[] buf, int off, int nbytes) {
        if (impl == null) {
            throw new IllegalStateException();
        }
        // avoid int overflow, check null buf
        if (off <= buf.length && nbytes >= 0 && off >= 0 && buf.length - off >= nbytes) {
            impl.setDictionary(Arrays.copyOfRange(buf, off, buf.length), nbytes);
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void setInput(byte[] buf) {
        setInput(buf, 0, buf.length);
    }

    public void setInput(byte[] buf, int off, int nbytes) {
        if (impl == null) {
            throw new IllegalStateException();
        }
        // avoid int overflow, check null buf
        if (off <= buf.length && nbytes >= 0 && off >= 0 && buf.length - off >= nbytes) {
            inLength = nbytes;
            inRead = 0;
            // No init here. The constructor and reset() initialize the stream. The first setInput
            // initialized it again, which discarded a dictionary set before it and the strategy.
            impl.setInput(buf, off, nbytes, false);
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void setLevel(int level) {
        if (level < DEFAULT_COMPRESSION || level > BEST_COMPRESSION) {
            throw new IllegalArgumentException();
        }
        if (compressLevel != level) {
            compressLevel = level;
            paramsChanged = true;
        }
    }

    public void setStrategy(int strategy) {
        if (strategy < DEFAULT_STRATEGY || strategy > HUFFMAN_ONLY) {
            throw new IllegalArgumentException();
        }
        if (this.strategy != strategy) {
            this.strategy = strategy;
            paramsChanged = true;
        }
    }

    public long getBytesRead() {
        // Throw NPE here
        if (impl == null) {
            throw new NullPointerException();
        }
        return impl.getTotalIn();
    }

    public long getBytesWritten() {
        // Throw NPE here
        if (impl == null) {
            throw new NullPointerException();
        }
        return impl.getTotalOut() - (heldEnd - heldPos);
    }

    @Override
    public void close() throws Exception {
        end();
    }
}
