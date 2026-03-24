package com.sidekey.chat;

/**
 * Callback interface for ChatManager events.
 *
 * All callbacks may fire on a background thread.
 * Implementors must use runOnUiThread() before touching any views.
 */
public interface ChatCallback {

    /** A decrypted message arrived from the partner. */
    void onMessageReceived(String plaintext, long timestamp);

    /** Our own message was successfully encrypted and sent. */
    void onMessageSent(String plaintext);

    /** Something went wrong in the chat layer. */
    void onError(String reason);
}