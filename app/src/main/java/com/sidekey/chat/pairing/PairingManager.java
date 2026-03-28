package com.sidekey.chat.pairing;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.crypto.KeyManager;
import com.sidekey.chat.crypto.SecureStorage;
import com.sidekey.chat.model.PairingMessage;
import com.sidekey.chat.model.PairingState;
import com.sidekey.chat.model.UserKey;
import com.sidekey.chat.storage.TrustedDeviceStore;
import com.sidekey.chat.utils.JsonUtil;

/**
 * PairingManager — brain of the pairing flow.
 *
 * After confirmation, saves the device to TrustedDeviceStore so
 * it appears as known on future sessions.
 */
public class PairingManager {

    private static final String TAG = "SideKey-Pairing";

    private final KeyManager        keyManager;
    private final SecureStorage     storage;
    private final TrustedDeviceStore trustedStore;

    private PairingCallback callback;
    private PairingState    pendingState;

    // Set externally when BT connects — needed for TrustedDeviceStore
    private String connectedDeviceAddress;
    private String connectedDeviceName;

    public PairingManager(Context context) {
        this.keyManager   = new KeyManager(context);
        this.storage      = new SecureStorage(context);
        this.trustedStore = new TrustedDeviceStore(context);
        this.keyManager.init();
    }

    public void setCallback(PairingCallback callback) { this.callback = callback; }

    public void setConnectedDevice(String address, String name) {
        this.connectedDeviceAddress = address;
        this.connectedDeviceName    = name;
    }

    // ── PAIR message ──────────────────────────────────────────────────────────

    public byte[] createOwnMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) { Log.e(TAG, "createOwnMessage: null key"); return null; }
        PairingMessage msg = new PairingMessage(
                PairingMessage.TYPE_PAIR, publicKey, System.currentTimeMillis());
        String json = JsonUtil.toJson(msg);
        if (json == null) { Log.e(TAG, "createOwnMessage: serialisation failed"); return null; }
        Log.d(TAG, "Own PAIR message ready");
        return json.getBytes();
    }

    // ── Incoming ──────────────────────────────────────────────────────────────

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

    // ── Server confirm ────────────────────────────────────────────────────────

    public void confirmPairing() {
        if (pendingState == null) { Log.e(TAG, "confirmPairing: no pending state"); return; }
        pendingState.confirm();
        storage.savePartnerPublicKey(pendingState.getPartnerKey());

        // Save to trusted device store
        if (connectedDeviceAddress != null) {
            trustedStore.saveTrustedDevice(
                    connectedDeviceName != null ? connectedDeviceName : connectedDeviceAddress,
                    connectedDeviceAddress,
                    pendingState.getFingerprint()
            );
        }

        Log.d(TAG, "✅ Pairing confirmed — partner key saved");
        pendingState = null;
        if (callback != null) callback.onPairingComplete();
    }

    public void cancelPairing() {
        if (pendingState == null) return;
        Log.d(TAG, "Pairing cancelled");
        pendingState = null;
    }

    // ── ACK ───────────────────────────────────────────────────────────────────

    public byte[] createAckMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) return null;
        PairingMessage ack = new PairingMessage(
                PairingMessage.TYPE_ACK, publicKey, System.currentTimeMillis());
        String json = JsonUtil.toJson(ack);
        return json != null ? json.getBytes() : null;
    }

    // ── State queries ─────────────────────────────────────────────────────────

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

    public byte[] getOwnPrivateKey() {
        try { return keyManager.getPrivateKey(); }
        catch (Exception e) { Log.e(TAG, "getOwnPrivateKey: " + e.getMessage()); return null; }
    }

    public byte[] getPartnerPublicKey() {
        return storage.getPartnerPublicKey();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void handlePairMessage(PairingMessage message) {
        byte[] partnerKey = message.getPublicKey();
        if (partnerKey == null || partnerKey.length != 32) {
            Log.e(TAG, "handlePairMessage: invalid key");
            if (callback != null) callback.onPairingError("Partner sent invalid key");
            return;
        }
        UserKey partnerUserKey = new UserKey(partnerKey, message.getTimestamp());
        String fingerprint = partnerUserKey.getFingerprint();
        pendingState = new PairingState(partnerKey, message.getTimestamp(), fingerprint);
        Log.d(TAG, "Own fingerprint:     " + getOwnFingerprint());
        Log.d(TAG, "Partner fingerprint: " + fingerprint);
        if (callback != null) callback.onPartnerKeyReceived(fingerprint);
    }

    private void handleAckMessage(PairingMessage message) {
        // Client: server confirmed — save partner key from pending state
        if (pendingState != null) {
            storage.savePartnerPublicKey(pendingState.getPartnerKey());

            // Save trusted device on client side too
            if (connectedDeviceAddress != null) {
                trustedStore.saveTrustedDevice(
                        connectedDeviceName != null ? connectedDeviceName : connectedDeviceAddress,
                        connectedDeviceAddress,
                        pendingState.getFingerprint()
                );
            }

            Log.d(TAG, "✅ Client: partner key saved after ACK");
            pendingState = null;
        }
        Log.d(TAG, "ACK received — session derivation next");
        if (callback != null) {
            callback.onAckReceived();
            callback.onPairingComplete();
        }
    }
}