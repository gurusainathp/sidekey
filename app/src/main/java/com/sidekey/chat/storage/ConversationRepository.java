package com.sidekey.chat.storage;

import android.content.Context;
import android.util.Log;

import com.sidekey.chat.ui.chat.MessageItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ConversationRepository — single clean API for all chat persistence.
 *
 * Used by ChatActivity to save and load messages.
 * Converts between DbMessage (storage) and MessageItem (UI).
 *
 * Thread note: DB calls are fast for the volumes we expect.
 * If conversations grow very large in a later phase, move to background thread.
 */
public class ConversationRepository {

    private static final String TAG = "SideKey-ConvRepo";

    private final ChatMessageDao dao;

    public ConversationRepository(Context context) {
        ChatDatabaseHelper helper = new ChatDatabaseHelper(context);
        this.dao = new ChatMessageDao(helper);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void saveSentMessage(String content, String deviceAddress) {
        ChatMessageDao.DbMessage msg = new ChatMessageDao.DbMessage(
                generateId(),
                deviceAddress,
                ChatDatabaseHelper.DIR_SENT,
                "TEXT",
                content,
                System.currentTimeMillis()
        );
        dao.insertMessage(msg);
        Log.d(TAG, "Saved SENT message for " + deviceAddress);
    }

    public void saveReceivedMessage(String content, String deviceAddress, long timestamp) {
        ChatMessageDao.DbMessage msg = new ChatMessageDao.DbMessage(
                generateId(),
                deviceAddress,
                ChatDatabaseHelper.DIR_RECEIVED,
                "TEXT",
                content,
                timestamp
        );
        dao.insertMessage(msg);
        Log.d(TAG, "Saved RECEIVED message for " + deviceAddress);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Loads all messages for a device address as MessageItem (UI model).
     * Returns an empty list if no history exists.
     */
    public List<MessageItem> loadConversation(String deviceAddress) {
        List<ChatMessageDao.DbMessage> dbMessages = dao.getMessagesForDevice(deviceAddress);
        List<MessageItem> items = new ArrayList<>(dbMessages.size());
        for (ChatMessageDao.DbMessage db : dbMessages) {
            if (db.isSent()) {
                items.add(new MessageItem(db.content, true, db.timestamp));
            } else {
                items.add(new MessageItem(db.content, false, db.timestamp));
            }
        }
        Log.d(TAG, "Loaded " + items.size() + " messages for " + deviceAddress);
        return items;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteConversation(String deviceAddress) {
        dao.deleteConversation(deviceAddress);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String generateId() {
        return UUID.randomUUID().toString();
    }
}