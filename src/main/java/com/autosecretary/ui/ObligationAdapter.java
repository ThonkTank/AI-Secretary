package com.autosecretary.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.databinding.RowObligationBinding;
import com.autosecretary.ui.WorkItemRow;

import java.util.ArrayList;
import java.util.List;

/** Pure renderer for complete immutable work-item rows. */
final class ObligationAdapter extends RecyclerView.Adapter<ObligationAdapter.Holder> {
    interface Listener {
        void onComplete(String id);
        void onMove(String id, MoveWorkItemUseCase.Direction direction);
        void onEdit(String id, boolean routine);
    }

    private final Listener listener;
    private List<WorkItemRow> items = List.of();

    ObligationAdapter(Listener listener) { this.listener = listener; }

    void submit(List<WorkItemRow> values) {
        items = new ArrayList<>(values);
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
        row.ObligationLeaf.setBackgroundResource(position % 2 == 0
                ? R.drawable.bg_leaf_middle : R.drawable.bg_leaf_low);
        row.ObligationLeaf.setRotation(position % 2 == 0 ? 0.7f : -0.7f);
        row.RowGroup.setText(item.group());
        row.RowTitle.setText(item.title());
        row.RowMeta.setText(item.metadata());
        row.RowSteps.setVisibility(item.totalSteps() == 0 ? View.GONE : View.VISIBLE);
        row.RowSteps.removeAllViews();
        if (item.totalSteps() > 0) {
            TextView progress = new TextView(row.getRoot().getContext());
            progress.setText(item.completedSteps() + " von " + item.totalSteps() + " Schritten");
            progress.setTextColor(row.getRoot().getContext().getColor(R.color.marker));
            progress.setTextSize(14);
            progress.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
            row.RowSteps.addView(progress);
        }
        row.RowDone.setEnabled(item.open());
        row.RowDone.setText(item.completed() ? "●" : "○");
        row.RowDone.setOnClickListener(view -> listener.onComplete(item.id()));
        row.RowOrder.setVisibility(item.open() ? View.VISIBLE : View.GONE);
        row.RowOrder.setOnClickListener(view -> showOrderMenu(row.RowOrder, item.id()));
        row.getRoot().setOnClickListener(view -> listener.onEdit(item.id(), item.routine()));
    }

    private void showOrderMenu(View anchor, String id) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        addMove(menu, R.string.move_first, id, MoveWorkItemUseCase.Direction.FIRST);
        addMove(menu, R.string.move_earlier, id, MoveWorkItemUseCase.Direction.EARLIER);
        addMove(menu, R.string.move_later, id, MoveWorkItemUseCase.Direction.LATER);
        addMove(menu, R.string.move_last, id, MoveWorkItemUseCase.Direction.LAST);
        menu.show();
    }

    private void addMove(PopupMenu menu, int label, String id,
                         MoveWorkItemUseCase.Direction direction) {
        menu.getMenu().add(label).setOnMenuItemClickListener(ignored -> {
            listener.onMove(id, direction);
            return true;
        });
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
