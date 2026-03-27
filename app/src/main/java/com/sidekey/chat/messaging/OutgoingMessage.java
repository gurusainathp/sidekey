package com.sidekey.chat.messaging;

/**
 * OutgoingMessage — one encrypted message waiting in the queue.
 * No logic. No dependencies. Pure data.
 */
public class OutgoingMessage {

    private final byte[]  payload;
    private final long    timestamp;
    private final boolean encrypted;

    public OutgoingMessage(byte[] payload, long timestamp, boolean encrypted) {
        this.payload   = payload;
        this.timestamp = timestamp;
        this.encrypted = encrypted;
    }

    public byte[]  getPayload()   { return payload; }
    public long    getTimestamp() { return timestamp; }
    public boolean isEncrypted()  { return encrypted; }
}