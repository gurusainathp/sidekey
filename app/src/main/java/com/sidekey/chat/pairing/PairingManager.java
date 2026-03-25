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
 *   3. Server confirms → save partner key, send ACK, derive session
 *   4. Client receives ACK → save partner key, derive session (no button)
 */
public class PairingManager {

    private static final String TAG = "SideKey-Pairing";

    private final KeyManager    keyManager;
    private final SecureStorage storage;

    private PairingCallback callback;
    private PairingState    pendingState;

    public PairingManager(Context context) {
        this.keyManager = new KeyManager(context);
        this.storage    = new SecureStorage(context);
        this.keyManager.init();
    }

    public void setCallback(PairingCallback callback) {
        this.callback = callback;
    }

    // -------------------------------------------------------------------------
    // Build PAIR message
    // -------------------------------------------------------------------------

    public byte[] createOwnMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) {
            Log.e(TAG, "createOwnMessage: public key null");
            return null;
        }
        PairingMessage msg = new PairingMessage(
                PairingMessage.TYPE_PAIR, publicKey, System.currentTimeMillis());
        String json = JsonUtil.toJson(msg);
        if (json == null) { Log.e(TAG, "createOwnMessage: serialisation failed"); return null; }
        Log.d(TAG, "Own PAIR message ready");
        return json.getBytes();
    }

    // -------------------------------------------------------------------------
    // Handle raw bytes from Bluetooth
    // -------------------------------------------------------------------------

    public void handleIncoming(byte[] data) {
        String json = new String(data);
        PairingMessage message = JsonUtil.fromJson(json);
        if (message == null) {
            Log.e(TAG, "handleIncoming: parse failed");
            if (callback != null) callback.onPairingError("Could not parse incoming message");
            return;
        }
        switch (message.getType()) {
            case PairingMessage.TYPE_PAIR: handlePairMessage(message); break;
            case PairingMessage.TYPE_ACK:  handleAckMessage(message);  break;
            default: Log.w(TAG, "Unknown type: " + message.getType());
        }
    }

    // -------------------------------------------------------------------------
    // Server confirms — save key
    // -------------------------------------------------------------------------

    public void confirmPairing() {
        if (pendingState == null) { Log.e(TAG, "confirmPairing: no pending state"); return; }
        pendingState.confirm();
        storage.savePartnerPublicKey(pendingState.getPartnerKey());
        Log.d(TAG, "✅ Pairing confirmed — partner key saved");
        Log.d(TAG, "Fingerprint: " + pendingState.getFingerprint());
        pendingState = null;
        if (callback != null) callback.onPairingComplete();
    }

    public void cancelPairing() {
        if (pendingState == null) return;
        Log.d(TAG, "Pairing cancelled");
        pendingState = null;
    }

    // -------------------------------------------------------------------------
    // Build ACK
    // -------------------------------------------------------------------------

    public byte[] createAckMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) return null;
        PairingMessage ack = new PairingMessage(
                PairingMessage.TYPE_ACK, publicKey, System.currentTimeMillis());
        String json = JsonUtil.toJson(ack);
        return json != null ? json.getBytes() : null;
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    public boolean isPaired()  { return storage.isPaired(); }
    public boolean isPending() { return pendingState != null; }

    public String getOwnFingerprint() {
        byte[] key = keyManager.getPublicKey();
        return key == null ? "unknown" : new UserKey(key).getFingerprint();
    }

    public String getPartnerFingerprint() {
        byte[] key = storage.getPartnerPublicKey();
        return key == null ? null : new UserKey(key).getFingerprint();
    }

    public String getPendingFingerprint() {
        return pendingState != null ? pendingState.getFingerprint() : null;
    }

    // -------------------------------------------------------------------------
    // Key material getters for ChatManager / SessionManager
    // -------------------------------------------------------------------------

    public byte[] getOwnPrivateKey() {
        try { return keyManager.getPrivateKey(); }
        catch (Exception e) { Log.e(TAG, "getOwnPrivateKey: " + e.getMessage()); return null; }
    }

    public byte[] getPartnerPublicKey() {
        return storage.getPartnerPublicKey();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void handlePairMessage(PairingMessage message) {
        byte[] partnerKey = message.getPublicKey();
        if (partnerKey == null || partnerKey.length != 32) {
            Log.e(TAG, "handlePairMessage: invalid key");
            if (callback != null) callback.onPairingError("Partner sent invalid key");
            return;
        }

        UserKey partnerUserKey = new UserKey(partnerKey, message.getTimestamp());
        String fingerprint = partnerUserKey.getFingerprint();

        // Store pending — NOT saved to disk yet
        pendingState = new PairingState(partnerKey, message.getTimestamp(), fingerprint);

        Log.d(TAG, "Own fingerprint:     " + getOwnFingerprint());
        Log.d(TAG, "Partner fingerprint: " + fingerprint);
        Log.d(TAG, "→ Both should match on the other phone");

        if (callback != null) callback.onPartnerKeyReceived(fingerprint);
    }

    private void handleAckMessage(PairingMessage message) {
        // ACK from server means server confirmed — client now saves partner key
        // The partner key was received earlier in handlePairMessage as pendingState
        if (pendingState != null) {
            storage.savePartnerPublicKey(pendingState.getPartnerKey());
            Log.d(TAG, "✅ Client: partner key saved after ACK");
            pendingState = null;
        }
        Log.d(TAG, "ACK received — notifying for session derivation");
        if (callback != null) {
            callback.onAckReceived();
            callback.onPairingComplete();
        }
    }
}