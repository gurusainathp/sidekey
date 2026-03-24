package com.sidekey.chat.crypto;

import android.util.Base64;
import android.util.Log;

/**
 * MessageCipher — thin wrapper around Encryptor for chat messages.
 *
 * Holds the key material needed for a session:
 *   - own private key  (for signing/encrypting outgoing)
 *   - partner public key (for encrypting to them / verifying from them)
 *
 * encryptMessage() → returns Base64 string ready to embed in ChatMessage.payload
 * decryptMessage() → takes Base64 payload, returns plaintext String
 *
 * No storage here. No Bluetooth here. Crypto only.
 */
public class MessageCipher {

    private static final String TAG = "SideKey-Cipher";

    private final Encryptor encryptor;
    private final byte[]    ownPrivateKey;
    private final byte[]    partnerPublicKey;

    public MessageCipher(byte[] ownPrivateKey, byte[] partnerPublicKey) {
        this.encryptor        = new Encryptor();
        this.ownPrivateKey    = ownPrivateKey;
        this.partnerPublicKey = partnerPublicKey;
    }

    /**
     * Encrypts a plaintext message for the partner.
     *
     * @param plaintext  message to encrypt
     * @return           Base64(nonce + ciphertext), or null on failure
     */
    public String encryptMessage(String plaintext) {
        if (ownPrivateKey == null || partnerPublicKey == null) {
            Log.e(TAG, "encryptMessage: key material missing");
            return null;
        }

        byte[] encrypted = encryptor.encrypt(plaintext, partnerPublicKey, ownPrivateKey);
        if (encrypted == null) {
            Log.e(TAG, "encryptMessage: Encryptor returned null");
            return null;
        }

        String b64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        Log.d(TAG, "✅ Message encrypted — payload length: " + b64.length());
        return b64;
    }

    /**
     * Decrypts a Base64 payload received from the partner.
     *
     * @param base64Payload  Base64(nonce + ciphertext) from ChatMessage.payload
     * @return               plaintext String, or null on failure
     */
    public String decryptMessage(String base64Payload) {
        if (ownPrivateKey == null || partnerPublicKey == null) {
            Log.e(TAG, "decryptMessage: key material missing");
            return null;
        }

        byte[] encrypted;
        try {
            encrypted = Base64.decode(base64Payload, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "decryptMessage: Base64 decode failed — " + e.getMessage());
            return null;
        }

        // Decrypt: partner's public key + our private key
        String plaintext = encryptor.decrypt(encrypted, partnerPublicKey, ownPrivateKey);
        if (plaintext == null) {
            Log.e(TAG, "decryptMessage: Encryptor returned null — wrong keys or tampered data");
            return null;
        }

        Log.d(TAG, "✅ Message decrypted");
        return plaintext;
    }
}