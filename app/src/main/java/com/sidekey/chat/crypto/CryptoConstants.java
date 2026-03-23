package com.sidekey.chat.crypto;

public class CryptoConstants {

    // Key sizes in bytes (libsodium crypto_box)
    public static final int PUBLIC_KEY_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = 32;
    public static final int NONCE_SIZE = 24;

    // SharedPreferences file name
    public static final String PREFS_NAME = "sidekey_prefs";

    // Keys inside SharedPreferences
    public static final String KEY_PUBLIC = "public_key";
    public static final String KEY_PARTNER = "partner_public_key";

    // Android Keystore alias for private key
    public static final String KEYSTORE_ALIAS = "sidekey_private_key";

    // Private constructor — no instances needed
    private CryptoConstants() {}
}