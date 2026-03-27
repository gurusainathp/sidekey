package com.sidekey.chat;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.crypto.MessageCipher;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.messaging.MessageQueue;
import com.sidekey.chat.messaging.OutgoingMessage;
import com.sidekey.chat.messaging.SendDispatcher;
import com.sidekey.chat.model.ChatMessage;
import com.sidekey.chat.model.MessageType;
import com.sidekey.chat.protocol.MessageParser;
import com.sidekey.chat.protocol.MessageSerializer;

/**
 * ChatManager — encrypts/decrypts structured ChatMessage objects.
 *
 * Send path:
 *   String → ChatMessage → MessageSerializer → encrypt → enqueue → SendDispatcher
 *
 * Receive path:
 *   byte[] → decrypt → MessageParser → ChatMessage → ChatCallback
 *
 * Wire format (no outer JSON wrapper):
 *   The encrypted payload IS the serialized ChatMessage.
 *   Decryption failure = not a chat message → route to PairingManager.
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

    /**
     * Sends a text message.
     * Creates a structured ChatMessage, serializes, encrypts, and enqueues.
     */
    public void sendMessage(String text) {
        // 1. Build structured message
        ChatMessage message = ChatMessage.text(text);

        // 2. Serialize to JSON string
        String json = MessageSerializer.serializeToString(message);
        if (json == null) {
            Log.e(TAG, "sendMessage: serialisation failed");
            if (callback != null) callback.onError("Serialisation failed");
            return;
        }

        // 3. Encrypt the JSON string
        String encrypted = cipher.encryptMessage(json);
        if (encrypted == null) {
            Log.e(TAG, "sendMessage: encryption failed");
            if (callback != null) callback.onError("Encryption failed");
            return;
        }

        // 4. Enqueue raw bytes — no outer wrapper
        byte[] payload = encrypted.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MessageQueue.getInstance().enqueue(
                new OutgoingMessage(payload, System.currentTimeMillis(), true));

        // 5. Notify dispatcher — it decides when to actually send
        SendDispatcher.getInstance().onNewMessage();

        Log.d(TAG, "Enqueued [" + message.getType() + "]: " + text);
        if (callback != null) callback.onMessageSent(text);
    }

    // ── Receive ───────────────────────────────────────────────────────────────

    /**
     * Attempts to handle incoming bytes as an encrypted chat message.
     *
     * Returns true  → consumed as chat message.
     * Returns false → not a chat message, caller should route to PairingManager.
     *
     * Decryption failure is the natural signal that bytes are NOT a chat message
     * (e.g. they are a pairing packet encrypted with a different scheme).
     */
    public boolean handleIncoming(byte[] data) {
        if (!sessionStore.isReady()) {
            // Session not ready — cannot be a chat message
            return false;
        }

        // 1. Treat the raw bytes as a Base64-encoded encrypted payload
        String encryptedB64 = new String(data, java.nio.charset.StandardCharsets.UTF_8);

        // 2. Decrypt — failure means this is NOT a chat message
        String decryptedJson = cipher.decryptMessage(encryptedB64);
        if (decryptedJson == null) {
            Log.d(TAG, "handleIncoming: decrypt failed — routing to PairingManager");
            return false;
        }

        // 3. Parse the decrypted JSON into a structured ChatMessage
        ChatMessage message = MessageParser.parse(decryptedJson);
        if (message == null) {
            Log.e(TAG, "handleIncoming: parse failed after decrypt — discarding");
            return true; // Was encrypted with our key, so consume it even if parse fails
        }

        // 4. Dispatch by type
        switch (message.getType()) {
            case TEXT:
                Log.d(TAG, "Received [TEXT]: " + message.getContent());
                if (callback != null)
                    callback.onMessageReceived(message.getContent(), message.getTimestamp());
                break;

            case SYSTEM:
                Log.d(TAG, "Received [SYSTEM]: " + message.getContent());
                // System messages are logged only for now — UI support in a later phase
                break;

            case CONTROL:
                Log.d(TAG, "Received [CONTROL]: " + message.getContent());
                // Typing indicator, keepalive etc. — future phase
                break;

            case PAIRING:
                // Should not arrive here — pairing uses a separate flow
                Log.w(TAG, "Received [PAIRING] inside ChatManager — ignored");
                break;

            default:
                Log.w(TAG, "Received unknown type: " + message.getType());
        }

        return true;
    }
}