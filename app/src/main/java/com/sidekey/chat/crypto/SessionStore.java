package com.sidekey.chat.crypto;

import android.util.Log;

/**
 * SessionStore — holds the active session key in memory only.
 *
 * SECURITY RULE: session key is NEVER written to SharedPreferences,
 * files, or any persistent store. It lives only for the duration of
 * the app session. On restart, initSession() must be called again.
 *
 * Thread-safe via volatile + synchronized clear().
 */
public class SessionStore {

    private static final String TAG = "SideKey-SessionStore";

    private volatile byte[]  sessionKey = null;
    private volatile boolean ready      = false;

    public void setSessionKey(byte[] key) {
        if (key == null || key.length != 32) {
            Log.e(TAG, "setSessionKey: invalid key — must be 32 bytes");
            return;
        }
        this.sessionKey = key;
        this.ready      = true;
        Log.d(TAG, "SessionStore: key stored");
        Log.d(TAG, "SessionStore: ready = true");
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Wipes the session key from memory.
     * Call on disconnect or app pause if security requires it.
     */
    public synchronized void clear() {
        if (sessionKey != null) {
            // Overwrite before releasing reference — reduces window for key leaks
            java.util.Arrays.fill(sessionKey, (byte) 0);
            sessionKey = null;
        }
        ready = false;
        Log.d(TAG, "SessionStore: cleared");
    }
}