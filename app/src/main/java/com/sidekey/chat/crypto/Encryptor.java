package com.sidekey.chat.crypto;

import android.util.Log;

import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Encryptor {

    private static final String TAG = "SideKey-Encryptor";

    private final SodiumAndroid sodium;
    private final LazySodiumAndroid lazySodium;

    public Encryptor() {
        this.sodium = new SodiumAndroid();
        this.lazySodium = new LazySodiumAndroid(sodium);
    }

    /**
     * Encrypts a plaintext message.
     *
     * @param message         plaintext string to encrypt
     * @param receiverPubKey  receiver's 32-byte public key
     * @param senderPrivKey   sender's 32-byte private key
     * @return                nonce (24 bytes) + ciphertext, or null on failure
     */
    public byte[] encrypt(String message, byte[] receiverPubKey, byte[] senderPrivKey) {
        try {
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext   = new byte[messageBytes.length + 16]; // +16 for MAC

            // Use LazySodium for nonce generation (randombytes_buf)
            byte[] nonce = lazySodium.randomBytesBuf(CryptoConstants.NONCE_SIZE);

            // Raw crypto_box_easy via SodiumAndroid
            int result = sodium.crypto_box_easy(
                    ciphertext,
                    messageBytes,
                    messageBytes.length,
                    nonce,
                    receiverPubKey,
                    senderPrivKey
            );

            if (result != 0) {
                Log.e(TAG, "❌ crypto_box_easy returned " + result);
                return null;
            }

            // Prepend nonce so receiver can split and decrypt
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce,      0, payload, 0,            nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);

            Log.d(TAG, "✅ Encrypt success — total bytes: " + payload.length);
            return payload;

        } catch (Exception e) {
            Log.e(TAG, "❌ Encrypt failed: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Decrypts a payload produced by encrypt().
     *
     * @param payload          nonce (24 bytes) + ciphertext
     * @param senderPubKey     sender's 32-byte public key
     * @param receiverPrivKey  receiver's 32-byte private key
     * @return                 decrypted plaintext string, or null on failure
     */
    public String decrypt(byte[] payload, byte[] senderPubKey, byte[] receiverPrivKey) {
        try {
            if (payload == null || payload.length <= CryptoConstants.NONCE_SIZE) {
                Log.e(TAG, "❌ Payload too short");
                return null;
            }

            byte[] nonce      = Arrays.copyOfRange(payload, 0, CryptoConstants.NONCE_SIZE);
            byte[] ciphertext = Arrays.copyOfRange(payload, CryptoConstants.NONCE_SIZE, payload.length);
            byte[] plaintext  = new byte[ciphertext.length - 16]; // -16 strips MAC

            int result = sodium.crypto_box_open_easy(
                    plaintext,
                    ciphertext,
                    ciphertext.length,
                    nonce,
                    senderPubKey,
                    receiverPrivKey
            );

            if (result != 0) {
                Log.e(TAG, "❌ crypto_box_open_easy returned " + result + " — wrong keys or tampered data");
                return null;
            }

            String decrypted = new String(plaintext, StandardCharsets.UTF_8);
            Log.d(TAG, "✅ Decrypt success");
            return decrypted;

        } catch (Exception e) {
            Log.e(TAG, "❌ Decrypt failed: " + e.getMessage(), e);
            return null;
        }
    }
}