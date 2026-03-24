package com.sidekey.chat.model;

import java.security.MessageDigest;

public class UserKey {

    private final byte[] publicKey;
    private final long   timestamp;
    private final String fingerprint;

    // Full constructor
    public UserKey(byte[] publicKey, long timestamp) {
        this.publicKey   = publicKey;
        this.timestamp   = timestamp;
        this.fingerprint = generateFingerprint(publicKey);
    }

    // Convenience constructor — uses current time
    public UserKey(byte[] publicKey) {
        this(publicKey, System.currentTimeMillis());
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a short human-readable fingerprint for pairing verification.
     * Format: A1F9-22C8-77D1
     */
    public String getFingerprint() {
        return fingerprint;
    }

    // SHA-256 of public key → hex → first 12 chars → formatted as XXXX-XXXX-XXXX
    private static String generateFingerprint(byte[] publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey);

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02X", b));
            }

            String raw = hex.substring(0, 12);
            return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);

        } catch (Exception e) {
            return "0000-0000-0000";
        }
    }

    @Override
    public String toString() {
        return "UserKey{fingerprint=" + fingerprint + ", timestamp=" + timestamp + "}";
    }
}