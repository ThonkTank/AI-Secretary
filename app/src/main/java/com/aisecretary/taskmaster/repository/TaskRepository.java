package com.aisecretary.taskmaster.repository;

import android.content.Context;

import com.aisecretary.taskmaster.database.CompletionHistoryDao;
import com.aisecretary.taskmaster.database.CompletionHistoryEntity;
import com.aisecretary.taskmaster.database.TaskDao;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.utils.RecurrenceManager;
import com.aisecretary.taskmaster.utils.StreakManager;
import com.aisecretary.taskmaster.utils.TaskScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * TaskRepository - Repository pattern for Task operations
 *
 * Provides a clean API for the UI layer to interact with task data.
 * Abstracts database details and provides business logic.
 * Phase 3.2: Added completion history tracking
 */
public class TaskRepository {

    private TaskDao taskDao;
    private CompletionHistoryDao historyDao;
    private Context context;
    private static TaskRepository instance;

    private TaskRepository(Context context) {
        this.context = context.getApplicationContext();
        taskDao = new TaskDao(context.getApplicationContext());
        historyDao = new CompletionHistoryDao(context.getApplicationContext());
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
        notifyWidgetUpdate();
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
     * Phase 3.2: Now saves to completion history and calculates averages from history
     */
    public void completeTask(long taskId, long completionTime, float difficulty) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return;

        long now = System.currentTimeMillis();

        // Save completion to history (Phase 3.2)
        CompletionHistoryEntity historyEntry = new CompletionHistoryEntity(
                taskId,
                now,
                completionTime,
                difficulty
        );
        historyDao.insert(historyEntry);

        // Update task completion status
        task.completed = true;
        task.completedAt = now;
        task.completionCount++;
        task.lastCompletedAt = now;

        // Calculate averages from history (Phase 3.2)
        task.averageCompletionTime = historyDao.getAverageCompletionTime(taskId);
        task.averageDifficulty = historyDao.getAverageDifficulty(taskId);

        // Update preferred time from history
        int mostCommonHour = historyDao.getMostCommonTimeOfDay(taskId);
        if (mostCommonHour >= 0) {
            task.preferredHour = mostCommonHour;
            // Set preferredTimeOfDay based on hour
            if (mostCommonHour >= 5 && mostCommonHour < 12) {
                task.preferredTimeOfDay = "morning";
            } else if (mostCommonHour >= 12 && mostCommonHour < 18) {
                task.preferredTimeOfDay = "afternoon";
            } else {
                task.preferredTimeOfDay = "evening";
            }
        }

        // Update streak
        updateStreak(task);

        taskDao.update(task);
        notifyWidgetUpdate();
    }

    /**
     * Complete a task (simple version without tracking)
     * Phase 3.2: Also saves to completion history (with default values)
     */
    public void completeTask(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return;

        long now = System.currentTimeMillis();

        // Save completion to history without tracking data (Phase 3.2)
        CompletionHistoryEntity historyEntry = new CompletionHistoryEntity(
                taskId,
                now,
                0, // No completion time tracked
                0  // No difficulty tracked
        );
        historyDao.insert(historyEntry);

        task.completed = true;
        task.completedAt = now;
        task.completionCount++;
        task.lastCompletedAt = now;

        updateStreak(task);
        taskDao.update(task);
        notifyWidgetUpdate();
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
     * Phase 4.1: Now uses StreakManager for centralized logic
     */
    private void updateStreak(TaskEntity task) {
        StreakManager.updateStreak(task);
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
     * Phase 2.3: Now uses RecurrenceManager for centralized logic
     */
    public void checkAndResetRecurringTasks() {
        List<TaskEntity> allTasks = taskDao.getAll();

        for (TaskEntity task : allTasks) {
            if (RecurrenceManager.shouldResetTask(task)) {
                RecurrenceManager.resetTask(task);
                taskDao.update(task);
                notifyWidgetUpdate();
            }
        }
    }

    /**
     * Get next task to do using intelligent scheduling
     * Phase 5: Now uses TaskScheduler for optimal task selection
     */
    public TaskEntity getNextTask() {
        List<TaskEntity> incompleteTasks = getIncompleteTasks();
        return TaskScheduler.getNextTask(incompleteTasks);
    }

    /**
     * Get incomplete tasks
     */
    public List<TaskEntity> getIncompleteTasks() {
        List<TaskEntity> allTasks = taskDao.getAll();
        List<TaskEntity> incomplete = new ArrayList<>();

        for (TaskEntity task : allTasks) {
            if (!task.completed) {
                incomplete.add(task);
            }
        }

        return incomplete;
    }

    // ==================== Phase 3.2: Completion History Methods ====================

    /**
     * Get completion history for a task
     */
    public List<CompletionHistoryEntity> getTaskHistory(long taskId) {
        return historyDao.getByTaskId(taskId);
    }

    /**
     * Get recent completion history (last N entries)
     */
    public List<CompletionHistoryEntity> getRecentTaskHistory(long taskId, int limit) {
        return historyDao.getRecentByTaskId(taskId, limit);
    }

    /**
     * Get completion history for a date range
     */
    public List<CompletionHistoryEntity> getTaskHistoryByDateRange(long taskId, long startTime, long endTime) {
        return historyDao.getByDateRange(taskId, startTime, endTime);
    }

    /**
     * Get average completion time from history
     */
    public long getAverageCompletionTimeFromHistory(long taskId) {
        return historyDao.getAverageCompletionTime(taskId);
    }

    /**
     * Get average difficulty from history
     */
    public float getAverageDifficultyFromHistory(long taskId) {
        return historyDao.getAverageDifficulty(taskId);
    }

    /**
     * Get most common time of day for task completion
     */
    public int getMostCommonCompletionHour(long taskId) {
        return historyDao.getMostCommonTimeOfDay(taskId);
    }

    // ==================== Phase 4.1: Streak Management Methods ====================

    /**
     * Check if a task's streak is at risk
     */
    public boolean isStreakAtRisk(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return false;
        return StreakManager.isStreakAtRisk(task);
    }

    /**
     * Get days until streak expires for a task
     */
    public int getDaysUntilStreakExpires(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return -1;
        return StreakManager.getDaysUntilStreakExpires(task);
    }

    /**
     * Get all tasks with streaks at risk
     */
    public List<TaskEntity> getTasksWithStreaksAtRisk() {
        List<TaskEntity> allTasks = taskDao.getTasksWithStreaks();
        List<TaskEntity> atRisk = new ArrayList<>();

        for (TaskEntity task : allTasks) {
            if (StreakManager.isStreakAtRisk(task)) {
                atRisk.add(task);
            }
        }

        return atRisk;
    }

    /**
     * Reset streak for a task (when manually uncompleted or missed)
     */
    public void resetStreak(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return;

        StreakManager.resetStreak(task);
        taskDao.update(task);
    }

    // ==================== Phase 2.3: Recurrence Management Methods ====================

    /**
     * Get recurrence description for a task
     */
    public String getRecurrenceDescription(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return "Einmalig";
        return RecurrenceManager.getRecurrenceDescription(task);
    }

    /**
     * Get next reset time for a recurring task
     */
    public long getNextResetTime(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return 0;
        return RecurrenceManager.getNextResetTime(task);
    }

    /**
     * Check if a task is due soon (within 24 hours)
     */
    public boolean isTaskDueSoon(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return false;
        return RecurrenceManager.isDueSoon(task);
    }

    /**
     * Get hours until task is due
     */
    public int getHoursUntilDue(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return -1;
        return RecurrenceManager.getHoursUntilDue(task);
    }

    /**
     * Get all tasks that are due soon (within 24 hours)
     */
    public List<TaskEntity> getTasksDueSoon() {
        List<TaskEntity> allTasks = taskDao.getAll();
        List<TaskEntity> dueSoon = new ArrayList<>();

        for (TaskEntity task : allTasks) {
            if (RecurrenceManager.isDueSoon(task)) {
                dueSoon.add(task);
            }
        }

        return dueSoon;
    }

    // ==================== Phase 5: Intelligent Scheduling Methods ====================

    /**
     * Get intelligently sorted task list (all incomplete tasks)
     */
    public List<TaskEntity> getSortedTasks() {
        List<TaskEntity> incompleteTasks = getIncompleteTasks();
        return TaskScheduler.sortTasks(incompleteTasks);
    }

    /**
     * Get today's tasks sorted intelligently
     */
    public List<TaskEntity> getTodaysSortedTasks() {
        List<TaskEntity> allTasks = taskDao.getAll();
        return TaskScheduler.getTodaysSortedTasks(allTasks);
    }

    /**
     * Get score explanation for a task (for debugging/display)
     */
    public String getTaskScoreExplanation(long taskId) {
        TaskEntity task = taskDao.getById(taskId);
        if (task == null) return "Task not found";
        return TaskScheduler.getScoreExplanation(task);
    }

    // ==================== Phase 4.5: Widget Integration ====================

    /**
     * Notify widget to update after data changes
     * Phase 4.5: Widget Update Integration
     * Phase 4.5.2: Extended to update all widget sizes
     */
    private void notifyWidgetUpdate() {
        // Update Large Widget
        try {
            Class<?> widgetClass = Class.forName("com.aisecretary.taskmaster.widget.TaskWidgetProvider");
            java.lang.reflect.Method updateMethod = widgetClass.getMethod("updateAllWidgets", Context.class);
            updateMethod.invoke(null, context);
        } catch (Exception e) {
            // Silently ignore
        }

        // Update Medium Widget
        try {
            Class<?> widgetClass = Class.forName("com.aisecretary.taskmaster.widget.TaskWidgetProviderMedium");
            java.lang.reflect.Method updateMethod = widgetClass.getMethod("updateAllWidgets", Context.class);
            updateMethod.invoke(null, context);
        } catch (Exception e) {
            // Silently ignore
        }

        // Update Small Widget
        try {
            Class<?> widgetClass = Class.forName("com.aisecretary.taskmaster.widget.TaskWidgetProviderSmall");
            java.lang.reflect.Method updateMethod = widgetClass.getMethod("updateAllWidgets", Context.class);
            updateMethod.invoke(null, context);
        } catch (Exception e) {
            // Silently ignore
        }
    }
}
