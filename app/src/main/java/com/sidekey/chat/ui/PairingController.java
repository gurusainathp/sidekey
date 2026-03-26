package com.sidekey.chat.ui;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.pairing.AutoSessionStarter;
import com.sidekey.chat.pairing.PairingManager;

/**
 * PairingController — bridge between UI confirm/cancel and PairingManager.
 *
 * Server path:
 *   onPairConfirmed() → confirmPairing() → send ACK → autoSessionStarter.onServerConfirmed()
 *
 * Client path:
 *   handled automatically via AutoSessionStarter.onAckReceived()
 */
public class PairingController {

    private static final String TAG = "SideKey-PairingCtrl";

    private final PairingManager     pairingManager;
    private final BluetoothService   bluetoothService;
    private final AutoSessionStarter autoSessionStarter;

    public PairingController(PairingManager pairingManager,
                             BluetoothService bluetoothService,
                             AutoSessionStarter autoSessionStarter) {
        this.pairingManager     = pairingManager;
        this.bluetoothService   = bluetoothService;
        this.autoSessionStarter = autoSessionStarter;
    }

    public void onFingerprintReady(String fingerprint) {
        Log.d(TAG, "Fingerprint ready: " + fingerprint);
    }

    /**
     * Server taps Confirm — persist key, send ACK, derive session.
     */
    public void onPairConfirmed() {
        Log.d(TAG, "Server confirmed pairing");

        if (!pairingManager.isPending()) {
            Log.e(TAG, "onPairConfirmed: no pending state");
            return;
        }

        // 1. Persist the partner key
        pairingManager.confirmPairing();

        // 2. Send ACK to client
        byte[] ack = pairingManager.createAckMessage();
        if (ack != null) {
            bluetoothService.send(ack);
            Log.d(TAG, "ACK sent to partner");
        } else {
            Log.e(TAG, "Failed to build ACK");
        }

        // 3. Derive session on server side
        autoSessionStarter.onServerConfirmed();
    }

    public void onPairCancelled() {
        Log.d(TAG, "Pairing cancelled");
        pairingManager.cancelPairing();
    }
}