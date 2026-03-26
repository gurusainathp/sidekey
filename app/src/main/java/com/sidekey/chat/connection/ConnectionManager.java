package com.sidekey.chat.connection;

import android.util.Log;

/**
 * ConnectionManager — singleton state machine for the connection lifecycle.
 *
 * Tracks the current ConnectionState and notifies a listener on every change.
 * No Bluetooth logic here — pure state tracking.
 *
 * Usage:
 *   ConnectionManager.getInstance().setState(ConnectionState.CONNECTED);
 *   ConnectionManager.getInstance().isSessionReady();
 */
public class ConnectionManager {

    private static final String TAG = "SideKey-ConnMgr";

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static ConnectionManager instance;

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) instance = new ConnectionManager();
        return instance;
    }

    private ConnectionManager() {
        currentState = ConnectionState.DISCONNECTED;
        Log.d(TAG, "ConnectionManager created — initial state: DISCONNECTED");
    }

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    public interface ConnectionStateListener {
        void onStateChanged(ConnectionState newState);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private ConnectionState         currentState;
    private ConnectionStateListener listener;

    public void setListener(ConnectionStateListener listener) {
        this.listener = listener;
    }

    public void setState(ConnectionState newState) {
        if (newState == currentState) return; // no change — skip log spam

        ConnectionState old = currentState;
        currentState = newState;

        ConnectionState.logStateChange(old, newState);

        if (listener != null) listener.onStateChanged(newState);
    }

    public ConnectionState getState() {
        return currentState;
    }

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    public boolean isConnected() {
        return currentState == ConnectionState.CONNECTED
                || currentState == ConnectionState.PAIRING
                || currentState == ConnectionState.SESSION_READY;
    }

    public boolean isSessionReady() {
        return currentState == ConnectionState.SESSION_READY;
    }

    public boolean isPairing() {
        return currentState == ConnectionState.PAIRING;
    }

    // -------------------------------------------------------------------------
    // Reset — call on socket close
    // -------------------------------------------------------------------------

    public void reset() {
        setState(ConnectionState.DISCONNECTED);
    }
}