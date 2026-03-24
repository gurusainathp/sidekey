package com.sidekey.chat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
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
    private Button     btnSend;

    // Core
    private BluetoothService bluetoothService;
    private PairingManager   pairingManager;
    private BluetoothAdapter bluetoothAdapter;

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

        setupButtonListeners();
        checkBluetoothState();
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
        tvStatus  = findViewById(R.id.tvStatus);
        tvLog     = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        btnServer = findViewById(R.id.btnServer);
        btnClient = findViewById(R.id.btnClient);
        btnSend   = findViewById(R.id.btnSend);
    }

    // -------------------------------------------------------------------------
    // Button listeners
    // -------------------------------------------------------------------------

    private void setupButtonListeners() {

        btnServer.setOnClickListener(v -> {
            if (!hasBluetoothPermissions()) {
                log("⚠️ Bluetooth permissions not granted yet");
                return;
            }
            log("Role: SERVER — waiting for connection...");
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
            tvStatus.setText("Waiting for connection...");
            bluetoothService.startServer();
        });

        btnClient.setOnClickListener(v -> {
            if (!hasBluetoothPermissions()) {
                log("⚠️ Bluetooth permissions not granted yet");
                return;
            }
            showDevicePicker();
        });

        btnSend.setOnClickListener(v -> {
            String msg = "Hello from SideKey!";
            bluetoothService.send(msg.getBytes());
            log("→ Sent test message");
        });
    }

    // -------------------------------------------------------------------------
    // BluetoothCallback — routes events into PairingManager (background thread)
    // -------------------------------------------------------------------------

    private final BluetoothCallback btCallback = new BluetoothCallback() {

        @Override
        public void onConnected() {
            byte[] ownMessage = pairingManager.createOwnMessage();
            if (ownMessage != null) {
                bluetoothService.send(ownMessage);
                Log.d(TAG, "Sent own pairing message after connect");
            }
        }

        @Override
        public void onMessage(byte[] data) {
            pairingManager.handleIncoming(data);
        }

        @Override
        public void onDisconnected() {}

        @Override
        public void onError(String message) {}
    };

    // -------------------------------------------------------------------------
    // BluetoothListener — UI updates only (background thread → runOnUiThread)
    // -------------------------------------------------------------------------

    private final BluetoothListener btListener = new BluetoothListener() {

        @Override
        public void onConnected(BluetoothDevice device) {
            // Guard: getRemoteDevice name needs BLUETOOTH_CONNECT on API 31+
            String deviceName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    deviceName = device.getName();
                } else {
                    deviceName = device.getAddress();
                }
            } else {
                deviceName = device.getName();
            }

            final String name = deviceName;
            runOnUiThread(() -> {
                log("✅ Connected to: " + name);
                tvStatus.setText("Connected: " + name);
                btnSend.setEnabled(true);
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
                btnSend.setEnabled(false);
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                log("❌ Error: " + message);
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
            });
        }
    };

    // -------------------------------------------------------------------------
    // PairingCallback — PairingManager results (may be background thread)
    // -------------------------------------------------------------------------

    private final PairingCallback pairingCallback = new PairingCallback() {

        @Override
        public void onPartnerKeyReceived(String fingerprint) {
            runOnUiThread(() -> {
                log("🔑 Partner key received!");
                log("Fingerprint: " + fingerprint);
                log("→ Show this to your partner and confirm they see the same");
                tvStatus.setText("Paired — verify fingerprint");
            });
        }

        @Override
        public void onPairingComplete() {
            runOnUiThread(() -> {
                log("✅ Pairing complete — both sides confirmed");
                tvStatus.setText("Paired ✅");
            });
        }

        @Override
        public void onPairingError(String reason) {
            runOnUiThread(() -> {
                log("❌ Pairing error: " + reason);
                tvStatus.setText("Pairing failed");
            });
        }
    };

    // -------------------------------------------------------------------------
    // Device picker — lists bonded devices for user to choose
    // -------------------------------------------------------------------------

    private void showDevicePicker() {
        if (bluetoothAdapter == null) {
            log("Bluetooth not available on this device");
            return;
        }

        // Guard: getBondedDevices requires BLUETOOTH_CONNECT on API 31+
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
            BluetoothDevice d = deviceList.get(i);
            String name;
            try {
                name = d.getName();
                if (name == null) name = d.getAddress();
            } catch (SecurityException e) {
                name = d.getAddress();
            }
            deviceNames[i] = name + "\n" + d.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select device to connect")
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice chosen = deviceList.get(which);
                    String chosenName;
                    try {
                        chosenName = chosen.getName();
                        if (chosenName == null) chosenName = chosen.getAddress();
                    } catch (SecurityException e) {
                        chosenName = chosen.getAddress();
                    }
                    log("Role: CLIENT — connecting to: " + chosenName);
                    btnServer.setEnabled(false);
                    btnClient.setEnabled(false);
                    tvStatus.setText("Connecting to " + chosenName + "...");
                    bluetoothService.connectToDevice(chosen);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Permission helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if all runtime Bluetooth permissions are granted
     * for the current API level.
     */
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
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
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
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                log("✅ All permissions granted");
            } else {
                log("⚠️ Some permissions denied — Bluetooth may not work.\n"
                        + "Go to Settings → Apps → SideKey → Permissions");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void checkBluetoothState() {
        if (bluetoothAdapter == null) {
            tvStatus.setText("Bluetooth not supported on this device");
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
        } else if (!bluetoothAdapter.isEnabled()) {
            tvStatus.setText("Bluetooth is OFF — please enable it");
        } else {
            tvStatus.setText("Bluetooth ready");
        }
    }

    private void log(String message) {
        runOnUiThread(() -> {
            tvLog.append(message + "\n");
            scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}