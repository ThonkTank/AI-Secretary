package com.aisecretary.taskmaster.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TaskAdapter - RecyclerView Adapter for Task items
 *
 * Implements ViewHolder pattern for efficient list rendering.
 * Handles task display with Design System styling.
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<TaskEntity> tasks;
    private TaskClickListener listener;

    /**
     * Interface for task interaction callbacks
     */
    public interface TaskClickListener {
        void onTaskClick(TaskEntity task);
        void onTaskCheckboxClick(TaskEntity task);
        void onTaskEditClick(TaskEntity task);
        void onTaskDeleteClick(TaskEntity task);
    }

    public TaskAdapter(TaskClickListener listener) {
        this.tasks = new ArrayList<>();
        this.listener = listener;
    }

    /**
     * ViewHolder for Task items
     */
    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        public TextView titleTextView;
        public TextView descriptionTextView;
        public TextView dueDateTextView;
        public TextView streakTextView;
        public TextView overdueWarningTextView;
        public TextView checkboxTextView;
        public LinearLayout infoContainer;
        public LinearLayout quickActionsContainer;
        public TextView editButton;
        public TextView deleteButton;

        public TaskViewHolder(View view) {
            super(view);
            titleTextView = view.findViewById(R.id.task_title);
            descriptionTextView = view.findViewById(R.id.task_description);
            dueDateTextView = view.findViewById(R.id.task_due_date);
            streakTextView = view.findViewById(R.id.task_streak);
            overdueWarningTextView = view.findViewById(R.id.task_overdue_warning);
            checkboxTextView = view.findViewById(R.id.task_checkbox);
            infoContainer = view.findViewById(R.id.info_container);
            quickActionsContainer = view.findViewById(R.id.quick_actions_container);
            editButton = view.findViewById(R.id.action_edit);
            deleteButton = view.findViewById(R.id.action_delete);
        }
    }

    /**
     * Create new ViewHolder
     */
    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_task, parent, false);
        return new TaskViewHolder(view);
    }

    /**
     * Bind data to ViewHolder
     */
    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        TaskEntity task = tasks.get(position);

        // Title with priority stars
        String titleText = task.getPriorityStars() + " " + task.title;
        holder.titleTextView.setText(titleText);

        // Set title color and style based on status
        if (task.isOverdue()) {
            holder.titleTextView.setTextColor(0xFFF44336); // Red
            holder.titleTextView.setPaintFlags(
                    holder.titleTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        } else if (task.completed) {
            holder.titleTextView.setTextColor(0xFF4CAF50); // Green
            holder.titleTextView.setPaintFlags(
                    holder.titleTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.titleTextView.setTextColor(0xFF000000); // Black
            holder.titleTextView.setPaintFlags(
                    holder.titleTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        // Description
        if (task.description != null && !task.description.isEmpty()) {
            holder.descriptionTextView.setText(task.description);
            holder.descriptionTextView.setVisibility(View.VISIBLE);
        } else {
            holder.descriptionTextView.setVisibility(View.GONE);
        }

        // Due Date
        if (task.dueAt > 0) {
            String dueDateText = formatDueDate(task.dueAt);
            holder.dueDateTextView.setText("📅 " + dueDateText);
            holder.dueDateTextView.setVisibility(View.VISIBLE);

            // Color code based on urgency
            if (task.isOverdue()) {
                holder.dueDateTextView.setTextColor(0xFFF44336); // Red
            } else if (task.isDueToday()) {
                holder.dueDateTextView.setTextColor(0xFFFFC107); // Yellow
            } else {
                holder.dueDateTextView.setTextColor(0xFF757575); // Grey
            }
        } else {
            holder.dueDateTextView.setVisibility(View.GONE);
        }

        // Streak Badge
        if (task.isRecurring && task.currentStreak > 0) {
            holder.streakTextView.setText("🔥 Streak: " + task.currentStreak);
            holder.streakTextView.setVisibility(View.VISIBLE);
        } else {
            holder.streakTextView.setVisibility(View.GONE);
        }

        // Show info container if any info is visible
        boolean hasInfo = holder.dueDateTextView.getVisibility() == View.VISIBLE ||
                holder.streakTextView.getVisibility() == View.VISIBLE;
        holder.infoContainer.setVisibility(hasInfo ? View.VISIBLE : View.GONE);

        // Overdue Warning
        if (task.isOverdue() && !task.completed) {
            long daysOverdue = task.getOverdueDuration() / 86400000;
            holder.overdueWarningTextView.setText("⚠️ OVERDUE (" + daysOverdue + " days)");
            holder.overdueWarningTextView.setVisibility(View.VISIBLE);
        } else {
            holder.overdueWarningTextView.setVisibility(View.GONE);
        }

        // Checkbox
        holder.checkboxTextView.setText(task.completed ? "✓" : "[ ]");
        holder.checkboxTextView.setTextColor(task.completed ? 0xFF4CAF50 : 0xFF757575);

        // Click Listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskClick(task);
            }
        });

        holder.checkboxTextView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskCheckboxClick(task);
            }
        });

        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskEditClick(task);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTaskDeleteClick(task);
            }
        });

        // Show quick actions on long click
        holder.itemView.setOnLongClickListener(v -> {
            toggleQuickActions(holder);
            return true;
        });
    }

    /**
     * Toggle visibility of quick actions
     */
    private void toggleQuickActions(TaskViewHolder holder) {
        if (holder.quickActionsContainer.getVisibility() == View.VISIBLE) {
            holder.quickActionsContainer.setVisibility(View.GONE);
        } else {
            holder.quickActionsContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Format due date for display
     */
    private String formatDueDate(long timestamp) {
        long now = System.currentTimeMillis();
        long dayStart = now - (now % 86400000);
        long tomorrow = dayStart + 86400000;
        long dayAfterTomorrow = tomorrow + 86400000;

        if (timestamp < dayStart) {
            // Past - show date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } else if (timestamp < tomorrow) {
            // Today - show time
            SimpleDateFormat sdf = new SimpleDateFormat("'Today' • HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } else if (timestamp < dayAfterTomorrow) {
            // Tomorrow
            SimpleDateFormat sdf = new SimpleDateFormat("'Tomorrow' • HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } else {
            // Future - show date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd • HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    /**
     * Get item count
     */
    @Override
    public int getItemCount() {
        return tasks.size();
    }

    /**
     * Update task list
     */
    public void setTasks(List<TaskEntity> tasks) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Get task at position
     */
    public TaskEntity getTaskAt(int position) {
        return tasks.get(position);
    }
}
