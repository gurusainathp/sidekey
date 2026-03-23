package com.sidekey.chat.bluetooth;

import java.util.UUID;

public class BluetoothConstants {

    /**
     * Fixed UUID for SideKey Bluetooth service.
     * Both devices must use the same UUID to connect.
     * Never change this after first release.
     */
    public static final UUID APP_UUID =
            UUID.fromString("e3a1c2d4-5b6f-7890-abcd-ef1234567890");

    public static final String APP_NAME = "SideKey";

    // How long to wait for a connection attempt (ms)
    public static final int CONNECT_TIMEOUT_MS = 10_000;

    // Private constructor — no instances
    private BluetoothConstants() {}
}