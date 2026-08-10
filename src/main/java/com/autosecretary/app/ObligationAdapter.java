package com.autosecretary.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.core.Obligation;
import com.google.android.material.button.MaterialButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class ObligationAdapter extends RecyclerView.Adapter<ObligationAdapter.Holder> {
    interface Listener {
        void onComplete(Obligation obligation);
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
        List<String> steps = item.stepTitlesFor(java.time.LocalDate.now());
        holder.steps.setVisibility(steps.isEmpty() ? View.GONE : View.VISIBLE);
        holder.steps.setText(String.join("  →  ", steps));
        holder.done.setEnabled(item.isOpenOn(java.time.LocalDate.now()));
        holder.done.setOnClickListener(view -> listener.onComplete(item));
        holder.itemView.setOnClickListener(view -> listener.onEdit(item));
    }

    private String metadata(Obligation item) {
        if (item.isRoutine()) {
            String due = item.nextDueDate == null ? "heute" : item.nextDueDate.toString();
            String streak = item.currentStreak > 0 ? " · 🔥 " + item.currentStreak : "";
            return "Alle " + item.cadenceDays + " Tage · fällig " + due + streak;
        }
        if (item.completed) return "Erledigt";
        if (item.deadlineAt == null) return item.durationMinutes + " Min · ohne Deadline";
        return item.durationMinutes + " Min · bis "
                + item.deadlineAt.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView steps;
        final MaterialButton done;

        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.RowTitle);
            meta = view.findViewById(R.id.RowMeta);
            steps = view.findViewById(R.id.RowSteps);
            done = view.findViewById(R.id.RowDone);
        }
    }
}
