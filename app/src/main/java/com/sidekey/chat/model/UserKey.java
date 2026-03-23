package com.sidekey.chat.model;

import java.security.MessageDigest;

public class UserKey {

    private final byte[] publicKey;
    private final long   timestamp;

    public UserKey(byte[] publicKey, long timestamp) {
        this.publicKey = publicKey;
        this.timestamp = timestamp;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns a human-readable fingerprint from the public key.
     * Format: "A1F9-22C8-77D1" (first 12 hex chars of SHA-256, grouped in 4s)
     */
    public String getFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey);

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02X", b));
            }

            // Take first 12 chars and group into 3 blocks of 4
            String raw = hex.substring(0, 12);
            return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);

        } catch (Exception e) {
            return "????-????-????";
        }
    }

    @Override
    public String toString() {
        return "UserKey{fingerprint=" + getFingerprint() + ", timestamp=" + timestamp + "}";
    }
}