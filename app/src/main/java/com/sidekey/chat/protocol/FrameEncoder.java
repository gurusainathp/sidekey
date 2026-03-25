package com.sidekey.chat.protocol;

import android.util.Log;

/**
 * FrameEncoder — prepends a 4-byte big-endian length to a payload.
 *
 * Wire format:
 *   [0..3]  length of payload as 4-byte big-endian int
 *   [4..]   payload bytes
 *
 * No encryption here. No JSON here. Transport layer only.
 */
public class FrameEncoder {

    private static final String TAG = "SideKey-Frame";

    private FrameEncoder() {}

    /**
     * Encodes a payload into a length-prefixed frame.
     *
     * @param payload  raw bytes to frame
     * @return         4-byte length header + payload
     */
    public static byte[] encode(byte[] payload) {
        if (payload == null) payload = new byte[0];

        int    len   = payload.length;
        byte[] frame = new byte[4 + len];

        // Big-endian length in first 4 bytes
        frame[0] = (byte) ((len >> 24) & 0xFF);
        frame[1] = (byte) ((len >> 16) & 0xFF);
        frame[2] = (byte) ((len >>  8) & 0xFF);
        frame[3] = (byte) ( len        & 0xFF);

        System.arraycopy(payload, 0, frame, 4, len);

        Log.d(TAG, "FrameEncoder: payload=" + len + " frame=" + frame.length);
        return frame;
    }
}