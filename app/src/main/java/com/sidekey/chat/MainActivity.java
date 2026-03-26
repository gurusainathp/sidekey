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
import android.widget.EditText;
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

import com.sidekey.chat.bluetooth.BluetoothListener;
import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.connection.ConnectionState;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.pairing.AutoSessionStarter;
import com.sidekey.chat.pairing.PairingManager;
import com.sidekey.chat.ui.PairingController;

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
    private EditText   etMessage;
    private Button     btnSend;

    // ── Core ─────────────────────────────────────────────────────────────────
    private BluetoothAdapter    bluetoothAdapter;
    private BluetoothService    bluetoothService;
    private PairingManager      pairingManager;
    private AutoSessionStarter  autoSessionStarter;
    private PairingController   pairingController;
    private ChatManager         chatManager;
    private SessionStore        sessionStore;
    private ConnectionManager   connectionManager;

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

        // ── State machine ─────────────────────────────────────────────────────
        connectionManager = ConnectionManager.getInstance();
        connectionManager.setListener(this);

        // ── Crypto + session ──────────────────────────────────────────────────
        sessionStore   = new SessionStore();
        pairingManager = new PairingManager(this);

        // ── Transport ─────────────────────────────────────────────────────────
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothService = new BluetoothService(this);
        bluetoothService.setListener(btListener);

        // ── AutoSessionStarter owns BluetoothCallback + PairingCallback ───────
        autoSessionStarter = new AutoSessionStarter(
                this, pairingManager, bluetoothService, sessionStore, connectionManager);
        autoSessionStarter.setUICallback(this);
        bluetoothService.setCallback(autoSessionStarter);

        // ── PairingController only used by server Confirm button ──────────────
        pairingController = new PairingController(
                pairingManager, bluetoothService, autoSessionStarter);

        setupButtonListeners();
        setChatInputVisible(false);
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
                    setChatInputVisible(false);
                    break;
                case CONNECTING:
                    tvStatus.setText("Connecting...");
                    break;
                case CONNECTED:
                    tvStatus.setText("Connected");
                    break;
                case PAIRING:
                    tvStatus.setText("Pairing...");
                    break;
                case SESSION_READY:
                    tvStatus.setText("Paired ✅ — chat ready");
                    log("✅ Session ready — chat open");
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
                log("→ Waiting for server to confirm...");
                tvStatus.setText("Waiting for confirmation...");
            }
        });
    }

    @Override
    public void onSessionReady() {
        runOnUiThread(() -> {
            setPairingConfirmVisible(false);
            initChatManager();
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
    // BluetoothListener — UI only
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

        @Override
        public void onDataReceived(byte[] data) {
            // Silent — avoids log spam, framing handles it
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                log("⚠️ Disconnected");
                connectionManager.reset();
                chatManager = null;
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> log("❌ BT: " + message));
        }
    };

    // =========================================================================
    // ChatCallback
    // =========================================================================

    private final ChatCallback chatCallback = new ChatCallback() {
        @Override
        public void onMessageReceived(String plaintext, long timestamp) {
            runOnUiThread(() -> log("💬 Partner: " + plaintext));
        }
        @Override
        public void onMessageSent(String plaintext) {
            runOnUiThread(() -> log("💬 You: " + plaintext));
        }
        @Override
        public void onError(String reason) {
            runOnUiThread(() -> log("❌ Chat: " + reason));
        }
    };

    // =========================================================================
    // Button setup
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
            // ChatManager init happens via onSessionReady() callback
        });

        btnCancel.setOnClickListener(v -> {
            log("→ Pairing cancelled");
            pairingController.onPairCancelled();
            setPairingConfirmVisible(false);
            connectionManager.reset();
        });

        btnSend.setOnClickListener(v -> {
            if (chatManager == null) { log("❌ Not paired yet"); return; }
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            chatManager.sendMessage(text);
            etMessage.setText("");
        });
    }

    // =========================================================================
    // ChatManager init — called from onSessionReady()
    // =========================================================================

    private void initChatManager() {
        if (chatManager != null) return;
        chatManager = new ChatManager(this, sessionStore, bluetoothService);
        chatManager.setCallback(chatCallback);
        Log.d(TAG, "ChatManager initialised");
        log("💬 Chat ready");
        setChatInputVisible(true);

        // Wire chat message routing — incoming bytes go to ChatManager first
        // Override the BT callback to handle chat after session is ready
        bluetoothService.setCallback(new com.sidekey.chat.bluetooth.BluetoothCallback() {
            @Override public void onConnected() { autoSessionStarter.onConnected(); }
            @Override public void onMessage(byte[] data) {
                if (!chatManager.handleIncoming(data)) {
                    pairingManager.handleIncoming(data);
                }
            }
            @Override public void onDisconnected() { autoSessionStarter.onDisconnected(); }
            @Override public void onError(String m) { autoSessionStarter.onError(m); }
        });
    }

    // =========================================================================
    // View helpers
    // =========================================================================

    private void bindViews() {
        tvStatus   = findViewById(R.id.tvStatus);
        tvLog      = findViewById(R.id.tvLog);
        scrollLog  = findViewById(R.id.scrollLog);
        btnServer  = findViewById(R.id.btnServer);
        btnClient  = findViewById(R.id.btnClient);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel  = findViewById(R.id.btnCancel);
        etMessage  = findViewById(R.id.etMessage);
        btnSend    = findViewById(R.id.btnSend);
    }

    private void setPairingConfirmVisible(boolean visible) {
        int v = (visible && isServer) ? View.VISIBLE : View.GONE;
        btnConfirm.setVisibility(v);
        btnCancel.setVisibility(v);
    }

    private void setChatInputVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        etMessage.setVisibility(v);
        btnSend.setVisibility(v);
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