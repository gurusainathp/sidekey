package com.sidekey.chat.messaging;

import android.util.Log;

import com.sidekey.chat.connection.ConnectionManager;

/**
 * SendDispatcher — sends queued messages only when SESSION_READY.
 *
 * ✅ Correct flow (per spec):
 *
 *   ChatManager.sendMessage()
 *     → MessageQueue.enqueue()
 *     → SendDispatcher.onNewMessage()
 *         → trySend()
 *
 *   trySend():
 *     if not SESSION_READY → log "waiting" → return
 *     if queue empty       → return
 *     msg = queue.peek()
 *     TransportSender.send(msg.payload)
 *     queue.dequeue()
 *     ← STOP. Do not loop. Do not recurse.
 *
 *   When session becomes ready:
 *     SendDispatcher.onSessionReady()
 *       → drainQueue() — sends all pre-queued messages in order
 *
 * ❌ trySend() is NEVER called from BluetoothService or on message receive.
 *
 * Re-entry guard (boolean sending) prevents double-send if somehow
 * trySend is called while a send is in progress.
 */
public class SendDispatcher {

    private static final String TAG = "SideKey-Dispatcher";

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static SendDispatcher instance;

    public static synchronized SendDispatcher getInstance() {
        if (instance == null) instance = new SendDispatcher();
        return instance;
    }

    private SendDispatcher() {}

    // ── Dependencies ──────────────────────────────────────────────────────────
    private TransportSender transport;
    private final MessageQueue queue = MessageQueue.getInstance();

    /** Must be called once before any send, e.g. in MainActivity.onCreate(). */
    public void init(TransportSender transport) {
        this.transport = transport;
        Log.d(TAG, "SendDispatcher initialised");
    }

    // ── Re-entry guard ────────────────────────────────────────────────────────
    private boolean sending = false;

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Called by ChatManager after enqueueing a new message.
     * Tries to send the head of the queue if session is ready.
     */
    public synchronized void onNewMessage() {
        trySend();
    }

    /**
     * Called when session becomes SESSION_READY.
     * Drains all pre-queued messages that were waiting.
     */
    public synchronized void onSessionReady() {
        Log.d(TAG, "Dispatcher: session ready — draining queue (" + queue.size() + " message(s))");
        drainQueue();
    }

    /**
     * Called on disconnect. Queue is preserved — messages hold for reconnect.
     */
    public synchronized void onDisconnected() {
        sending = false; // reset guard in case a send was in progress
        Log.d(TAG, "Dispatcher: disconnected — "
                + queue.size() + " message(s) held in queue");
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    /**
     * Sends ONE message from the head of the queue.
     *
     * Returns immediately if:
     *   - session not ready
     *   - queue is empty
     *   - a send is already in progress (re-entry guard)
     */
    private void trySend() {
        if (transport == null) {
            Log.e(TAG, "Dispatcher: not initialised — call init() first");
            return;
        }

        if (!ConnectionManager.getInstance().isSessionReady()) {
            if (!queue.isEmpty()) {
                Log.d(TAG, "Dispatcher: waiting for session — "
                        + queue.size() + " message(s) queued");
            }
            return;
        }

        if (queue.isEmpty()) {
            Log.d(TAG, "Dispatcher: queue empty");
            return;
        }

        if (sending) {
            Log.d(TAG, "Dispatcher: send already in progress — skipping");
            return;
        }

        sending = true;

        OutgoingMessage msg = queue.peek();
        if (msg == null) {
            sending = false;
            return;
        }

        Log.d(TAG, "Dispatcher: sending message (" + msg.getPayload().length + " bytes)");
        transport.send(msg.getPayload());
        queue.dequeue();

        sending = false;
        // ← STOP. No loop. No recursive call.
        // Next message sends when next onNewMessage() fires.
    }

    /**
     * Drains all queued messages in order.
     * Called ONLY from onSessionReady() — never from trySend().
     * Safe because it only runs when session state just changed,
     * not on every message receive.
     */
    private void drainQueue() {
        while (!queue.isEmpty()) {
            OutgoingMessage msg = queue.peek();
            if (msg == null) break;

            Log.d(TAG, "Dispatcher: draining — sending (" + msg.getPayload().length + " bytes)");
            transport.send(msg.getPayload());
            queue.dequeue();
        }

        if (queue.isEmpty()) {
            Log.d(TAG, "Dispatcher: drain complete — queue empty");
        }
    }
}