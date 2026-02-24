package com.autosecretary.views.taskTab;

import androidx.recyclerview.widget.RecyclerView;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.CheckBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;

import com.autosecretary.views.models.ViewSlotList.ViewSlot;
import com.autosecretary.R;

public class ListRowAdapter extends RecyclerView.Adapter<ListRowAdapter.TaskRowViewHolder> {
    List<ViewSlot> viewSlots;
    Consumer<ViewSlot> onCheck;
    Consumer<ViewSlot> onLongPress;

    public ListRowAdapter(List<ViewSlot> viewSlots, Consumer<ViewSlot> onCheck, Consumer<ViewSlot> onLongPress) {
        this.viewSlots = viewSlots;
        this.onCheck = onCheck;
        this.onLongPress = onLongPress;
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView start;
        TextView end;
        CheckBox checkBox;
        TextView deadlineCountdown;
        TextView streakDisplay;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.start = taskRow.findViewById(R.id.StartTime);
            this.end = taskRow.findViewById(R.id.EndTime);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
            this.deadlineCountdown = taskRow.findViewById(R.id.DeadlineCountdown);
            this.streakDisplay = taskRow.findViewById(R.id.StreakDisplay);
        }
    }

    // Adapter Methoden
    @Override
    public int getItemCount() {
        return viewSlots.size();
    }

    @Override
    public TaskRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View taskRow = LayoutInflater.from(parent.getContext()).inflate(R.layout.task_row, parent, false);
        return new TaskRowViewHolder(taskRow);
    }

    @Override
    public void onBindViewHolder(TaskRowViewHolder holder, int position) {
        ViewSlot viewSlot = viewSlots.get(position);
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);

        holder.title.setText(viewSlot.task.core.title);

        String startString = viewSlot.slot.start != null ? viewSlot.slot.start.format(DateTimeFormatter.ofPattern("HH:mm")) : "Nicht";
        String endString = viewSlot.slot.end != null ? viewSlot.slot.end.format(DateTimeFormatter.ofPattern("HH:mm")) : "Heute";
        holder.start.setText(startString);
        holder.end.setText(endString);
        holder.itemView.setPadding(step * viewSlot.depth, 0, 0, 0);

        // Deadline countdown
        if (viewSlot.task.core.deadline != null) {
            long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), viewSlot.task.core.deadline);
            if (daysUntil < 0) {
                holder.deadlineCountdown.setText("Fällig!");
                holder.deadlineCountdown.setTextColor(0xFFFF0000);
            } else if (daysUntil == 0) {
                holder.deadlineCountdown.setText("Heute");
                holder.deadlineCountdown.setTextColor(0xFFFF8800);
            } else {
                holder.deadlineCountdown.setText(daysUntil + "d");
                holder.deadlineCountdown.setTextColor(daysUntil <= 3 ? 0xFFFF8800 : 0xFF888888);
            }
            holder.deadlineCountdown.setVisibility(View.VISIBLE);
        } else {
            holder.deadlineCountdown.setVisibility(View.GONE);
        }

        // Streak
        int streak = viewSlot.task.core.history.currentStreak;
        if (streak > 0) {
            holder.streakDisplay.setText(streak + "x");
            holder.streakDisplay.setVisibility(View.VISIBLE);
        } else {
            holder.streakDisplay.setVisibility(View.GONE);
        }

        // In-progress visual state
        boolean inProgress = viewSlot.slot.realStart != null && !viewSlot.slot.completed;
        if (inProgress) {
            holder.itemView.setBackgroundColor(0x1A4CAF50);
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(0xFF4CAF50));
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
            holder.checkBox.setButtonTintList(null);
        }

        // Checkbox: prevent auto-toggle, let ViewModel manage state via data refresh
        holder.checkBox.setOnClickListener(v -> {
            holder.checkBox.setChecked(viewSlot.slot.completed);
            onCheck.accept(viewSlot);
        });
        holder.checkBox.setChecked(viewSlot.slot.completed);
        holder.checkBox.setEnabled(!viewSlot.slot.completed);

        holder.itemView.setOnLongClickListener(v -> {
            onLongPress.accept(viewSlot);
            return true;
        });
    }

    public void setList(List<ViewSlot> viewSlots) {
        this.viewSlots = viewSlots;
        notifyDataSetChanged();
    }
}
