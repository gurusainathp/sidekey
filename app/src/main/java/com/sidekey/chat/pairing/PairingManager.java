package com.sidekey.chat.pairing;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.crypto.KeyManager;
import com.sidekey.chat.crypto.SecureStorage;
import com.sidekey.chat.model.PairingMessage;
import com.sidekey.chat.model.PairingState;
import com.sidekey.chat.model.UserKey;
import com.sidekey.chat.utils.JsonUtil;

/**
 * PairingManager — brain of the pairing flow.
 *
 * Secure flow:
 *   1. Receive partner key → store in memory as PairingState (NOT saved yet)
 *   2. Show fingerprint to user for visual verification
 *   3. User confirms → save partner key to SecureStorage
 *   4. User cancels → discard PairingState, nothing persisted
 *
 * Does NOT touch Bluetooth.
 * Does NOT touch UI.
 * Communicates results via PairingCallback.
 */
public class PairingManager {

    private static final String TAG = "SideKey-Pairing";

    private final KeyManager    keyManager;
    private final SecureStorage storage;

    private PairingCallback callback;
    private PairingState    pendingState;   // held in memory until confirmed or cancelled

    public PairingManager(Context context) {
        this.keyManager = new KeyManager(context);
        this.storage    = new SecureStorage(context);
        this.keyManager.init();
    }

    public void setCallback(PairingCallback callback) {
        this.callback = callback;
    }

    // -------------------------------------------------------------------------
    // Build our own PAIR message — call this, then pass bytes to BluetoothService
    // PairingManager never calls BluetoothService directly
    // -------------------------------------------------------------------------

    public byte[] createOwnMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) {
            Log.e(TAG, "createOwnMessage: public key null — KeyManager not ready");
            return null;
        }

        PairingMessage message = new PairingMessage(
                PairingMessage.TYPE_PAIR,
                publicKey,
                System.currentTimeMillis()
        );

        String json = JsonUtil.toJson(message);
        if (json == null) {
            Log.e(TAG, "createOwnMessage: JSON serialisation failed");
            return null;
        }

        Log.d(TAG, "Own PAIR message ready");
        return json.getBytes();
    }

    // -------------------------------------------------------------------------
    // Handle raw bytes from BluetoothCallback.onMessage()
    // -------------------------------------------------------------------------

    public void handleIncoming(byte[] data) {
        String json = new String(data);
        Log.d(TAG, "Incoming raw JSON: " + json);

        PairingMessage message = JsonUtil.fromJson(json);
        if (message == null) {
            Log.e(TAG, "handleIncoming: JSON parse failed — not a pairing message?");
            if (callback != null) callback.onPairingError("Could not parse incoming message");
            return;
        }

        switch (message.getType()) {
            case PairingMessage.TYPE_PAIR:
                handlePairMessage(message);
                break;
            case PairingMessage.TYPE_ACK:
                handleAckMessage();
                break;
            default:
                Log.w(TAG, "handleIncoming: unknown type — " + message.getType());
        }
    }

    // -------------------------------------------------------------------------
    // Confirmation — called by UI after user verifies fingerprint
    // Only now do we persist the partner key
    // -------------------------------------------------------------------------

    public void confirmPairing() {
        if (pendingState == null) {
            Log.e(TAG, "confirmPairing: no pending state — nothing to confirm");
            return;
        }

        pendingState.confirm();
        storage.savePartnerPublicKey(pendingState.getPartnerKey());

        Log.d(TAG, "✅ Pairing confirmed — partner key saved");
        Log.d(TAG, "Fingerprint locked in: " + pendingState.getFingerprint());

        pendingState = null;

        if (callback != null) callback.onPairingComplete();
    }

    // -------------------------------------------------------------------------
    // Cancellation — discard pending state, nothing is saved
    // -------------------------------------------------------------------------

    public void cancelPairing() {
        if (pendingState == null) return;

        Log.d(TAG, "Pairing cancelled — pending state discarded, nothing saved");
        pendingState = null;
    }

    // -------------------------------------------------------------------------
    // Build ACK to send back after confirming
    // -------------------------------------------------------------------------

    public byte[] createAckMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) return null;

        PairingMessage ack = new PairingMessage(
                PairingMessage.TYPE_ACK,
                publicKey,
                System.currentTimeMillis()
        );

        String json = JsonUtil.toJson(ack);
        return json != null ? json.getBytes() : null;
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    public boolean isPaired() {
        return storage.isPaired();
    }

    public boolean isPending() {
        return pendingState != null;
    }

    public String getOwnFingerprint() {
        byte[] key = keyManager.getPublicKey();
        if (key == null) return "unknown";
        return new UserKey(key).getFingerprint();
    }

    public String getPartnerFingerprint() {
        byte[] key = storage.getPartnerPublicKey();
        if (key == null) return null;
        return new UserKey(key).getFingerprint();
    }

    public String getPendingFingerprint() {
        if (pendingState == null) return null;
        return pendingState.getFingerprint();
    }

    // -------------------------------------------------------------------------
    // Internal handlers
    // -------------------------------------------------------------------------

    private void handlePairMessage(PairingMessage message) {
        byte[] partnerKey = message.getPublicKey();

        if (partnerKey == null || partnerKey.length != 32) {
            Log.e(TAG, "handlePairMessage: invalid key length — "
                    + (partnerKey != null ? partnerKey.length : "null"));
            if (callback != null) callback.onPairingError("Partner sent invalid key");
            return;
        }

        // Build fingerprint from received key
        UserKey partnerUserKey = new UserKey(partnerKey, message.getTimestamp());
        String fingerprint = partnerUserKey.getFingerprint();

        // Store in memory ONLY — do not save to storage yet
        pendingState = new PairingState(partnerKey, message.getTimestamp(), fingerprint);

        Log.d(TAG, "Pending pairing fingerprint: " + fingerprint);
        Log.d(TAG, "→ Waiting for user confirmation before saving");

        if (callback != null) callback.onPartnerKeyReceived(fingerprint);
    }

    private void handleAckMessage() {
        Log.d(TAG, "✅ ACK received — partner confirmed pairing on their side");
        if (callback != null) callback.onPairingComplete();
    }
}