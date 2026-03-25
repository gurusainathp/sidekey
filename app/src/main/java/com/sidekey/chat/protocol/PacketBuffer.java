package com.sidekey.chat.protocol;

import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * PacketBuffer — simple in-memory byte accumulator.
 *
 * Not used in the current framing flow (FrameDecoder handles reads directly).
 * Kept as a placeholder for future use cases such as:
 *   - reassembling fragmented large media payloads
 *   - buffering incoming bytes before a full frame is available
 *
 * No parsing logic here. No crypto. Transport utility only.
 */
public class PacketBuffer {

    private static final String TAG = "SideKey-PacketBuf";

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * Appends bytes to the internal buffer.
     */
    public void append(byte[] data) {
        if (data == null || data.length == 0) return;
        try {
            buffer.write(data);
            Log.d(TAG, "PacketBuffer: appended " + data.length
                    + " bytes, total=" + buffer.size());
        } catch (Exception e) {
            Log.e(TAG, "PacketBuffer: append failed — " + e.getMessage());
        }
    }

    /**
     * Returns a snapshot of all buffered bytes.
     */
    public byte[] toBytes() {
        return buffer.toByteArray();
    }

    /**
     * Returns total number of bytes currently buffered.
     */
    public int size() {
        return buffer.size();
    }

    /**
     * Clears all buffered data.
     */
    public void clear() {
        buffer.reset();
        Log.d(TAG, "PacketBuffer: cleared");
    }
}