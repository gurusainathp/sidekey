package com.sidekey.chat.ui.chat;

/**
 * MessageItem — data class for a single row in the chat RecyclerView.
 *
 * Intentionally separate from ChatMessage (domain model).
 * UI layer only — no Android imports, no crypto, no networking.
 */
public class MessageItem {

    private final String  content;
    private final boolean isSent;
    private final long    timestamp;

    public MessageItem(String content, boolean isSent, long timestamp) {
        this.content   = content;
        this.isSent    = isSent;
        this.timestamp = timestamp;
    }

    public static MessageItem sent(String content) {
        return new MessageItem(content, true, System.currentTimeMillis());
    }

    public static MessageItem received(String content, long timestamp) {
        return new MessageItem(content, false, timestamp);
    }

    public String  getContent()   { return content; }
    public boolean isSent()       { return isSent; }
    public long    getTimestamp() { return timestamp; }
}