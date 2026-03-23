package com.sidekey.chat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import java.util.Set;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;
import com.goterl.lazysodium.utils.KeyPair;

import com.sidekey.chat.crypto.Encryptor;
import com.sidekey.chat.crypto.KeyManager;
import com.sidekey.chat.crypto.SecureStorage;
import com.sidekey.chat.model.UserKey;
import com.sidekey.chat.bluetooth.BluetoothService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SideKey";

    private static final boolean TEST_SERVER = false;
    private static final int BT_PERMISSION_CODE = 1001;

    private KeyManager    keyManager;
    private SecureStorage secureStorage;

    private BluetoothService bluetoothService;

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

        // Phase 3 — own key pair
        keyManager    = new KeyManager(this);
        secureStorage = new SecureStorage(this);
        keyManager.init();

        bluetoothService = new BluetoothService(this);

        // Phase 4 — encryption test
        testEncryptor();

        // Phase 5 — partner key test
        testPartnerKey();

        checkPermissions();
    }

    // -------------------------------------------------------------------------
    // Phase 4 test — encrypt / decrypt between two simulated users
    // -------------------------------------------------------------------------

    private void testEncryptor() {
        try {
            LazySodiumAndroid sodium = new LazySodiumAndroid(new SodiumAndroid());

            KeyPair keypairA = sodium.cryptoBoxKeypair();
            KeyPair keypairB = sodium.cryptoBoxKeypair();

            byte[] pubA  = keypairA.getPublicKey().getAsBytes();
            byte[] privA = keypairA.getSecretKey().getAsBytes();
            byte[] pubB  = keypairB.getPublicKey().getAsBytes();
            byte[] privB = keypairB.getSecretKey().getAsBytes();

            String original = "Hey, this is SideKey!";
            Log.d(TAG, "Original message: " + original);

            Encryptor encryptor = new Encryptor();

            byte[] encrypted = encryptor.encrypt(original, pubB, privA);
            if (encrypted == null) { Log.e(TAG, "❌ Encryption failed"); return; }

            String decrypted = encryptor.decrypt(encrypted, pubA, privB);
            if (decrypted == null) { Log.e(TAG, "❌ Decryption failed"); return; }

            Log.d(TAG, "Decrypted message: " + decrypted);

            if (original.equals(decrypted)) {
                Log.d(TAG, "✅ Encryptor test PASSED");
            } else {
                Log.e(TAG, "❌ Encryptor test FAILED — message mismatch");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Encryptor test exception: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5 test — partner key save / load / fingerprint / pair status
    // -------------------------------------------------------------------------

    private void testPartnerKey() {
        try {
            // Simulate a partner public key (32 random bytes stand in for a real key)
            LazySodiumAndroid sodium = new LazySodiumAndroid(new SodiumAndroid());
            byte[] simulatedPartnerKey = sodium.randomBytesBuf(32);

            // Save partner key
            secureStorage.savePartnerKey(simulatedPartnerKey);
            Log.d(TAG, "Partner key saved");

            // Load partner key back
            byte[] loaded = secureStorage.getPartnerKey();
            if (loaded == null) {
                Log.e(TAG, "❌ Partner key load returned null");
                return;
            }
            Log.d(TAG, "Partner key loaded");

            // Verify pair status
            boolean paired = secureStorage.isPaired();
            Log.d(TAG, "Pair status: " + paired);

            if (!paired) {
                Log.e(TAG, "❌ isPaired() returned false after save");
                return;
            }

            // Build UserKey model and check fingerprint
            UserKey partnerUserKey = new UserKey(loaded, secureStorage.getPartnerTimestamp());
            Log.d(TAG, "Partner fingerprint: " + partnerUserKey.getFingerprint());
            Log.d(TAG, "Partner toString: "    + partnerUserKey);

            Log.d(TAG, "✅ Partner key test PASSED");

        } catch (Exception e) {
            Log.e(TAG, "❌ Partner key test exception: " + e.getMessage(), e);
        }
    }


    private void testBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            return;
        }

        if (!adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is OFF");
            return;
        }

        if (TEST_SERVER) {
            Log.d(TAG, "TEST MODE: SERVER");
            bluetoothService.startServer();
            Log.d(TAG, "Server started");
        } else {
            Log.d(TAG, "TEST MODE: CLIENT");

            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            if (devices == null || devices.isEmpty()) {
                Log.e(TAG, "No bonded devices found");
                return;
            }

            BluetoothDevice device = devices.iterator().next();
            Log.d(TAG, "Connecting to: " + device.getName());
            bluetoothService.connectToDevice(device);

            // small delay so connection can establish
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    bluetoothService.send("hello".getBytes());
                    Log.d(TAG, "Sent hello");
                } catch (Exception e) {
                    Log.e(TAG, "Send error", e);
                }
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001) {

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "Location permission granted");

                testBluetooth();

            } else {

                Log.e(TAG, "Location permission denied");
            }
        }
    }

    private void checkPermissions() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1001
            );

            return;
        }

        testBluetooth();
    }
}