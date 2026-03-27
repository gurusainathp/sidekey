package com.sidekey.chat;

import android.app.Application;

/**
 * SideKeyApp — Application class.
 *
 * Holds a reference to ChatManager so ChatActivity can access it
 * without passing non-serializable objects through Intents.
 *
 * ChatManager is set by MainActivity after session is ready.
 * ChatActivity reads it on start.
 */
public class SideKeyApp extends Application {

    private static SideKeyApp instance;
    private ChatManager chatManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static SideKeyApp getInstance() {
        return instance;
    }

    public void setChatManager(ChatManager manager) {
        this.chatManager = manager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }
}