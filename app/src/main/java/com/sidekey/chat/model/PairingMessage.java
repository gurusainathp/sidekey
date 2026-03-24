package com.sidekey.chat.model;

/**
 * Data model for a pairing packet sent between devices over Bluetooth.
 *
 * TYPE_PAIR → initial key exchange from sender
 * TYPE_ACK  → acknowledgement from receiver confirming they saved the key
 */
public class PairingMessage {

    public static final String TYPE_PAIR = "PAIR";
    public static final String TYPE_ACK  = "ACK";

    private final String type;
    private final byte[] publicKey;
    private final long   timestamp;

    public PairingMessage(String type, byte[] publicKey, long timestamp) {
        this.type      = type;
        this.publicKey = publicKey;
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PairingMessage{type=" + type + ", timestamp=" + timestamp + "}";
    }
}