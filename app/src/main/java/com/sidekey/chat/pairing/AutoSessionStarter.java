package com.sidekey.chat.pairing;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothCallback;
import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.connection.ConnectionState;
import com.sidekey.chat.crypto.SessionManager;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.bluetooth.BluetoothService;

/**
 * AutoSessionStarter — automatically drives the pairing → session pipeline.
 *
 * Implements BluetoothCallback so it receives raw BT events.
 * Implements PairingCallback so it receives pairing lifecycle events.
 *
 * On connect:
 *   1. Sets state → PAIRING
 *   2. Sends own public key immediately
 *
 * On pairing complete (server confirmed / client got ACK):
 *   3. Derives session key via SessionManager
 *   4. Sets state → SESSION_READY
 *
 * UI events (fingerprint display, errors) are forwarded to a UICallback
 * so MainActivity can update views without being part of the flow.
 */
public class AutoSessionStarter implements BluetoothCallback, PairingCallback {

    private static final String TAG = "SideKey-AutoSession";

    // -------------------------------------------------------------------------
    // UI bridge — MainActivity implements this for fingerprint display
    // -------------------------------------------------------------------------

    public interface UICallback {
        /** Partner key received — show fingerprint for verification. */
        void onFingerprintReady(String fingerprint);
        /** Session is fully ready — open chat UI. */
        void onSessionReady();
        /** Something went wrong. */
        void onError(String reason);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PairingManager     pairingManager;
    private final BluetoothService   bluetoothService;
    private final SessionManager     sessionManager;
    private final ConnectionManager  connectionManager;

    private UICallback uiCallback;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AutoSessionStarter(Context context,
                              PairingManager pairingManager,
                              BluetoothService bluetoothService,
                              SessionStore sessionStore,
                              ConnectionManager connectionManager) {
        this.pairingManager    = pairingManager;
        this.bluetoothService  = bluetoothService;
        this.connectionManager = connectionManager;
        this.sessionManager    = new SessionManager(context, sessionStore);

        // AutoSessionStarter owns the PairingCallback — events come here first
        this.pairingManager.setCallback(this);
    }

    public void setUICallback(UICallback callback) {
        this.uiCallback = callback;
    }

    // -------------------------------------------------------------------------
    // BluetoothCallback — fired by BluetoothService
    // -------------------------------------------------------------------------

    @Override
    public void onConnected() {
        Log.d(TAG, "AutoSessionStarter: BT connected → starting pairing");
        connectionManager.setState(ConnectionState.PAIRING);

        // Send our own public key immediately
        byte[] ownMsg = pairingManager.createOwnMessage();
        if (ownMsg != null) {
            bluetoothService.send(ownMsg);
            Log.d(TAG, "AutoSessionStarter: own pairing message sent");
        } else {
            Log.e(TAG, "AutoSessionStarter: failed to build pairing message");
        }
    }

    @Override
    public void onMessage(byte[] data) {
        // Route to PairingManager — it fires PairingCallback (back to us) when done
        pairingManager.handleIncoming(data);
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "AutoSessionStarter: BT disconnected");
        connectionManager.reset();
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "AutoSessionStarter: BT error — " + message);
        connectionManager.reset();
        if (uiCallback != null) uiCallback.onError(message);
    }

    // -------------------------------------------------------------------------
    // PairingCallback — fired by PairingManager
    // -------------------------------------------------------------------------

    @Override
    public void onPartnerKeyReceived(String fingerprint) {
        // Key is in pending state — not saved yet. Forward to UI for verification.
        Log.d(TAG, "AutoSessionStarter: partner key received, fingerprint=" + fingerprint);
        if (uiCallback != null) uiCallback.onFingerprintReady(fingerprint);
    }

    @Override
    public void onAckReceived() {
        // Client side: server has confirmed — save is already done in PairingManager
        Log.d(TAG, "AutoSessionStarter: ACK received (client) → deriving session");
        deriveSession();
    }

    @Override
    public void onPairingComplete() {
        // Called after confirmPairing() on server, and after ACK on client
        // Session derivation is triggered by the specific path (confirm or ack)
        // so we don't double-derive here — just log
        Log.d(TAG, "AutoSessionStarter: pairing complete");
    }

    @Override
    public void onPairingError(String reason) {
        Log.e(TAG, "AutoSessionStarter: pairing error — " + reason);
        connectionManager.reset();
        if (uiCallback != null) uiCallback.onError("Pairing error: " + reason);
    }

    // -------------------------------------------------------------------------
    // Server confirm — called by PairingController (server taps Confirm button)
    // -------------------------------------------------------------------------

    /**
     * Called by PairingController after server user taps Confirm.
     * Saves partner key and derives session on the server side.
     */
    public void onServerConfirmed() {
        Log.d(TAG, "AutoSessionStarter: server confirmed → deriving session");
        deriveSession();
    }

    // -------------------------------------------------------------------------
    // Session derivation — shared by server and client paths
    // -------------------------------------------------------------------------

    private void deriveSession() {
        boolean ok = sessionManager.initSession();
        if (ok) {
            onSessionReady();
        } else {
            Log.e(TAG, "AutoSessionStarter: session derivation failed");
            if (uiCallback != null) uiCallback.onError("Session derivation failed");
        }
    }

    private void onSessionReady() {
        Log.d(TAG, "AutoSessionStarter: session ready ✅");
        connectionManager.setState(ConnectionState.SESSION_READY);
        if (uiCallback != null) uiCallback.onSessionReady();
    }
}