package com.auroranotesnative.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auroranotesnative.databinding.ItemNoteBinding;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.model.Note;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    private final List<Note> notes = new ArrayList<>();
    private final OnNoteClickListener listener;

    public NotesAdapter(OnNoteClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Note> items) {
        notes.clear();
        notes.addAll(items);
        notifyDataSetChanged();
    }


    public Note getNoteAt(int position) {
        return notes.get(position);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new NoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.bind(notes.get(position));
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        private final ItemNoteBinding binding;

        NoteViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Note note) {
            // Title
            binding.tvTitle.setText(
                    note.getTitle().isEmpty() ? "Untitled" : note.getTitle()
            );

            // Content preview
            binding.tvContent.setText(note.getContent());

            // FIXED: Human-readable time
            binding.tvUpdatedAt.setText(formatUpdatedAt(note.getUpdatedAt()));

            // Pinned indicator
            binding.tvPinned.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

            // Summary (AI)
            String summary = SummaryEngine.summarize(
                    note.getTitle(),
                    note.getContent()
            );

            if (summary == null || summary.isEmpty()) {
                binding.tvSummary.setVisibility(View.GONE);
            } else {
                binding.tvSummary.setVisibility(View.VISIBLE);
                binding.tvSummary.setText(summary);
            }

            // Click
            binding.getRoot().setOnClickListener(v -> listener.onNoteClick(note));
        }
    }

    /**
     * Format timestamp into human-friendly text
     */
    private String formatUpdatedAt(long updatedAt) {
        long now = System.currentTimeMillis();
        long diff = now - updatedAt;

        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "Updated just now";
        } else if (minutes < 60) {
            return "Updated " + minutes + " min ago";
        } else if (hours < 24) {
            return "Updated " + hours + " hours ago";
        } else {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("MMM d, yyyy", Locale.US);
            return "Updated at" + sdf.format(new Date(updatedAt));
        }
    }

}