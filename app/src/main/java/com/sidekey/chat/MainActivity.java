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
    private BluetoothAdapter  bluetoothAdapter;

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                            tvStatus.setText("Bluetooth ready");
                            log("✅ Bluetooth enabled");
                        } else {
                            tvStatus.setText("Bluetooth is OFF — cannot pair");
                            log("⚠️ Bluetooth was not enabled");
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

        pairingManager = new PairingManager(this);
        pairingManager.setCallback(pairingCallback);

        bluetoothService = new BluetoothService(this);
        bluetoothService.setListener(btListener);
        bluetoothService.setCallback(btCallback);

        pairingController = new PairingController(pairingManager, bluetoothService);

        setupButtonListeners();
        setChatInputVisible(false);
        setPairingConfirmVisible(false);
        checkAndPromptBluetooth();
        requestRequiredPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothService.stop();
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
    // Button listeners
    // -------------------------------------------------------------------------

    private void setupButtonListeners() {

        btnServer.setOnClickListener(v -> {
            if (!isBluetoothReady()) return;
            log("Role: SERVER — waiting for connection...");
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
            tvStatus.setText("Waiting for connection...");
            bluetoothService.startServer();
        });

        btnClient.setOnClickListener(v -> {
            if (!isBluetoothReady()) return;
            showDevicePicker();
        });

        btnConfirm.setOnClickListener(v -> {
            log("→ Confirming pairing...");
            pairingController.onPairConfirmed();
            setPairingConfirmVisible(false);

            // Init ChatManager now that pairing is confirmed
            initChatManager();
        });

        btnCancel.setOnClickListener(v -> {
            log("→ Pairing cancelled");
            pairingController.onPairCancelled();
            setPairingConfirmVisible(false);
            tvStatus.setText("Pairing cancelled");
        });

        btnSend.setOnClickListener(v -> {
            if (chatManager == null) {
                log("❌ Not paired yet — cannot send");
                return;
            }
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
            byte[] ownMessage = pairingManager.createOwnMessage();
            if (ownMessage != null) {
                bluetoothService.send(ownMessage);
                Log.d(TAG, "Sent own pairing message");
            }
        }

        @Override
        public void onMessage(byte[] data) {
            // Try ChatManager first (only if it's been initialised after pairing)
            if (chatManager != null && chatManager.handleIncoming(data)) {
                return; // consumed as a chat message
            }
            // Otherwise hand to PairingManager
            pairingManager.handleIncoming(data);
        }

        @Override
        public void onDisconnected() {
            bluetoothService.setPairingInProgress(false);
        }

        @Override
        public void onError(String message) {
            bluetoothService.setPairingInProgress(false);
        }
    };

    // -------------------------------------------------------------------------
    // BluetoothListener — UI thread via runOnUiThread
    // -------------------------------------------------------------------------

    private final BluetoothListener btListener = new BluetoothListener() {

        @Override
        public void onConnected(BluetoothDevice device) {
            String name = getDeviceName(device);
            runOnUiThread(() -> {
                log("✅ Connected to: " + name);
                tvStatus.setText("Connected: " + name);
            });
        }

        @Override
        public void onDataReceived(byte[] data) {
            runOnUiThread(() -> log("📨 Received " + data.length + " bytes"));
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
                log("❌ BT Error: " + message);
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
        public void onPartnerKeyReceived(String fingerprint) {
            pairingController.onFingerprintReady(fingerprint);
            runOnUiThread(() -> {
                log("🔑 Partner key received (NOT saved yet)");
                log("Fingerprint: " + fingerprint);
                log("→ Confirm ONLY if partner shows the same fingerprint");
                tvStatus.setText("Verify: " + fingerprint);
                setPairingConfirmVisible(true);
                bluetoothService.setPairingInProgress(false);
            });
        }

        @Override
        public void onPairingComplete() {
            runOnUiThread(() -> {
                log("✅ Pairing complete — partner key saved");
                tvStatus.setText("Paired ✅");
                setPairingConfirmVisible(false);
                // If ChatManager isn't ready yet (other side confirmed first), init now
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
            runOnUiThread(() -> {
                log("💬 Partner: " + plaintext);
            });
        }

        @Override
        public void onMessageSent(String plaintext) {
            runOnUiThread(() -> log("💬 You: " + plaintext));
        }

        @Override
        public void onError(String reason) {
            runOnUiThread(() -> log("❌ Chat error: " + reason));
        }
    };

    // -------------------------------------------------------------------------
    // ChatManager init — called after pairing confirmed
    // -------------------------------------------------------------------------

    private void initChatManager() {
        chatManager = new ChatManager(this, pairingManager, bluetoothService);
        chatManager.setCallback(chatCallback);
        Log.d(TAG, "ChatManager initialised");
        runOnUiThread(() -> {
            log("💬 Chat ready — type a message below");
            setChatInputVisible(true);
        });
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void setPairingConfirmVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
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
                log("⚠️ BLUETOOTH_CONNECT not granted");
                return;
            }
            bonded = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            log("❌ Security exception: " + e.getMessage());
            return;
        }

        if (bonded == null || bonded.isEmpty()) {
            log("No bonded devices.\nPair phones in Android Settings first.");
            return;
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
                    log("Connecting to: " + name);
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
            log("⚠️ Bluetooth OFF — requesting enable...");
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return false;
        }
        if (!hasBluetoothPermissions()) {
            log("⚠️ Permissions missing — requesting...");
            requestRequiredPermissions();
            return false;
        }
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
        else
            log("✅ All permissions granted");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean ok = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            log(ok ? "✅ All permissions granted" : "⚠️ Some permissions denied");
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