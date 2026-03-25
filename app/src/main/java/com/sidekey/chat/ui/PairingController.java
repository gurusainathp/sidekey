package com.sidekey.chat.ui;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.crypto.SessionManager;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.pairing.PairingManager;

/**
 * PairingController — bridge between UI and PairingManager.
 *
 * Also triggers SessionManager.initSession() immediately after
 * pairing is confirmed so both sides derive the same session key.
 */
public class PairingController {

    private static final String TAG = "SideKey-PairingCtrl";

    private final PairingManager   pairingManager;
    private final BluetoothService bluetoothService;
    private final SessionManager   sessionManager;

    public PairingController(Context context,
                             PairingManager pairingManager,
                             BluetoothService bluetoothService,
                             SessionStore sessionStore) {
        this.pairingManager   = pairingManager;
        this.bluetoothService = bluetoothService;
        this.sessionManager   = new SessionManager(context, sessionStore);
    }

    // -------------------------------------------------------------------------
    // Called by UI when fingerprint is ready to show
    // -------------------------------------------------------------------------

    public void onFingerprintReady(String fingerprint) {
        Log.d(TAG, "Fingerprint ready: " + fingerprint);
    }

    // -------------------------------------------------------------------------
    // Called when SERVER taps Confirm
    // -------------------------------------------------------------------------

    public void onPairConfirmed() {
        Log.d(TAG, "Server confirmed pairing");

        if (!pairingManager.isPending()) {
            Log.e(TAG, "onPairConfirmed: no pending state");
            return;
        }

        // 1. Persist partner key
        pairingManager.confirmPairing();

        // 2. Send ACK so client knows server confirmed
        byte[] ack = pairingManager.createAckMessage();
        if (ack != null) {
            bluetoothService.send(ack);
            Log.d(TAG, "ACK sent to partner");
        } else {
            Log.e(TAG, "Failed to build ACK");
        }

        // 3. Derive session key — AFTER partner key is saved
        boolean ok = sessionManager.initSession();
        if (ok) {
            Log.d(TAG, "Session key derived after server confirmation ✅");
        } else {
            Log.e(TAG, "Session key derivation failed after confirmation");
        }
    }

    // -------------------------------------------------------------------------
    // Called when CLIENT receives ACK (no button — auto-triggered)
    // -------------------------------------------------------------------------

    public void onAckReceived() {
        Log.d(TAG, "Client received ACK — deriving session key");

        boolean ok = sessionManager.initSession();
        if (ok) {
            Log.d(TAG, "Session key derived after ACK received ✅");
        } else {
            Log.e(TAG, "Session key derivation failed after ACK");
        }
    }

    // -------------------------------------------------------------------------
    // Called when user cancels
    // -------------------------------------------------------------------------

    public void onPairCancelled() {
        Log.d(TAG, "Pairing cancelled");
        pairingManager.cancelPairing();
    }

    public SessionStore getSessionStore() {
        return sessionManager != null
                ? extractSessionStore()
                : null;
    }

    // SessionStore is owned by caller — expose via constructor reference instead
    private SessionStore extractSessionStore() {
        return null; // caller holds the reference directly — see MainActivity
    }
}