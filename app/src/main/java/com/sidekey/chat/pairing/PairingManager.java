package com.sidekey.chat.pairing;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.crypto.KeyManager;
import com.sidekey.chat.crypto.SecureStorage;
import com.sidekey.chat.model.PairingMessage;
import com.sidekey.chat.model.UserKey;
import com.sidekey.chat.utils.JsonUtil;

/**
 * PairingManager — brain of the pairing flow.
 *
 * Sits above BluetoothService. Never touches Bluetooth directly.
 * BluetoothCallback.onMessage() feeds bytes in here.
 * Results go up via PairingCallback.
 */
public class PairingManager {

    private static final String TAG = "SideKey-Pairing";

    private final KeyManager    keyManager;
    private final SecureStorage storage;

    private PairingCallback callback;

    public PairingManager(Context context) {
        this.keyManager = new KeyManager(context);
        this.storage    = new SecureStorage(context);
        this.keyManager.init();
    }

    public void setCallback(PairingCallback callback) {
        this.callback = callback;
    }

    // -------------------------------------------------------------------------
    // Build our own PAIR message — call createOwnMessage() then send the bytes
    // via BluetoothService.send(). PairingManager never calls send() directly.
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

        Log.d(TAG, "Own PAIR message ready: " + message);
        return json.getBytes();
    }

    // -------------------------------------------------------------------------
    // Handle raw bytes arriving from BluetoothCallback.onMessage()
    // -------------------------------------------------------------------------

    public void handleIncoming(byte[] data) {
        String json = new String(data);
        Log.d(TAG, "Incoming raw JSON: " + json);

        PairingMessage message = JsonUtil.fromJson(json);
        if (message == null) {
            Log.e(TAG, "handleIncoming: JSON parse failed");
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
    // Build ACK to send after user confirms fingerprint
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

    public String getOwnFingerprint() {
        byte[] key = keyManager.getPublicKey();
        if (key == null) return "unknown";
        return new UserKey(key).getFingerprint();  // uses convenience constructor
    }

    public String getPartnerFingerprint() {
        byte[] key = storage.getPartnerPublicKey();
        if (key == null) return null;
        return new UserKey(key).getFingerprint();
    }

    // -------------------------------------------------------------------------
    // Internal handlers
    // -------------------------------------------------------------------------

    private void handlePairMessage(PairingMessage message) {
        byte[] partnerKey = message.getPublicKey();

        if (partnerKey == null || partnerKey.length != 32) {
            Log.e(TAG, "handlePairMessage: invalid key — length=" +
                    (partnerKey != null ? partnerKey.length : "null"));
            if (callback != null) callback.onPairingError("Partner sent invalid key");
            return;
        }

        storage.savePartnerPublicKey(partnerKey);

        UserKey partnerUserKey = new UserKey(partnerKey, message.getTimestamp());
        String fingerprint = partnerUserKey.getFingerprint();

        Log.d(TAG, "✅ Partner key saved");
        Log.d(TAG, "Fingerprint: " + fingerprint);

        if (callback != null) callback.onPartnerKeyReceived(fingerprint);
    }

    private void handleAckMessage() {
        Log.d(TAG, "✅ ACK received — pairing complete both sides");
        if (callback != null) callback.onPairingComplete();
    }
}