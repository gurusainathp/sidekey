package com.sidekey.chat.crypto;

import android.util.Log;

import com.goterl.lazysodium.SodiumAndroid;

/**
 * SessionKeyDeriver — computes Diffie-Hellman shared secret.
 *
 * Uses libsodium crypto_scalarmult (Curve25519).
 * Both phones call this with their own private key and the partner's public key.
 * The math guarantees both sides get the same 32-byte output.
 *
 * Output is raw — do NOT use directly as encryption key.
 * Pass to KeyDerivationUtil.deriveSessionKey() first.
 */
public class SessionKeyDeriver {

    private static final String TAG = "SideKey-Session";

    private final SodiumAndroid sodium;

    public SessionKeyDeriver() {
        this.sodium = new SodiumAndroid();
    }

    /**
     * Derives a 32-byte shared secret from own private key + partner public key.
     *
     * @param ownPrivateKey    our 32-byte private key
     * @param partnerPublicKey partner's 32-byte public key
     * @return                 32-byte shared secret, or null on failure
     */
    public byte[] deriveSharedSecret(byte[] ownPrivateKey, byte[] partnerPublicKey) {
        if (ownPrivateKey == null || ownPrivateKey.length != 32) {
            Log.e(TAG, "deriveSharedSecret: invalid own private key");
            return null;
        }
        if (partnerPublicKey == null || partnerPublicKey.length != 32) {
            Log.e(TAG, "deriveSharedSecret: invalid partner public key");
            return null;
        }

        byte[] sharedSecret = new byte[32];

        // crypto_scalarmult(q, n, p)
        //   q = output buffer (32 bytes)
        //   n = own private key
        //   p = partner public key
        int result = sodium.crypto_scalarmult(sharedSecret, ownPrivateKey, partnerPublicKey);

        if (result != 0) {
            Log.e(TAG, "deriveSharedSecret: crypto_scalarmult returned " + result);
            return null;
        }

        Log.d(TAG, "shared secret len = " + sharedSecret.length);
        Log.d(TAG, "shared secret hex = " + bytesToHex(sharedSecret));

        return sharedSecret;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}