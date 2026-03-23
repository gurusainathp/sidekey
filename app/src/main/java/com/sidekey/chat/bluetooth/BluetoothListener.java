package com.sidekey.chat.bluetooth;

import android.bluetooth.BluetoothDevice;

/**
 * Callback interface for BluetoothService events.
 * BluetoothService only deals in raw bytes — crypto happens above this layer.
 */
public interface BluetoothListener {

    /** A remote device has connected (either as server or client). */
    void onConnected(BluetoothDevice device);

    /** Raw bytes received from the connected device. */
    void onDataReceived(byte[] data);

    /** The connection was lost or closed. */
    void onDisconnected();

    /** Any transport-level error. */
    void onError(String message);
}