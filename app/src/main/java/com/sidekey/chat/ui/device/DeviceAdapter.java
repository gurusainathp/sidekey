package com.sidekey.chat.ui.device;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClick(DeviceItem device);
    }

    private final List<DeviceItem>      devices  = new ArrayList<>();
    private final OnDeviceClickListener listener;

    public DeviceAdapter(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void addDevice(DeviceItem item) {
        // Avoid duplicates by address
        for (DeviceItem existing : devices) {
            if (existing.getAddress().equals(item.getAddress())) return;
        }
        devices.add(item);
        notifyItemInserted(devices.size() - 1);
    }

    public void clear() {
        int size = devices.size();
        devices.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceItem item = devices.get(position);
        holder.tvDeviceName.setText(item.getDisplayName());
        holder.tvDeviceAddress.setText(item.getAddress());
        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(item));
    }

    @Override
    public int getItemCount() { return devices.size(); }
}