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
import com.sidekey.chat.crypto.SecureStorage;
import com.sidekey.chat.model.UserKey;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SideKey";

    private KeyManager    keyManager;
    private SecureStorage secureStorage;

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

        // Phase 4 — encryption test
        testEncryptor();

        // Phase 5 — partner key test
        testPartnerKey();
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
}