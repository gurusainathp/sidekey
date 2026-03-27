package com.sidekey.chat.utils;

import android.util.Base64;

import com.sidekey.chat.model.ChatMessage;
import com.sidekey.chat.model.MessageType;
import com.sidekey.chat.model.PairingMessage;

import org.json.JSONObject;

/**
 * JsonUtil — serialises and deserialises PairingMessage and ChatMessage.
 *
 * No Bluetooth here. No storage here. Pure conversion only.
 *
 * All public keys are Base64-encoded in JSON because JSON is text-only
 * and raw crypto bytes must survive the round-trip.
 */
public class JsonUtil {

    // -------------------------------------------------------------------------
    // PairingMessage fields
    // -------------------------------------------------------------------------

    private static final String PAIR_TYPE       = "type";
    private static final String PAIR_PUBLIC_KEY = "publicKey";
    private static final String PAIR_TIMESTAMP  = "timestamp";

    // -------------------------------------------------------------------------
    // ChatMessage fields
    // -------------------------------------------------------------------------

    private static final String CHAT_TYPE      = "type";
    private static final String CHAT_PAYLOAD   = "payload";
    private static final String CHAT_TIMESTAMP = "timestamp";

    // -------------------------------------------------------------------------
    // PairingMessage ↔ JSON
    // -------------------------------------------------------------------------

    public static String toJson(PairingMessage message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(PAIR_TYPE,       message.getType());
            obj.put(PAIR_PUBLIC_KEY, bytesToBase64(message.getPublicKey()));
            obj.put(PAIR_TIMESTAMP,  message.getTimestamp());
            return obj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static PairingMessage fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            // Must have publicKey field to be a PairingMessage
            if (!obj.has(PAIR_PUBLIC_KEY)) return null;

            String type      = obj.getString(PAIR_TYPE);
            byte[] publicKey = base64ToBytes(obj.getString(PAIR_PUBLIC_KEY));
            long   timestamp = obj.getLong(PAIR_TIMESTAMP);

            return new PairingMessage(type, publicKey, timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // ChatMessage ↔ JSON
    // -------------------------------------------------------------------------

    public static String toJson(ChatMessage message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(CHAT_TYPE,      message.getType());
            obj.put(CHAT_PAYLOAD,   message.getContent());
            obj.put(CHAT_TIMESTAMP, message.getTimestamp());
            return obj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static ChatMessage chatFromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            // Must have payload field to be a ChatMessage
            if (!obj.has(CHAT_PAYLOAD)) return null;

            String type      = obj.getString(CHAT_TYPE);
            String payload   = obj.getString(CHAT_PAYLOAD);
            long   timestamp = obj.getLong(CHAT_TIMESTAMP);

            MessageType messageType = MessageType.valueOf(type);

            return new ChatMessage(messageType, payload, timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    public static String bytesToBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] base64ToBytes(String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }
}