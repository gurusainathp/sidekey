package com.sidekey.chat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sidekey.chat.bluetooth.BluetoothCallback;
import com.sidekey.chat.bluetooth.BluetoothListener;
import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.connection.ConnectionState;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.messaging.SendDispatcher;
import com.sidekey.chat.messaging.TransportSender;
import com.sidekey.chat.pairing.AutoSessionStarter;
import com.sidekey.chat.pairing.PairingManager;
import com.sidekey.chat.ui.PairingController;
import com.sidekey.chat.ui.chat.ChatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity
        implements AutoSessionStarter.UICallback,
        ConnectionManager.ConnectionStateListener {

    private static final String TAG = "SideKey";
    private static final int    PERMISSION_REQUEST_CODE = 1001;

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextView   tvStatus;
    private TextView   tvLog;
    private ScrollView scrollLog;
    private Button     btnServer;
    private Button     btnClient;
    private Button     btnConfirm;
    private Button     btnCancel;

    // ── Core ──────────────────────────────────────────────────────────────────
    private BluetoothAdapter   bluetoothAdapter;
    private BluetoothService   bluetoothService;
    private PairingManager     pairingManager;
    private AutoSessionStarter autoSessionStarter;
    private PairingController  pairingController;
    private SessionStore       sessionStore;
    private ConnectionManager  connectionManager;

    private boolean isServer = false;

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        boolean on = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
                        tvStatus.setText(on ? "Bluetooth ready" : "Bluetooth is OFF");
                        log(on ? "✅ Bluetooth enabled" : "⚠️ Bluetooth not enabled");
                    });

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        bindViews();

        connectionManager = ConnectionManager.getInstance();
        connectionManager.setListener(this);

        sessionStore   = new SessionStore();
        pairingManager = new PairingManager(this);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothService = new BluetoothService(this);
        bluetoothService.setListener(btListener);

        TransportSender transportSender = new TransportSender(bluetoothService);
        SendDispatcher.getInstance().init(transportSender);

        autoSessionStarter = new AutoSessionStarter(
                this, pairingManager, bluetoothService, sessionStore, connectionManager);
        autoSessionStarter.setUICallback(this);
        bluetoothService.setCallback(autoSessionStarter);

        pairingController = new PairingController(
                pairingManager, bluetoothService, autoSessionStarter);

        setupButtonListeners();
        setPairingConfirmVisible(false);
        checkAndPromptBluetooth();
        requestRequiredPermissions();

        log("🔑 Your fingerprint: " + pairingManager.getOwnFingerprint());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothService.stop();
        sessionStore.clear();
        connectionManager.setListener(null);
    }

    // =========================================================================
    // ConnectionStateListener
    // =========================================================================

    @Override
    public void onStateChanged(ConnectionState newState) {
        runOnUiThread(() -> {
            switch (newState) {
                case DISCONNECTED:
                    tvStatus.setText("Disconnected");
                    btnServer.setEnabled(true);
                    btnClient.setEnabled(true);
                    setPairingConfirmVisible(false);
                    SendDispatcher.getInstance().onDisconnected();
                    break;
                case CONNECTING:  tvStatus.setText("Connecting...");  break;
                case CONNECTED:   tvStatus.setText("Connected");      break;
                case PAIRING:     tvStatus.setText("Pairing...");     break;
                case SESSION_READY:
                    tvStatus.setText("Paired ✅");
                    log("✅ Session ready — opening chat");
                    SendDispatcher.getInstance().onSessionReady();
                    break;
            }
        });
    }

    // =========================================================================
    // AutoSessionStarter.UICallback
    // =========================================================================

    @Override
    public void onFingerprintReady(String fingerprint) {
        runOnUiThread(() -> {
            log("─────────────────────────");
            log("You:     " + pairingManager.getOwnFingerprint());
            log("Partner: " + fingerprint);
            log("─────────────────────────");
            if (isServer) {
                log("→ Confirm if fingerprints match");
                tvStatus.setText("Verify fingerprints");
                setPairingConfirmVisible(true);
            } else {
                log("→ Waiting for server...");
                tvStatus.setText("Waiting for confirmation...");
            }
        });
    }

    @Override
    public void onSessionReady() {
        runOnUiThread(() -> {
            setPairingConfirmVisible(false);
            initChatAndLaunch();
        });
    }

    @Override
    public void onError(String reason) {
        runOnUiThread(() -> {
            log("❌ " + reason);
            tvStatus.setText("Error — see log");
            btnServer.setEnabled(true);
            btnClient.setEnabled(true);
        });
    }

    // =========================================================================
    // BluetoothListener — UI updates
    // =========================================================================

    private final BluetoothListener btListener = new BluetoothListener() {
        @Override
        public void onConnected(BluetoothDevice device) {
            String name = getDeviceName(device);
            runOnUiThread(() -> {
                connectionManager.setState(ConnectionState.CONNECTED);
                log("✅ Connected: " + name);
                if (!isServer) log("→ Waiting for server to confirm fingerprint...");
            });
        }
        @Override public void onDataReceived(byte[] data) { /* silent */ }
        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                log("⚠️ Disconnected");
                connectionManager.reset();
            });
        }
        @Override
        public void onError(String message) {
            runOnUiThread(() -> log("❌ BT: " + message));
        }
    };

    // =========================================================================
    // Chat init and launch
    // =========================================================================

    private void initChatAndLaunch() {
        // Create ChatManager and store in Application singleton
        ChatManager chatManager = new ChatManager(this, sessionStore);
        SideKeyApp.getInstance().setChatManager(chatManager);

        // Wire incoming bytes to ChatManager after session is ready
        bluetoothService.setCallback(new BluetoothCallback() {
            @Override public void onConnected() { autoSessionStarter.onConnected(); }
            @Override public void onMessage(byte[] data) {
                ChatManager cm = SideKeyApp.getInstance().getChatManager();
                if (cm == null || !cm.handleIncoming(data)) {
                    pairingManager.handleIncoming(data);
                }
            }
            @Override public void onDisconnected() { autoSessionStarter.onDisconnected(); }
            @Override public void onError(String m) { autoSessionStarter.onError(m); }
        });

        // Launch ChatActivity
        startActivity(new Intent(this, ChatActivity.class));
        log("💬 Chat screen opened");
    }

    // =========================================================================
    // Buttons
    // =========================================================================

    private void setupButtonListeners() {
        btnServer.setOnClickListener(v -> {
            if (!isBluetoothReady()) return;
            isServer = true;
            connectionManager.setState(ConnectionState.CONNECTING);
            log("Role: SERVER");
            log("Your fingerprint: " + pairingManager.getOwnFingerprint());
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
            bluetoothService.startServer();
        });

        btnClient.setOnClickListener(v -> {
            if (!isBluetoothReady()) return;
            isServer = false;
            showDevicePicker();
        });

        btnConfirm.setOnClickListener(v -> {
            log("→ Confirming pairing...");
            pairingController.onPairConfirmed();
            setPairingConfirmVisible(false);
        });

        btnCancel.setOnClickListener(v -> {
            log("→ Pairing cancelled");
            pairingController.onPairCancelled();
            setPairingConfirmVisible(false);
            connectionManager.reset();
        });
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private void bindViews() {
        tvStatus   = findViewById(R.id.tvStatus);
        tvLog      = findViewById(R.id.tvLog);
        scrollLog  = findViewById(R.id.scrollLog);
        btnServer  = findViewById(R.id.btnServer);
        btnClient  = findViewById(R.id.btnClient);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel  = findViewById(R.id.btnCancel);
    }

    private void setPairingConfirmVisible(boolean visible) {
        int v = (visible && isServer) ? View.VISIBLE : View.GONE;
        btnConfirm.setVisibility(v);
        btnCancel.setVisibility(v);
    }

    // =========================================================================
    // Device picker
    // =========================================================================

    private void showDevicePicker() {
        if (bluetoothAdapter == null) { log("Bluetooth not available"); return; }
        Set<BluetoothDevice> bonded;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                log("⚠️ BLUETOOTH_CONNECT not granted"); return;
            }
            bonded = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            log("❌ Security: " + e.getMessage()); return;
        }
        if (bonded == null || bonded.isEmpty()) {
            log("No bonded devices. Pair in Android Settings first."); return;
        }
        final List<BluetoothDevice> list  = new ArrayList<>(bonded);
        final String[]              names = new String[list.size()];
        for (int i = 0; i < list.size(); i++)
            names[i] = getDeviceName(list.get(i)) + "\n" + list.get(i).getAddress();

        new AlertDialog.Builder(this)
                .setTitle("Select device")
                .setItems(names, (d, which) -> {
                    BluetoothDevice chosen = list.get(which);
                    connectionManager.setState(ConnectionState.CONNECTING);
                    log("CLIENT — connecting to: " + getDeviceName(chosen));
                    btnServer.setEnabled(false);
                    btnClient.setEnabled(false);
                    tvStatus.setText("Connecting...");
                    bluetoothService.connectToDevice(chosen);
                })
                .setNegativeButton("Cancel", null).show();
    }

    // =========================================================================
    // Bluetooth state / permissions
    // =========================================================================

    private boolean isBluetoothReady() {
        if (bluetoothAdapter == null) { log("❌ Bluetooth not supported"); return false; }
        if (!bluetoothAdapter.isEnabled()) {
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return false;
        }
        if (!hasBluetoothPermissions()) { requestRequiredPermissions(); return false; }
        return true;
    }

    private void checkAndPromptBluetooth() {
        if (bluetoothAdapter == null) {
            tvStatus.setText("Bluetooth not supported");
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
        } else {
            tvStatus.setText(bluetoothAdapter.isEnabled() ? "Bluetooth ready" : "Bluetooth is OFF");
        }
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRequiredPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        else log("✅ Permissions granted");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean ok = true;
            for (int r : grantResults)
                if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            log(ok ? "✅ Permissions granted" : "⚠️ Some permissions denied");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String getDeviceName(BluetoothDevice device) {
        try {
            String n = device.getName();
            return n != null ? n : device.getAddress();
        } catch (SecurityException e) {
            return device.getAddress();
        }
    }

    private void log(String message) {
        runOnUiThread(() -> {
            tvLog.append(message + "\n");
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}