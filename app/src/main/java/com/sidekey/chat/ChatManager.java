package com.sidekey.chat;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.crypto.MessageCipher;
import com.sidekey.chat.crypto.SecureStorage;
import com.sidekey.chat.model.ChatMessage;
import com.sidekey.chat.pairing.PairingManager;
import com.sidekey.chat.utils.JsonUtil;

/**
 * ChatManager — brain of the encrypted messaging flow.
 *
 * Responsibilities:
 *   - Build and encrypt outgoing messages
 *   - Receive, parse, and decrypt incoming messages
 *   - Route decrypted text to ChatCallback for UI display
 *
 * Does NOT touch Bluetooth directly (passes bytes to BluetoothService).
 * Does NOT touch UI directly (fires ChatCallback).
 * Only active after pairing is complete.
 */
public class ChatManager {

    private static final String TAG = "SideKey-Chat";

    private final BluetoothService bluetoothService;
    private final MessageCipher    cipher;

    private ChatCallback callback;

    /**
     * @param context          Android context for SecureStorage
     * @param pairingManager   used to fetch key material
     * @param bluetoothService used to send encrypted bytes
     */
    public ChatManager(Context context,
                       PairingManager pairingManager,
                       BluetoothService bluetoothService) {

        this.bluetoothService = bluetoothService;

        // Pull key material from SecureStorage via PairingManager getters
        byte[] ownPrivKey      = pairingManager.getOwnPrivateKey();
        byte[] partnerPubKey   = pairingManager.getPartnerPublicKey();

        if (ownPrivKey == null || partnerPubKey == null) {
            Log.e(TAG, "ChatManager: key material missing — is pairing complete?");
            this.cipher = null;
        } else {
            this.cipher = new MessageCipher(ownPrivKey, partnerPubKey);
            Log.d(TAG, "ChatManager ready — cipher initialised");
        }
    }

    public void setCallback(ChatCallback callback) {
        this.callback = callback;
    }

    // -------------------------------------------------------------------------
    // Send an encrypted message
    // -------------------------------------------------------------------------

    /**
     * Encrypts plaintext and sends it over Bluetooth.
     * Call only after pairing is complete.
     */
    public void sendMessage(String plaintext) {
        if (cipher == null) {
            Log.e(TAG, "sendMessage: cipher not ready — pairing incomplete?");
            if (callback != null) callback.onError("Cannot send — not paired");
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

        // Echo to own UI so sender sees their message
        if (callback != null) callback.onMessageSent(plaintext);
    }

    // -------------------------------------------------------------------------
    // Handle raw bytes arriving from BluetoothCallback.onMessage()
    // -------------------------------------------------------------------------

    /**
     * Called by BluetoothCallback when raw bytes arrive.
     * Returns true if the bytes were a chat message (consumed).
     * Returns false if bytes should be passed to PairingManager instead.
     */
    public boolean handleIncoming(byte[] data) {
        String json = new String(data);

        ChatMessage message = JsonUtil.chatFromJson(json);
        if (message == null) {
            // Not a chat message — let PairingManager handle it
            return false;
        }

        switch (message.getType()) {
            case ChatMessage.TYPE_MSG:
                handleIncomingMessage(message);
                return true;
            case ChatMessage.TYPE_ACK:
                Log.d(TAG, "Message ACK received");
                return true;
            default:
                Log.w(TAG, "Unknown chat message type: " + message.getType());
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void handleIncomingMessage(ChatMessage message) {
        if (cipher == null) {
            Log.e(TAG, "handleIncomingMessage: cipher not ready");
            if (callback != null) callback.onError("Cannot decrypt — not paired");
            return;
        }

        Log.d(TAG, "Encrypted message received — decrypting...");

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