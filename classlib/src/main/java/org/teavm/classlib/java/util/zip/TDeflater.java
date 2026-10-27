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
    // Output space below which a sync or full flush goes to the internal buffer. Each resumed flush writes an empty
    // static block and an empty stored block, at most 7 bytes, so a larger buffer always makes progress.
    private static final int MIN_FLUSH_OUTPUT = 64;
    private int flushParm = Z_NO_FLUSH;
    private boolean finished;
    private int compressLevel = DEFAULT_COMPRESSION;
    private int strategy = DEFAULT_STRATEGY;
    private Deflater impl;
    private int inRead;
    private int inLength;
    private boolean nowrap;
    // A setLevel or setStrategy change not applied yet. The Javadoc of setLevel: "the next invocation of deflate will
    // compress the input available so far with the old level (and may be flushed); the new level will take effect
    // only after that invocation". Before the stream reads any input, setLevel and setStrategy apply a change at once.
    private boolean paramsChanged;
    // The level that the stream compresses with now (compressLevel may be a change not applied yet).
    private int appliedLevel;
    // Output of a parameter-change flush, or of a flush into a small buffer, that the caller's buffers have not
    // taken yet (see flushFully). getTotalOut and getBytesWritten do not count it, as the JDK counts only delivered
    // bytes.
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

        int count = takeHeld(buf, off, nbytes);
        if (held != null) {
            return count;
        }
        off += count;
        nbytes -= count;
        long sin = impl.total_in;
        long sout = impl.total_out;
        // Zlib resumes a SYNC_FLUSH or FULL_FLUSH that filled the output with a new flush, which writes another
        // empty block (zlib.h: "make sure that avail_out is greater than six to avoid repeated flush markers").
        // In a buffer smaller than that block, the flush never completed. So such a flush goes to the internal
        // buffer, and the caller's buffers take its output on the next calls. A pending parameter change takes the
        // usual path, which applies it.
        if ((flushParam == SYNC_FLUSH || flushParam == FULL_FLUSH) && nbytes < MIN_FLUSH_OUTPUT && !paramsChanged) {
            hold(flushFully(flushParam));
            inRead += impl.total_in - sin;
            return count + takeHeld(buf, off, nbytes);
        }
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
        int written = (int) (impl.total_out - sout);
        count += written;
        // The input available at this call was compressed with the old parameters, so a pending change applies now.
        // Not after finish(): zlib rejects the flush of a finishing stream, and the change no longer matters.
        if (paramsChanged && impl.avail_in == 0 && flushParm != Z_FINISH) {
            applyParams();
            count += takeHeld(buf, off + written, nbytes - written);
        }
        return count;
    }

    // Copies held output to the caller's buffer.
    private int takeHeld(byte[] buf, int off, int nbytes) {
        if (held == null) {
            return 0;
        }
        int count = Math.min(nbytes, heldRemaining());
        System.arraycopy(held, heldPos, buf, off, count);
        heldPos += count;
        if (heldPos == heldEnd) {
            dropHeld();
        }
        return count;
    }

    private void applyParams() {
        paramsChanged = false;
        if (impl.total_in != 0 && function(appliedLevel) != function(compressLevel)) {
            flushAndSetParams();
        } else {
            setParams();
        }
    }

    // Zlib's deflateParams. When the compression function changes on a stream that has read input,
    // jzlib (zlib 1.1) runs a Z_PARTIAL_FLUSH and switches the function even when that flush did not fit the
    // output. The lazy "slow" function (levels 4-9) holds one pending literal, so switching early lost a byte.
    // So the flush must complete before params() runs. It goes to an internal buffer, which grows until the flush
    // fits: a flush left half done in a small caller buffer cannot be resumed, because each new Z_PARTIAL_FLUSH
    // after a full output buffer emits another empty block, and with a 1-byte buffer the stream never ended.
    // This runs only when no input is available (avail_in == 0), so the buffer holds only data that the stream has
    // already read (at most one block), and params() cannot compress new input with the old function.
    private void flushAndSetParams() {
        byte[] out = flushFully(JZlib.Z_PARTIAL_FLUSH);
        // params() repeats the flush, which now finds nothing to do; any output still goes to out.
        setParams();
        hold(out);
    }

    // Runs a flush in an internal buffer that grows until the flush fits. The output is the start of the returned
    // buffer, up to impl.avail_out bytes before its end.
    private byte[] flushFully(int flushParam) {
        int length = 0;
        byte[] out = new byte[4096];
        while (true) {
            impl.setOutput(out, length, out.length - length);
            check(impl.deflate(flushParam));
            length = out.length - impl.avail_out;
            if (impl.avail_out > 0) {
                return out;
            }
            out = Arrays.copyOf(out, out.length * 2);
        }
    }

    // Keeps the output that flushFully wrote to out for the caller's buffers.
    private void hold(byte[] out) {
        heldEnd = out.length - impl.avail_out;
        if (heldEnd > 0) {
            held = out;
            heldPos = 0;
        }
    }

    private void setParams() {
        check(impl.params(compressLevel, strategy));
        appliedLevel = compressLevel;
    }

    private static void check(int err) {
        if (err != JZlib.Z_OK && err != JZlib.Z_BUF_ERROR) {
            throw new RuntimeException("Error: " + err);
        }
    }

    // Output that the caller's buffers have not taken yet.
    private int heldRemaining() {
        return heldEnd - heldPos;
    }

    // The compression function for a level in jzlib 1.1.3 (Deflate.config_table, as in zlib 1.1): 0 stores, 1-3 use
    // the fast function, 4-9 the lazy one. DEFAULT_COMPRESSION is level 6. Deflate.deflateParams flushes only when
    // this function changes (not for a strategy change), so a jzlib upgrade must recheck this.
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

        return (int) (impl.getTotalOut() - heldRemaining());
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
        // The old input would otherwise go into the new stream.
        impl.next_in = null;
        impl.next_in_index = 0;
        impl.avail_in = 0;
        inRead = 0;
        inLength = 0;
        setParams();
        paramsChanged = false;
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
            paramsChanged();
        }
    }

    public void setStrategy(int strategy) {
        if (strategy < DEFAULT_STRATEGY || strategy > HUFFMAN_ONLY) {
            throw new IllegalArgumentException();
        }
        if (this.strategy != strategy) {
            this.strategy = strategy;
            paramsChanged();
        }
    }

    // Before the stream reads input, jzlib's params() applies a change without a flush.
    private void paramsChanged() {
        if (impl != null && impl.total_in == 0) {
            setParams();
        } else {
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
        return impl.getTotalOut() - heldRemaining();
    }

    @Override
    public void close() throws Exception {
        end();
    }
}
