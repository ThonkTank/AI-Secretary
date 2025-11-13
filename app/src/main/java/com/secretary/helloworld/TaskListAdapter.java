package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying tasks in a ListView.
 * Extracted from TaskActivity inner class for better code organization.
 */
public class TaskListAdapter extends BaseAdapter {
    private final Activity context;
    private final List<Task> taskList;
    private final TaskActionListener listener;
    private final SimpleDateFormat dateFormat;

    /**
     * Interface for handling task actions
     */
    public interface TaskActionListener {
        void onTaskCheckChanged(Task task, boolean isChecked);
        void onTaskEdit(Task task);
        void onTaskDelete(Task task);
        void onTasksChanged(); // Called when tasks need to be reloaded
    }

    public TaskListAdapter(Activity context, List<Task> taskList, TaskActionListener listener) {
        this.context = context;
        this.taskList = taskList;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
    }

    @Override
    public int getCount() {
        return taskList.size();
    }

    @Override
    public Task getItem(int position) {
        return taskList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return taskList.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.task_list_item, parent, false);
        }

        Task task = getItem(position);

        // Find views
        CheckBox checkBox = convertView.findViewById(R.id.taskCheckBox);
        TextView titleText = convertView.findViewById(R.id.taskTitleText);
        TextView descriptionText = convertView.findViewById(R.id.taskDescriptionText);
        TextView priorityText = convertView.findViewById(R.id.taskPriorityText);
        Button editButton = convertView.findViewById(R.id.editTaskButton);
        Button deleteButton = convertView.findViewById(R.id.deleteTaskButton);

        // Set data
        checkBox.setChecked(task.isCompleted());
        titleText.setText(task.getTitle());

        // Strike through if completed
        if (task.isCompleted()) {
            titleText.setPaintFlags(titleText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            titleText.setPaintFlags(titleText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        // Description
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            descriptionText.setText(task.getDescription());
            descriptionText.setVisibility(View.VISIBLE);
        } else {
            descriptionText.setVisibility(View.GONE);
        }

        // Build info text
        String info = buildInfoText(task);
        priorityText.setText(info);

        // Set click listeners
        setupClickListeners(checkBox, editButton, deleteButton, task);

        return convertView;
    }

    private String buildInfoText(Task task) {
        StringBuilder info = new StringBuilder();

        // Category and Priority
        info.append(task.getCategory()).append(" | Priority: ").append(task.getPriorityString());

        // Add streak if > 0
        if (task.getCurrentStreak() > 0) {
            info.append(" | 🔥 ").append(task.getCurrentStreak()).append(" day");
            if (task.getCurrentStreak() > 1) {
                info.append("s");
            }
        }

        // Add due date if set
        if (task.getDueDate() > 0) {
            info.append(" | Due: ").append(dateFormat.format(new Date(task.getDueDate())));

            // Check if overdue
            if (!task.isCompleted() && task.getDueDate() < System.currentTimeMillis()) {
                info.append(" (OVERDUE)");
            }
        }

        // Add recurrence info
        if (task.isRecurring()) {
            info.append(" | 🔁 ").append(task.getRecurrenceString());

            // Add progress for frequency tasks
            if (task.getRecurrenceType() == Task.RECURRENCE_FREQUENCY) {
                info.append(" ").append(task.getProgressString());
            }

            // Show when interval tasks will reappear
            if (task.getRecurrenceType() == Task.RECURRENCE_INTERVAL && task.isCompleted()) {
                info.append(task.getNextAppearanceString());
            }
        }

        return info.toString();
    }

    private void setupClickListeners(CheckBox checkBox, Button editButton, Button deleteButton, Task task) {
        // Checkbox listener
        checkBox.setOnClickListener(v -> {
            listener.onTaskCheckChanged(task, checkBox.isChecked());
        });

        // Edit button
        editButton.setOnClickListener(v -> {
            listener.onTaskEdit(task);
        });

        // Delete button
        deleteButton.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        listener.onTaskDelete(task);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}