package com.sidekey.chat.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * BluetoothService — raw transport layer only.
 * No crypto. No pairing logic. Just moves bytes.
 */
public class BluetoothService {

    private static final String TAG = "SideKey-BT";

    private final BluetoothAdapter adapter;

    private BluetoothListener listener;  // UI callbacks (connected, disconnected, received)
    private BluetoothCallback callback;  // Logic callbacks (PairingManager, ChatManager)

    private AcceptThread    acceptThread;
    private ConnectThread   connectThread;
    private ConnectedThread connectedThread;

    public BluetoothService(Context context) {
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setListener(BluetoothListener listener) {
        this.listener = listener;
    }

    public void setCallback(BluetoothCallback callback) {
        this.callback = callback;
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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

    public void send(byte[] data) {
        ConnectedThread thread;
        synchronized (this) {
            thread = connectedThread;
        }
        if (thread == null) {
            Log.e(TAG, "send() called but not connected");
            if (listener != null) listener.onError("Not connected");
            if (callback != null) callback.onError("Not connected");
            return;
        }
        thread.write(data);
    }

    public synchronized void stop() {
        cancelAcceptThread();
        cancelConnectThread();
        cancelConnectedThread();
        Log.d(TAG, "BluetoothService stopped");
    }

    // -------------------------------------------------------------------------
    // Internal handoff — called when a live socket is ready
    // -------------------------------------------------------------------------

    private synchronized void startConnectedThread(BluetoothSocket socket) {
        cancelConnectedThread();

        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        BluetoothDevice remote = socket.getRemoteDevice();
        Log.d(TAG, "ConnectedThread started for: " + remote.getName());

        if (listener != null) listener.onConnected(remote);
        if (callback != null) callback.onConnected();
    }

    // -------------------------------------------------------------------------
    // Cancel helpers
    // -------------------------------------------------------------------------

    private void cancelAcceptThread() {
        if (acceptThread != null) { acceptThread.cancel();    acceptThread    = null; }
    }

    private void cancelConnectThread() {
        if (connectThread != null) { connectThread.cancel();  connectThread   = null; }
    }

    private void cancelConnectedThread() {
        if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
    }

    // -------------------------------------------------------------------------
    // AcceptThread
    // -------------------------------------------------------------------------

    private class AcceptThread extends Thread {

        private final BluetoothServerSocket serverSocket;

        AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = adapter.listenUsingRfcommWithServiceRecord(
                        BluetoothConstants.APP_NAME,
                        BluetoothConstants.APP_UUID
                );
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread: listenUsingRfcomm failed — " + e.getMessage());
                if (listener != null) listener.onError("Server socket failed: " + e.getMessage());
                if (callback != null) callback.onError("Server socket failed: " + e.getMessage());
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            if (serverSocket == null) return;
            Log.d(TAG, "AcceptThread: blocking on accept()...");

            BluetoothSocket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                Log.d(TAG, "AcceptThread: accept() ended — " + e.getMessage());
                return;
            }

            Log.d(TAG, "AcceptThread: accept() returned socket");
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

    // -------------------------------------------------------------------------
    // ConnectThread
    // -------------------------------------------------------------------------

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
                if (listener != null) listener.onError("Socket creation failed: " + e.getMessage());
                if (callback != null) callback.onError("Socket creation failed: " + e.getMessage());
            }
            socket = tmp;
        }

        @Override
        public void run() {
            if (socket == null) return;
            adapter.cancelDiscovery();

            Log.d(TAG, "ConnectThread: calling connect()...");
            try {
                socket.connect();
                Log.d(TAG, "ConnectThread: connect() succeeded");
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: connect() failed — " + e.getMessage());
                if (listener != null) listener.onError("Connection failed: " + e.getMessage());
                if (callback != null) callback.onError("Connection failed: " + e.getMessage());
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

    // -------------------------------------------------------------------------
    // ConnectedThread
    // -------------------------------------------------------------------------

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
                if (listener != null) listener.onError("Stream setup failed: " + e.getMessage());
                if (callback != null) callback.onError("Stream setup failed: " + e.getMessage());
            }
            inputStream  = tmpIn;
            outputStream = tmpOut;
        }

        @Override
        public void run() {
            Log.d(TAG, "ConnectedThread: read loop started");
            byte[] buffer = new byte[4096];

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        byte[] received = new byte[bytes];
                        System.arraycopy(buffer, 0, received, 0, bytes);
                        Log.d(TAG, "Received " + bytes + " bytes");
                        if (listener != null) listener.onDataReceived(received);
                        if (callback != null) callback.onMessage(received);
                    }
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

        void write(byte[] data) {
            try {
                outputStream.write(data);
                outputStream.flush();
                Log.d(TAG, "Sent " + data.length + " bytes");
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: write failed — " + e.getMessage());
                if (listener != null) listener.onError("Send failed: " + e.getMessage());
                if (callback != null) callback.onError("Send failed: " + e.getMessage());
            }
        }

        void cancel() {
            interrupt();
            try { if (socket != null) socket.close(); }
            catch (IOException e) { Log.w(TAG, "ConnectedThread cancel: " + e.getMessage()); }
        }
    }
}