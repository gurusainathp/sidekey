package com.sidekey.chat.protocol;

import android.util.Log;

import com.sidekey.chat.model.ChatMessage;

import org.json.JSONObject;

/**
 * MessageSerializer — converts a ChatMessage to a JSON String or byte[].
 *
 * Wire format (before encryption):
 * {
 *   "type":      "TEXT",
 *   "content":   "hello",
 *   "timestamp": 1234567890,
 *   "senderId":  null         ← omitted if null
 * }
 *
 * No encryption here. No Bluetooth here. Pure serialisation.
 * MessageCipher encrypts the output of this class.
 */
public class MessageSerializer {

    private static final String TAG = "SideKey-Serializer";

    private static final String FIELD_TYPE      = "type";
    private static final String FIELD_CONTENT   = "content";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_SENDER    = "senderId";

    private MessageSerializer() {}

    /**
     * Serialises a ChatMessage to a JSON String ready for encryption.
     *
     * @param message  the message to serialise
     * @return         JSON String, or null on failure
     */
    public static String serializeToString(ChatMessage message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(FIELD_TYPE,      message.getType().name());
            obj.put(FIELD_CONTENT,   message.getContent());
            obj.put(FIELD_TIMESTAMP, message.getTimestamp());
            if (message.getSenderId() != null) {
                obj.put(FIELD_SENDER, message.getSenderId());
            }
            String json = obj.toString();
            Log.d(TAG, "Serialized [" + message.getType() + "] len=" + json.length());
            return json;
        } catch (Exception e) {
            Log.e(TAG, "serialize failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Serialises a ChatMessage to bytes (UTF-8 JSON).
     * Convenience wrapper around serializeToString().
     *
     * @param message  the message to serialise
     * @return         UTF-8 bytes, or null on failure
     */
    public static byte[] serialize(ChatMessage message) {
        String json = serializeToString(message);
        if (json == null) return null;
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}