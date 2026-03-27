package com.sidekey.chat.messaging;

import android.util.Log;

import java.util.LinkedList;

/**
 * MessageQueue — thread-safe singleton FIFO queue of outgoing messages.
 *
 * Messages are NOT persisted. If the app is killed, unsent messages are lost.
 * Persistence is a future phase.
 */
public class MessageQueue {

    private static final String TAG = "SideKey-MsgQueue";

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static MessageQueue instance;

    public static synchronized MessageQueue getInstance() {
        if (instance == null) instance = new MessageQueue();
        return instance;
    }

    private MessageQueue() {}

    // ── Storage ───────────────────────────────────────────────────────────────
    private final LinkedList<OutgoingMessage> queue = new LinkedList<>();

    // ── API ───────────────────────────────────────────────────────────────────

    public synchronized void enqueue(OutgoingMessage message) {
        queue.addLast(message);
        Log.d(TAG, "Queue add — size=" + queue.size());
    }

    /** Returns head without removing. */
    public synchronized OutgoingMessage peek() {
        return queue.peekFirst();
    }

    /** Removes and returns head, or null if empty. */
    public synchronized OutgoingMessage dequeue() {
        OutgoingMessage msg = queue.pollFirst();
        if (msg != null) Log.d(TAG, "Queue remove — size=" + queue.size());
        return msg;
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }

    /** Discards everything. Call only on intentional reset. */
    public synchronized void clear() {
        int was = queue.size();
        queue.clear();
        Log.d(TAG, "Queue cleared — discarded " + was + " message(s)");
    }
}