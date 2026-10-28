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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class TGZIPInputStream extends TInflaterInputStream {
    private static final int FCOMMENT = 16;
    private static final int FEXTRA = 4;
    private static final int FHCRC = 2;
    private static final int FNAME = 8;
    public final static int GZIP_MAGIC = 0x8b1f;
    protected TCRC32 crc = new TCRC32();
    protected boolean eos;
    // Bytes of buf that follow a member's deflate data and that neither the inflater nor the trailer took:
    // buf[bufPos..bufEnd). The next member's header starts there.
    private int bufPos;
    private int bufEnd;

    public TGZIPInputStream(InputStream is) throws IOException {
        this(is, BUF_SIZE);
    }

    public TGZIPInputStream(InputStream is, int size) throws IOException {
        super(is, new TInflater(true), size);
        readHeader();
    }

    private void readHeader() throws IOException {
        byte[] header = new byte[10];
        readFully(header, 0, header.length);
        if (getShort(header, 0) != GZIP_MAGIC) {
            throw new IOException();
        }
        int flags = header[3];
        boolean hcrc = (flags & FHCRC) != 0;
        if (hcrc) {
            crc.update(header, 0, header.length);
        }
        if ((flags & FEXTRA) != 0) {
            readFully(header, 0, 2);
            if (hcrc) {
                crc.update(header, 0, 2);
            }
            int length = getShort(header, 0);
            while (length > 0) {
                int b = readByte();
                if (hcrc) {
                    crc.update(b);
                }
                length--;
            }
        }
        if ((flags & FNAME) != 0) {
            readZeroTerminated(hcrc);
        }
        if ((flags & FCOMMENT) != 0) {
            readZeroTerminated(hcrc);
        }
        if (hcrc) {
            readFully(header, 0, 2);
            int crc16 = getShort(header, 0);
            if ((crc.getValue() & 0xffff) != crc16) {
                throw new IOException();
            }
            crc.reset();
        }
    }

    @Override
    public void close() throws IOException {
        eos = true;
        super.close();
    }

    private long getLong(byte[] buffer, int off) {
        long l = 0;
        l |= buffer[off] & 0xFF;
        l |= (buffer[off + 1] & 0xFF) << 8;
        l |= (buffer[off + 2] & 0xFF) << 16;
        l |= ((long) (buffer[off + 3] & 0xFF)) << 24;
        return l;
    }

    private int getShort(byte[] buffer, int off) {
        return (buffer[off] & 0xFF) | ((buffer[off + 1] & 0xFF) << 8);
    }

    @Override
    public int read(byte[] buffer, int off, int nbytes) throws IOException {
        if (closed) {
            throw new IOException();
        }
        if (eos) {
            return -1;
        }
        // avoid int overflow, check null buffer
        if (off > buffer.length || nbytes < 0 || off < 0 || buffer.length - off < nbytes) {
            throw new ArrayIndexOutOfBoundsException();
        }

        while (true) {
            int bytesRead;
            try {
                bytesRead = super.read(buffer, off, nbytes);
            } finally {
                eos = eof; // update eos after every read(), even when it throws
            }

            if (bytesRead != -1) {
                crc.update(buffer, off, bytesRead);
            }

            if (eos) {
                readTrailer();
                if (!eos && bytesRead == -1) {
                    // Another member follows. Read from it.
                    continue;
                }
            }
            return bytesRead;
        }
    }

    private void readTrailer() throws IOException {
        // The bytes the inflater did not consume start the trailer.
        int remaining = inf.getRemaining();
        bufPos = len - remaining;
        bufEnd = len;

        byte[] b = new byte[8]; // crc (4 bytes) + total out (4 bytes)
        readFully(b, 0, b.length);
        if (getLong(b, 0) != crc.getValue()) {
            throw new IOException();
        }
        if ((int) getLong(b, 4) != inf.getTotalOut()) {
            throw new IOException();
        }

        // RFC 1952, 2.2: "A gzip file consists of a series of "members"". As in the JDK, another member is read only
        // when more bytes are available without blocking, and bytes that do not form a member header end the stream.
        if (bufPos == bufEnd && in.available() <= 0) {
            return;
        }
        crc.reset();
        try {
            readHeader();
        } catch (IOException e) {
            return;
        }
        inf.reset();
        if (bufPos < bufEnd) {
            inf.setInput(buf, bufPos, bufEnd - bufPos);
        }
        bufPos = 0;
        bufEnd = 0;
        eof = false;
        eos = false;
    }

    private int readByte() throws IOException {
        if (bufPos < bufEnd) {
            return buf[bufPos++] & 0xFF;
        }
        int result = in.read();
        if (result == -1) {
            throw new EOFException();
        }
        return result;
    }

    private void readFully(byte[] buffer, int offset, int length) throws IOException {
        int fromBuf = Math.min(length, bufEnd - bufPos);
        if (fromBuf > 0) {
            System.arraycopy(buf, bufPos, buffer, offset, fromBuf);
            bufPos += fromBuf;
            offset += fromBuf;
            length -= fromBuf;
        }
        while (length > 0) {
            int result = in.read(buffer, offset, length);
            if (result == -1) {
                throw new EOFException();
            }
            offset += result;
            length -= result;
        }
    }

    private void readZeroTerminated(boolean hcrc) throws IOException {
        int result;
        do {
            result = readByte();
            if (hcrc) {
                crc.update(result);
            }
        } while (result != 0);
    }
}
