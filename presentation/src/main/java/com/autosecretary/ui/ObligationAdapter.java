package com.autosecretary.ui;

import android.view.LayoutInflater;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.presentation.R;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.presentation.databinding.RowObligationBinding;
import com.autosecretary.ui.WorkItemRow;

import java.util.ArrayList;
import java.util.List;

/** Pure renderer for complete immutable work-item rows. */
final class ObligationAdapter extends RecyclerView.Adapter<ObligationAdapter.Holder> {
    interface Listener {
        void onComplete(String id);
        void onMove(String id, MoveWorkItemUseCase.Direction direction);
        void onEdit(String id, boolean routine);
        void onOmit(String id);
        void onDelete(WorkItemRow item);
    }

    private final Listener listener;
    private List<WorkItemRow> items = List.of();
    private boolean evening;

    ObligationAdapter(Listener listener) { this.listener = listener; }

    void submit(List<WorkItemRow> values) {
        items = new ArrayList<>(values);
        notifyDataSetChanged();
    }

    void setEvening(boolean value) {
        if (evening == value) return;
        evening = value;
        notifyDataSetChanged();
    }

    @NonNull
    @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(RowObligationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        WorkItemRow item = items.get(position);
        RowObligationBinding row = holder.binding;
        row.ObligationLeaf.setBackgroundResource("überfällig".equals(item.group())
                ? R.drawable.bg_leaf_overdue
                : position % 2 == 0
                ? evening ? R.drawable.bg_leaf_middle_evening : R.drawable.bg_leaf_middle
                : evening ? R.drawable.bg_leaf_low_evening : R.drawable.bg_leaf_low);
        row.ObligationLeaf.setRotation(position % 2 == 0 ? 0.7f : -0.7f);
        boolean firstInGroup = position == 0
                || !items.get(position - 1).group().equals(item.group());
        row.RowGroup.setText(item.group());
        row.RowGroup.setTextColor(evening ? 0xFFA08B62
                : row.getRoot().getContext().getColor(R.color.marker));
        row.RowGroup.setVisibility(firstInGroup ? View.VISIBLE : View.GONE);
        row.RowTitle.setText(item.title());
        row.RowTitle.setPaintFlags(item.completed()
                ? row.RowTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : row.RowTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        row.RowTitle.setTextColor(evening
                ? item.completed() ? 0xFF7A6742 : 0xFFF8ECD2
                : row.getRoot().getContext().getColor(
                item.completed() ? R.color.completed : R.color.ink));
        row.RowMeta.setText(item.metadata());
        row.RowMeta.setTextColor(evening ? 0xFFC3AE86
                : row.getRoot().getContext().getColor(R.color.ink_muted));
        row.RowSteps.setVisibility(View.GONE);
        row.RowSteps.removeAllViews();
        row.RowProgress.setVisibility(item.totalSteps() == 0 ? View.GONE : View.VISIBLE);
        row.RowProgressBar.setMax(Math.max(1, item.totalSteps()));
        row.RowProgressBar.setProgress(item.completedSteps());
        row.RowProgressLabel.setText(item.completedSteps() + " von " + item.totalSteps());
        row.RowToday.setVisibility(item.group().contains("heute") ? View.VISIBLE : View.GONE);
        row.RowDone.setEnabled(item.open());
        row.RowDone.setText(item.completed() ? "●" : "○");
        row.RowDone.setOnClickListener(view -> listener.onComplete(item.id()));
        row.RowOrder.setVisibility(item.open() ? View.VISIBLE : View.GONE);
        row.RowOrder.setOnClickListener(view -> showRowMenu(row.RowOrder, item));
        row.getRoot().setOnClickListener(view -> listener.onEdit(item.id(), item.routine()));
    }

    private void showRowMenu(View anchor, WorkItemRow item) {
        LeafActionMenu.show(anchor, List.of(
                new LeafActionMenu.Action("Bearbeiten", false,
                        () -> listener.onEdit(item.id(), item.routine())),
                new LeafActionMenu.Action("Für heute holen", false,
                        () -> listener.onMove(item.id(), MoveWorkItemUseCase.Direction.FIRST)),
                new LeafActionMenu.Action("Diesmal aussetzen", false,
                        () -> listener.onOmit(item.id())),
                new LeafActionMenu.Action("Reihenfolge heute", false,
                        () -> listener.onMove(item.id(), MoveWorkItemUseCase.Direction.LAST)),
                new LeafActionMenu.Action("Löschen", true,
                        () -> listener.onDelete(item))));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final RowObligationBinding binding;
        Holder(RowObligationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
