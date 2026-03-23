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
import com.goterl.lazysodium.utils.KeyPair;

import com.sidekey.chat.crypto.Encryptor;
import com.sidekey.chat.crypto.KeyManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SideKey";

    private KeyManager keyManager;

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

        // Phase 3 — key storage
        keyManager = new KeyManager(this);
        keyManager.init();

        // Phase 4 — encryption test
        testEncryptor();
    }

    private void testEncryptor() {
        try {
            LazySodiumAndroid sodium = new LazySodiumAndroid(new SodiumAndroid());

            // Simulate two users with fresh keypairs
            KeyPair keypairA = sodium.cryptoBoxKeypair();
            KeyPair keypairB = sodium.cryptoBoxKeypair();

            byte[] pubA  = keypairA.getPublicKey().getAsBytes();
            byte[] privA = keypairA.getSecretKey().getAsBytes();
            byte[] pubB  = keypairB.getPublicKey().getAsBytes();
            byte[] privB = keypairB.getSecretKey().getAsBytes();

            String original = "Hey, this is SideKey!";
            Log.d(TAG, "Original message: " + original);

            Encryptor encryptor = new Encryptor();

            // User A encrypts for User B
            byte[] encrypted = encryptor.encrypt(original, pubB, privA);

            if (encrypted == null) {
                Log.e(TAG, "❌ Encryption failed — stopping test");
                return;
            }

            // User B decrypts using A's public key
            String decrypted = encryptor.decrypt(encrypted, pubA, privB);

            if (decrypted == null) {
                Log.e(TAG, "❌ Decryption failed — stopping test");
                return;
            }

            Log.d(TAG, "Decrypted message: " + decrypted);

            if (original.equals(decrypted)) {
                Log.d(TAG, "✅ Message matches — Encryptor test PASSED");
            } else {
                Log.e(TAG, "❌ Message mismatch — Encryptor test FAILED");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Encryptor test exception: " + e.getMessage(), e);
        }
    }
}