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

import com.jcraft.jzlib.GZIPException;
import com.jcraft.jzlib.Inflater;
import com.jcraft.jzlib.JZlib;
import java.util.Arrays;
import org.teavm.classlib.java.lang.TAutoCloseable;

public class TInflater implements TAutoCloseable {
    private boolean finished;
    private boolean nowrap;
    int inLength;
    int inRead;
    private boolean needsDictionary;
    // The zlib header's two bytes, kept as inflate consumes them (see the Z_NEED_DICT case).
    private final byte[] header = new byte[2];
    private int headerLength;
    private Inflater impl;

    public TInflater() {
        this(false);
    }

    public TInflater(boolean noHeader) {
        nowrap = noHeader;
        try {
            impl = new Inflater(noHeader);
        } catch (GZIPException e) {
            // Do nothing
        }
    }

    public void end() {
        inRead = 0;
        inLength = 0;
        impl = null;
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

    private native int getAdlerImpl(long handle);

    public long getBytesRead() {
        if (impl == null) {
            throw new IllegalStateException();
        }
        return impl.getTotalIn();
    }

    public long getBytesWritten() {
        if (impl == null) {
            throw new IllegalStateException();
        }
        return impl.getTotalOut();
    }

    public int getRemaining() {
        return inLength - inRead;
    }

    public int getTotalIn() {
        return (int) getBytesRead();
    }

    private native long getTotalInImpl(long handle);

    public int getTotalOut() {
        return (int) getBytesWritten();
    }

    private native long getTotalOutImpl(long handle);

    public int inflate(byte[] buf) throws TDataFormatException {
        return inflate(buf, 0, buf.length);
    }

    public int inflate(byte[] buf, int off, int nbytes) throws TDataFormatException {
        // avoid int overflow, check null buf
        if (off > buf.length || nbytes < 0 || off < 0 || buf.length - off < nbytes) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (impl == null) {
            throw new IllegalStateException();
        }

        long lastInSize = impl.total_in;
        long lastOutSize = impl.total_out;
        boolean neededDict = needsDictionary;
        needsDictionary = false;
        impl.setOutput(buf, off, nbytes);

        byte[] inArray = impl.next_in;
        int inIndex = impl.next_in_index;
        int errCode = impl.inflate(0);
        if (!nowrap && headerLength < 2) {
            long upTo = Math.min(2, impl.total_in);
            for (long k = Math.max(lastInSize, headerLength); k < upTo; k++) {
                header[(int) k] = inArray[inIndex + (int) (k - lastInSize)];
            }
            headerLength = (int) upTo;
            // Zlib's header checks, in zlib's order. jzlib checks none of them before it inflates.
            if (headerLength == 2) {
                int cmf = header[0] & 0xFF;
                if (((cmf << 8) | (header[1] & 0xFF)) % 31 != 0) {
                    throw new TDataFormatException();
                }
                if ((cmf & 0x0F) != 8) {
                    throw new TDataFormatException();
                }
                if ((cmf >> 4) + 8 > 15) {
                    throw new TDataFormatException();
                }
            }
        }
        switch (errCode) {
            case JZlib.Z_OK:
                break;
            case JZlib.Z_NEED_DICT:
                needsDictionary = true;
                break;
            case JZlib.Z_STREAM_END:
                finished = true;
                break;
            case JZlib.Z_BUF_ERROR:
                // Zlib reports "no progress possible" this way. The JDK's Inflater returns 0 bytes for it, not an
                // error.
                break;
            default:
                throw new TDataFormatException("Error occurred: " + errCode);
        }

        if (needsDictionary && neededDict) {
            throw new TDataFormatException();
        }

        inRead += impl.total_in - lastInSize;
        return (int) (impl.total_out - lastOutSize);
    }

    public boolean needsDictionary() {
        return needsDictionary;
    }

    public boolean needsInput() {
        return inRead == inLength;
    }

    public void reset() {
        headerLength = 0;
        if (impl == null) {
            throw new NullPointerException();
        }
        finished = false;
        needsDictionary = false;
        inLength = 0;
        inRead = 0;
        impl.init(nowrap);
    }

    private native void resetImpl(long handle);

    public void setDictionary(byte[] buf) {
        setDictionary(buf, 0, buf.length);
    }

    public void setDictionary(byte[] buf, int off, int nbytes) {
        if (impl == null) {
            throw new IllegalStateException();
        }
        // avoid int overflow, check null buf
        if (off <= buf.length && nbytes >= 0 && off >= 0 && buf.length - off >= nbytes) {
            if (off > 0) {
                buf = Arrays.copyOfRange(buf, off, buf.length);
            }
            impl.setDictionary(buf, nbytes);
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
            inRead = 0;
            inLength = nbytes;
            impl.setInput(buf, off, nbytes, false);
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Override
    public void close() throws Exception {
        end();
    }
}
