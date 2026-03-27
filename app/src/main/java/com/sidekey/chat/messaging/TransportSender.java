package com.sidekey.chat.messaging;

import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;

/**
 * TransportSender — the single exit point for all outgoing bytes.
 *
 * Currently wraps BluetoothService. In a future phase this abstraction
 * allows swapping to TCP/WebSocket without changing any caller.
 */
public class TransportSender {

    private static final String TAG = "SideKey-Transport";

    private final BluetoothService bluetoothService;

    public TransportSender(BluetoothService bluetoothService) {
        this.bluetoothService = bluetoothService;
    }

    /**
     * Sends raw payload bytes to the connected peer.
     * Framing is applied inside BluetoothService — callers pass plain payload.
     */
    public void send(byte[] payload) {
        Log.d(TAG, "TransportSender: send len=" + payload.length);
        bluetoothService.send(payload);
    }
}