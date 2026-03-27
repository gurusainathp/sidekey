package com.sidekey.chat.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import com.sidekey.chat.protocol.FrameDecoder;
import com.sidekey.chat.protocol.FrameEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * BluetoothService — raw transport layer only.
 *
 * Responsibilities:
 *   - Manage RFCOMM socket lifecycle (AcceptThread / ConnectThread)
 *   - Frame outgoing bytes via FrameEncoder
 *   - Decode incoming frames via FrameDecoder
 *   - Fire BluetoothListener (UI) and BluetoothCallback (logic) on events
 *
 * BluetoothService knows NOTHING about:
 *   - Crypto
 *   - Message queue
 *   - SendDispatcher
 *   - Pairing
 *   - Session
 *
 * It only moves bytes. SendDispatcher decides WHEN to send.
 */
public class BluetoothService {

    private static final String TAG = "SideKey-BT";

    private final BluetoothAdapter adapter;

    private BluetoothListener listener;
    private BluetoothCallback callback;

    private AcceptThread    acceptThread;
    private ConnectThread   connectThread;
    private ConnectedThread connectedThread;

    private volatile boolean pairingInProgress = false;

    public BluetoothService(Context context) {
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setListener(BluetoothListener listener) { this.listener = listener; }
    public void setCallback(BluetoothCallback callback) { this.callback = callback; }
    public void setPairingInProgress(boolean b)         { this.pairingInProgress = b; }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public synchronized void startServer() {
        cancelAcceptThread();
        cancelConnectThread();
        acceptThread = new AcceptThread();
        acceptThread.start();
        Log.d(TAG, "Server started — waiting for connection");
    }

    public synchronized void connectToDevice(BluetoothDevice device) {
        cancelAcceptThread();
        cancelConnectThread();
        connectThread = new ConnectThread(device);
        connectThread.start();
        Log.d(TAG, "Connecting to: " + device.getName() + " [" + device.getAddress() + "]");
    }

    /**
     * Sends payload as a length-prefixed frame.
     * Called by TransportSender only — never by ChatManager directly.
     */
    public void send(byte[] payload) {
        ConnectedThread thread;
        synchronized (this) { thread = connectedThread; }
        if (thread == null) {
            Log.e(TAG, "send() — not connected");
            notifyError("Not connected");
            return;
        }
        thread.write(payload);
    }

    public synchronized void stop() {
        cancelAcceptThread();
        cancelConnectThread();
        cancelConnectedThread();
        Log.d(TAG, "BluetoothService stopped");
    }

    // ── Handoff ───────────────────────────────────────────────────────────────

    private synchronized void startConnectedThread(BluetoothSocket socket) {
        cancelConnectedThread();
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        BluetoothDevice remote = socket.getRemoteDevice();
        Log.d(TAG, "ConnectedThread started for: " + remote.getName());
        if (listener != null) listener.onConnected(remote);
        if (callback != null) callback.onConnected();
    }

    // ── Cancel helpers ────────────────────────────────────────────────────────

    private void cancelAcceptThread() {
        if (acceptThread != null)    { acceptThread.cancel();    acceptThread    = null; }
    }
    private void cancelConnectThread() {
        if (connectThread != null)   { connectThread.cancel();   connectThread   = null; }
    }
    private void cancelConnectedThread() {
        if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
    }
    private void notifyError(String msg) {
        if (listener != null) listener.onError(msg);
        if (callback != null) callback.onError(msg);
    }

    // ── AcceptThread ──────────────────────────────────────────────────────────

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = adapter.listenUsingRfcommWithServiceRecord(
                        BluetoothConstants.APP_NAME, BluetoothConstants.APP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread: listen failed — " + e.getMessage());
                notifyError("Server socket failed: " + e.getMessage());
            }
            serverSocket = tmp;
        }

        @Override public void run() {
            if (serverSocket == null) return;
            Log.d(TAG, "AcceptThread: blocking on accept()...");
            BluetoothSocket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                Log.d(TAG, "AcceptThread: accept() ended — " + e.getMessage());
                return;
            }
            startConnectedThread(socket);
            try { serverSocket.close(); } catch (IOException e) {
                Log.w(TAG, "AcceptThread: serverSocket.close() — " + e.getMessage());
            }
            synchronized (BluetoothService.this) {
                if (acceptThread == this) acceptThread = null;
            }
        }

        void cancel() {
            try { if (serverSocket != null) serverSocket.close(); }
            catch (IOException e) { Log.w(TAG, "AcceptThread cancel: " + e.getMessage()); }
        }
    }

    // ── ConnectThread ─────────────────────────────────────────────────────────

    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(BluetoothConstants.APP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: createRfcomm failed — " + e.getMessage());
                notifyError("Socket creation failed: " + e.getMessage());
            }
            socket = tmp;
        }

        @Override public void run() {
            if (socket == null) return;
            adapter.cancelDiscovery();
            try {
                socket.connect();
                Log.d(TAG, "ConnectThread: connect() succeeded");
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: connect() failed — " + e.getMessage());
                notifyError("Connection failed: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}
                return;
            }
            startConnectedThread(socket);
            synchronized (BluetoothService.this) {
                if (connectThread == this) connectThread = null;
            }
        }

        void cancel() {
            try { if (socket != null) socket.close(); }
            catch (IOException e) { Log.w(TAG, "ConnectThread cancel: " + e.getMessage()); }
        }
    }

    // ── ConnectedThread ───────────────────────────────────────────────────────

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream     inputStream;
        private final OutputStream    outputStream;

        ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream  tmpIn  = null;
            OutputStream tmpOut = null;
            try {
                tmpIn  = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: stream setup failed — " + e.getMessage());
                notifyError("Stream setup failed: " + e.getMessage());
            }
            inputStream  = tmpIn;
            outputStream = tmpOut;
        }

        @Override public void run() {
            Log.d(TAG, "ConnectedThread: read loop started");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    byte[] payload = FrameDecoder.decode(inputStream);
                    Log.d(TAG, "BT: frame received len=" + payload.length);
                    if (listener != null) listener.onDataReceived(payload);
                    if (callback != null) callback.onMessage(payload);
                    // ← SendDispatcher is NOT called here. Receive ≠ trigger send.
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Log.e(TAG, "ConnectedThread: read error — " + e.getMessage());
                        if (listener != null) listener.onDisconnected();
                        if (callback != null) callback.onDisconnected();
                    }
                    break;
                }
            }
            Log.d(TAG, "ConnectedThread: read loop ended");
        }

        void write(byte[] payload) {
            try {
                byte[] frame = FrameEncoder.encode(payload);
                Log.d(TAG, "BT: sending frame len=" + frame.length);
                outputStream.write(frame);
                outputStream.flush();
                // ← SendDispatcher is NOT called here.
                // Dispatcher decides WHEN to send. BT just executes the write.
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: write failed — " + e.getMessage());
                notifyError("Send failed: " + e.getMessage());
            }
        }

        void cancel() {
            interrupt();
            try { if (socket != null) socket.close(); }
            catch (IOException e) { Log.w(TAG, "ConnectedThread cancel: " + e.getMessage()); }
        }
    }
}