package com.autosecretary.features.task.ui;

import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
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

/**
 * RecyclerView adapter for task list rows. Renders each {@link ViewSlot} with tree indentation,
 * deadline urgency coloring, in-progress/completed visual states, and streak display.
 */
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

        // Tree indentation: depth × indent_step for parent-child hierarchy
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);

        // Time display: start-end range, fallback text for unscheduled tasks
        holder.title.setText(item.title);
        holder.itemView.setContentDescription(item.title);

        // Time display: start-end range, fallback text for unscheduled tasks
        String startString = item.start != null ? item.start.format(DateTimeFormatter.ofPattern("HH:mm")) : "Nicht";
        String endString = item.end != null ? item.end.format(DateTimeFormatter.ofPattern("HH:mm")) : "Heute";
        holder.start.setText(startString);
        holder.end.setText(endString);
        holder.itemView.setPaddingRelative(
                step * viewSlot.depth,
                holder.itemView.getPaddingTop(),
                holder.itemView.getPaddingEnd(),
                holder.itemView.getPaddingBottom());

        // Deadline urgency: red=overdue, orange=today/soon, gray=future, hidden=none
        TaskListItem.DeadlineUrgency deadlineUrgency = item.deadlineUrgency();
        if (deadlineUrgency != TaskListItem.DeadlineUrgency.NONE) {
            long daysUntil = item.daysUntilDeadline();
            if (deadlineUrgency == TaskListItem.DeadlineUrgency.OVERDUE) {
                holder.deadlineCountdown.setText("! Fällig");
                holder.deadlineCountdown.setTextColor(0xFFFF0000); // red = overdue
                holder.deadlineCountdown.setContentDescription("Überfällig");
            } else if (deadlineUrgency == TaskListItem.DeadlineUrgency.TODAY) {
                holder.deadlineCountdown.setText("! Heute fällig");
                holder.deadlineCountdown.setTextColor(0xFFFF8800); // orange = today/soon
                holder.deadlineCountdown.setContentDescription("Heute fällig");
            } else {
                holder.deadlineCountdown.setText("in " + daysUntil + "d");
                holder.deadlineCountdown.setTextColor(deadlineUrgency == TaskListItem.DeadlineUrgency.SOON ? 0xFFFF8800 : 0xFF888888); // orange or gray
                holder.deadlineCountdown.setContentDescription("Fällig in " + daysUntil + " Tagen");
            }
            holder.deadlineCountdown.setVisibility(View.VISIBLE);
        } else {
            holder.deadlineCountdown.setVisibility(View.GONE);
            holder.deadlineCountdown.setContentDescription(null);
        }

        // Streak display: consecutive successful periods shown as "Nx"
        if (item.streak > 0) {
            holder.streakDisplay.setText(item.streak + "x");
            holder.streakDisplay.setVisibility(View.VISIBLE);
        } else {
            holder.streakDisplay.setVisibility(View.GONE);
        }

        // In-progress state: green background + checkbox tint when started but not completed
        if (item.inProgress) {
            holder.itemView.setBackgroundColor(0x1A4CAF50); // translucent green background
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(0xFF4CAF50)); // green checkbox
            ViewCompat.setStateDescription(holder.itemView, "In Bearbeitung");
        } else {
            holder.itemView.setBackgroundColor(0x00000000);
            holder.checkBox.setButtonTintList(null);
            ViewCompat.setStateDescription(holder.itemView, null);
        }

        // Completion state: checkbox enabled only when slot exists and not yet completed
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
