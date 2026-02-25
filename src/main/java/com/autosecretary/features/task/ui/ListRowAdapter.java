package com.autosecretary.features.task.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.features.task.application.TaskListItem;
import com.autosecretary.features.task.ui.state.ViewSlotList.ViewSlot;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * RecyclerView adapter for task list rows.
 */
public class ListRowAdapter extends RecyclerView.Adapter<ListRowAdapter.TaskRowViewHolder> {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    List<ViewSlot> viewSlots;
    Consumer<ViewSlot> onCheck;
    Consumer<ViewSlot> onEdit;
    boolean interactionsEnabled = true;

    public ListRowAdapter(List<ViewSlot> viewSlots, Consumer<ViewSlot> onCheck, Consumer<ViewSlot> onEdit) {
        this.viewSlots = viewSlots;
        this.onCheck = onCheck;
        this.onEdit = onEdit;
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        LinearLayout root;
        TextView title;
        TextView start;
        TextView end;
        CheckBox checkBox;
        TextView deadlineCountdown;
        TextView streakDisplay;
        ImageButton editButton;
        TextView calendarChip;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.root = taskRow.findViewById(R.id.TaskRowRoot);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.start = taskRow.findViewById(R.id.StartTime);
            this.end = taskRow.findViewById(R.id.EndTime);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
            this.deadlineCountdown = taskRow.findViewById(R.id.DeadlineCountdown);
            this.streakDisplay = taskRow.findViewById(R.id.StreakDisplay);
            this.editButton = taskRow.findViewById(R.id.EditTaskButton);
            this.calendarChip = taskRow.findViewById(R.id.CalendarChip);
        }
    }

    @Override
    public int getItemCount() {
        return viewSlots.size();
    }

    @Override
    public TaskRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View taskRow = LayoutInflater.from(parent.getContext()).inflate(R.layout.task_row_item, parent, false);
        return new TaskRowViewHolder(taskRow);
    }

    @Override
    public void onBindViewHolder(TaskRowViewHolder holder, int position) {
        ViewSlot viewSlot = viewSlots.get(position);
        TaskListItem item = viewSlot.item;

        holder.title.setText(item.title);
        holder.itemView.setContentDescription(item.title);

        bindIndentation(holder, viewSlot.depth);
        bindTimeRange(holder, item);

        if (item.isCalendarEvent()) {
            bindCalendarEventRow(holder);
            return;
        }

        bindTaskRow(holder);
        bindDeadline(holder, item);
        bindStreak(holder, item);
        bindProgressState(holder, item);
        bindInteractions(holder, item, viewSlot);
    }

    private void bindIndentation(TaskRowViewHolder holder, int depth) {
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);
        holder.itemView.setPaddingRelative(
                step * depth,
                holder.itemView.getPaddingTop(),
                holder.itemView.getPaddingEnd(),
                holder.itemView.getPaddingBottom());
    }

    private void bindTaskRow(TaskRowViewHolder holder) {
        holder.root.setBackgroundResource(R.drawable.bg_task_row);
        holder.checkBox.setVisibility(View.VISIBLE);
        holder.deadlineCountdown.setVisibility(View.VISIBLE);
        holder.streakDisplay.setVisibility(View.VISIBLE);
        holder.editButton.setVisibility(View.VISIBLE);
        holder.calendarChip.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
    }

    private void bindCalendarEventRow(TaskRowViewHolder holder) {
        Context context = holder.itemView.getContext();
        holder.root.setBackgroundResource(R.drawable.bg_calendar_row);
        holder.checkBox.setVisibility(View.GONE);
        holder.deadlineCountdown.setVisibility(View.GONE);
        holder.streakDisplay.setVisibility(View.GONE);
        holder.editButton.setVisibility(View.GONE);
        holder.calendarChip.setVisibility(View.VISIBLE);
        holder.calendarChip.setText(context.getString(R.string.task_calendar_label));
        holder.checkBox.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setOnClickListener(null);
        ViewCompat.setStateDescription(holder.itemView, context.getString(R.string.task_calendar_state_description));
    }

    private void bindTimeRange(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        String startString = item.start != null
                ? item.start.format(TIME_FORMATTER)
                : context.getString(R.string.task_time_fallback_start);
        String endString = item.end != null
                ? item.end.format(TIME_FORMATTER)
                : context.getString(R.string.task_time_fallback_end);
        holder.start.setText(startString);
        holder.end.setText(endString);
    }

    private void bindDeadline(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        TaskListItem.DeadlineUrgency deadlineUrgency = item.deadlineUrgency();
        if (deadlineUrgency == TaskListItem.DeadlineUrgency.NONE) {
            holder.deadlineCountdown.setVisibility(View.GONE);
            holder.deadlineCountdown.setContentDescription(null);
            return;
        }

        long daysUntil = item.daysUntilDeadline();
        if (deadlineUrgency == TaskListItem.DeadlineUrgency.OVERDUE) {
            holder.deadlineCountdown.setText(R.string.task_deadline_overdue_label);
            holder.deadlineCountdown.setTextColor(ContextCompat.getColor(context, R.color.task_deadline_overdue));
            holder.deadlineCountdown.setContentDescription(context.getString(R.string.task_deadline_overdue_content_description));
        } else if (deadlineUrgency == TaskListItem.DeadlineUrgency.TODAY) {
            holder.deadlineCountdown.setText(R.string.task_deadline_today_label);
            holder.deadlineCountdown.setTextColor(ContextCompat.getColor(context, R.color.task_deadline_soon));
            holder.deadlineCountdown.setContentDescription(context.getString(R.string.task_deadline_today_content_description));
        } else {
            holder.deadlineCountdown.setText(context.getString(R.string.task_deadline_in_days_label, daysUntil));
            int countdownColor = deadlineUrgency == TaskListItem.DeadlineUrgency.SOON
                    ? R.color.task_deadline_soon
                    : R.color.task_deadline_future;
            holder.deadlineCountdown.setTextColor(ContextCompat.getColor(context, countdownColor));
            holder.deadlineCountdown.setContentDescription(context.getString(R.string.task_deadline_in_days_content_description, daysUntil));
        }
        holder.deadlineCountdown.setVisibility(View.VISIBLE);
    }

    private void bindStreak(TaskRowViewHolder holder, TaskListItem item) {
        if (item.streak > 0) {
            holder.streakDisplay.setText(holder.itemView.getContext().getString(R.string.task_streak_display, item.streak));
            holder.streakDisplay.setVisibility(View.VISIBLE);
        } else {
            holder.streakDisplay.setVisibility(View.GONE);
        }
    }

    private void bindProgressState(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        if (item.inProgress) {
            holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.task_in_progress_background));
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.task_in_progress_checkbox_tint)));
            ViewCompat.setStateDescription(holder.itemView, context.getString(R.string.task_in_progress_state_description));
        } else {
            holder.checkBox.setButtonTintList(null);
            ViewCompat.setStateDescription(holder.itemView, null);
        }
    }

    private void bindInteractions(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        holder.checkBox.setOnClickListener(v -> {
            holder.checkBox.setChecked(item.completed);
            onCheck.accept(viewSlot);
        });
        holder.checkBox.setChecked(item.completed);
        boolean checkable = !item.completed && item.slotId != null && interactionsEnabled;
        holder.checkBox.setEnabled(checkable);
        holder.checkBox.setAlpha(interactionsEnabled ? 1.0f : 0.4f);

        if (interactionsEnabled) {
            holder.itemView.setOnLongClickListener(v -> {
                onEdit.accept(viewSlot);
                return true;
            });
            holder.editButton.setOnClickListener(v -> onEdit.accept(viewSlot));
            holder.editButton.setAlpha(1.0f);
        } else {
            holder.itemView.setOnLongClickListener(null);
            holder.editButton.setOnClickListener(null);
            holder.editButton.setAlpha(0.4f);
        }
    }

    public void setList(List<ViewSlot> viewSlots) {
        this.viewSlots = viewSlots;
        notifyDataSetChanged();
    }

    public void setInteractionsEnabled(boolean enabled) {
        if (this.interactionsEnabled != enabled) {
            this.interactionsEnabled = enabled;
            notifyDataSetChanged();
        }
    }
}
