package com.autosecretary.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.core.PlanItem;
import com.autosecretary.core.PlanMove;
import com.autosecretary.core.PlanStep;
import com.google.android.material.button.MaterialButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class FocusAdapter extends RecyclerView.Adapter<FocusAdapter.Holder> {
    interface Listener {
        void onComplete(PlanItem item);
        void onStepChanged(PlanItem item, PlanStep step, boolean completed);
        void onMove(PlanItem item, PlanMove move);
    }

    private final Listener listener;
    private List<PlanItem> items = new ArrayList<>();

    FocusAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<PlanItem> values) {
        items = new ArrayList<>(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_focus, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlanItem item = items.get(position);
        holder.position.setText(position == 0 ? R.string.now : position == 1 ? R.string.next : R.string.later);
        holder.title.setText(item.obligation().title);
        boolean anchor = position == 0;
        holder.leaf.setBackgroundResource(anchor
                ? R.drawable.bg_leaf_focus
                : position == 1 ? R.drawable.bg_leaf_middle : R.drawable.bg_leaf_low);
        holder.leaf.setRotation(anchor ? -0.7f : position == 1 ? 1.1f : -0.8f);
        holder.leaf.setElevation(dp(holder.itemView, anchor ? 10 : 5));
        holder.title.setTextSize(anchor ? 34 : 23);
        holder.steps.setVisibility(anchor && !item.steps().isEmpty() ? View.VISIBLE : View.GONE);
        holder.steps.removeAllViews();
        int shownSteps = Math.min(5, item.steps().size());
        for (int index = 0; index < shownSteps; index++) {
            PlanStep step = item.steps().get(index);
            CheckBox checkBox = new CheckBox(holder.itemView.getContext());
            checkBox.setText(step.title());
            checkBox.setTextSize(19);
            checkBox.setTextColor(holder.itemView.getContext().getColor(
                    step.completed() ? R.color.completed : R.color.ink));
            checkBox.setChecked(step.completed());
            checkBox.setOnCheckedChangeListener((button, completed) ->
                    listener.onStepChanged(item, step, completed));
            holder.steps.addView(checkBox);
        }
        if (anchor && item.steps().size() > shownSteps) {
            TextView more = new TextView(holder.itemView.getContext());
            more.setText("und " + (item.steps().size() - shownSteps) + " weitere Schritte");
            more.setTextColor(holder.itemView.getContext().getColor(R.color.marker));
            more.setTextSize(15);
            more.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC);
            more.setPadding(dp(holder.itemView, 8), dp(holder.itemView, 5), 0, 0);
            holder.steps.addView(more);
        }
        String time = item.suggestedStart() == null
                ? "Heute, sobald Platz ist"
                : item.suggestedStart().format(DateTimeFormatter.ofPattern("HH:mm"))
                    + "–" + item.suggestedEnd().format(DateTimeFormatter.ofPattern("HH:mm"));
        String calendarContext = item.precedingCalendarBlock() == null
                ? ""
                : " · nach " + item.precedingCalendarBlock().title();
        holder.meta.setText(time + " · ca. " + item.obligation().durationMinutes + " Min" + calendarContext);
        holder.done.setOnClickListener(view -> listener.onComplete(item));
        holder.later.setOnClickListener(view -> listener.onMove(item, PlanMove.LAST));
        holder.order.setOnClickListener(view -> showOrderMenu(holder.order, item));
        holder.later.setVisibility(anchor ? View.VISIBLE : View.GONE);
        if (anchor) {
            long completedSteps = item.steps().stream().filter(PlanStep::completed).count();
            String label = item.steps().isEmpty() ? "Erledigt"
                    : completedSteps == 0 ? "Alle erledigen"
                    : item.steps().size() - completedSteps == 1 ? "letzten Schritt erledigen"
                    : "Rest erledigen";
            holder.done.setText(label);
            holder.done.setTextColor(holder.itemView.getContext().getColor(R.color.white));
            holder.done.setBackgroundTintList(ColorStateList.valueOf(
                    holder.itemView.getContext().getColor(R.color.forest)));
        } else {
            holder.done.setText("○");
            holder.done.setTextColor(holder.itemView.getContext().getColor(R.color.outline));
            holder.done.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        }
    }

    private void showOrderMenu(View anchor, PlanItem item) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(R.string.move_first).setOnMenuItemClickListener(ignored -> move(item, PlanMove.FIRST));
        menu.getMenu().add(R.string.move_earlier).setOnMenuItemClickListener(ignored -> move(item, PlanMove.EARLIER));
        menu.getMenu().add(R.string.move_later).setOnMenuItemClickListener(ignored -> move(item, PlanMove.LATER));
        menu.getMenu().add(R.string.move_last).setOnMenuItemClickListener(ignored -> move(item, PlanMove.LAST));
        menu.show();
    }

    private boolean move(PlanItem item, PlanMove move) {
        listener.onMove(item, move);
        return true;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView position;
        final View leaf;
        final TextView title;
        final LinearLayout steps;
        final TextView meta;
        final MaterialButton done;
        final MaterialButton later;
        final MaterialButton order;

        Holder(View view) {
            super(view);
            position = view.findViewById(R.id.FocusPosition);
            leaf = view.findViewById(R.id.FocusLeaf);
            title = view.findViewById(R.id.FocusTitle);
            steps = view.findViewById(R.id.FocusSteps);
            meta = view.findViewById(R.id.FocusMeta);
            done = view.findViewById(R.id.FocusDone);
            later = view.findViewById(R.id.FocusLater);
            order = view.findViewById(R.id.FocusOrder);
        }
    }
}
