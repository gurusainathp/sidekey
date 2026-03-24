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
 * Also exposes key material getters for ChatManager.
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
    // Build PAIR message — caller sends bytes via BluetoothService
    // -------------------------------------------------------------------------

    public byte[] createOwnMessage() {
        byte[] publicKey = keyManager.getPublicKey();
        if (publicKey == null) {
            Log.e(TAG, "createOwnMessage: public key null");
            return null;
        }
        PairingMessage message = new PairingMessage(
                PairingMessage.TYPE_PAIR, publicKey, System.currentTimeMillis());
        String json = JsonUtil.toJson(message);
        if (json == null) {
            Log.e(TAG, "createOwnMessage: serialisation failed");
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
        Log.d(TAG, "Incoming: " + json);

        PairingMessage message = JsonUtil.fromJson(json);
        if (message == null) {
            Log.e(TAG, "handleIncoming: parse failed");
            if (callback != null) callback.onPairingError("Could not parse incoming message");
            return;
        }

        switch (message.getType()) {
            case PairingMessage.TYPE_PAIR: handlePairMessage(message); break;
            case PairingMessage.TYPE_ACK:  handleAckMessage();         break;
            default:
                Log.w(TAG, "handleIncoming: unknown type — " + message.getType());
        }
    }

    // -------------------------------------------------------------------------
    // Confirm — only now do we persist
    // -------------------------------------------------------------------------

    public void confirmPairing() {
        if (pendingState == null) {
            Log.e(TAG, "confirmPairing: no pending state");
            return;
        }
        pendingState.confirm();
        storage.savePartnerPublicKey(pendingState.getPartnerKey());
        Log.d(TAG, "✅ Pairing confirmed — partner key saved");
        Log.d(TAG, "Fingerprint locked in: " + pendingState.getFingerprint());
        pendingState = null;
        if (callback != null) callback.onPairingComplete();
    }

    public void cancelPairing() {
        if (pendingState == null) return;
        Log.d(TAG, "Pairing cancelled — pending state discarded");
        pendingState = null;
    }

    // -------------------------------------------------------------------------
    // Build ACK — send after confirming
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
        return pendingState != null ? pendingState.getFingerprint() : null;
    }

    // -------------------------------------------------------------------------
    // Key material getters — used by ChatManager
    // -------------------------------------------------------------------------

    /**
     * Returns own private key for use in MessageCipher.
     * Never expose this outside the crypto layer.
     */
    public byte[] getOwnPrivateKey() {
        try {
            return keyManager.getPrivateKey();
        } catch (Exception e) {
            Log.e(TAG, "getOwnPrivateKey failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the saved partner public key, or null if not paired.
     */
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
        pendingState = new PairingState(partnerKey, message.getTimestamp(), fingerprint);
        Log.d(TAG, "Pending pairing fingerprint: " + fingerprint);
        if (callback != null) callback.onPartnerKeyReceived(fingerprint);
    }

    private void handleAckMessage() {
        Log.d(TAG, "✅ ACK received — partner confirmed pairing");
        if (callback != null) callback.onPairingComplete();
    }
}