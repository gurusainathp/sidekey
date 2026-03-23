package com.sidekey.chat.crypto;

public class CryptoConstants {

    // Key sizes in bytes (libsodium crypto_box)
    public static final int PUBLIC_KEY_SIZE = 32;
    public static final int PRIVATE_KEY_SIZE = 32;
    public static final int NONCE_SIZE = 24;

    // SharedPreferences file name
    public static final String PREFS_NAME = "sidekey_prefs";

    // SharedPreferences keys
    public static final String KEY_PUBLIC = "public_key";
    public static final String KEY_PARTNER = "partner_public_key";
    public static final String KEY_PRIVATE_ENCRYPTED = "private_key_encrypted";

    // Android Keystore alias — this is the AES key that encrypts the private key
    public static final String KEYSTORE_ALIAS = "sidekey_aes_key";
    public static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    // AES/GCM config for encrypting the private key
    public static final String AES_MODE = "AES/GCM/NoPadding";
    public static final int AES_KEY_SIZE = 256;
    public static final int GCM_IV_SIZE = 12;
    public static final int GCM_TAG_SIZE = 128;

    // Private constructor — no instances needed
    private CryptoConstants() {}
}