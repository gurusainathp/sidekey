package com.sidekey.chat;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SideKey";

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

        testLibsodium();
    }

    private void testLibsodium() {
        try {
            // Step 1: Initialize libsodium
            LazySodiumAndroid sodium = new LazySodiumAndroid(new SodiumAndroid());
            Log.d(TAG, "libsodium initialized successfully");

            // Step 2: Generate 32 random bytes
            byte[] randomBytes = sodium.randomBytesBuf(32);

            // Step 3: Verify length
            Log.d(TAG, "Random bytes length: " + randomBytes.length);

            // Step 4: Print bytes as hex string (sanity check they aren't all zeros)
            StringBuilder hex = new StringBuilder();
            for (byte b : randomBytes) {
                hex.append(String.format("%02x", b));
            }
            Log.d(TAG, "Random bytes (hex): " + hex);

            if (randomBytes.length == 32) {
                Log.d(TAG, "✅ libsodium test PASSED — crypto ready");
            } else {
                Log.e(TAG, "❌ libsodium test FAILED — unexpected byte length");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ libsodium init FAILED: " + e.getMessage(), e);
        }
    }
}