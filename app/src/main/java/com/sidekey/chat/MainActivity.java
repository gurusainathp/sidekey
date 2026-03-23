package com.sidekey.chat;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import com.sidekey.chat.bluetooth.BluetoothListener;
import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.crypto.KeyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    // UI
    private TextView   tvStatus;
    private TextView   tvLog;
    private ScrollView scrollLog;
    private Button     btnServer;
    private Button     btnClient;
    private Button     btnSend;

    // Core
    private KeyManager       keyManager;
    private BluetoothService bluetoothService;
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
        setupButtonListeners();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothService = new BluetoothService(this);
        bluetoothService.setListener(btListener);

        keyManager = new KeyManager(this);
        keyManager.init();

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
            log("Role: SERVER — waiting for connection...");
            btnServer.setEnabled(false);
            btnClient.setEnabled(false);
            tvStatus.setText("Waiting for connection...");
            bluetoothService.startServer();
        });

        btnClient.setOnClickListener(v -> showDevicePicker());

        btnSend.setOnClickListener(v -> {
            String msg = "Hello from SideKey!";
            bluetoothService.send(msg.getBytes());
            log("→ Sent: " + msg);
        });
    }

    // -------------------------------------------------------------------------
    // Device picker — shows all bonded devices so user picks the right one
    // -------------------------------------------------------------------------

    private void showDevicePicker() {
        if (bluetoothAdapter == null) {
            log("ERROR: Bluetooth not available on this device");
            return;
        }

        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();

        if (bonded == null || bonded.isEmpty()) {
            log("No bonded devices found.\nGo to Android Settings → Bluetooth and pair both phones first.");
            return;
        }

        // Build parallel lists for dialog display and selection
        final List<BluetoothDevice> deviceList   = new ArrayList<>(bonded);
        final String[]              deviceNames  = new String[deviceList.size()];

        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice d = deviceList.get(i);
            deviceNames[i] = d.getName() + "\n" + d.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select device to connect")
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice chosen = deviceList.get(which);
                    log("Role: CLIENT — connecting to: " + chosen.getName());
                    btnServer.setEnabled(false);
                    btnClient.setEnabled(false);
                    tvStatus.setText("Connecting to " + chosen.getName() + "...");
                    bluetoothService.connectToDevice(chosen);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // BluetoothListener callbacks — always dispatch to UI thread
    // -------------------------------------------------------------------------

    private final BluetoothListener btListener = new BluetoothListener() {

        @Override
        public void onConnected(BluetoothDevice device) {
            runOnUiThread(() -> {
                log("✅ Connected to: " + device.getName());
                tvStatus.setText("Connected: " + device.getName());
                btnSend.setEnabled(true);
            });
        }

        @Override
        public void onDataReceived(byte[] data) {
            String message = new String(data);
            runOnUiThread(() -> log("📨 Received: " + message));
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
                tvStatus.setText("Error — see log");
                btnServer.setEnabled(true);
                btnClient.setEnabled(true);
            });
        }
    };

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    private void requestRequiredPermissions() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    needed.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
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
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                log("✅ All permissions granted");
            } else {
                log("⚠️ Some permissions denied — Bluetooth may not work.\nGo to Settings → Apps → SideKey → Permissions");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void checkBluetoothState() {
        if (bluetoothAdapter == null) {
            tvStatus.setText("Bluetooth not supported");
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