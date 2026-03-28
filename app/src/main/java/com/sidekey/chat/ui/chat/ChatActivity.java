package com.sidekey.chat.ui.chat;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.ChatCallback;
import com.sidekey.chat.ChatManager;
import com.sidekey.chat.R;
import com.sidekey.chat.SideKeyApp;
import com.sidekey.chat.storage.ConversationRepository;

import java.util.List;

/**
 * ChatActivity — encrypted chat screen.
 *
 * No EdgeToEdge.enable() — it conflicts with adjustResize.
 * No ViewCompat inset listener on the root — the inset padding was
 * double-stacking with the keyboard resize and causing the input to
 * be pushed above the keyboard instead of sitting just below it.
 *
 * The system theme handles status bar / nav bar padding automatically
 * when EdgeToEdge is disabled. adjustResize in the manifest then works
 * exactly as designed: the window shrinks, ConstraintLayout reflows,
 * RecyclerView shrinks up, input row stays at the bottom edge.
 */
public class ChatActivity extends AppCompatActivity implements ChatCallback {

    private RecyclerView           rvMessages;
    private EditText               etInput;
    private ImageButton            btnSend;
    private ChatAdapter            adapter;
    private ChatManager            chatManager;
    private ConversationRepository repository;
    private String                 partnerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No EdgeToEdge.enable() — deliberately omitted
        setContentView(R.layout.activity_chat);
        // No ViewCompat inset listener — deliberately omitted

        chatManager = SideKeyApp.getInstance().getChatManager();
        if (chatManager == null) {
            Toast.makeText(this, "Session not ready — go back and pair first",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        partnerAddress = SideKeyApp.getInstance()
                .getBluetoothService().getConnectedDeviceAddress();

        repository = new ConversationRepository(this);
        chatManager.setCallback(this);

        bindViews();
        setupRecyclerView();
        setupInput();
        loadHistory();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("SideKey");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatManager != null) chatManager.setCallback(null);
    }

    // ── View setup ────────────────────────────────────────────────────────────

    private void bindViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etInput    = findViewById(R.id.etChatInput);
        btnSend    = findViewById(R.id.btnChatSend);
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);
    }

    private void setupInput() {
        btnSend.setOnClickListener(v -> sendCurrentInput());
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendCurrentInput();
                return true;
            }
            return false;
        });
    }

    private void loadHistory() {
        if (partnerAddress == null) return;
        List<MessageItem> history = repository.loadConversation(partnerAddress);
        for (MessageItem item : history) adapter.addMessage(item);
        if (!history.isEmpty())
            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void sendCurrentInput() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;
        addMessage(MessageItem.sent(text));
        if (partnerAddress != null) repository.saveSentMessage(text, partnerAddress);
        chatManager.sendMessage(text);
        etInput.setText("");
    }

    private void addMessage(MessageItem item) {
        adapter.addMessage(item);
        rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    // ── ChatCallback ──────────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(String plaintext, long timestamp) {
        runOnUiThread(() -> {
            MessageItem item = MessageItem.received(plaintext, timestamp);
            addMessage(item);
            if (partnerAddress != null)
                repository.saveReceivedMessage(plaintext, partnerAddress, timestamp);
        });
    }

    @Override public void onMessageSent(String plaintext) { /* added in sendCurrentInput */ }

    @Override
    public void onError(String reason) {
        runOnUiThread(() ->
                Toast.makeText(this, "Error: " + reason, Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}