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

    // Core
    private BluetoothService   bluetoothService;
    private PairingManager     pairingManager;
    private PairingController  pairingController;
    private BluetoothAdapter   bluetoothAdapter;

    // Launcher for the system "enable Bluetooth" dialog
    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                            tvStatus.setText("Bluetooth ready");
                            log("✅ Bluetooth enabled");
                        } else {
                            tvStatus.setText("Bluetooth is OFF — cannot pair");
                            log("⚠️ Bluetooth was not enabled — pairing unavailable");
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
        });

        btnCancel.setOnClickListener(v -> {
            log("→ Pairing cancelled by user");
            pairingController.onPairCancelled();
            setPairingConfirmVisible(false);
            tvStatus.setText("Pairing cancelled");
        });
    }

    // -------------------------------------------------------------------------
    // BluetoothCallback — background thread, no UI calls here
    // -------------------------------------------------------------------------

    private final BluetoothCallback btCallback = new BluetoothCallback() {

        @Override
        public void onConnected() {
            // Mark pairing in progress on the service (for logging)
            bluetoothService.setPairingInProgress(true);

            // Send our own public key immediately after connection
            byte[] ownMessage = pairingManager.createOwnMessage();
            if (ownMessage != null) {
                bluetoothService.send(ownMessage);
                Log.d(TAG, "Sent own pairing message");
            } else {
                Log.e(TAG, "Failed to build own pairing message");
            }
        }

        @Override
        public void onMessage(byte[] data) {
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
    // BluetoothListener — UI updates, must use runOnUiThread
    // -------------------------------------------------------------------------

    private final BluetoothListener btListener = new BluetoothListener() {

        @Override
        public void onConnected(BluetoothDevice device) {
            String deviceName = getDeviceName(device);
            runOnUiThread(() -> {
                log("✅ Connected to: " + deviceName);
                tvStatus.setText("Connected: " + deviceName);
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
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                log("❌ Error: " + message);
                tvStatus.setText("Error — see log");
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
            });
        }
    };

    // -------------------------------------------------------------------------
    // PairingCallback — may fire on background thread
    // -------------------------------------------------------------------------

    private final PairingCallback pairingCallback = new PairingCallback() {

        @Override
        public void onPartnerKeyReceived(String fingerprint) {
            // Key is NOT saved yet — waiting for user confirmation
            pairingController.onFingerprintReady(fingerprint);

            runOnUiThread(() -> {
                log("🔑 Partner key received (NOT saved yet)");
                log("Fingerprint: " + fingerprint);
                log("→ Confirm only if your partner shows the same fingerprint");
                tvStatus.setText("Verify fingerprint: " + fingerprint);
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
    // Show / hide confirm + cancel buttons
    // -------------------------------------------------------------------------

    private void setPairingConfirmVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        btnConfirm.setVisibility(visibility);
        btnCancel.setVisibility(visibility);
    }

    // -------------------------------------------------------------------------
    // Device picker
    // -------------------------------------------------------------------------

    private void showDevicePicker() {
        if (bluetoothAdapter == null) {
            log("Bluetooth not available");
            return;
        }

        Set<BluetoothDevice> bonded;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    log("⚠️ BLUETOOTH_CONNECT permission not granted");
                    return;
                }
            }
            bonded = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            log("❌ Security exception reading bonded devices: " + e.getMessage());
            return;
        }

        if (bonded == null || bonded.isEmpty()) {
            log("No bonded devices found.\nPair phones in Android Settings → Bluetooth first.");
            return;
        }

        final List<BluetoothDevice> deviceList  = new ArrayList<>(bonded);
        final String[]              deviceNames = new String[deviceList.size()];

        for (int i = 0; i < deviceList.size(); i++) {
            deviceNames[i] = getDeviceName(deviceList.get(i))
                    + "\n" + deviceList.get(i).getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select device to connect")
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice chosen = deviceList.get(which);
                    String name = getDeviceName(chosen);
                    log("Role: CLIENT — connecting to: " + name);
                    btnServer.setEnabled(false);
                    btnClient.setEnabled(false);
                    tvStatus.setText("Connecting to " + name + "...");
                    bluetoothService.connectToDevice(chosen);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Bluetooth state helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if Bluetooth is supported, enabled, and permissions are granted.
     * Prompts appropriately if any condition is not met.
     */
    private boolean isBluetoothReady() {
        if (bluetoothAdapter == null) {
            log("❌ Bluetooth not supported on this device");
            return false;
        }
        if (!bluetoothAdapter.isEnabled()) {
            log("⚠️ Bluetooth is OFF — requesting to enable...");
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return false;
        }
        if (!hasBluetoothPermissions()) {
            log("⚠️ Bluetooth permissions not granted — requesting...");
            requestRequiredPermissions();
            return false;
        }
        return true;
    }

    private void checkAndPromptBluetooth() {
        if (bluetoothAdapter == null) {
            tvStatus.setText("Bluetooth not supported on this device");
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
        } else if (!bluetoothAdapter.isEnabled()) {
            tvStatus.setText("Bluetooth is OFF");
            log("Bluetooth is OFF — tap a button and you will be prompted to enable it");
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
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
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
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            log("✅ All permissions granted");
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int r : grantResults)
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            log(allGranted ? "✅ All permissions granted"
                    : "⚠️ Some permissions denied — go to Settings → Apps → SideKey → Permissions");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return (name != null) ? name : device.getAddress();
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