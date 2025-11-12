package com.secretary.helloworld.ui.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.secretary.helloworld.R;
import com.secretary.helloworld.Task;
import com.secretary.helloworld.AppLogger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying tasks in a ListView.
 * Separated from TaskActivity for better code organization.
 */
public class TaskListAdapter extends BaseAdapter {
    private static final String TAG = "TaskListAdapter";

    private final Context context;
    private final List<Task> taskList;
    private final TaskActionListener listener;
    private final AppLogger logger;
    private final SimpleDateFormat dateFormat;

    /**
     * Interface for handling task actions
     */
    public interface TaskActionListener {
        void onTaskCheckedChanged(Task task, boolean isChecked);
        void onTaskEdit(Task task);
        void onTaskDelete(Task task);
    }

    public TaskListAdapter(Context context, List<Task> taskList, TaskActionListener listener) {
        this.context = context;
        this.taskList = taskList;
        this.listener = listener;
        this.logger = AppLogger.getInstance(context);
        this.dateFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());
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
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.task_list_item, parent, false);

            holder = new ViewHolder();
            holder.checkBox = convertView.findViewById(R.id.taskCheckBox);
            holder.titleText = convertView.findViewById(R.id.taskTitleText);
            holder.descriptionText = convertView.findViewById(R.id.taskDescriptionText);
            holder.priorityText = convertView.findViewById(R.id.taskPriorityText);
            holder.editButton = convertView.findViewById(R.id.editTaskButton);
            holder.deleteButton = convertView.findViewById(R.id.deleteTaskButton);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Task task = getItem(position);
        bindTaskToView(holder, task);

        return convertView;
    }

    private void bindTaskToView(ViewHolder holder, Task task) {
        // Set checkbox state
        holder.checkBox.setChecked(task.isCompleted());

        // Set title with strike-through if completed
        holder.titleText.setText(task.getTitle());
        if (task.isCompleted()) {
            holder.titleText.setPaintFlags(holder.titleText.getPaintFlags() |
                                          Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.titleText.setPaintFlags(holder.titleText.getPaintFlags() &
                                          (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        // Set description
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            holder.descriptionText.setText(task.getDescription());
            holder.descriptionText.setVisibility(View.VISIBLE);
        } else {
            holder.descriptionText.setVisibility(View.GONE);
        }

        // Build info text (priority, category, due date, recurrence)
        String info = buildInfoText(task);
        holder.priorityText.setText(info);

        // Set click listeners
        holder.checkBox.setOnClickListener(v -> {
            listener.onTaskCheckedChanged(task, holder.checkBox.isChecked());
        });

        holder.editButton.setOnClickListener(v -> {
            listener.onTaskEdit(task);
        });

        holder.deleteButton.setOnClickListener(v -> {
            showDeleteConfirmation(task);
        });
    }

    private String buildInfoText(Task task) {
        StringBuilder info = new StringBuilder();

        // Priority
        switch (task.getPriority()) {
            case 0: info.append("⚪ Low"); break;
            case 1: info.append("🟡 Medium"); break;
            case 2: info.append("🔴 High"); break;
        }

        // Category
        if (task.getCategory() != null && !task.getCategory().isEmpty()) {
            info.append(" | ").append(task.getCategory());
        }

        // Due date
        if (task.getDueDate() > 0) {
            Date dueDate = new Date(task.getDueDate());
            info.append(" | Due: ").append(dateFormat.format(dueDate));

            // Add overdue indicator
            if (!task.isCompleted() && task.getDueDate() < System.currentTimeMillis()) {
                info.append(" ⚠️");
            }
        }

        // Recurrence
        if (task.isRecurring()) {
            info.append(" | 🔁 ").append(task.getRecurrenceString());

            // Progress for frequency tasks
            if (task.getRecurrenceType() == Task.RECURRENCE_FREQUENCY) {
                info.append(" ").append(task.getProgressString());
            }

            // Next appearance for interval tasks
            if (task.getRecurrenceType() == Task.RECURRENCE_INTERVAL && task.isCompleted()) {
                info.append(task.getNextAppearanceString());
            }
        }

        return info.toString();
    }

    private void showDeleteConfirmation(Task task) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    listener.onTaskDelete(task);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * ViewHolder pattern for better performance
     */
    private static class ViewHolder {
        CheckBox checkBox;
        TextView titleText;
        TextView descriptionText;
        TextView priorityText;
        Button editButton;
        Button deleteButton;
    }
}