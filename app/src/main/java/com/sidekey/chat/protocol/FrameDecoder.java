package com.sidekey.chat.protocol;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * FrameDecoder — reads exactly one length-prefixed frame from an InputStream.
 *
 * Wire format expected:
 *   [0..3]  payload length as 4-byte big-endian int
 *   [4..]   payload bytes
 *
 * CRITICAL: InputStream.read() may return fewer bytes than requested.
 * Both the header read and the payload read loop until the exact number
 * of bytes is received before returning. This prevents partial JSON,
 * split messages, and silent corruption.
 *
 * Throws IOException when the stream closes — caller handles reconnect.
 */
public class FrameDecoder {

    private static final String TAG     = "SideKey-Frame";
    private static final int    HEADER  = 4;
    private static final int    MAX_LEN = 10 * 1024 * 1024; // 10 MB sanity cap

    private FrameDecoder() {}

    /**
     * Blocks until a full frame is available, then returns the payload.
     *
     * @param in  open InputStream from BluetoothSocket
     * @return    payload bytes (no length header)
     * @throws IOException if stream closes or payload length is invalid
     */
    public static byte[] decode(InputStream in) throws IOException {

        // ── Step 1: read exactly 4 header bytes ──────────────────────────────
        byte[] header  = new byte[HEADER];
        int    read    = 0;

        while (read < HEADER) {
            int n = in.read(header, read, HEADER - read);
            if (n < 0) throw new IOException("Stream closed while reading header");
            read += n;
        }

        // ── Step 2: decode big-endian length ─────────────────────────────────
        int len = ((header[0] & 0xFF) << 24)
                | ((header[1] & 0xFF) << 16)
                | ((header[2] & 0xFF) <<  8)
                |  (header[3] & 0xFF);

        if (len < 0 || len > MAX_LEN) {
            throw new IOException("Invalid frame length: " + len);
        }

        Log.d(TAG, "FrameDecoder: length=" + len);

        // ── Step 3: read exactly len payload bytes ────────────────────────────
        byte[] payload   = new byte[len];
        int    received  = 0;

        while (received < len) {
            int n = in.read(payload, received, len - received);
            if (n < 0) throw new IOException("Stream closed while reading payload");
            received += n;
        }

        Log.d(TAG, "FrameDecoder: full frame received len=" + len);
        return payload;
    }
}