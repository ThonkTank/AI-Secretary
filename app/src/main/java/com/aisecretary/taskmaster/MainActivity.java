package com.aisecretary.taskmaster;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity - Entry point for AI Secretary Taskmaster
 *
 * This is the main activity that displays the task list and statistics.
 * Part of the Taskmaster feature suite.
 */
public class MainActivity extends Activity {

    private LinearLayout taskListContainer;
    private TextView statsTextView;
    private Button addTaskButton;

    private List<Task> tasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize task list
        tasks = new ArrayList<>();

        // Find views
        taskListContainer = findViewById(R.id.task_list_container);
        statsTextView = findViewById(R.id.stats_text);
        addTaskButton = findViewById(R.id.add_task_button);

        // Set up button listener
        addTaskButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddTaskClicked();
            }
        });

        // Load initial data
        loadTasks();
        updateStats();
        displayTasks();
    }

    /**
     * Load tasks from database
     * TODO: Replace with actual database implementation
     */
    private void loadTasks() {
        // Placeholder: Add some sample tasks
        tasks.add(new Task(1, "Welcome to AI Secretary!", "This is your first task", 1, false));
        tasks.add(new Task(2, "Setup your daily routine", "Add recurring tasks for daily activities", 2, false));
        tasks.add(new Task(3, "Explore Taskmaster features", "Check out task types and tracking options", 1, false));
    }

    /**
     * Display all tasks in the UI
     */
    private void displayTasks() {
        taskListContainer.removeAllViews();

        for (Task task : tasks) {
            View taskView = createTaskView(task);
            taskListContainer.addView(taskView);
        }
    }

    /**
     * Create a view for a single task
     */
    private View createTaskView(Task task) {
        // Create a simple text view for now
        TextView taskTextView = new TextView(this);
        taskTextView.setText(task.getTitle());
        taskTextView.setTextSize(18);
        taskTextView.setPadding(16, 16, 16, 16);

        // Set text color based on completion status
        if (task.isCompleted()) {
            taskTextView.setTextColor(0xFF4CAF50); // Green for completed
        } else {
            taskTextView.setTextColor(0xFF000000); // Black for pending
        }

        // Add click listener to toggle completion
        taskTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTaskCompletion(task);
            }
        });

        return taskTextView;
    }

    /**
     * Toggle task completion status
     */
    private void toggleTaskCompletion(Task task) {
        task.setCompleted(!task.isCompleted());
        displayTasks();
        updateStats();

        String message = task.isCompleted() ? "Task completed!" : "Task marked as incomplete";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Update statistics display
     */
    private void updateStats() {
        int completedCount = 0;
        int totalCount = tasks.size();

        for (Task task : tasks) {
            if (task.isCompleted()) {
                completedCount++;
            }
        }

        String statsText = String.format("Today: %d/%d tasks completed", completedCount, totalCount);
        statsTextView.setText(statsText);
    }

    /**
     * Handle add task button click
     */
    private void onAddTaskClicked() {
        // TODO: Implement task creation dialog
        Toast.makeText(this, "Add task feature coming soon!", Toast.LENGTH_SHORT).show();
    }
}
