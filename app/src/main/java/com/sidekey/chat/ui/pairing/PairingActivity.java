package com.sidekey.chat.ui.pairing;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sidekey.chat.ChatManager;
import com.sidekey.chat.R;
import com.sidekey.chat.SideKeyApp;
import com.sidekey.chat.bluetooth.BluetoothCallback;
import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.pairing.AutoSessionStarter;
import com.sidekey.chat.pairing.PairingController;
import com.sidekey.chat.pairing.PairingManager;
import com.sidekey.chat.ui.chat.ChatActivity;

/**
 * PairingActivity — handles fingerprint verification and session establishment.
 *
 * Shows:
 *   - Both fingerprints for comparison
 *   - Confirm/Cancel for server
 *   - "Waiting..." for client
 *   - Navigates to ChatActivity when SESSION_READY
 */
public class PairingActivity extends AppCompatActivity
        implements AutoSessionStarter.UICallback {

    private static final String TAG = "SideKey-PairingUI";

    // Intent extra — true if this device is the server
    public static final String EXTRA_IS_SERVER = "is_server";

    private TextView tvPairingStatus;
    private TextView tvOwnFingerprint;
    private TextView tvPartnerFingerprint;
    private Button   btnConfirm;
    private Button   btnCancel;

    private PairingManager     pairingManager;
    private PairingController  pairingController;
    private BluetoothService   bluetoothService;
    private AutoSessionStarter autoSessionStarter;
    private boolean            isServer;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);

        isServer           = getIntent().getBooleanExtra(EXTRA_IS_SERVER, true);
        pairingManager     = SideKeyApp.getInstance().getPairingManager();
        bluetoothService   = SideKeyApp.getInstance().getBluetoothService();
        autoSessionStarter = SideKeyApp.getInstance().getAutoSessionStarter();

        autoSessionStarter.setUICallback(this);

        pairingController = new PairingController(
                pairingManager, bluetoothService, autoSessionStarter);

        bindViews();
        setupButtons();
        showOwnFingerprint();

        // Re-register BT callback so messages keep flowing here
        bluetoothService.setCallback(btCallback);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isServer ? "Confirm Pairing" : "Pairing...");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Only clear UICallback — don't stop services
        if (autoSessionStarter != null) autoSessionStarter.setUICallback(null);
    }

    // =========================================================================
    // View setup
    // =========================================================================

    private void bindViews() {
        tvPairingStatus       = findViewById(R.id.tvPairingStatus);
        tvOwnFingerprint      = findViewById(R.id.tvOwnFingerprint);
        tvPartnerFingerprint  = findViewById(R.id.tvPartnerFingerprint);
        btnConfirm            = findViewById(R.id.btnPairingConfirm);
        btnCancel             = findViewById(R.id.btnPairingCancel);
    }

    private void showOwnFingerprint() {
        tvOwnFingerprint.setText("Your key: " + pairingManager.getOwnFingerprint());
        tvPairingStatus.setText(isServer
                ? "Waiting for partner key..."
                : "Exchanging keys...");
        setPairingButtonsVisible(false);
    }

    private void setupButtons() {
        btnConfirm.setOnClickListener(v -> {
            Log.d(TAG, "Server confirmed pairing");
            pairingController.onPairConfirmed();
            btnConfirm.setEnabled(false);
            btnCancel.setEnabled(false);
            tvPairingStatus.setText("Confirmed — establishing session...");
        });

        btnCancel.setOnClickListener(v -> {
            pairingController.onPairCancelled();
            ConnectionManager.getInstance().reset();
            finish();
        });
    }

    // =========================================================================
    // AutoSessionStarter.UICallback
    // =========================================================================

    @Override
    public void onFingerprintReady(String partnerFingerprint) {
        runOnUiThread(() -> {
            tvPartnerFingerprint.setText("Partner key: " + partnerFingerprint);
            tvPartnerFingerprint.setVisibility(View.VISIBLE);

            if (isServer) {
                tvPairingStatus.setText("Compare fingerprints — confirm if they match");
                setPairingButtonsVisible(true);
            } else {
                tvPairingStatus.setText("Waiting for server to confirm...");
            }
        });
    }

    @Override
    public void onSessionReady() {
        runOnUiThread(() -> {
            tvPairingStatus.setText("Session ready ✅");
            setPairingButtonsVisible(false);
            launchChat();
        });
    }

    @Override
    public void onError(String reason) {
        runOnUiThread(() -> {
            tvPairingStatus.setText("Error: " + reason);
            setPairingButtonsVisible(false);
        });
    }

    // =========================================================================
    // BT callback — keeps message routing alive while on this screen
    // =========================================================================

    private final BluetoothCallback btCallback = new BluetoothCallback() {
        @Override public void onConnected() { autoSessionStarter.onConnected(); }
        @Override public void onMessage(byte[] data) { autoSessionStarter.onMessage(data); }
        @Override public void onDisconnected() {
            runOnUiThread(() -> {
                tvPairingStatus.setText("Disconnected");
                ConnectionManager.getInstance().reset();
                finish();
            });
        }
        @Override public void onError(String message) {
            runOnUiThread(() -> tvPairingStatus.setText("BT Error: " + message));
        }
    };

    // =========================================================================
    // Launch chat
    // =========================================================================

    private void launchChat() {
        // Create ChatManager and store in app
        ChatManager chatManager = new ChatManager(this,
                SideKeyApp.getInstance().getSessionStore());
        SideKeyApp.getInstance().setChatManager(chatManager);

        // Swap BT callback so chat messages route correctly
        bluetoothService.setCallback(new BluetoothCallback() {
            @Override public void onConnected() { autoSessionStarter.onConnected(); }
            @Override public void onMessage(byte[] data) {
                ChatManager cm = SideKeyApp.getInstance().getChatManager();
                if (cm == null || !cm.handleIncoming(data)) {
                    pairingManager.handleIncoming(data);
                }
            }
            @Override public void onDisconnected() { autoSessionStarter.onDisconnected(); }
            @Override public void onError(String m) { autoSessionStarter.onError(m); }
        });

        startActivity(new Intent(this, ChatActivity.class));
        finish(); // don't keep pairing screen in back stack
    }

    private void setPairingButtonsVisible(boolean visible) {
        // Only server gets buttons
        int v = (visible && isServer) ? View.VISIBLE : View.GONE;
        btnConfirm.setVisibility(v);
        btnCancel.setVisibility(v);
    }
}