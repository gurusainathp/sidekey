package com.sidekey.chat.ui.device;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.R;

public class DeviceViewHolder extends RecyclerView.ViewHolder {

    final TextView tvDeviceName;
    final TextView tvDeviceAddress;

    public DeviceViewHolder(@NonNull View itemView) {
        super(itemView);
        tvDeviceName    = itemView.findViewById(R.id.tvDeviceName);
        tvDeviceAddress = itemView.findViewById(R.id.tvDeviceAddress);
    }
}