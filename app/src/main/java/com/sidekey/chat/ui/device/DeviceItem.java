package com.sidekey.chat.ui.device;

import android.bluetooth.BluetoothDevice;

/**
 * DeviceItem — represents one discovered or bonded phone in the device list.
 * Name is used exactly as reported by Bluetooth — no filtering or prefix stripping.
 */
public class DeviceItem {

    private final String          displayName;
    private final String          address;
    private final BluetoothDevice device;

    public DeviceItem(BluetoothDevice device, String name) {
        this.device      = device;
        this.address     = device.getAddress();
        this.displayName = (name != null && !name.isEmpty()) ? name : address;
    }

    public String          getDisplayName() { return displayName; }
    public String          getAddress()     { return address; }
    public BluetoothDevice getDevice()      { return device; }
}