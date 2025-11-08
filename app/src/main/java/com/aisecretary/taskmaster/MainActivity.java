package com.aisecretary.taskmaster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aisecretary.taskmaster.adapter.TaskAdapter;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.dialogs.CompletionDialog;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.aisecretary.taskmaster.service.NotificationService;
import com.aisecretary.taskmaster.service.RecurringTaskService;
import com.aisecretary.taskmaster.utils.StatsManager;
import com.aisecretary.taskmaster.utils.StreakManager;
import com.aisecretary.taskmaster.utils.SwipeHelper;

import java.util.List;

/**
 * MainActivity - Entry point for AI Secretary Taskmaster
 *
 * Uses RecyclerView with SwipeHelper for efficient task list rendering.
 * Implements Design System v1.0 with swipe gestures (Right: Complete, Left: Delete).
 */
public class MainActivity extends Activity
        implements TaskAdapter.TaskClickListener, SwipeHelper.SwipeListener {

    private RecyclerView recyclerView;
    private TextView statsTextView;
    private TextView streakTextView;
    private Button addTaskButton;
    private Button statisticsButton;
    private Button dailyPlanButton;

    private TaskRepository repository;
    private TaskAdapter adapter;
    private List<TaskEntity> tasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize repository and adapter
        repository = TaskRepository.getInstance(this);
        adapter = new TaskAdapter(this);

        // Find views
        recyclerView = findViewById(R.id.task_list_recycler);
        statsTextView = findViewById(R.id.stats_text);
        streakTextView = findViewById(R.id.streak_text);
        addTaskButton = findViewById(R.id.add_task_button);
        statisticsButton = findViewById(R.id.statistics_button);
        dailyPlanButton = findViewById(R.id.daily_plan_button);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Set up swipe gestures
        SwipeHelper swipeHelper = new SwipeHelper(adapter, this);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeHelper);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Set up button listeners
        addTaskButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddTaskClicked();
            }
        });

        statisticsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openStatistics();
            }
        });

        dailyPlanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDailyPlan();
            }
        });

        // Initialize database with sample tasks if empty
        initializeSampleTasks();

        // Start recurring task service for automatic task resets
        // Phase 2.3: Background service for recurring tasks
        RecurringTaskService.startService(this);

        // Start notification service for reminders and summaries
        // Phase 8.1: Notification system
        NotificationService.startService(this);

        // Load and display data
        refreshData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to activity
        refreshData();
    }

    /**
     * Refresh all data and UI
     */
    private void refreshData() {
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
            long id1 = repository.createTask(morningRoutine.title, morningRoutine.description, morningRoutine.priority);

            // Update with recurring data
            TaskEntity task1 = repository.getTask(id1);
            if (task1 != null) {
                task1.isRecurring = morningRoutine.isRecurring;
                task1.recurrenceType = morningRoutine.recurrenceType;
                task1.recurrenceX = morningRoutine.recurrenceX;
                task1.recurrenceY = morningRoutine.recurrenceY;
                task1.currentStreak = morningRoutine.currentStreak;
                task1.longestStreak = morningRoutine.longestStreak;
                task1.dueAt = morningRoutine.dueAt;
                repository.updateTask(task1);
            }

            // Sample Task 2: Team Meeting (today)
            long id2 = repository.createTask(
                    "Team Meeting",
                    "Weekly sync with development team",
                    2,
                    now + (6 * 3600000) // Due in 6 hours
            );

            // Sample Task 3: Pay Bills (OVERDUE)
            long id3 = repository.createTask(
                    "Pay Electricity Bill",
                    "Overdue by 2 days!",
                    4,
                    now - (2 * dayInMillis) // Overdue by 2 days
            );

            // Sample Task 4: Read Book (recurring with small streak)
            TaskEntity reading = new TaskEntity(
                    "Read for 30 minutes",
                    "Continue reading current book",
                    1
            );
            reading.isRecurring = true;
            reading.recurrenceType = "x_per_y";
            reading.recurrenceX = 5;
            reading.recurrenceY = "week";
            reading.currentStreak = 3;
            reading.longestStreak = 7;
            long id4 = repository.createTask(reading.title, reading.description, reading.priority);

            TaskEntity task4 = repository.getTask(id4);
            if (task4 != null) {
                task4.isRecurring = reading.isRecurring;
                task4.recurrenceType = reading.recurrenceType;
                task4.recurrenceX = reading.recurrenceX;
                task4.recurrenceY = reading.recurrenceY;
                task4.currentStreak = reading.currentStreak;
                task4.longestStreak = reading.longestStreak;
                repository.updateTask(task4);
            }

            // Sample Task 5: Grocery Shopping
            repository.createTask(
                    "Buy groceries",
                    "Milk, eggs, bread, vegetables",
                    2,
                    now + dayInMillis // Tomorrow
            );

            Toast.makeText(this, "Sample tasks created!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Load tasks from repository
     * Phase 5: Now uses intelligent sorting algorithm
     */
    private void loadTasks() {
        tasks = repository.getTodaysSortedTasks();
        adapter.setTasks(tasks);
    }

    /**
     * Display all tasks using RecyclerView
     */
    private void displayTasks() {
        // RecyclerView automatically handles empty state and updates via adapter
        // No need to manually create/remove views
    }

    /**
     * Update statistics display
     * Phase 4.1: Enhanced with StreakManager features
     * Phase 4.2: Enhanced with StatsManager features
     */
    private void updateStats() {
        int completedToday = repository.getCompletedTodayCount();
        int totalToday = tasks != null ? tasks.size() : 0;

        // Calculate today's stats using StatsManager
        StatsManager.TodayStats todayStats = new StatsManager.TodayStats(
                completedToday,
                totalToday + completedToday
        );

        // Format with percentage and motivational message
        String statsText = String.format("Today: %s\n%s",
                StatsManager.formatCompletionPercentage(todayStats.completedCount, todayStats.totalCount),
                StatsManager.getMotivationalMessage(todayStats.completionPercentage));
        statsTextView.setText(statsText);

        // Get streak summaries using StatsManager
        List<TaskEntity> streakTasks = repository.getTasksWithStreaks();
        List<StatsManager.StreakSummary> topStreaks = StatsManager.getTopStreaks(streakTasks, 1);
        List<StatsManager.StreakSummary> atRiskStreaks = StatsManager.getStreaksAtRisk(streakTasks);

        // Build streak display text
        if (!topStreaks.isEmpty()) {
            StatsManager.StreakSummary topStreak = topStreaks.get(0);
            String streakText = topStreak.emoji + " " + topStreak.streak;

            // Add warning if streaks are at risk
            if (!atRiskStreaks.isEmpty()) {
                streakText += " (⚠️ " + atRiskStreaks.size() + " at risk)";
            }

            streakTextView.setText(streakText);
        } else {
            streakTextView.setText("Streak: 0");
        }
    }

    /**
     * Handle add task button click
     */
    private void onAddTaskClicked() {
        // Open AddTaskActivity
        Intent intent = new Intent(this, AddTaskActivity.class);
        startActivity(intent);
    }

    // TaskAdapter.TaskClickListener implementations

    @Override
    public void onTaskClick(TaskEntity task) {
        // Open edit dialog
        openEditDialog(task);
    }

    @Override
    public void onTaskCheckboxClick(TaskEntity task) {
        if (task.completed) {
            // Uncomplete task
            repository.uncompleteTask(task.id);
            Toast.makeText(this, "Task marked as incomplete", Toast.LENGTH_SHORT).show();
            refreshData();
        } else {
            // Show completion dialog
            showCompletionDialog(task);
        }
    }

    /**
     * Show completion dialog for task tracking
     */
    private void showCompletionDialog(TaskEntity task) {
        CompletionDialog dialog = new CompletionDialog(this, task, new CompletionDialog.CompletionListener() {
            @Override
            public void onCompleteWithTracking(TaskEntity task, long duration, float difficulty) {
                // Complete task with tracking data
                repository.completeTask(task.id, duration, difficulty);

                // Show success message with streak info
                String message = "Task completed!";
                if (task.isRecurring) {
                    TaskEntity updatedTask = repository.getTask(task.id);
                    if (updatedTask != null && updatedTask.currentStreak > 0) {
                        message = "🔥 Streak: " + updatedTask.currentStreak + " Tage!";

                        // Check for milestone
                        int streak = updatedTask.currentStreak;
                        if (streak == 10 || streak == 25 || streak == 50 || streak == 100) {
                            message = "🎉 " + streak + " Tage Streak! 🎉";
                        }
                    }
                }

                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                refreshData();
            }

            @Override
            public void onCompleteWithoutTracking(TaskEntity task) {
                // Complete task without tracking (quick complete)
                repository.completeTask(task.id);

                String message = "Task completed!";
                if (task.isRecurring) {
                    TaskEntity updatedTask = repository.getTask(task.id);
                    if (updatedTask != null && updatedTask.currentStreak > 0) {
                        message = "Task completed! 🔥 Streak: " + updatedTask.currentStreak;
                    }
                }

                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                refreshData();
            }

            @Override
            public void onCancel() {
                // User cancelled - do nothing
            }
        });

        dialog.show();
    }

    @Override
    public void onTaskEditClick(TaskEntity task) {
        // Open edit dialog
        openEditDialog(task);
    }

    /**
     * Open AddTaskActivity in edit mode
     */
    private void openEditDialog(TaskEntity task) {
        Intent intent = new Intent(this, AddTaskActivity.class);
        intent.putExtra(AddTaskActivity.EXTRA_TASK_ID, task.id);
        startActivity(intent);
    }

    @Override
    public void onTaskDeleteClick(TaskEntity task) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete \"" + task.title + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteTask(task.id);
                    Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Open Statistics Activity
     */
    private void openStatistics() {
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
    }

    /**
     * Open Daily Plan Activity
     * Phase 5.2: Timeline-based daily task plan
     */
    private void openDailyPlan() {
        Intent intent = new Intent(this, DailyPlanActivity.class);
        startActivity(intent);
    }

    // SwipeHelper.SwipeListener implementations

    @Override
    public void onSwipeRight(TaskEntity task, int position) {
        // Right swipe: Complete/Uncomplete task
        if (task.completed) {
            repository.uncompleteTask(task.id);
            Toast.makeText(this, "Task marked as incomplete", Toast.LENGTH_SHORT).show();
            refreshData();
        } else {
            // Show completion dialog
            showCompletionDialog(task);
        }
    }

    @Override
    public void onSwipeLeft(TaskEntity task, int position) {
        // Left swipe: Delete task (with confirmation)
        new AlertDialog.Builder(this)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete \"" + task.title + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.deleteTask(task.id);
                    Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                    refreshData();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // User cancelled - refresh to restore the swiped item
                    refreshData();
                })
                .setOnCancelListener(dialog -> {
                    // User dismissed - refresh to restore the swiped item
                    refreshData();
                })
                .show();
    }
}
