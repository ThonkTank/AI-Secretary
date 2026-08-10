package com.autosecretary.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.core.Obligation;
import com.autosecretary.core.PlanMove;
import com.autosecretary.core.PlanStep;
import com.autosecretary.core.TimePreference;
import com.google.android.material.button.MaterialButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class ObligationAdapter extends RecyclerView.Adapter<ObligationAdapter.Holder> {
    interface Listener {
        void onComplete(Obligation obligation);
        void onStepChanged(Obligation obligation, PlanStep step, boolean completed);
        void onMove(Obligation obligation, PlanMove move);
        void onEdit(Obligation obligation);
    }

    private final Listener listener;
    private List<Obligation> items = new ArrayList<>();

    ObligationAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<Obligation> values) {
        items = new ArrayList<>(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_obligation, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Obligation item = items.get(position);
        holder.title.setText(item.title);
        holder.meta.setText(metadata(item));
        List<PlanStep> steps = item.planStepsFor(java.time.LocalDate.now());
        holder.steps.setVisibility(steps.isEmpty() ? View.GONE : View.VISIBLE);
        holder.steps.removeAllViews();
        for (PlanStep step : steps) {
            CheckBox checkBox = new CheckBox(holder.itemView.getContext());
            checkBox.setText(step.title());
            checkBox.setChecked(step.completed());
            checkBox.setEnabled(item.isOpenOn(java.time.LocalDate.now()));
            checkBox.setOnCheckedChangeListener((button, completed) ->
                    listener.onStepChanged(item, step, completed));
            holder.steps.addView(checkBox);
        }
        holder.done.setEnabled(item.isOpenOn(java.time.LocalDate.now()));
        holder.done.setOnClickListener(view -> listener.onComplete(item));
        holder.order.setVisibility(item.isOpenOn(java.time.LocalDate.now()) ? View.VISIBLE : View.GONE);
        holder.order.setOnClickListener(view -> showOrderMenu(holder.order, item));
        holder.itemView.setOnClickListener(view -> listener.onEdit(item));
    }

    private void showOrderMenu(View anchor, Obligation item) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(R.string.move_first).setOnMenuItemClickListener(ignored -> move(item, PlanMove.FIRST));
        menu.getMenu().add(R.string.move_earlier).setOnMenuItemClickListener(ignored -> move(item, PlanMove.EARLIER));
        menu.getMenu().add(R.string.move_later).setOnMenuItemClickListener(ignored -> move(item, PlanMove.LATER));
        menu.getMenu().add(R.string.move_last).setOnMenuItemClickListener(ignored -> move(item, PlanMove.LAST));
        menu.show();
    }

    private boolean move(Obligation item, PlanMove move) {
        listener.onMove(item, move);
        return true;
    }

    private String metadata(Obligation item) {
        String preference = timePreference(item.timePreference);
        if (item.isRoutine()) {
            String due = item.nextDueDate == null ? "heute" : item.nextDueDate.toString();
            String streak = item.currentStreak > 0 ? " · 🔥 " + item.currentStreak : "";
            return "Alle " + item.cadenceDays + " Tage · fällig " + due + preference + streak;
        }
        if (item.completed) return "Erledigt";
        if (item.deadlineAt == null) return item.durationMinutes + " Min · ohne Deadline" + preference;
        return item.durationMinutes + " Min · bis "
                + item.deadlineAt.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")) + preference;
    }

    private String timePreference(TimePreference preference) {
        if (preference == null) return "";
        return switch (preference) {
            case MORNING -> " · morgens";
            case MIDDAY -> " · mittags";
            case EVENING -> " · abends";
        };
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final LinearLayout steps;
        final MaterialButton done;
        final MaterialButton order;

        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.RowTitle);
            meta = view.findViewById(R.id.RowMeta);
            steps = view.findViewById(R.id.RowSteps);
            done = view.findViewById(R.id.RowDone);
            order = view.findViewById(R.id.RowOrder);
        }
    }
}
