package com.autosecretary.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
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
        void onOmit(String id);
    }

    private final Listener listener;
    private List<FocusRow> items = List.of();
    private int startPosition;
    private int totalVisible;
    private boolean evening;
    private String movedId;
    private String movedLabel;
    private long movedUntil;

    FocusAdapter(Listener listener) { this.listener = listener; }

    void submit(List<FocusRow> values) {
        submit(values, 0, values.size());
    }

    void setEvening(boolean value) {
        if (evening == value) return;
        evening = value;
        notifyDataSetChanged();
    }

    void submit(List<FocusRow> values, int firstPosition, int total) {
        items = new ArrayList<>(values);
        startPosition = firstPosition;
        totalVisible = total;
        notifyDataSetChanged();
    }

    @NonNull
    @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(RowFocusBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        FocusRow item = items.get(position);
        int visualPosition = startPosition + position;
        RowFocusBinding row = holder.binding;
        row.FocusLeaf.animate().cancel();
        row.FocusLeaf.setTranslationX(0f);
        row.FocusLeaf.setTranslationY(0f);
        row.FocusLeaf.setAlpha(1f);
        String marker = item.overdue() ? "überfällig"
                : row.getRoot().getContext().getString(visualPosition == 0 ? R.string.now
                : visualPosition == 1 ? R.string.next : R.string.later);
        boolean highlighted = item.id().equals(movedId)
                && android.os.SystemClock.uptimeMillis() < movedUntil;
        row.FocusPosition.setText(highlighted ? marker + " · " + movedLabel : marker);
        row.FocusPosition.setTextColor(evening ? 0xFFF0A03C
                : row.getRoot().getContext().getColor(R.color.forest));
        row.FocusLeaf.setForeground(highlighted
                ? row.getRoot().getContext().getDrawable(R.drawable.bg_leaf_highlight) : null);
        if (highlighted) row.FocusLeaf.postDelayed(() -> {
            if (item.id().equals(movedId)) {
                movedId = null;
                int changed = holder.getBindingAdapterPosition();
                if (changed != RecyclerView.NO_POSITION) notifyItemChanged(changed);
            }
        }, Math.max(1, movedUntil - android.os.SystemClock.uptimeMillis()));
        row.FocusTitle.setText(item.title());
        row.FocusTitle.setTextColor(evening ? 0xFFF8ECD2
                : row.getRoot().getContext().getColor(R.color.ink));
        boolean anchor = visualPosition == 0;
        boolean secondFullBlock = visualPosition == 1 && totalVisible == 2;
        row.FocusLeaf.setBackgroundResource(item.overdue() ? R.drawable.bg_leaf_overdue
                : anchor ? evening ? R.drawable.bg_leaf_focus_evening : R.drawable.bg_leaf_focus
                : visualPosition == 1
                ? evening ? R.drawable.bg_leaf_middle_evening : R.drawable.bg_leaf_middle
                : evening ? R.drawable.bg_leaf_low_evening : R.drawable.bg_leaf_low);
        row.FocusLeaf.setRotation(anchor ? -0.7f : visualPosition == 1 ? 1.1f : -0.8f);
        row.FocusLeaf.setElevation(dp(row.getRoot(), anchor ? 10 : 5));
        row.FocusTitle.setTextSize(anchor ? (item.title().length() > 42 ? 30 : 37)
                : secondFullBlock ? 28 : 23);
        Typeface titleTypeface = ResourcesCompat.getFont(row.getRoot().getContext(),
                anchor || secondFullBlock ? R.font.newsreader_extra_light : R.font.newsreader);
        row.FocusTitle.setTypeface(titleTypeface);
        row.FocusSteps.setVisibility((anchor || secondFullBlock) && !item.steps().isEmpty()
                ? View.VISIBLE : View.GONE);
        row.FocusSteps.removeAllViews();
        int shown = Math.min(5, item.steps().size());
        for (int index = 0; index < shown; index++) {
            StepRow step = item.steps().get(index);
            CheckBox check = new CheckBox(row.getRoot().getContext());
            check.setText(step.title());
            check.setTextSize(19);
            check.setTextColor(evening
                    ? step.completed() ? 0xFF7A6742 : 0xFFF8ECD2
                    : row.getRoot().getContext().getColor(
                    step.completed() ? R.color.completed : R.color.ink));
            check.setTypeface(ResourcesCompat.getFont(
                    row.getRoot().getContext(), R.font.alegreya_sans));
            check.setPaintFlags(step.completed()
                    ? check.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : check.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            check.setChecked(step.completed());
            check.setOnCheckedChangeListener((button, completed) ->
                    listener.onStepChanged(item.id(), step.id(), completed));
            row.FocusSteps.addView(check);
        }
        if ((anchor || secondFullBlock) && item.steps().size() > shown) {
            TextView more = new TextView(row.getRoot().getContext());
            int remaining = item.steps().size() - shown;
            more.setText(remaining == 1 ? "und ein weiterer Schritt"
                    : "und " + remaining + " weitere Schritte");
            more.setTextColor(row.getRoot().getContext().getColor(R.color.marker));
            more.setTextSize(15);
            more.setTypeface(ResourcesCompat.getFont(
                    row.getRoot().getContext(), R.font.newsreader_light_italic));
            more.setPadding(dp(row.getRoot(), 8), dp(row.getRoot(), 5), 0, 0);
            row.FocusSteps.addView(more);
        }
        String time = item.suggestedStart() == null ? "heute, sobald Platz ist"
                : "voraussichtlich ab "
                + item.suggestedStart().format(DateTimeFormatter.ofPattern("HH:mm"));
        String context = item.precedingCalendarTitle() == null
                ? "" : " · nach " + item.precedingCalendarTitle();
        row.FocusMeta.setText(time + " · " + durationCopy(item.durationMinutes()) + context);
        row.FocusMeta.setTextColor(evening ? 0xFFC3AE86
                : row.getRoot().getContext().getColor(R.color.ink_muted));
        row.FocusDone.setOnClickListener(view -> listener.onComplete(item.id()));
        row.getRoot().setTag(item.id());
        row.FocusLater.setOnClickListener(view -> listener.onOmit(item.id()));
        row.FocusOrder.setOnClickListener(view -> showOrderMenu(row.FocusOrder, item.id()));
        row.FocusLater.setVisibility(anchor || secondFullBlock ? View.VISIBLE : View.GONE);
        row.FocusLater.setText(item.routine() && item.overdue()
                ? "diesmal überspringen" : "später");
        row.FocusLater.setTextColor(evening ? 0xFFC3AE86
                : row.getRoot().getContext().getColor(R.color.ink_muted));
        if (anchor || secondFullBlock) {
            long completed = item.steps().stream().filter(StepRow::completed).count();
            row.FocusDone.setText(item.steps().isEmpty() ? "Erledigt"
                    : completed == 0 ? "Alle erledigen"
                    : item.steps().size() - completed == 1 ? "letzten Schritt erledigen"
                    : "Rest erledigen");
            row.FocusDone.setTextColor(evening ? 0xFF231A0E
                    : row.getRoot().getContext().getColor(R.color.action_text));
            row.FocusDone.setBackgroundTintList(ColorStateList.valueOf(
                    evening ? 0xFFF0A03C
                            : row.getRoot().getContext().getColor(R.color.forest)));
        } else {
            row.FocusDone.setText("○");
            row.FocusDone.setTextColor(evening ? 0xFF7E6C48
                    : row.getRoot().getContext().getColor(R.color.outline));
            row.FocusDone.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        }
    }

    private void showOrderMenu(View anchor, String id) {
        LeafActionMenu.show(anchor, List.of(
                new LeafActionMenu.Action("Zuerst · ganz nach vorn", false,
                        () -> move(id, MoveWorkItemUseCase.Direction.FIRST, "ganz nach vorn")),
                new LeafActionMenu.Action("Eins nach vorne", false,
                        () -> move(id, MoveWorkItemUseCase.Direction.EARLIER, "eine Stelle nach vorn")),
                new LeafActionMenu.Action("Eins nach hinten", false,
                        () -> move(id, MoveWorkItemUseCase.Direction.LATER, "eine Stelle nach hinten")),
                new LeafActionMenu.Action("Zuletzt · ans Ende", false,
                        () -> move(id, MoveWorkItemUseCase.Direction.LAST, "ans Ende")),
                new LeafActionMenu.Action("Später", false, () -> listener.onOmit(id))));
    }

    private void move(String id, MoveWorkItemUseCase.Direction direction, String label) {
        movedId = id;
        movedLabel = label;
        movedUntil = android.os.SystemClock.uptimeMillis() + 1_000L;
        listener.onMove(id, direction);
    }

    @Override public int getItemCount() { return items.size(); }

    private static String durationCopy(int minutes) {
        return switch (minutes) {
            case 15 -> "etwa eine Viertelstunde";
            case 30 -> "etwa eine halbe Stunde";
            case 45 -> "etwa drei Viertelstunden";
            case 60 -> "etwa eine Stunde";
            default -> "ca. " + minutes + " Min";
        };
    }

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
