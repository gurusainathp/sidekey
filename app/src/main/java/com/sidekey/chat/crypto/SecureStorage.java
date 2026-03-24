package com.sidekey.chat.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureStorage {

    private static final String TAG = "SideKey-SecureStorage";

    private final SharedPreferences prefs;

    public SecureStorage(Context context) {
        prefs = context.getSharedPreferences(
                CryptoConstants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }

    // -------------------------------------------------------------------------
    // AES key in Keystore — internal only, used to encrypt the private key
    // -------------------------------------------------------------------------

    private SecretKey getOrCreateAesKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(CryptoConstants.KEYSTORE_PROVIDER);
        keyStore.load(null);

        if (keyStore.containsAlias(CryptoConstants.KEYSTORE_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                    keyStore.getEntry(CryptoConstants.KEYSTORE_ALIAS, null);
            return entry.getSecretKey();
        }

        KeyGenerator keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                CryptoConstants.KEYSTORE_PROVIDER
        );

        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                CryptoConstants.KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(CryptoConstants.AES_KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .build();

        keyGen.init(spec);
        Log.d(TAG, "AES key generated in Keystore");
        return keyGen.generateKey();
    }

    // -------------------------------------------------------------------------
    // Own private key — AES-GCM encrypted, stored in SharedPreferences
    // -------------------------------------------------------------------------

    public void savePrivateKey(byte[] privateKey) throws Exception {
        SecretKey aesKey = getOrCreateAesKey();
        Cipher cipher = Cipher.getInstance(CryptoConstants.AES_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);

        byte[] iv        = cipher.getIV();
        byte[] encrypted = cipher.doFinal(privateKey);

        String stored = Base64.encodeToString(iv, Base64.NO_WRAP)
                + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);

        prefs.edit()
                .putString(CryptoConstants.KEY_PRIVATE_ENCRYPTED, stored)
                .apply();

        Log.d(TAG, "Private key encrypted and saved");
    }

    public byte[] getPrivateKey() throws Exception {
        String stored = prefs.getString(CryptoConstants.KEY_PRIVATE_ENCRYPTED, null);
        if (stored == null) return null;

        String[] parts = stored.split(":");
        if (parts.length != 2) {
            Log.e(TAG, "Corrupted private key entry");
            return null;
        }

        byte[] iv        = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);

        SecretKey aesKey = getOrCreateAesKey();
        Cipher cipher    = Cipher.getInstance(CryptoConstants.AES_MODE);
        cipher.init(Cipher.DECRYPT_MODE, aesKey,
                new GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE, iv));

        return cipher.doFinal(encrypted);
    }

    public boolean hasPrivateKey() {
        return prefs.contains(CryptoConstants.KEY_PRIVATE_ENCRYPTED);
    }

    // -------------------------------------------------------------------------
    // Own public key
    // -------------------------------------------------------------------------

    public void savePublicKey(byte[] publicKey) {
        prefs.edit()
                .putString(CryptoConstants.KEY_PUBLIC,
                        Base64.encodeToString(publicKey, Base64.NO_WRAP))
                .apply();
    }

    public byte[] getPublicKey() {
        String encoded = prefs.getString(CryptoConstants.KEY_PUBLIC, null);
        if (encoded == null) return null;
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    public boolean hasPublicKey() {
        return prefs.contains(CryptoConstants.KEY_PUBLIC);
    }

    // -------------------------------------------------------------------------
    // Partner public key
    // Called by: PairingManager.savePartnerPublicKey / getPartnerPublicKey
    // -------------------------------------------------------------------------

    public void savePartnerPublicKey(byte[] partnerKey) {
        prefs.edit()
                .putString(CryptoConstants.KEY_PARTNER,
                        Base64.encodeToString(partnerKey, Base64.NO_WRAP))
                .putLong(CryptoConstants.KEY_PARTNER_TIMESTAMP, System.currentTimeMillis())
                .putBoolean(CryptoConstants.KEY_IS_PAIRED, true)
                .apply();

        Log.d(TAG, "Partner public key saved — isPaired = true");
    }

    public byte[] getPartnerPublicKey() {
        String encoded = prefs.getString(CryptoConstants.KEY_PARTNER, null);
        if (encoded == null) return null;
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    public boolean hasPartnerPublicKey() {
        return prefs.contains(CryptoConstants.KEY_PARTNER);
    }

    // -------------------------------------------------------------------------
    // Pair status
    // -------------------------------------------------------------------------

    public boolean isPaired() {
        return prefs.getBoolean(CryptoConstants.KEY_IS_PAIRED, false);
    }

    public long getPartnerTimestamp() {
        return prefs.getLong(CryptoConstants.KEY_PARTNER_TIMESTAMP, 0L);
    }

    /**
     * Removes partner key and resets pair status.
     * No UI trigger yet — called manually for testing or reset flow.
     */
    public void clearPartner() {
        prefs.edit()
                .remove(CryptoConstants.KEY_PARTNER)
                .remove(CryptoConstants.KEY_PARTNER_TIMESTAMP)
                .putBoolean(CryptoConstants.KEY_IS_PAIRED, false)
                .apply();

        Log.d(TAG, "Partner key cleared — isPaired = false");
    }
}