package com.sidekey.chat.bluetooth;

/**
 * Callback interface for BluetoothService events.
 *
 * BluetoothService calls these methods when something happens.
 * PairingManager (and later ChatManager) will implement this.
 *
 * All callbacks may fire on a background thread.
 * Callers must use runOnUiThread() if they touch the UI.
 */
public interface BluetoothCallback {

    /** A remote device has connected and the socket is ready. */
    void onConnected();

    /** The connection was dropped or closed cleanly. */
    void onDisconnected();

    /** Raw bytes arrived from the connected device. */
    void onMessage(byte[] data);

    /** A transport-level error occurred. */
    void onError(String message);
}