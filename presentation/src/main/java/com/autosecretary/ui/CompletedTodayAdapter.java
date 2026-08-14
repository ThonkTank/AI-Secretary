package com.autosecretary.ui;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.domain.WorkItem;
import com.autosecretary.presentation.databinding.RowCompletedTodayBinding;

/** Regular, diffable rows for work completed today. */
final class CompletedTodayAdapter
        extends ListAdapter<WorkItem, CompletedTodayAdapter.Holder> {
    private static final DiffUtil.ItemCallback<WorkItem> DIFFERENCE =
            new DiffUtil.ItemCallback<>() {
                @Override public boolean areItemsTheSame(
                        @NonNull WorkItem oldItem, @NonNull WorkItem newItem) {
                    return oldItem.id().equals(newItem.id());
                }
                @Override public boolean areContentsTheSame(
                        @NonNull WorkItem oldItem, @NonNull WorkItem newItem) {
                    return oldItem.title().equals(newItem.title());
                }
            };

    CompletedTodayAdapter() { super(DIFFERENCE); }

    @NonNull @Override public Holder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        return new Holder(RowCompletedTodayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        WorkItem item = getItem(position);
        holder.binding.CompletedTitle.setText("heute erledigt\n" + item.title());
        holder.binding.CompletedTitle.setPaintFlags(
                holder.binding.CompletedTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        holder.binding.CompletedTitle.setRotation(position % 2 == 0 ? 1.1f : -1.0f);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final RowCompletedTodayBinding binding;
        Holder(RowCompletedTodayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
