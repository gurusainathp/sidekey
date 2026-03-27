package com.sidekey.chat.model;

/**
 * ChatMessage — structured message model.
 *
 * This model holds PLAINTEXT content. It is never stored encrypted.
 * Encryption/decryption happens at the transport boundary (MessageCipher),
 * not inside this model.
 *
 * Wire format is handled by MessageSerializer / MessageParser.
 */
public class ChatMessage {

    private final MessageType type;
    private final String      content;
    private final long        timestamp;
    private final String      senderId;   // nullable — for future multi-user support

    // ── Full constructor ──────────────────────────────────────────────────────
    public ChatMessage(MessageType type, String content, long timestamp, String senderId) {
        this.type      = type;
        this.content   = content;
        this.timestamp = timestamp;
        this.senderId  = senderId;
    }

    // ── Convenience — no senderId ─────────────────────────────────────────────
    public ChatMessage(MessageType type, String content, long timestamp) {
        this(type, content, timestamp, null);
    }

    // ── Convenience — TEXT with current time ──────────────────────────────────
    public static ChatMessage text(String content) {
        return new ChatMessage(MessageType.TEXT, content, System.currentTimeMillis());
    }

    // ── Convenience — SYSTEM message ─────────────────────────────────────────
    public static ChatMessage system(String content) {
        return new ChatMessage(MessageType.SYSTEM, content, System.currentTimeMillis());
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public MessageType getType()      { return type; }
    public String      getContent()   { return content; }
    public long        getTimestamp() { return timestamp; }
    public String      getSenderId()  { return senderId; }

    @Override
    public String toString() {
        return "ChatMessage{type=" + type + ", content='" + content
                + "', timestamp=" + timestamp + "}";
    }
}