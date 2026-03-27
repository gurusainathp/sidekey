package com.sidekey.chat.ui.chat;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.R;

/**
 * ChatViewHolder — holds views for one message row.
 * Used for both sent and received bubbles — which layout is inflated
 * is decided by ChatAdapter based on ViewType.
 */
public class ChatViewHolder extends RecyclerView.ViewHolder {

    final TextView tvContent;
    final TextView tvTime;

    public ChatViewHolder(@NonNull View itemView) {
        super(itemView);
        tvContent = itemView.findViewById(R.id.tvMessageContent);
        tvTime    = itemView.findViewById(R.id.tvMessageTime);
    }
}