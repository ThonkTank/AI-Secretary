package com.autosecretary.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.databinding.RowFocusBinding;
import com.autosecretary.ui.FocusRow;
import com.autosecretary.ui.StepRow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Pure renderer: all domain decisions already live in FocusRow. */
final class FocusAdapter extends RecyclerView.Adapter<FocusAdapter.Holder> {
    interface Listener {
        void onComplete(String id);
        void onStepChanged(String id, String stepId, boolean completed);
        void onMove(String id, MoveWorkItemUseCase.Direction direction);
    }

    private final Listener listener;
    private List<FocusRow> items = List.of();

    FocusAdapter(Listener listener) { this.listener = listener; }

    void submit(List<FocusRow> values) {
        items = new ArrayList<>(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(RowFocusBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        FocusRow item = items.get(position);
        RowFocusBinding row = holder.binding;
        row.FocusPosition.setText(position == 0 ? R.string.now
                : position == 1 ? R.string.next : R.string.later);
        row.FocusTitle.setText(item.title());
        boolean anchor = position == 0;
        row.FocusLeaf.setBackgroundResource(anchor ? R.drawable.bg_leaf_focus
                : position == 1 ? R.drawable.bg_leaf_middle : R.drawable.bg_leaf_low);
        row.FocusLeaf.setRotation(anchor ? -0.7f : position == 1 ? 1.1f : -0.8f);
        row.FocusLeaf.setElevation(dp(row.getRoot(), anchor ? 10 : 5));
        row.FocusTitle.setTextSize(anchor ? 34 : 23);
        row.FocusSteps.setVisibility(anchor && !item.steps().isEmpty() ? View.VISIBLE : View.GONE);
        row.FocusSteps.removeAllViews();
        int shown = Math.min(5, item.steps().size());
        for (int index = 0; index < shown; index++) {
            StepRow step = item.steps().get(index);
            CheckBox check = new CheckBox(row.getRoot().getContext());
            check.setText(step.title());
            check.setTextSize(19);
            check.setTextColor(row.getRoot().getContext().getColor(
                    step.completed() ? R.color.completed : R.color.ink));
            check.setChecked(step.completed());
            check.setOnCheckedChangeListener((button, completed) ->
                    listener.onStepChanged(item.id(), step.id(), completed));
            row.FocusSteps.addView(check);
        }
        if (anchor && item.steps().size() > shown) {
            TextView more = new TextView(row.getRoot().getContext());
            more.setText("und " + (item.steps().size() - shown) + " weitere Schritte");
            more.setTextColor(row.getRoot().getContext().getColor(R.color.marker));
            more.setTextSize(15);
            more.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
            more.setPadding(dp(row.getRoot(), 8), dp(row.getRoot(), 5), 0, 0);
            row.FocusSteps.addView(more);
        }
        String time = item.suggestedStart() == null ? "Heute, sobald Platz ist"
                : item.suggestedStart().format(DateTimeFormatter.ofPattern("HH:mm")) + "–"
                + item.suggestedEnd().format(DateTimeFormatter.ofPattern("HH:mm"));
        String context = item.precedingCalendarTitle() == null
                ? "" : " · nach " + item.precedingCalendarTitle();
        row.FocusMeta.setText(time + " · ca. " + item.durationMinutes() + " Min" + context);
        row.FocusDone.setOnClickListener(view -> listener.onComplete(item.id()));
        row.FocusLater.setOnClickListener(view ->
                listener.onMove(item.id(), MoveWorkItemUseCase.Direction.LAST));
        row.FocusOrder.setOnClickListener(view -> showOrderMenu(row.FocusOrder, item.id()));
        row.FocusLater.setVisibility(anchor ? View.VISIBLE : View.GONE);
        if (anchor) {
            long completed = item.steps().stream().filter(StepRow::completed).count();
            row.FocusDone.setText(item.steps().isEmpty() ? "Erledigt"
                    : completed == 0 ? "Alle erledigen"
                    : item.steps().size() - completed == 1 ? "letzten Schritt erledigen"
                    : "Rest erledigen");
            row.FocusDone.setTextColor(row.getRoot().getContext().getColor(R.color.white));
            row.FocusDone.setBackgroundTintList(ColorStateList.valueOf(
                    row.getRoot().getContext().getColor(R.color.forest)));
        } else {
            row.FocusDone.setText("○");
            row.FocusDone.setTextColor(row.getRoot().getContext().getColor(R.color.outline));
            row.FocusDone.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        }
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

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final RowFocusBinding binding;
        Holder(RowFocusBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
