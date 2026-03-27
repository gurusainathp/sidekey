package com.sidekey.chat.protocol;

import android.util.Log;

import com.sidekey.chat.model.ChatMessage;
import com.sidekey.chat.model.MessageType;

import org.json.JSONObject;

/**
 * MessageParser — converts decrypted JSON into a ChatMessage.
 *
 * Counterpart to MessageSerializer.
 * Returns null on any parse failure — never throws.
 * Callers must always null-check the result.
 */
public class MessageParser {

    private static final String TAG = "SideKey-Parser";

    private static final String FIELD_TYPE      = "type";
    private static final String FIELD_CONTENT   = "content";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_SENDER    = "senderId";

    private MessageParser() {}

    /**
     * Parses a JSON String into a ChatMessage.
     *
     * @param json  decrypted JSON string from MessageSerializer
     * @return      ChatMessage, or null if the string is not a valid chat message
     */
    public static ChatMessage parse(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);

            // Must have both type and content to be a chat message
            if (!obj.has(FIELD_TYPE) || !obj.has(FIELD_CONTENT)) return null;

            String typeStr = obj.getString(FIELD_TYPE);
            MessageType type;
            try {
                type = MessageType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Unknown MessageType: " + typeStr);
                return null;
            }

            String content   = obj.getString(FIELD_CONTENT);
            long   timestamp = obj.optLong(FIELD_TIMESTAMP, System.currentTimeMillis());
            String senderId  = obj.has(FIELD_SENDER) ? obj.getString(FIELD_SENDER) : null;

            ChatMessage msg = new ChatMessage(type, content, timestamp, senderId);
            Log.d(TAG, "Parsed [" + type + "]: " + content);
            return msg;

        } catch (Exception e) {
            Log.e(TAG, "parse failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses UTF-8 bytes into a ChatMessage.
     * Convenience wrapper around parse(String).
     *
     * @param data  decrypted bytes from MessageCipher
     * @return      ChatMessage, or null on failure
     */
    public static ChatMessage parse(byte[] data) {
        if (data == null || data.length == 0) return null;
        return parse(new String(data, java.nio.charset.StandardCharsets.UTF_8));
    }
}