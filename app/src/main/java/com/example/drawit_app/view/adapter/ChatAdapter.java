package com.example.drawit_app.view.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.ItemChatMessageBinding;
import com.example.drawit_app.model.ChatMessage;

import java.util.List;

/**
 * Adapter for displaying chat messages during gameplay
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<ChatMessage> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        public MessageViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ChatMessage message) {
            switch (message.getType()) {
                case PLAYER_MESSAGE:
                    // Regular player message
                    binding.tvUsername.setText(message.getSender().getUsername());
                    binding.tvMessage.setText(message.getMessage());
                    binding.tvUsername.setTypeface(null, Typeface.NORMAL);
                    binding.tvMessage.setTypeface(null, Typeface.NORMAL);
                    binding.tvMessage.setTextColor(
                            ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorText));
                    break;
                    
                case CORRECT_GUESS:
                    // Correct guess notification
                    binding.tvUsername.setText(message.getSender().getUsername());
                    binding.tvMessage.setText(R.string.correct_guess);
                    binding.tvUsername.setTypeface(null, Typeface.BOLD);
                    binding.tvMessage.setTypeface(null, Typeface.BOLD);
                    binding.tvMessage.setTextColor(
                            ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorSuccess));
                    break;
                    
                case SYSTEM_MESSAGE:
                    // System notification
                    binding.tvUsername.setText(R.string.system);
                    binding.tvMessage.setText(message.getMessage());
                    binding.tvUsername.setTypeface(null, Typeface.BOLD_ITALIC);
                    binding.tvMessage.setTypeface(null, Typeface.ITALIC);
                    binding.tvMessage.setTextColor(
                            ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorPrimary));
                    break;
                    
                case CLOSE_GUESS:
                    // Close guess notification
                    binding.tvUsername.setText(message.getSender().getUsername());
                    binding.tvMessage.setText(R.string.close_guess);
                    binding.tvUsername.setTypeface(null, Typeface.NORMAL);
                    binding.tvMessage.setTypeface(null, Typeface.ITALIC);
                    binding.tvMessage.setTextColor(
                            ContextCompat.getColor(binding.getRoot().getContext(), R.color.colorWarning));
                    break;
            }
        }
    }
}
