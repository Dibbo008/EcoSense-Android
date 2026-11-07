package com.ecosense.app.ui.symptom;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ecosense.app.R;
import com.ecosense.app.data.remote.ChatRepository;

import java.util.ArrayList;
import java.util.List;

/** Minimal chat bubbles: user on the right (dark), EcoSense on the left (light). */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
    private final List<ChatRepository.Message> items = new ArrayList<>();

    public int add(ChatRepository.Message m) {
        for (ChatRepository.Message x : items) {
            if (x.id.equals(m.id)) return -1; // already shown
        }
        items.add(m);
        notifyItemInserted(items.size() - 1);
        return items.size() - 1;
    }

    public void remove(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void clear() {
        int n = items.size();
        items.clear();
        notifyItemRangeRemoved(0, n);
    }

    public int size() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChatRepository.Message m = items.get(position);
        h.bubble.setText(m.text);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bubble.getLayoutParams();
        lp.gravity = m.fromUser ? android.view.Gravity.END : android.view.Gravity.START;
        h.bubble.setLayoutParams(lp);
        h.bubble.setBackgroundResource(m.fromUser ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_bot);
        h.bubble.setTextColor(ContextCompat.getColor(h.bubble.getContext(),
                m.fromUser ? R.color.eco_on_navy : R.color.eco_text));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView bubble;

        VH(@NonNull View v) {
            super(v);
            bubble = v.findViewById(R.id.tvBubble);
        }
    }
}
