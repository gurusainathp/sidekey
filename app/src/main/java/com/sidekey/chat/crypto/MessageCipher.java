package com.sidekey.chat.crypto;

import android.util.Base64;
import android.util.Log;

import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;

import java.util.Arrays;

/**
 * MessageCipher — encrypts and decrypts chat messages using the session key.
 *
 * Uses libsodium crypto_secretbox_easy (XSalsa20-Poly1305).
 * Session key must be ready in SessionStore before any operation.
 *
 * No session = no encryption. That is correct — messages must never
 * fall back to unencrypted or use raw keypairs directly.
 *
 * Wire format: Base64( nonce[24] + ciphertext )
 */
public class MessageCipher {

    private static final String TAG     = "SideKey-Cipher";
    private static final int    MAC_LEN = 16; // Poly1305 MAC

    private final SessionStore      sessionStore;
    private final SodiumAndroid     sodium;
    private final LazySodiumAndroid lazySodium;

    public MessageCipher(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
        this.sodium       = new SodiumAndroid();
        this.lazySodium   = new LazySodiumAndroid(sodium);
    }

    // -------------------------------------------------------------------------
    // Encrypt
    // -------------------------------------------------------------------------

    /**
     * Encrypts a plaintext message using the session key.
     *
     * @param plaintext  message to encrypt
     * @return           Base64( nonce[24] + ciphertext ), or null on failure
     */
    public String encryptMessage(String plaintext) {
        if (!sessionStore.isReady()) {
            Log.e(TAG, "encryptMessage: session not ready — cannot encrypt");
            return null;
        }

        byte[] sessionKey = sessionStore.getSessionKey();
        byte[] message    = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // Fresh random nonce every message
        byte[] nonce      = lazySodium.randomBytesBuf(CryptoConstants.NONCE_SIZE);
        byte[] ciphertext = new byte[message.length + MAC_LEN];

        Log.d(TAG, "MessageCipher: using session key");

        int result = sodium.crypto_secretbox_easy(
                ciphertext, message, message.length, nonce, sessionKey);

        if (result != 0) {
            Log.e(TAG, "encryptMessage: crypto_secretbox_easy returned " + result);
            return null;
        }

        // Prepend nonce so receiver can extract it
        byte[] payload = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce,      0, payload, 0,            nonce.length);
        System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);

        String b64 = Base64.encodeToString(payload, Base64.NO_WRAP);
        Log.d(TAG, "MessageCipher: encrypted len = " + b64.length());
        return b64;
    }

    // -------------------------------------------------------------------------
    // Decrypt
    // -------------------------------------------------------------------------

    /**
     * Decrypts a Base64 payload produced by encryptMessage().
     *
     * @param base64Payload  Base64( nonce[24] + ciphertext )
     * @return               plaintext String, or null on failure
     */
    public String decryptMessage(String base64Payload) {
        if (!sessionStore.isReady()) {
            Log.e(TAG, "decryptMessage: session not ready — cannot decrypt");
            return null;
        }

        byte[] sessionKey = sessionStore.getSessionKey();

        byte[] payload;
        try {
            payload = Base64.decode(base64Payload, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "decryptMessage: Base64 decode failed — " + e.getMessage());
            return null;
        }

        if (payload.length <= CryptoConstants.NONCE_SIZE) {
            Log.e(TAG, "decryptMessage: payload too short");
            return null;
        }

        byte[] nonce      = Arrays.copyOfRange(payload, 0, CryptoConstants.NONCE_SIZE);
        byte[] ciphertext = Arrays.copyOfRange(payload, CryptoConstants.NONCE_SIZE, payload.length);
        byte[] plaintext  = new byte[ciphertext.length - MAC_LEN];

        Log.d(TAG, "MessageCipher: decrypt using session key");

        int result = sodium.crypto_secretbox_open_easy(
                plaintext, ciphertext, ciphertext.length, nonce, sessionKey);

        if (result != 0) {
            Log.e(TAG, "decryptMessage: crypto_secretbox_open_easy returned " + result
                    + " — wrong key or tampered data");
            return null;
        }

        return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    }
}