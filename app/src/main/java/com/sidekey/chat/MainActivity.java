package com.sidekey.chat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.ui.device.DeviceListActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity — entry point and service initialiser.
 *
 * Responsibilities:
 *   1. Init all core services in SideKeyApp
 *   2. Request Bluetooth permissions
 *   3. Route to DeviceListActivity
 *
 * After this activity, all navigation is:
 *   DeviceListActivity → PairingActivity → ChatActivity
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SideKey-Main";
    private static final int    PERMISSION_REQUEST_CODE = 1001;

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (isBluetoothEnabled()) {
                            onBluetoothReady();
                        } else {
                            Log.e(TAG, "Bluetooth not enabled — app requires Bluetooth");
                            finish();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init core services — idempotent
        SideKeyApp.getInstance().initServices();
        ConnectionManager.getInstance(); // ensure singleton exists

        // Request permissions then proceed
        requestPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // =========================================================================
    // Permission + BT flow
    // =========================================================================

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            onPermissionsGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            onPermissionsGranted(); // proceed even if partially denied — BT checks happen per-action
        }
    }

    private void onPermissionsGranted() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            finish();
            return;
        }
        if (!adapter.isEnabled()) {
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            onBluetoothReady();
        }
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private void onBluetoothReady() {
        // Route to device list
        startActivity(new Intent(this, DeviceListActivity.class));
        finish(); // MainActivity is not needed in the back stack
    }
}