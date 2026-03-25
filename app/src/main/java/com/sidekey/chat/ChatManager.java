package com.sidekey.chat;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.crypto.MessageCipher;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.model.ChatMessage;
import com.sidekey.chat.utils.JsonUtil;

/**
 * ChatManager — brain of the encrypted messaging flow.
 *
 * Uses SessionStore (symmetric session key) for all encryption.
 * No longer depends on PairingManager or raw key material.
 *
 * Only active after pairing is confirmed and session is ready.
 */
public class ChatManager {

    private static final String TAG = "SideKey-Chat";

    private final BluetoothService bluetoothService;
    private final MessageCipher    cipher;
    private final SessionStore     sessionStore;

    private ChatCallback callback;

    public ChatManager(Context context,
                       SessionStore sessionStore,
                       BluetoothService bluetoothService) {
        this.bluetoothService = bluetoothService;
        this.sessionStore     = sessionStore;
        this.cipher           = new MessageCipher(sessionStore);

        if (sessionStore.isReady()) {
            Log.d(TAG, "ChatManager: session ready ✅");
        } else {
            Log.e(TAG, "ChatManager: session NOT ready — messages will fail until session is derived");
        }
    }

    public void setCallback(ChatCallback callback) {
        this.callback = callback;
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    public void sendMessage(String plaintext) {
        if (!sessionStore.isReady()) {
            Log.e(TAG, "ChatManager: session not ready — cannot send");
            if (callback != null) callback.onError("Session not ready — pair first");
            return;
        }

        String encryptedPayload = cipher.encryptMessage(plaintext);
        if (encryptedPayload == null) {
            Log.e(TAG, "sendMessage: encryption failed");
            if (callback != null) callback.onError("Encryption failed");
            return;
        }

        ChatMessage message = new ChatMessage(
                ChatMessage.TYPE_MSG,
                encryptedPayload,
                System.currentTimeMillis()
        );

        String json = JsonUtil.toJson(message);
        if (json == null) {
            Log.e(TAG, "sendMessage: JSON serialisation failed");
            return;
        }

        bluetoothService.send(json.getBytes());
        Log.d(TAG, "✅ Encrypted message sent");

        if (callback != null) callback.onMessageSent(plaintext);
    }

    // -------------------------------------------------------------------------
    // Receive — returns true if consumed as a chat message
    // -------------------------------------------------------------------------

    public boolean handleIncoming(byte[] data) {
        String json = new String(data);

        ChatMessage message = JsonUtil.chatFromJson(json);
        if (message == null) return false; // not a chat message

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

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void handleIncomingMessage(ChatMessage message) {
        if (!sessionStore.isReady()) {
            Log.e(TAG, "handleIncomingMessage: session not ready — cannot decrypt");
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