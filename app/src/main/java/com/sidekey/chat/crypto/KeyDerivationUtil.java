package com.sidekey.chat.crypto;

import android.util.Log;

import java.security.MessageDigest;

/**
 * KeyDerivationUtil — turns a raw shared secret into a usable session key.
 *
 * Method: SHA-256(sharedSecret) → 32 bytes
 *
 * Why hash the raw secret?
 *   - crypto_scalarmult output has a small set of weak points
 *   - hashing removes any structure and produces a uniformly random key
 *   - Later upgrade path: replace SHA-256 with HKDF for domain separation
 *
 * Pure Java — no Android framework dependencies.
 */
public class KeyDerivationUtil {

    private static final String TAG = "SideKey-Session";

    // Private constructor — static utility only
    private KeyDerivationUtil() {}

    /**
     * Derives a 32-byte session key from a shared secret.
     *
     * @param sharedSecret  raw 32-byte output of crypto_scalarmult
     * @return              32-byte session key, or null on failure
     */
    public static byte[] deriveSessionKey(byte[] sharedSecret) {
        if (sharedSecret == null || sharedSecret.length == 0) {
            Log.e(TAG, "deriveSessionKey: null or empty shared secret");
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sessionKey = digest.digest(sharedSecret);

            Log.d(TAG, "session key len = " + sessionKey.length);
            Log.d(TAG, "session key hex = " + bytesToHex(sessionKey));

            return sessionKey;

        } catch (Exception e) {
            Log.e(TAG, "deriveSessionKey: SHA-256 failed — " + e.getMessage());
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}