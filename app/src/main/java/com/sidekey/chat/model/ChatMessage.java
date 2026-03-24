package com.sidekey.chat.model;

/**
 * Data model for a chat packet sent over Bluetooth.
 *
 * TYPE_MSG  → an encrypted message from sender to receiver
 * TYPE_ACK  → delivery acknowledgement (future use)
 *
 * payload is Base64-encoded so it survives JSON serialisation.
 * The raw bytes it encodes are: nonce (24 bytes) + ciphertext.
 */
public class ChatMessage {

    public static final String TYPE_MSG = "MSG";
    public static final String TYPE_ACK = "ACK";

    private final String type;
    private final String payload;    // Base64(nonce + ciphertext)
    private final long   timestamp;

    public ChatMessage(String type, String payload, long timestamp) {
        this.type      = type;
        this.payload   = payload;
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ChatMessage{type=" + type + ", timestamp=" + timestamp + "}";
    }
}