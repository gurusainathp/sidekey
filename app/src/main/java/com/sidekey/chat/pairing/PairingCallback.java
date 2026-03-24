package com.sidekey.chat.pairing;

/**
 * Callback interface for PairingManager events.
 *
 * PairingManager calls these after processing incoming Bluetooth data.
 * MainActivity (or a PairingActivity later) implements this to update the UI.
 *
 * All callbacks may arrive on a background thread.
 * Implementors must use runOnUiThread() before touching any views.
 */
public interface PairingCallback {

    /**
     * Partner's public key was received and saved.
     * Show the fingerprint to the user for visual verification.
     *
     * @param fingerprint  formatted key fingerprint e.g. "A1F9-22C8-77D1"
     */
    void onPartnerKeyReceived(String fingerprint);

    /**
     * Both sides have confirmed pairing (ACK received).
     * Safe to navigate to chat screen.
     */
    void onPairingComplete();

    /**
     * Something went wrong during pairing.
     *
     * @param reason  human-readable error description
     */
    void onPairingError(String reason);
}