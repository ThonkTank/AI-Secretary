package com.autosecretary.features.task.ui;

import androidx.recyclerview.widget.RecyclerView;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.ImageButton;

import java.util.List;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;

import com.autosecretary.features.task.application.model.TaskListItem;
import com.autosecretary.features.task.ui.model.ViewSlotList.ViewSlot;
import com.autosecretary.R;

public class ListRowAdapter extends RecyclerView.Adapter<ListRowAdapter.TaskRowViewHolder> {
    List<ViewSlot> viewSlots;
    Consumer<ViewSlot> onCheck;
    Consumer<ViewSlot> onLongPress;
    Consumer<ViewSlot> onEditClick;

    public ListRowAdapter(List<ViewSlot> viewSlots, Consumer<ViewSlot> onCheck, Consumer<ViewSlot> onLongPress, Consumer<ViewSlot> onEditClick) {
        this.viewSlots = viewSlots;
        this.onCheck = onCheck;
        this.onLongPress = onLongPress;
        this.onEditClick = onEditClick;
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView start;
        TextView end;
        CheckBox checkBox;
        TextView deadlineCountdown;
        TextView streakDisplay;
        ImageButton editButton;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.start = taskRow.findViewById(R.id.StartTime);
            this.end = taskRow.findViewById(R.id.EndTime);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
            this.deadlineCountdown = taskRow.findViewById(R.id.DeadlineCountdown);
            this.streakDisplay = taskRow.findViewById(R.id.StreakDisplay);
            this.editButton = taskRow.findViewById(R.id.EditTaskButton);
        }
    }

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
        TaskListItem item = viewSlot.item;
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);

        holder.title.setText(item.title);

        String startString = item.start != null ? item.start.format(DateTimeFormatter.ofPattern("HH:mm")) : "Nicht";
        String endString = item.end != null ? item.end.format(DateTimeFormatter.ofPattern("HH:mm")) : "Heute";
        holder.start.setText(startString);
        holder.end.setText(endString);
        holder.itemView.setPadding(step * viewSlot.depth, 0, 0, 0);

        TaskListItem.DeadlineUrgency deadlineUrgency = item.deadlineUrgency();
        if (deadlineUrgency != TaskListItem.DeadlineUrgency.NONE) {
            long daysUntil = item.daysUntilDeadline();
            if (deadlineUrgency == TaskListItem.DeadlineUrgency.OVERDUE) {
                holder.deadlineCountdown.setText("Fällig!");
                holder.deadlineCountdown.setTextColor(0xFFFF0000);
            } else if (deadlineUrgency == TaskListItem.DeadlineUrgency.TODAY) {
                holder.deadlineCountdown.setText("Heute");
                holder.deadlineCountdown.setTextColor(0xFFFF8800);
            } else {
                holder.deadlineCountdown.setText(daysUntil + "d");
                holder.deadlineCountdown.setTextColor(deadlineUrgency == TaskListItem.DeadlineUrgency.SOON ? 0xFFFF8800 : 0xFF888888);
            }
            holder.deadlineCountdown.setVisibility(View.VISIBLE);
        } else {
            holder.deadlineCountdown.setVisibility(View.GONE);
        }

        if (item.streak > 0) {
            holder.streakDisplay.setText(item.streak + "x");
            holder.streakDisplay.setVisibility(View.VISIBLE);
        } else {
            holder.streakDisplay.setVisibility(View.GONE);
        }

        if (item.inProgress) {
            holder.itemView.setBackgroundColor(0x1A4CAF50);
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(0xFF4CAF50));
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
            holder.checkBox.setButtonTintList(null);
        }

        holder.checkBox.setOnClickListener(v -> {
            holder.checkBox.setChecked(item.completed);
            onCheck.accept(viewSlot);
        });
        holder.checkBox.setChecked(item.completed);
        holder.checkBox.setEnabled(!item.completed && item.slotId != null);

        holder.itemView.setOnLongClickListener(v -> {
            onLongPress.accept(viewSlot);
            return true;
        });

        holder.editButton.setOnClickListener(v -> onEditClick.accept(viewSlot));
    }

    public void setList(List<ViewSlot> viewSlots) {
        this.viewSlots = viewSlots;
        notifyDataSetChanged();
    }
}
