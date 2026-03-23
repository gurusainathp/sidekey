package com.sidekey.chat.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public class SecureStorage {

    private final SharedPreferences prefs;

    public SecureStorage(Context context) {
        prefs = context.getSharedPreferences(
                CryptoConstants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }

    // --- Public Key ---

    public void savePublicKey(byte[] publicKey) {
        String encoded = Base64.encodeToString(publicKey, Base64.NO_WRAP);
        prefs.edit().putString(CryptoConstants.KEY_PUBLIC, encoded).apply();
    }

    public byte[] getPublicKey() {
        String encoded = prefs.getString(CryptoConstants.KEY_PUBLIC, null);
        if (encoded == null) return null;
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    public boolean hasPublicKey() {
        return prefs.contains(CryptoConstants.KEY_PUBLIC);
    }

    // --- Partner Key (wired up during Bluetooth pairing phase) ---

    public void savePartnerKey(byte[] partnerKey) {
        String encoded = Base64.encodeToString(partnerKey, Base64.NO_WRAP);
        prefs.edit().putString(CryptoConstants.KEY_PARTNER, encoded).apply();
    }

    public byte[] getPartnerKey() {
        String encoded = prefs.getString(CryptoConstants.KEY_PARTNER, null);
        if (encoded == null) return null;
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    public boolean hasPartnerKey() {
        return prefs.contains(CryptoConstants.KEY_PARTNER);
    }
}