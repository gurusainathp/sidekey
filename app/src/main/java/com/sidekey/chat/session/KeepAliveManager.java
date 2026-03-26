package com.sidekey.chat.session;

import android.util.Log;

/**
 * KeepAliveManager — skeleton for periodic ping/pong over the active connection.
 *
 * Not yet implemented — timers and real BT send will be added in a later phase.
 * Placeholder methods are here so callers can be wired now without breakage.
 *
 * Future plan:
 *   - send a small PING frame every N seconds via BluetoothService
 *   - expect a PONG within a timeout window
 *   - if no PONG → assume connection dead → trigger reconnect
 */
public class KeepAliveManager {

    private static final String TAG        = "SideKey-KeepAlive";
    private static final int    INTERVAL_S = 15; // future: ping every 15 seconds

    private boolean running = false;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the keepalive loop.
     * Currently logs only — timer will be added later.
     */
    public void start() {
        if (running) {
            Log.d(TAG, "KeepAlive: already running");
            return;
        }
        running = true;
        Log.d(TAG, "KeepAlive: start (interval=" + INTERVAL_S + "s)");
        // TODO: schedule periodic sendPing() using Handler or ScheduledExecutorService
    }

    /**
     * Stops the keepalive loop.
     */
    public void stop() {
        running = false;
        Log.d(TAG, "KeepAlive: stop");
        // TODO: cancel scheduled task
    }

    // -------------------------------------------------------------------------
    // Ping / pong
    // -------------------------------------------------------------------------

    /**
     * Sends a PING to the connected device.
     * Currently a no-op — will write a framed PING packet via BluetoothService.
     */
    public void sendPing() {
        if (!running) return;
        Log.d(TAG, "KeepAlive: ping →");
        // TODO: bluetoothService.send(FrameEncoder.encode(PING_BYTES))
    }

    /**
     * Called when a PONG is received from the connected device.
     */
    public void onPong() {
        Log.d(TAG, "KeepAlive: ← pong received");
        // TODO: reset timeout counter
    }

    public boolean isRunning() {
        return running;
    }
}