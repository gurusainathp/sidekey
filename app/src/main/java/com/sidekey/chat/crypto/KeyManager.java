package com.sidekey.chat.crypto;

import android.content.Context;
import android.util.Log;

import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;
import com.goterl.lazysodium.utils.KeyPair;

public class KeyManager {

    private static final String TAG = "SideKey-KeyManager";

    private final SecureStorage storage;
    private final LazySodiumAndroid sodium;

    private byte[] publicKey;

    public KeyManager(Context context) {
        this.storage = new SecureStorage(context);
        this.sodium = new LazySodiumAndroid(new SodiumAndroid());
    }

    public void init() {
        if (storage.hasPublicKey()) {
            publicKey = storage.getPublicKey();
            Log.d(TAG, "✅ Public key loaded from storage");
            Log.d(TAG, "Public key (hex): " + bytesToHex(publicKey));
        } else {
            generateAndSaveKeys();
        }
    }

    private void generateAndSaveKeys() {
        try {
            KeyPair keypair = sodium.cryptoBoxKeypair();

            publicKey = keypair.getPublicKey().getAsBytes();
            byte[] privateKey = keypair.getSecretKey().getAsBytes();

            storage.savePublicKey(publicKey);

            // Private key Keystore persistence comes next phase
            // Do NOT store raw private key in SharedPreferences
            Log.d(TAG, "✅ New keypair generated");
            Log.d(TAG, "Public key (hex): " + bytesToHex(publicKey));
            Log.w(TAG, "⚠️ Private key not yet persisted — Keystore coming next phase");

        } catch (Exception e) {
            Log.e(TAG, "❌ Key generation failed: " + e.getMessage(), e);
        }
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}