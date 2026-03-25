package com.sidekey.chat.pairing;

/**
 * Callback interface for PairingManager events.
 * All callbacks may fire on a background thread — use runOnUiThread for UI.
 */
public interface PairingCallback {

    /**
     * Partner's public key was received and stored in pending state (NOT saved yet).
     * Show fingerprint to user for verification.
     *
     * @param fingerprint  formatted key fingerprint e.g. "A1F9-22C8-77D1"
     */
    void onPartnerKeyReceived(String fingerprint);

    /**
     * ACK received from partner — they confirmed our key on their side.
     * Client uses this to trigger session key derivation (no button needed).
     */
    void onAckReceived();

    /**
     * Pairing fully complete — both sides confirmed.
     */
    void onPairingComplete();

    /**
     * Something went wrong.
     */
    void onPairingError(String reason);
}