package com.sidekey.chat.model;

/**
 * Holds temporary pairing state between key receipt and user confirmation.
 *
 * Key is NOT saved to storage until confirmPairing() is explicitly called.
 * If the user cancels, this object is discarded and nothing is persisted.
 */
public class PairingState {

    private final byte[]  partnerKey;
    private final long    timestamp;
    private final String  fingerprint;
    private       boolean confirmed;

    public PairingState(byte[] partnerKey, long timestamp, String fingerprint) {
        this.partnerKey  = partnerKey;
        this.timestamp   = timestamp;
        this.fingerprint = fingerprint;
        this.confirmed   = false;
    }

    public byte[] getPartnerKey() {
        return partnerKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void confirm() {
        this.confirmed = true;
    }

    @Override
    public String toString() {
        return "PairingState{fingerprint=" + fingerprint
                + ", confirmed=" + confirmed + "}";
    }
}