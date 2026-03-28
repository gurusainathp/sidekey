package com.sidekey.chat.ui.device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.R;
import com.sidekey.chat.SideKeyApp;
import com.sidekey.chat.bluetooth.BluetoothCallback;
import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.connection.ConnectionState;
import com.sidekey.chat.ui.pairing.PairingActivity;

import java.util.Set;

public class DeviceListActivity extends AppCompatActivity {

    private static final String TAG = "SideKey-DeviceList";

    // ── Views ─────────────────────────────────────────────────────────────────
    private RecyclerView  rvPaired;
    private RecyclerView  rvNearby;
    private DeviceAdapter pairedAdapter;
    private DeviceAdapter nearbyAdapter;
    private ProgressBar   progressScan;
    private TextView      tvScanStatus;
    private TextView      tvNearbyLabel;
    private Button        btnScan;
    private Button        btnServer;

    // ── State ─────────────────────────────────────────────────────────────────
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothService bluetoothService;
    private boolean          isServer           = false;
    private boolean          receiverRegistered = false;

    // ── Discovery receiver ────────────────────────────────────────────────────

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case BluetoothDevice.ACTION_FOUND: {
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device == null || !isPhone(device)) return;
                    String name = getDeviceName(device);
                    Log.d(TAG, "Found: " + name + " [" + device.getAddress() + "]");
                    runOnUiThread(() -> {
                        nearbyAdapter.addDevice(new DeviceItem(device, name));
                        tvNearbyLabel.setVisibility(View.VISIBLE);
                        rvNearby.setVisibility(View.VISIBLE);
                    });
                    break;
                }
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    runOnUiThread(() -> {
                        progressScan.setVisibility(View.VISIBLE);
                        tvScanStatus.setText("Scanning for nearby phones...");
                        nearbyAdapter.clear();
                        tvNearbyLabel.setVisibility(View.GONE);
                        rvNearby.setVisibility(View.GONE);
                    });
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    runOnUiThread(() -> {
                        progressScan.setVisibility(View.GONE);
                        int found = nearbyAdapter.getItemCount();
                        tvScanStatus.setText(found == 0
                                ? "No phones found — make sure the other phone is discoverable"
                                : found + " phone(s) found");
                    });
                    break;
            }
        }
    };

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        // Handle window insets for tall/notched phones
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.deviceListRoot), (v, insets) -> {
                    var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothService = SideKeyApp.getInstance().getBluetoothService();

        if (bluetoothService == null) {
            Toast.makeText(this, "App not initialised — restart", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        setupRecyclerViews();
        setupButtons();
        registerDiscoveryReceiver();
        loadPairedPhones();

        bluetoothService.setCallback(btCallback);

        if (getSupportActionBar() != null)
            getSupportActionBar().hide(); // full-screen look; title is in the layout
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        if (receiverRegistered) {
            unregisterReceiver(discoveryReceiver);
            receiverRegistered = false;
        }
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private void bindViews() {
        rvPaired      = findViewById(R.id.rvPairedDevices);
        rvNearby      = findViewById(R.id.rvNearbyDevices);
        progressScan  = findViewById(R.id.progressScan);
        tvScanStatus  = findViewById(R.id.tvScanStatus);
        tvNearbyLabel = findViewById(R.id.tvNearbyLabel);
        btnScan       = findViewById(R.id.btnScan);
        btnServer     = findViewById(R.id.btnBeServer);
    }

    private void setupRecyclerViews() {
        pairedAdapter = new DeviceAdapter(this::onDeviceSelected);
        rvPaired.setLayoutManager(new LinearLayoutManager(this));
        rvPaired.setAdapter(pairedAdapter);
        rvPaired.setNestedScrollingEnabled(false);

        nearbyAdapter = new DeviceAdapter(this::onDeviceSelected);
        rvNearby.setLayoutManager(new LinearLayoutManager(this));
        rvNearby.setAdapter(nearbyAdapter);
        rvNearby.setNestedScrollingEnabled(false);

        tvNearbyLabel.setVisibility(View.GONE);
        rvNearby.setVisibility(View.GONE);
    }

    private void setupButtons() {
        btnServer.setOnClickListener(v -> {
            isServer = true;                              // ← this device is server
            tvScanStatus.setText("Waiting for the other phone to connect...");
            btnServer.setEnabled(false);
            btnScan.setEnabled(false);
            ConnectionManager.getInstance().setState(ConnectionState.CONNECTING);
            bluetoothService.startServer();               // no discoverability popup
        });

        btnScan.setOnClickListener(v -> startScan());
    }

    // =========================================================================
    // Paired phones
    // =========================================================================

    private void loadPairedPhones() {
        if (bluetoothAdapter == null) return;
        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded == null) return;
            for (BluetoothDevice d : bonded) {
                if (isPhone(d)) {
                    pairedAdapter.addDevice(new DeviceItem(d, getDeviceName(d)));
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "loadPairedPhones: " + e.getMessage());
        }
    }

    // =========================================================================
    // Discovery
    // =========================================================================

    private void registerDiscoveryReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryReceiver, filter);
        receiverRegistered = true;
    }

    private void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is OFF", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
            bluetoothAdapter.startDiscovery();
        } catch (SecurityException e) {
            Log.e(TAG, "startScan: " + e.getMessage());
            Toast.makeText(this, "Scan permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopScan() {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering())
                bluetoothAdapter.cancelDiscovery();
        } catch (SecurityException ignored) {}
    }

    // =========================================================================
    // Device selected (client path)
    // =========================================================================

    private void onDeviceSelected(DeviceItem item) {
        isServer = false;                                 // ← this device is client
        stopScan();
        Log.d(TAG, "Connecting to: " + item.getDisplayName());
        tvScanStatus.setText("Connecting to " + item.getDisplayName() + "...");
        btnScan.setEnabled(false);
        btnServer.setEnabled(false);
        ConnectionManager.getInstance().setState(ConnectionState.CONNECTING);
        bluetoothService.connectToDevice(item.getDevice());
    }

    // =========================================================================
    // BluetoothCallback — navigate to PairingActivity with isServer flag
    // =========================================================================

    private final BluetoothCallback btCallback = new BluetoothCallback() {
        @Override
        public void onConnected() {
            SideKeyApp.getInstance().getAutoSessionStarter().onConnected();
            runOnUiThread(() -> {
                Intent intent = new Intent(DeviceListActivity.this, PairingActivity.class);
                intent.putExtra(PairingActivity.EXTRA_IS_SERVER, isServer); // ← passes role
                startActivity(intent);
            });
        }

        @Override
        public void onMessage(byte[] data) {
            SideKeyApp.getInstance().getAutoSessionStarter().onMessage(data);
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                tvScanStatus.setText("Disconnected — try again");
                btnScan.setEnabled(true);
                btnServer.setEnabled(true);
                ConnectionManager.getInstance().reset();
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                tvScanStatus.setText("Error: " + message);
                btnScan.setEnabled(true);
                btnServer.setEnabled(true);
            });
        }
    };

    // =========================================================================
    // Helpers
    // =========================================================================

    private String getDeviceName(BluetoothDevice device) {
        try { String n = device.getName(); return n != null ? n : device.getAddress(); }
        catch (SecurityException e) { return device.getAddress(); }
    }

    private boolean isPhone(BluetoothDevice device) {
        try {
            BluetoothClass c = device.getBluetoothClass();
            if (c == null) return true;
            int major = c.getMajorDeviceClass();
            return major == BluetoothClass.Device.Major.PHONE
                    || major == BluetoothClass.Device.Major.COMPUTER
                    || major == BluetoothClass.Device.Major.UNCATEGORIZED;
        } catch (SecurityException e) { return true; }
    }
}