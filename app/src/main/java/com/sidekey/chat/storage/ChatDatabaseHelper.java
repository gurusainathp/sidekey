package com.sidekey.chat.storage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * ChatDatabaseHelper — SQLite schema for chat persistence.
 *
 * Table: messages
 *   id           TEXT  — unique per message (timestamp + direction)
 *   deviceAddress TEXT  — MAC address of the partner
 *   direction    TEXT  — "SENT" or "RECEIVED"
 *   messageType  TEXT  — from MessageType enum (TEXT, SYSTEM, etc.)
 *   content      TEXT  — plaintext message content
 *   timestamp    INTEGER — epoch milliseconds
 *
 * Note: content is stored as plaintext here. Phase 24 upgrades this to
 * encrypted-at-rest storage. For now the database file is private to
 * the app and not accessible without root.
 */
public class ChatDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "sidekey_chat.db";
    private static final int    DB_VERSION = 1;

    public static final String TABLE_MESSAGES = "messages";
    public static final String COL_ID          = "id";
    public static final String COL_ADDRESS     = "deviceAddress";
    public static final String COL_DIRECTION   = "direction";
    public static final String COL_TYPE        = "messageType";
    public static final String COL_CONTENT     = "content";
    public static final String COL_TIMESTAMP   = "timestamp";

    public static final String DIR_SENT     = "SENT";
    public static final String DIR_RECEIVED = "RECEIVED";

    private static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_MESSAGES + " ("
                    + COL_ID        + " TEXT PRIMARY KEY, "
                    + COL_ADDRESS   + " TEXT NOT NULL, "
                    + COL_DIRECTION + " TEXT NOT NULL, "
                    + COL_TYPE      + " TEXT NOT NULL, "
                    + COL_CONTENT   + " TEXT NOT NULL, "
                    + COL_TIMESTAMP + " INTEGER NOT NULL"
                    + ")";

    public ChatDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        db.execSQL("CREATE INDEX idx_address_ts ON " + TABLE_MESSAGES
                + " (" + COL_ADDRESS + ", " + COL_TIMESTAMP + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Phase 24: migrate to encrypted storage here
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }
}