package com.sidekey.chat.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TrustedDeviceStore — persists paired device records locally.
 *
 * Stored per device address in SharedPreferences as JSON.
 * Upgraded to encrypted SQLite in a later phase.
 */
public class TrustedDeviceStore {

    private static final String TAG       = "SideKey-TrustedStore";
    private static final String PREFS     = "trusted_devices";
    private static final String KEY_PFX   = "device_";

    private final SharedPreferences prefs;

    public TrustedDeviceStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public void saveTrustedDevice(String name, String address,
                                  String fingerprint) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name",        name);
            obj.put("address",     address);
            obj.put("fingerprint", fingerprint);
            obj.put("lastSeen",    System.currentTimeMillis());
            obj.put("isTrusted",   true);

            prefs.edit().putString(KEY_PFX + address, obj.toString()).apply();
            Log.d(TAG, "Trusted device saved: " + name + " [" + address + "]");
        } catch (Exception e) {
            Log.e(TAG, "saveTrustedDevice failed: " + e.getMessage());
        }
    }

    public void updateLastSeen(String address) {
        try {
            String raw = prefs.getString(KEY_PFX + address, null);
            if (raw == null) return;
            JSONObject obj = new JSONObject(raw);
            obj.put("lastSeen", System.currentTimeMillis());
            prefs.edit().putString(KEY_PFX + address, obj.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "updateLastSeen failed: " + e.getMessage());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public boolean isTrusted(String address) {
        return prefs.contains(KEY_PFX + address);
    }

    public TrustedDevice getTrustedDevice(String address) {
        String raw = prefs.getString(KEY_PFX + address, null);
        return raw != null ? parse(raw) : null;
    }

    public List<TrustedDevice> getAllTrustedDevices() {
        List<TrustedDevice> result = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(KEY_PFX) && entry.getValue() instanceof String) {
                TrustedDevice td = parse((String) entry.getValue());
                if (td != null) result.add(td);
            }
        }
        return result;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void removeTrustedDevice(String address) {
        prefs.edit().remove(KEY_PFX + address).apply();
        Log.d(TAG, "Removed trusted device: " + address);
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    public static class TrustedDevice {
        public final String  name;
        public final String  address;
        public final String  fingerprint;
        public final long    lastSeen;
        public final boolean isTrusted;

        TrustedDevice(String name, String address, String fingerprint,
                      long lastSeen, boolean isTrusted) {
            this.name        = name;
            this.address     = address;
            this.fingerprint = fingerprint;
            this.lastSeen    = lastSeen;
            this.isTrusted   = isTrusted;
        }
    }

    private TrustedDevice parse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return new TrustedDevice(
                    obj.optString("name", "Unknown"),
                    obj.optString("address", ""),
                    obj.optString("fingerprint", ""),
                    obj.optLong("lastSeen", 0),
                    obj.optBoolean("isTrusted", true)
            );
        } catch (Exception e) {
            Log.e(TAG, "parse failed: " + e.getMessage());
            return null;
        }
    }
}