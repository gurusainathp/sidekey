package com.sidekey.chat.ui.chat;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.ChatCallback;
import com.sidekey.chat.ChatManager;
import com.sidekey.chat.R;
import com.sidekey.chat.SideKeyApp;

/**
 * ChatActivity — the real chat screen.
 *
 * Displays sent and received messages in a RecyclerView.
 * Gets ChatManager from SideKeyApp (set by MainActivity after session ready).
 * Implements ChatCallback to receive incoming messages.
 */
public class ChatActivity extends AppCompatActivity implements ChatCallback {

    private RecyclerView  rvMessages;
    private EditText      etInput;
    private ImageButton   btnSend;
    private ChatAdapter   adapter;
    private ChatManager   chatManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get ChatManager from Application singleton
        chatManager = SideKeyApp.getInstance().getChatManager();
        if (chatManager == null) {
            Toast.makeText(this, "Session not ready — go back and pair first",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Register as callback — incoming messages will arrive here
        chatManager.setCallback(this);

        bindViews();
        setupRecyclerView();
        setupInput();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("SideKey Chat");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't null the chatManager — it lives in SideKeyApp
        // Remove activity as callback so no dead references
        if (chatManager != null) {
            chatManager.setCallback(null);
        }
    }

    // ── View setup ────────────────────────────────────────────────────────────

    private void bindViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etInput    = findViewById(R.id.etChatInput);
        btnSend    = findViewById(R.id.btnChatSend);
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // new messages appear at bottom
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
    }

    private void setupInput() {
        btnSend.setOnClickListener(v -> sendCurrentInput());

        // Allow sending with keyboard "Done" / "Send" action
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendCurrentInput();
                return true;
            }
            return false;
        });
    }

    private void sendCurrentInput() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Add to UI immediately as sent
        addMessage(MessageItem.sent(text));

        // Send via ChatManager → MessageQueue → SendDispatcher → BT
        chatManager.sendMessage(text);

        etInput.setText("");
    }

    private void addMessage(MessageItem item) {
        adapter.addMessage(item);
        // Scroll to show the new message
        rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    // ── ChatCallback ──────────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(String plaintext, long timestamp) {
        // May arrive on a background thread — must update UI on main thread
        runOnUiThread(() -> addMessage(MessageItem.received(plaintext, timestamp)));
    }

    @Override
    public void onMessageSent(String plaintext) {
        // Already added to UI in sendCurrentInput() — nothing to do here
    }

    @Override
    public void onError(String reason) {
        runOnUiThread(() ->
                Toast.makeText(this, "Error: " + reason, Toast.LENGTH_SHORT).show());
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}