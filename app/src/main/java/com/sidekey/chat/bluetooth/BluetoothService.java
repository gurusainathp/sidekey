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
 * BluetoothService — transport layer only.
 *
 * Responsibilities:
 *   - Open server socket and wait for incoming connections (AcceptThread)
 *   - Connect to a remote device as client (ConnectThread)
 *   - Send and receive raw byte[] over an open socket (ConnectedThread)
 *
 * No crypto here. No pairing logic here.
 * This layer only moves bytes.
 */
public class BluetoothService {

    private static final String TAG = "SideKey-BT";

    private final Context          context;
    private final BluetoothAdapter adapter;

    private BluetoothListener listener;

    private AcceptThread    acceptThread;
    private ConnectThread   connectThread;
    private ConnectedThread connectedThread;

    public BluetoothService(Context context) {
        this.context = context;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setListener(BluetoothListener listener) {
        this.listener = listener;
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Start server mode — wait for a remote device to connect.
     * Call this on the device that will act as "host" during pairing.
     */
    public void startServer() {
        stopAllThreads();
        acceptThread = new AcceptThread();
        acceptThread.start();
        Log.d(TAG, "Server started — waiting for connection");
    }

    /**
     * Connect to a specific remote device as client.
     * Call this on the device that will "join" during pairing.
     */
    public void connectToDevice(BluetoothDevice device) {
        stopAllThreads();
        connectThread = new ConnectThread(device);
        connectThread.start();
        Log.d(TAG, "Connecting to: " + device.getName() + " [" + device.getAddress() + "]");
    }

    /**
     * Send raw bytes to the connected device.
     * Must call only after onConnected() fires.
     */
    public void send(byte[] data) {
        if (connectedThread == null) {
            Log.e(TAG, "send() called but no active connection");
            if (listener != null) listener.onError("Not connected");
            return;
        }
        connectedThread.write(data);
    }

    /** Close all threads and sockets cleanly. */
    public void stop() {
        stopAllThreads();
        Log.d(TAG, "BluetoothService stopped");
    }

    // -------------------------------------------------------------------------
    // Internal — called when a socket is ready (from either thread)
    // -------------------------------------------------------------------------

    private synchronized void onSocketConnected(BluetoothSocket socket) {
        // Stop whichever thread opened the socket — ConnectedThread takes over
        if (acceptThread  != null) { acceptThread.cancel();  acceptThread  = null; }
        if (connectThread != null) { connectThread.cancel(); connectThread = null; }

        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        if (listener != null) listener.onConnected(socket.getRemoteDevice());
        Log.d(TAG, "Connected to: " + socket.getRemoteDevice().getName());
    }

    private synchronized void stopAllThreads() {
        if (acceptThread    != null) { acceptThread.cancel();    acceptThread    = null; }
        if (connectThread   != null) { connectThread.cancel();   connectThread   = null; }
        if (connectedThread != null) { connectedThread.cancel(); connectedThread = null; }
    }

    // -------------------------------------------------------------------------
    // AcceptThread — server side
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
                Log.e(TAG, "AcceptThread: failed to open server socket", e);
                if (listener != null) listener.onError("Server socket failed: " + e.getMessage());
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            if (serverSocket == null) return;

            BluetoothSocket socket = null;
            try {
                // Blocking — waits until client connects
                socket = serverSocket.accept();
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread: accept() failed", e);
                if (listener != null) listener.onError("Accept failed: " + e.getMessage());
                return;
            }

            // Connection accepted — hand off to ConnectedThread
            if (socket != null) {
                onSocketConnected(socket);
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        }

        void cancel() {
            try { if (serverSocket != null) serverSocket.close(); }
            catch (IOException e) { Log.e(TAG, "AcceptThread cancel failed", e); }
        }
    }

    // -------------------------------------------------------------------------
    // ConnectThread — client side
    // -------------------------------------------------------------------------

    private class ConnectThread extends Thread {

        private final BluetoothSocket  socket;
        private final BluetoothDevice  device;

        ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(BluetoothConstants.APP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: failed to create socket", e);
                if (listener != null) listener.onError("Socket creation failed: " + e.getMessage());
            }
            socket = tmp;
        }

        @Override
        public void run() {
            if (socket == null) return;

            // Stop discovery before connecting — saves battery and improves reliability
            adapter.cancelDiscovery();

            try {
                socket.connect();
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: connect() failed", e);
                if (listener != null) listener.onError("Connection failed: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}
                return;
            }

            onSocketConnected(socket);
        }

        void cancel() {
            try { if (socket != null) socket.close(); }
            catch (IOException e) { Log.e(TAG, "ConnectThread cancel failed", e); }
        }
    }

    // -------------------------------------------------------------------------
    // ConnectedThread — handles an open socket (both sides end up here)
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
                Log.e(TAG, "ConnectedThread: stream setup failed", e);
                if (listener != null) listener.onError("Stream setup failed: " + e.getMessage());
            }

            inputStream  = tmpIn;
            outputStream = tmpOut;
        }

        @Override
        public void run() {
            // Read loop — stays alive as long as connection is open
            byte[] buffer = new byte[4096];
            int    bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        byte[] received = new byte[bytes];
                        System.arraycopy(buffer, 0, received, 0, bytes);
                        Log.d(TAG, "Received " + bytes + " bytes");
                        if (listener != null) listener.onDataReceived(received);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "ConnectedThread: read failed — connection lost", e);
                    if (listener != null) listener.onDisconnected();
                    break;
                }
            }
        }

        void write(byte[] data) {
            try {
                outputStream.write(data);
                outputStream.flush();
                Log.d(TAG, "Sent " + data.length + " bytes");
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: write failed", e);
                if (listener != null) listener.onError("Send failed: " + e.getMessage());
            }
        }

        void cancel() {
            try { if (socket != null) socket.close(); }
            catch (IOException e) { Log.e(TAG, "ConnectedThread cancel failed", e); }
        }
    }
}