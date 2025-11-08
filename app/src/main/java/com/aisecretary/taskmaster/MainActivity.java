package com.aisecretary.taskmaster;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;

import java.util.List;

/**
 * MainActivity - Entry point for AI Secretary Taskmaster
 *
 * Updated to use TaskRepository for database operations.
 * Implements Design System v1.0 colors and styles.
 */
public class MainActivity extends Activity {

    private LinearLayout taskListContainer;
    private TextView statsTextView;
    private TextView streakTextView;
    private Button addTaskButton;

    private TaskRepository repository;
    private List<TaskEntity> tasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize repository
        repository = TaskRepository.getInstance(this);

        // Find views
        taskListContainer = findViewById(R.id.task_list_container);
        statsTextView = findViewById(R.id.stats_text);
        streakTextView = findViewById(R.id.streak_text);
        addTaskButton = findViewById(R.id.add_task_button);

        // Set up button listener
        addTaskButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddTaskClicked();
            }
        });

        // Initialize database with sample tasks if empty
        initializeSampleTasks();

        // Load and display data
        loadTasks();
        updateStats();
        displayTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to activity
        loadTasks();
        updateStats();
        displayTasks();
    }

    /**
     * Initialize database with sample tasks (first run only)
     */
    private void initializeSampleTasks() {
        List<TaskEntity> existingTasks = repository.getAllTasks();

        if (existingTasks.isEmpty()) {
            // Create sample tasks with streaks
            long now = System.currentTimeMillis();
            long dayInMillis = 86400000;

            // Sample Task 1: Morning Routine (recurring, with streak)
            TaskEntity morningRoutine = new TaskEntity(
                    "Morning Routine",
                    "Meditation, Stretching, Cold Shower",
                    3 // High priority
            );
            morningRoutine.isRecurring = true;
            morningRoutine.recurrenceType = "every_x_y";
            morningRoutine.recurrenceX = 1;
            morningRoutine.recurrenceY = "day";
            morningRoutine.currentStreak = 12;
            morningRoutine.longestStreak = 18;
            morningRoutine.dueAt = now + (3 * 3600000); // Due in 3 hours
            repository.createTask(morningRoutine.title, morningRoutine.description, morningRoutine.priority);

            // Sample Task 2: Team Meeting (today)
            TaskEntity meeting = new TaskEntity(
                    "Team Meeting",
                    "Weekly sync with development team",
                    2 // Medium priority
            );
            meeting.dueAt = now + (6 * 3600000); // Due in 6 hours (15:00)
            repository.createTask(meeting.title, meeting.description, meeting.priority);

            // Sample Task 3: Pay Bills (OVERDUE)
            TaskEntity bills = new TaskEntity(
                    "Pay Electricity Bill",
                    "Overdue by 2 days!",
                    4 // Critical priority
            );
            bills.dueAt = now - (2 * dayInMillis); // Overdue by 2 days
            bills.overdueSince = now - (2 * dayInMillis);
            repository.createTask(bills.title, bills.description, bills.priority);

            // Sample Task 4: Read Book (recurring with small streak)
            TaskEntity reading = new TaskEntity(
                    "Read for 30 minutes",
                    "Continue reading current book",
                    1 // Low priority
            );
            reading.isRecurring = true;
            reading.recurrenceType = "x_per_y";
            reading.recurrenceX = 5;
            reading.recurrenceY = "week";
            reading.currentStreak = 3;
            reading.longestStreak = 7;
            repository.createTask(reading.title, reading.description, reading.priority);

            // Sample Task 5: Grocery Shopping
            TaskEntity groceries = new TaskEntity(
                    "Buy groceries",
                    "Milk, eggs, bread, vegetables",
                    2
            );
            groceries.dueAt = now + dayInMillis; // Tomorrow
            repository.createTask(groceries.title, groceries.description, groceries.priority);

            Toast.makeText(this, "Sample tasks created!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Load tasks from repository
     */
    private void loadTasks() {
        // Load today's tasks (including overdue)
        tasks = repository.getTodayTasks();
    }

    /**
     * Display all tasks in the UI
     */
    private void displayTasks() {
        taskListContainer.removeAllViews();

        if (tasks.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No tasks for today! 🎉");
            emptyView.setTextSize(16);
            emptyView.setPadding(16, 32, 16, 32);
            taskListContainer.addView(emptyView);
            return;
        }

        for (TaskEntity task : tasks) {
            View taskView = createTaskView(task);
            taskListContainer.addView(taskView);
        }
    }

    /**
     * Create a view for a single task
     */
    private View createTaskView(TaskEntity task) {
        // Create container
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(16, 12, 16, 12);

        // Task content (left side)
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        contentLayout.setLayoutParams(contentParams);

        // Title with priority stars
        TextView titleView = new TextView(this);
        String titleText = task.getPriorityStars() + " " + task.title;
        titleView.setText(titleText);
        titleView.setTextSize(16);

        // Set color based on status
        if (task.isOverdue()) {
            titleView.setTextColor(0xFFF44336); // Red
        } else if (task.completed) {
            titleView.setTextColor(0xFF4CAF50); // Green
            titleView.setPaintFlags(titleView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            titleView.setTextColor(0xFF000000); // Black
        }

        contentLayout.addView(titleView);

        // Description/Details
        if (task.description != null && !task.description.isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(task.description);
            descView.setTextSize(12);
            descView.setTextColor(0xFF757575);
            contentLayout.addView(descView);
        }

        // Streak badge for recurring tasks
        if (task.isRecurring && task.currentStreak > 0) {
            TextView streakBadge = new TextView(this);
            streakBadge.setText("🔥 Streak: " + task.currentStreak);
            streakBadge.setTextSize(11);
            streakBadge.setTextColor(0xFFFF6B00);
            contentLayout.addView(streakBadge);
        }

        // Overdue warning
        if (task.isOverdue()) {
            TextView overdueView = new TextView(this);
            long daysOverdue = task.getOverdueDuration() / 86400000;
            overdueView.setText("⚠️ OVERDUE (" + daysOverdue + " days)");
            overdueView.setTextSize(11);
            overdueView.setTextColor(0xFFF44336);
            overdueView.setTextStyle(android.graphics.Typeface.BOLD);
            contentLayout.addView(overdueView);
        }

        container.addView(contentLayout);

        // Checkbox (right side)
        TextView checkbox = new TextView(this);
        checkbox.setText(task.completed ? "✓" : "[ ]");
        checkbox.setTextSize(24);
        checkbox.setTextColor(task.completed ? 0xFF4CAF50 : 0xFF757575);
        checkbox.setPadding(16, 0, 0, 0);

        checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTaskCompletion(task);
            }
        });

        container.addView(checkbox);

        // Click listener for whole task (for future detail view)
        container.setClickable(true);
        container.setBackgroundResource(android.R.drawable.list_selector_background);

        return container;
    }

    /**
     * Toggle task completion status
     */
    private void toggleTaskCompletion(TaskEntity task) {
        if (task.completed) {
            // Uncomplete task
            repository.uncompleteTask(task.id);
            Toast.makeText(this, "Task marked as incomplete", Toast.LENGTH_SHORT).show();
        } else {
            // Complete task (simple version - later we'll add completion dialog)
            repository.completeTask(task.id);

            String message = "Task completed!";
            if (task.isRecurring && task.currentStreak > 0) {
                // Reload task to get updated streak
                task = repository.getTask(task.id);
                message = "Task completed! 🔥 Streak: " + task.currentStreak;
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }

        // Refresh UI
        loadTasks();
        updateStats();
        displayTasks();
    }

    /**
     * Update statistics display
     */
    private void updateStats() {
        int completedToday = repository.getCompletedTodayCount();
        int totalToday = tasks != null ? tasks.size() : 0;

        String statsText = String.format("Today: %d/%d tasks completed", completedToday, totalToday + completedToday);
        statsTextView.setText(statsText);

        // Find highest streak
        List<TaskEntity> streakTasks = repository.getTasksWithStreaks();
        int maxStreak = 0;
        for (TaskEntity task : streakTasks) {
            if (task.currentStreak > maxStreak) {
                maxStreak = task.currentStreak;
            }
        }

        if (maxStreak > 0) {
            streakTextView.setText("🔥 Streak: " + maxStreak);
        } else {
            streakTextView.setText("Streak: 0");
        }
    }

    /**
     * Handle add task button click
     */
    private void onAddTaskClicked() {
        // TODO: Implement AddTaskActivity/Dialog in Phase 2
        Toast.makeText(this, "Add task feature coming in Phase 2!", Toast.LENGTH_SHORT).show();

        // For testing: Add a quick sample task
        long taskId = repository.createTask(
                "New Task " + System.currentTimeMillis(),
                "Created at " + new java.util.Date(),
                2
        );

        Toast.makeText(this, "Test task created with ID: " + taskId, Toast.LENGTH_SHORT).show();

        // Refresh UI
        loadTasks();
        updateStats();
        displayTasks();
    }
}
