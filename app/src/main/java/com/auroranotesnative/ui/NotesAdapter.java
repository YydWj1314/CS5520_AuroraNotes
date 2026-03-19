package com.auroranotesnative.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auroranotesnative.databinding.ItemNoteBinding;
import com.auroranotesnative.ai.SummaryEngine;
import com.auroranotesnative.model.Note;

import java.util.ArrayList;
import java.util.List;

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

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
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
            binding.tvTitle.setText(note.getTitle().isEmpty() ? "Untitled" : note.getTitle());
            binding.tvContent.setText(note.getContent());
            binding.tvUpdatedAt.setText("Updated " + note.getUpdatedAt());
            binding.tvPinned.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

            String summary = SummaryEngine.summarize(note.getTitle(), note.getContent());
            if (summary == null || summary.isEmpty()) {
                binding.tvSummary.setVisibility(View.GONE);
            } else {
                binding.tvSummary.setVisibility(View.VISIBLE);
                binding.tvSummary.setText(summary);
            }

            binding.getRoot().setOnClickListener(v -> listener.onNoteClick(note));
        }
    }
}
