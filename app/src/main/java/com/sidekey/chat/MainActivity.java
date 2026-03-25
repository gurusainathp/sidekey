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

import com.sidekey.chat.bluetooth.BluetoothCallback;
import com.sidekey.chat.bluetooth.BluetoothListener;
import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.pairing.PairingCallback;
import com.sidekey.chat.pairing.PairingManager;
import com.sidekey.chat.ui.PairingController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SideKey";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // UI
    private TextView   tvStatus;
    private TextView   tvLog;
    private ScrollView scrollLog;
    private Button     btnServer;
    private Button     btnClient;
    private Button     btnConfirm;
    private Button     btnCancel;
    private EditText   etMessage;
    private Button     btnSend;

    // Core
    private BluetoothService  bluetoothService;
    private PairingManager    pairingManager;
    private PairingController pairingController;
    private ChatManager       chatManager;
    private SessionStore      sessionStore;
    private BluetoothAdapter  bluetoothAdapter;

    private boolean isServer = false;

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                            tvStatus.setText("Bluetooth ready");
                            log("✅ Bluetooth enabled");
                        } else {
                            tvStatus.setText("Bluetooth is OFF");
                            log("⚠️ Bluetooth not enabled");
                        }
                    }
            );

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        sessionStore     = new SessionStore();

        pairingManager = new PairingManager(this);
        pairingManager.setCallback(pairingCallback);

        bluetoothService = new BluetoothService(this);
        bluetoothService.setListener(btListener);
        bluetoothService.setCallback(btCallback);

        pairingController = new PairingController(
                this, pairingManager, bluetoothService, sessionStore);

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
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtonListeners() {

        btnServer.setOnClickListener(v -> {
            if (!isBluetoothReady()) return;
            isServer = true;
            log("Role: SERVER");
            log("Your fingerprint: " + pairingManager.getOwnFingerprint());
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
            tvStatus.setText("Waiting for connection...");
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
            initChatManager();
        });

        btnCancel.setOnClickListener(v -> {
            log("→ Pairing cancelled");
            pairingController.onPairCancelled();
            setPairingConfirmVisible(false);
            tvStatus.setText("Pairing cancelled");
            btnServer.setEnabled(true);
            btnClient.setEnabled(true);
        });

        btnSend.setOnClickListener(v -> {
            if (chatManager == null) { log("❌ Not paired yet"); return; }
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            chatManager.sendMessage(text);
            etMessage.setText("");
        });
    }

    // -------------------------------------------------------------------------
    // BluetoothCallback — background thread
    // -------------------------------------------------------------------------

    private final BluetoothCallback btCallback = new BluetoothCallback() {
        @Override
        public void onConnected() {
            bluetoothService.setPairingInProgress(true);
            byte[] own = pairingManager.createOwnMessage();
            if (own != null) bluetoothService.send(own);
        }

        @Override
        public void onMessage(byte[] data) {
            if (chatManager != null && chatManager.handleIncoming(data)) return;
            pairingManager.handleIncoming(data);
        }

        @Override public void onDisconnected() { bluetoothService.setPairingInProgress(false); }
        @Override public void onError(String m) { bluetoothService.setPairingInProgress(false); }
    };

    // -------------------------------------------------------------------------
    // BluetoothListener — UI thread
    // -------------------------------------------------------------------------

    private final BluetoothListener btListener = new BluetoothListener() {

        @Override
        public void onConnected(BluetoothDevice device) {
            String name = getDeviceName(device);
            runOnUiThread(() -> {
                log("✅ Connected: " + name);
                tvStatus.setText("Connected: " + name);
                if (!isServer) {
                    log("Your fingerprint: " + pairingManager.getOwnFingerprint());
                    log("→ Waiting for server to confirm...");
                }
            });
        }

        @Override
        public void onDataReceived(byte[] data) {
            // Silent — avoids log spam on every packet
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                log("⚠️ Disconnected");
                tvStatus.setText("Disconnected");
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
                setPairingConfirmVisible(false);
                setChatInputVisible(false);
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                log("❌ BT: " + message);
                tvStatus.setText("Error — see log");
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
            });
        }
    };

    // -------------------------------------------------------------------------
    // PairingCallback
    // -------------------------------------------------------------------------

    private final PairingCallback pairingCallback = new PairingCallback() {

        @Override
        public void onPartnerKeyReceived(String partnerFingerprint) {
            pairingController.onFingerprintReady(partnerFingerprint);
            runOnUiThread(() -> {
                log("─────────────────────────");
                log("You:     " + pairingManager.getOwnFingerprint());
                log("Partner: " + partnerFingerprint);
                log("─────────────────────────");
                bluetoothService.setPairingInProgress(false);
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
        public void onAckReceived() {
            runOnUiThread(() -> {
                log("✅ Server confirmed — deriving session...");
                pairingController.onAckReceived();
                initChatManager();
            });
        }

        @Override
        public void onPairingComplete() {
            runOnUiThread(() -> {
                log("✅ Paired");
                tvStatus.setText("Paired ✅");
                setPairingConfirmVisible(false);
                if (chatManager == null) initChatManager();
            });
        }

        @Override
        public void onPairingError(String reason) {
            runOnUiThread(() -> {
                log("❌ Pairing error: " + reason);
                tvStatus.setText("Pairing failed");
                setPairingConfirmVisible(false);
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
            });
        }
    };

    // -------------------------------------------------------------------------
    // ChatCallback
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // ChatManager init — called after session is ready
    // -------------------------------------------------------------------------

    private void initChatManager() {
        if (chatManager != null) return;
        // ← KEY CHANGE: pass sessionStore, not pairingManager
        chatManager = new ChatManager(this, sessionStore, bluetoothService);
        chatManager.setCallback(chatCallback);
        Log.d(TAG, "ChatManager initialised");
        runOnUiThread(() -> {
            log("💬 Chat ready");
            setChatInputVisible(true);
        });
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Device picker
    // -------------------------------------------------------------------------

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
            log("❌ Security exception: " + e.getMessage()); return;
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
                    String name = getDeviceName(chosen);
                    log("CLIENT — connecting to: " + name);
                    log("Your fingerprint: " + pairingManager.getOwnFingerprint());
                    btnServer.setEnabled(false);
                    btnClient.setEnabled(false);
                    tvStatus.setText("Connecting to " + name + "...");
                    bluetoothService.connectToDevice(chosen);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Bluetooth state
    // -------------------------------------------------------------------------

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
        } else if (!bluetoothAdapter.isEnabled()) {
            tvStatus.setText("Bluetooth is OFF");
        } else {
            tvStatus.setText("Bluetooth ready");
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null ? name : device.getAddress();
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