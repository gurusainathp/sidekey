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
    private byte[] privateKey;

    public KeyManager(Context context) {
        this.storage = new SecureStorage(context);
        this.sodium = new LazySodiumAndroid(new SodiumAndroid());
    }

    public void init() {
        if (storage.hasPublicKey() && storage.hasPrivateKey()) {
            loadExistingKeys();
        } else {
            generateAndSaveKeys();
        }
    }

    private void loadExistingKeys() {
        try {
            publicKey = storage.getPublicKey();
            privateKey = storage.getPrivateKey();
            Log.d(TAG, "✅ Public key loaded from storage");
            Log.d(TAG, "✅ Private key loaded from Keystore");
            Log.d(TAG, "Public key (hex): " + bytesToHex(publicKey));
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load keys: " + e.getMessage(), e);
        }
    }

    private void generateAndSaveKeys() {
        try {
            KeyPair keypair = sodium.cryptoBoxKeypair();

            publicKey = keypair.getPublicKey().getAsBytes();
            privateKey = keypair.getSecretKey().getAsBytes();

            storage.savePublicKey(publicKey);
            storage.savePrivateKey(privateKey);

            Log.d(TAG, "✅ New keypair generated");
            Log.d(TAG, "✅ Private key encrypted and stored in Keystore");
            Log.d(TAG, "Public key (hex): " + bytesToHex(publicKey));

        } catch (Exception e) {
            Log.e(TAG, "❌ Key generation failed: " + e.getMessage(), e);
        }
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}