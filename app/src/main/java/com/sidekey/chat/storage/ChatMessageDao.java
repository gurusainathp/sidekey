package com.sidekey.chat.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatMessageDao — data access object for the messages table.
 * All database logic lives here — no SQL in UI code.
 */
public class ChatMessageDao {

    private static final String TAG = "SideKey-MsgDao";

    private final ChatDatabaseHelper helper;

    public ChatMessageDao(ChatDatabaseHelper helper) {
        this.helper = helper;
    }

    // ── DbMessage ─────────────────────────────────────────────────────────────

    public static class DbMessage {
        public final String id;
        public final String deviceAddress;
        public final String direction;   // SENT or RECEIVED
        public final String messageType;
        public final String content;
        public final long   timestamp;

        public DbMessage(String id, String deviceAddress, String direction,
                         String messageType, String content, long timestamp) {
            this.id            = id;
            this.deviceAddress = deviceAddress;
            this.direction     = direction;
            this.messageType   = messageType;
            this.content       = content;
            this.timestamp     = timestamp;
        }

        public boolean isSent() {
            return ChatDatabaseHelper.DIR_SENT.equals(direction);
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void insertMessage(DbMessage message) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(ChatDatabaseHelper.COL_ID,        message.id);
            values.put(ChatDatabaseHelper.COL_ADDRESS,   message.deviceAddress);
            values.put(ChatDatabaseHelper.COL_DIRECTION, message.direction);
            values.put(ChatDatabaseHelper.COL_TYPE,      message.messageType);
            values.put(ChatDatabaseHelper.COL_CONTENT,   message.content);
            values.put(ChatDatabaseHelper.COL_TIMESTAMP, message.timestamp);
            db.insertOrThrow(ChatDatabaseHelper.TABLE_MESSAGES, null, values);
        } catch (Exception e) {
            Log.e(TAG, "insertMessage failed: " + e.getMessage());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<DbMessage> getMessagesForDevice(String deviceAddress) {
        List<DbMessage> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    ChatDatabaseHelper.TABLE_MESSAGES,
                    null,
                    ChatDatabaseHelper.COL_ADDRESS + " = ?",
                    new String[]{deviceAddress},
                    null, null,
                    ChatDatabaseHelper.COL_TIMESTAMP + " ASC"
            );
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getMessagesForDevice failed: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }

    public DbMessage getLastMessage(String deviceAddress) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    ChatDatabaseHelper.TABLE_MESSAGES,
                    null,
                    ChatDatabaseHelper.COL_ADDRESS + " = ?",
                    new String[]{deviceAddress},
                    null, null,
                    ChatDatabaseHelper.COL_TIMESTAMP + " DESC",
                    "1"
            );
            if (cursor.moveToFirst()) return fromCursor(cursor);
        } catch (Exception e) {
            Log.e(TAG, "getLastMessage failed: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteConversation(String deviceAddress) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            int rows = db.delete(
                    ChatDatabaseHelper.TABLE_MESSAGES,
                    ChatDatabaseHelper.COL_ADDRESS + " = ?",
                    new String[]{deviceAddress}
            );
            Log.d(TAG, "deleteConversation: removed " + rows + " rows for " + deviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "deleteConversation failed: " + e.getMessage());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private DbMessage fromCursor(Cursor c) {
        return new DbMessage(
                c.getString(c.getColumnIndexOrThrow(ChatDatabaseHelper.COL_ID)),
                c.getString(c.getColumnIndexOrThrow(ChatDatabaseHelper.COL_ADDRESS)),
                c.getString(c.getColumnIndexOrThrow(ChatDatabaseHelper.COL_DIRECTION)),
                c.getString(c.getColumnIndexOrThrow(ChatDatabaseHelper.COL_TYPE)),
                c.getString(c.getColumnIndexOrThrow(ChatDatabaseHelper.COL_CONTENT)),
                c.getLong(c.getColumnIndexOrThrow(ChatDatabaseHelper.COL_TIMESTAMP))
        );
    }
}