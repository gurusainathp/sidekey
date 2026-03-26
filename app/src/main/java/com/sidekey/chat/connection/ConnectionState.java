package com.sidekey.chat.connection;

import android.util.Log;

/**
 * ConnectionState — all possible states of the SideKey connection lifecycle.
 *
 * DISCONNECTED   → no Bluetooth socket
 * CONNECTING     → socket attempt in progress
 * CONNECTED      → socket open, no session yet
 * PAIRING        → key exchange in progress
 * SESSION_READY  → session key derived, chat available
 */
public enum ConnectionState {

    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PAIRING,
    SESSION_READY;

    private static final String TAG = "SideKey-ConnState";

    public static void logStateChange(ConnectionState from, ConnectionState to) {
        Log.d(TAG, "ConnectionState → " + from.name() + " → " + to.name());
    }
}