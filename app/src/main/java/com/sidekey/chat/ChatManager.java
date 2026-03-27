package com.sidekey.chat;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.crypto.MessageCipher;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.messaging.MessageQueue;
import com.sidekey.chat.messaging.OutgoingMessage;
import com.sidekey.chat.messaging.SendDispatcher;
import com.sidekey.chat.model.ChatMessage;
import com.sidekey.chat.utils.JsonUtil;

/**
 * ChatManager — encrypts/decrypts messages and routes through MessageQueue.
 *
 * Send path:  plaintext → encrypt → JSON → enqueue → SendDispatcher
 * Receive path: raw bytes → JSON parse → decrypt → ChatCallback
 *
 * ChatManager has NO reference to BluetoothService.
 * All outgoing bytes go via MessageQueue → SendDispatcher → TransportSender → BT.
 */
public class ChatManager {

    private static final String TAG = "SideKey-Chat";

    private final MessageCipher cipher;
    private final SessionStore  sessionStore;
    private ChatCallback        callback;

    public ChatManager(Context context, SessionStore sessionStore) {
        this.sessionStore = sessionStore;
        this.cipher       = new MessageCipher(sessionStore);

        if (sessionStore.isReady()) {
            Log.d(TAG, "ChatManager: session ready ✅");
        } else {
            Log.e(TAG, "ChatManager: session NOT ready — messages will be queued");
        }
    }

    public void setCallback(ChatCallback callback) {
        this.callback = callback;
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    public void sendMessage(String plaintext) {
        if (!sessionStore.isReady()) {
            Log.e(TAG, "ChatManager: session not ready — message queued anyway");
            // Still enqueue — dispatcher will send when session is ready
        }

        String encryptedPayload = cipher.encryptMessage(plaintext);
        if (encryptedPayload == null) {
            Log.e(TAG, "sendMessage: encryption failed");
            if (callback != null) callback.onError("Encryption failed");
            return;
        }

        ChatMessage message = new ChatMessage(
                ChatMessage.TYPE_MSG, encryptedPayload, System.currentTimeMillis());

        String json = JsonUtil.toJson(message);
        if (json == null) {
            Log.e(TAG, "sendMessage: JSON serialisation failed");
            return;
        }

        // Enqueue — never send directly via BT
        MessageQueue.getInstance().enqueue(
                new OutgoingMessage(json.getBytes(), System.currentTimeMillis(), true));

        // Notify dispatcher — it decides whether to send now or wait
        SendDispatcher.getInstance().onNewMessage();

        if (callback != null) callback.onMessageSent(plaintext);
    }

    // ── Receive ───────────────────────────────────────────────────────────────

    /**
     * Returns true if data was a chat message (consumed).
     * Returns false if caller should route to PairingManager.
     */
    public boolean handleIncoming(byte[] data) {
        String json = new String(data);

        ChatMessage message = JsonUtil.chatFromJson(json);
        if (message == null) return false;

        switch (message.getType()) {
            case ChatMessage.TYPE_MSG:
                handleIncomingMessage(message);
                return true;
            case ChatMessage.TYPE_ACK:
                Log.d(TAG, "Message ACK received");
                return true;
            default:
                Log.w(TAG, "Unknown chat type: " + message.getType());
                return false;
        }
    }

    private void handleIncomingMessage(ChatMessage message) {
        if (!sessionStore.isReady()) {
            Log.e(TAG, "handleIncomingMessage: session not ready");
            if (callback != null) callback.onError("Session not ready");
            return;
        }

        String plaintext = cipher.decryptMessage(message.getPayload());
        if (plaintext == null) {
            Log.e(TAG, "handleIncomingMessage: decryption failed");
            if (callback != null) callback.onError("Decryption failed");
            return;
        }

        Log.d(TAG, "✅ Decrypted: " + plaintext);
        if (callback != null) callback.onMessageReceived(plaintext, message.getTimestamp());
    }
}