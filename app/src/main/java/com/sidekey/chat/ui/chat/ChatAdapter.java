package com.sidekey.chat.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sidekey.chat.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ChatAdapter — RecyclerView adapter for the chat message list.
 *
 * Two ViewTypes:
 *   VIEW_TYPE_SENT     → right-aligned bubble (our messages)
 *   VIEW_TYPE_RECEIVED → left-aligned bubble (partner's messages)
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {

    private static final int VIEW_TYPE_SENT     = 0;
    private static final int VIEW_TYPE_RECEIVED = 1;

    private final List<MessageItem>    messages = new ArrayList<>();
    private final SimpleDateFormat     timeFmt  =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ── Data ──────────────────────────────────────────────────────────────────

    /**
     * Adds a message to the end of the list.
     * Must be called on the UI thread.
     */
    public void addMessage(MessageItem item) {
        messages.add(item);
        notifyItemInserted(messages.size() - 1);
    }

    public int getMessageCount() {
        return messages.size();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSent()
                ? VIEW_TYPE_SENT
                : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = viewType == VIEW_TYPE_SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;

        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutRes, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        MessageItem item = messages.get(position);
        holder.tvContent.setText(item.getContent());
        holder.tvTime.setText(timeFmt.format(new Date(item.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}