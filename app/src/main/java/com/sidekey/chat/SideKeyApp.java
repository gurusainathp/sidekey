package com.sidekey.chat;

import android.app.Application;
import android.util.Log;

import com.sidekey.chat.bluetooth.BluetoothService;
import com.sidekey.chat.connection.ConnectionManager;
import com.sidekey.chat.crypto.SessionStore;
import com.sidekey.chat.messaging.SendDispatcher;
import com.sidekey.chat.messaging.TransportSender;
import com.sidekey.chat.pairing.AutoSessionStarter;
import com.sidekey.chat.pairing.PairingManager;

/**
 * SideKeyApp — Application class and DI container.
 * Core services initialised once and shared across all activities.
 */
public class SideKeyApp extends Application {

    private static final String TAG = "SideKey-App";

    private static SideKeyApp instance;

    private BluetoothService   bluetoothService;
    private PairingManager     pairingManager;
    private AutoSessionStarter autoSessionStarter;
    private SessionStore       sessionStore;
    private ChatManager        chatManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static SideKeyApp getInstance() {
        return instance;
    }

    public void initServices() {
        if (bluetoothService != null) return;

        sessionStore     = new SessionStore();
        pairingManager   = new PairingManager(this);
        bluetoothService = new BluetoothService(this);

        TransportSender transportSender = new TransportSender(bluetoothService);
        SendDispatcher.getInstance().init(transportSender);

        autoSessionStarter = new AutoSessionStarter(
                this, pairingManager, bluetoothService,
                sessionStore, ConnectionManager.getInstance());

        Log.d(TAG, "Core services initialised");
    }

    public BluetoothService   getBluetoothService()   { return bluetoothService; }
    public PairingManager     getPairingManager()      { return pairingManager; }
    public AutoSessionStarter getAutoSessionStarter()  { return autoSessionStarter; }
    public SessionStore       getSessionStore()        { return sessionStore; }

    public void setChatManager(ChatManager manager)    { this.chatManager = manager; }
    public ChatManager getChatManager()                { return chatManager; }
}