package com.sidekey.chat.ui;

import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.pairing.PairingManager;

/**
 * PairingController — bridge between UI actions and PairingManager.
 *
 * Responsibilities:
 *   - Receive UI events (confirm / cancel)
 *   - Delegate to PairingManager
 *   - Send ACK over Bluetooth after confirmation
 *
 * This keeps MainActivity clean — it calls controller methods,
 * not PairingManager directly.
 *
 * Later a dedicated PairingActivity will own this controller.
 */
public class PairingController {

    private static final String TAG = "SideKey-PairingCtrl";

    private final PairingManager   pairingManager;
    private final BluetoothService bluetoothService;

    public PairingController(PairingManager pairingManager, BluetoothService bluetoothService) {
        this.pairingManager   = pairingManager;
        this.bluetoothService = bluetoothService;
    }

    // -------------------------------------------------------------------------
    // Called when UI shows fingerprint to user
    // -------------------------------------------------------------------------

    public void onFingerprintReady(String fingerprint) {
        Log.d(TAG, "Fingerprint ready for display: " + fingerprint);
        // Later: navigate to confirmation screen
        // For now: auto-log only, user must call confirmPairing() separately
    }

    // -------------------------------------------------------------------------
    // Called when user taps "Confirm" on fingerprint screen
    // -------------------------------------------------------------------------

    public void onPairConfirmed() {
        Log.d(TAG, "User confirmed pairing");

        if (!pairingManager.isPending()) {
            Log.e(TAG, "onPairConfirmed: no pending pairing state — ignoring");
            return;
        }

        // 1. Tell PairingManager to persist the key
        pairingManager.confirmPairing();

        // 2. Send ACK to the other phone so they know we confirmed
        byte[] ack = pairingManager.createAckMessage();
        if (ack != null) {
            bluetoothService.send(ack);
            Log.d(TAG, "ACK sent to partner");
        } else {
            Log.e(TAG, "Failed to build ACK message");
        }
    }

    // -------------------------------------------------------------------------
    // Called when user taps "Cancel" on fingerprint screen
    // -------------------------------------------------------------------------

    public void onPairCancelled() {
        Log.d(TAG, "User cancelled pairing — pending state discarded");
        pairingManager.cancelPairing();
    }
}