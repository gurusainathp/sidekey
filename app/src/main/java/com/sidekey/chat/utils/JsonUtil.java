package com.sidekey.chat.utils;

import android.util.Base64;

import com.sidekey.chat.model.PairingMessage;

import org.json.JSONObject;

/**
 * Converts PairingMessage to JSON string and back.
 *
 * Public key is stored as Base64 in JSON because:
 *   - JSON is text-only
 *   - Bluetooth sends raw bytes which we wrap as UTF-8 JSON
 *   - Base64 is the standard way to embed binary data in text
 *
 * No Android UI here. No Bluetooth here. Pure conversion only.
 */
public class JsonUtil {

    private static final String FIELD_TYPE       = "type";
    private static final String FIELD_PUBLIC_KEY = "publicKey";
    private static final String FIELD_TIMESTAMP  = "timestamp";

    // -------------------------------------------------------------------------
    // PairingMessage → JSON string
    // -------------------------------------------------------------------------

    public static String toJson(PairingMessage message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(FIELD_TYPE,       message.getType());
            obj.put(FIELD_PUBLIC_KEY, bytesToBase64(message.getPublicKey()));
            obj.put(FIELD_TIMESTAMP,  message.getTimestamp());
            return obj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // JSON string → PairingMessage
    // -------------------------------------------------------------------------

    public static PairingMessage fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            String type      = obj.getString(FIELD_TYPE);
            byte[] publicKey = base64ToBytes(obj.getString(FIELD_PUBLIC_KEY));
            long   timestamp = obj.getLong(FIELD_TIMESTAMP);

            return new PairingMessage(type, publicKey, timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Base64 helpers — used externally too (e.g. for key display)
    // -------------------------------------------------------------------------

    public static String bytesToBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] base64ToBytes(String base64) {
        return Base64.decode(base64, Base64.NO_WRAP);
    }
}