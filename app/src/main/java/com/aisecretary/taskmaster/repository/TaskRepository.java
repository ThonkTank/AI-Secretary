package com.aisecretary.taskmaster.repository;

import android.content.Context;

import com.aisecretary.taskmaster.database.TaskDao;
import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.List;

/**
 * TaskRepository - Repository pattern for Task operations
 *
 * Provides a clean API for the UI layer to interact with task data.
 * Abstracts database details and provides business logic.
 */
public class TaskRepository {

    private TaskDao taskDao;
    private static TaskRepository instance;

    private TaskRepository(Context context) {
        taskDao = new TaskDao(context.getApplicationContext());
    }

    /**
     * Get singleton instance
     */
    public static synchronized TaskRepository getInstance(Context context) {
        if (instance == null) {
            instance = new TaskRepository(context);
        }
        return instance;
    }

    /**
     * Create a new task
     */
    public long createTask(String title, String description, int priority) {
        TaskEntity task = new TaskEntity(title, description, priority);
        return taskDao.insert(task);
    }

    /**
     * Create a new task with due date
     */
    public long createTask(String title, String description, int priority, long dueAt) {
        TaskEntity task = new TaskEntity(title, description, priority);
        task.dueAt = dueAt;
        return taskDao.insert(task);
    }

    /**
     * Update an existing task
     */
    public void updateTask(TaskEntity task) {
        taskDao.update(task);
    }

    /**
     * Delete a task
     */
    public void deleteTask(long taskId) {
        taskDao.delete(taskId);
    }

    /**
     * Get task by ID
     */
    public TaskEntity getTask(long id) {
        return taskDao.getById(id);
    }

    /**
     * Get all tasks
     */
    public List<TaskEntity> getAllTasks() {
        return taskDao.getAll();
    }

    /**
     * Get tasks for today
     */
    public List<TaskEntity> getTodayTasks() {
        return taskDao.getTasksForToday();
    }

    /**
     * Get overdue tasks
     */
    public List<TaskEntity> getOverdueTasks() {
        return taskDao.getOverdueTasks();
    }

    /**
     * Get tasks with active streaks
     */
    public List<TaskEntity> getTasksWithStreaks() {
        return taskDao.getTasksWithStreaks();
    }

    /**
     * Complete a task with tracking data
     */
    public void completeTask(long taskId, long completionTime, float difficulty) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return;

        task.completed = true;
        task.completedAt = System.currentTimeMillis();
        task.completionCount++;
        task.lastCompletedAt = task.completedAt;

        // Update average completion time
        if (task.averageCompletionTime == 0) {
            task.averageCompletionTime = completionTime;
        } else {
            task.averageCompletionTime = (task.averageCompletionTime + completionTime) / 2;
        }

        // Update average difficulty
        if (task.averageDifficulty == 0) {
            task.averageDifficulty = difficulty;
        } else {
            task.averageDifficulty = (task.averageDifficulty + difficulty) / 2;
        }

        // Update streak
        updateStreak(task);

        taskDao.update(task);
    }

    /**
     * Complete a task (simple version without tracking)
     */
    public void completeTask(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return;

        task.completed = true;
        task.completedAt = System.currentTimeMillis();
        task.completionCount++;
        task.lastCompletedAt = task.completedAt;

        updateStreak(task);
        taskDao.update(task);
    }

    /**
     * Uncomplete a task (mark as incomplete)
     */
    public void uncompleteTask(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return;

        task.completed = false;
        task.completedAt = 0;

        taskDao.update(task);
    }

    /**
     * Update task streak
     */
    private void updateStreak(TaskEntity task) {
        if (!task.isRecurring) {
            return; // Only recurring tasks have streaks
        }

        long now = System.currentTimeMillis();
        long dayInMillis = 86400000; // 24 hours
        long lastUpdate = task.streakLastUpdated;

        // Check if completed on consecutive days
        if (lastUpdate > 0) {
            long daysSinceLastUpdate = (now - lastUpdate) / dayInMillis;

            if (daysSinceLastUpdate <= 1) {
                // Completed on time - increment streak
                task.currentStreak++;
                if (task.currentStreak > task.longestStreak) {
                    task.longestStreak = task.currentStreak;
                }
            } else {
                // Missed a day - reset streak
                task.currentStreak = 1;
            }
        } else {
            // First completion
            task.currentStreak = 1;
            task.longestStreak = 1;
        }

        task.streakLastUpdated = now;
    }

    /**
     * Get completed tasks count for today
     */
    public int getCompletedTodayCount() {
        long now = System.currentTimeMillis();
        long dayStart = now - (now % 86400000);
        long dayEnd = dayStart + 86400000;

        return taskDao.getCompletedCount(dayStart, dayEnd);
    }

    /**
     * Get completed tasks count for last 7 days
     */
    public int getCompletedLast7DaysCount() {
        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - (7 * 86400000);

        return taskDao.getCompletedCount(sevenDaysAgo, now);
    }

    /**
     * Get average tasks per day (last 7 days)
     */
    public float getAverageTasksPerDay() {
        int count = getCompletedLast7DaysCount();
        return count / 7.0f;
    }

    /**
     * Check and update overdue tasks
     */
    public void updateOverdueStatus() {
        List<TaskEntity> allTasks = taskDao.getAll();
        long now = System.currentTimeMillis();

        for (TaskEntity task : allTasks) {
            if (!task.completed && task.dueAt > 0 && task.dueAt < now) {
                if (task.overdueSince == 0) {
                    task.overdueSince = now;
                    taskDao.update(task);
                }
            }
        }
    }

    /**
     * Reset recurring tasks if needed
     */
    public void checkAndResetRecurringTasks() {
        List<TaskEntity> allTasks = taskDao.getAll();
        long now = System.currentTimeMillis();

        for (TaskEntity task : allTasks) {
            if (task.isRecurring && task.completed) {
                boolean shouldReset = false;

                // Check based on recurrence type
                if ("every_x_y".equals(task.recurrenceType)) {
                    long interval = calculateRecurrenceInterval(task.recurrenceX, task.recurrenceY);
                    if (now - task.completedAt >= interval) {
                        shouldReset = true;
                    }
                }
                // TODO: Add logic for other recurrence types (x_per_y, scheduled)

                if (shouldReset) {
                    task.completed = false;
                    task.completedAt = 0;
                    task.dueAt = now + calculateRecurrenceInterval(task.recurrenceX, task.recurrenceY);
                    taskDao.update(task);
                }
            }
        }
    }

    /**
     * Calculate recurrence interval in milliseconds
     */
    private long calculateRecurrenceInterval(int x, String y) {
        long dayInMillis = 86400000;

        switch (y) {
            case "day":
                return x * dayInMillis;
            case "week":
                return x * 7 * dayInMillis;
            case "month":
                return x * 30 * dayInMillis; // Approximate
            default:
                return dayInMillis;
        }
    }

    /**
     * Get next task to do (highest priority, due soonest)
     */
    public TaskEntity getNextTask() {
        List<TaskEntity> todayTasks = getTodayTasks();
        if (todayTasks.isEmpty()) {
            return null;
        }

        // First incomplete task is the next one (already sorted by priority and due date)
        for (TaskEntity task : todayTasks) {
            if (!task.completed) {
                return task;
            }
        }

        return null;
    }
}
