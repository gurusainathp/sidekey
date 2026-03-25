package com.sidekey.chat.crypto;

import android.content.Context;
import android.util.Log;

/**
 * SessionManager — orchestrates session key derivation after pairing.
 *
 * Flow:
 *   1. Get own private key from KeyManager
 *   2. Get partner public key from SecureStorage
 *   3. Derive shared secret via SessionKeyDeriver (crypto_scalarmult)
 *   4. Derive session key via KeyDerivationUtil (SHA-256)
 *   5. Store in SessionStore (memory only)
 *
 * Call initSession() only after pairing is confirmed and partner key is saved.
 * Both phones run this independently — the maths guarantees same session key.
 */
public class SessionManager {

    private static final String TAG = "SideKey-SessionManager";

    private final KeyManager        keyManager;
    private final SecureStorage     storage;
    private final SessionKeyDeriver deriver;
    private final SessionStore      sessionStore;

    public SessionManager(Context context, SessionStore sessionStore) {
        this.keyManager   = new KeyManager(context);
        this.storage      = new SecureStorage(context);
        this.deriver      = new SessionKeyDeriver();
        this.sessionStore = sessionStore;

        // Keys must already be initialised from the pairing phase
        this.keyManager.init();
    }

    /**
     * Derives and stores the session key.
     * Safe to call multiple times — overwrites previous session.
     *
     * @return true if session key was successfully derived and stored
     */
    public boolean initSession() {
        Log.d(TAG, "SessionManager: deriving session...");

        // Step 1 — own private key
        byte[] ownPrivateKey;
        try {
            ownPrivateKey = keyManager.getPrivateKey();
        } catch (Exception e) {
            Log.e(TAG, "initSession: failed to load own private key — " + e.getMessage());
            return false;
        }

        if (ownPrivateKey == null) {
            Log.e(TAG, "initSession: own private key is null");
            return false;
        }

        // Step 2 — partner public key
        byte[] partnerPublicKey = storage.getPartnerPublicKey();
        if (partnerPublicKey == null) {
            Log.e(TAG, "initSession: partner public key is null — pairing complete?");
            return false;
        }

        // Step 3 — shared secret via Diffie-Hellman
        byte[] sharedSecret = deriver.deriveSharedSecret(ownPrivateKey, partnerPublicKey);
        if (sharedSecret == null) {
            Log.e(TAG, "initSession: shared secret derivation failed");
            return false;
        }

        // Step 4 — session key via SHA-256
        byte[] sessionKey = KeyDerivationUtil.deriveSessionKey(sharedSecret);
        if (sessionKey == null) {
            Log.e(TAG, "initSession: session key derivation failed");
            return false;
        }

        // Step 5 — store in memory
        sessionStore.setSessionKey(sessionKey);

        Log.d(TAG, "SessionManager: session ready ✅");
        return true;
    }
}