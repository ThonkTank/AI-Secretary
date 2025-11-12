package com.secretary.helloworld.utils;

import android.content.Context;

import com.secretary.helloworld.Task;
import com.secretary.helloworld.data.dao.TaskDao;
import com.secretary.helloworld.data.dao.CompletionDao;

import java.util.Calendar;
import java.util.List;

/**
 * Manager class for calculating and managing task statistics.
 * Extracted from TaskActivity for better separation of concerns.
 */
public class StatsManager {
    private final TaskDao taskDao;
    private final CompletionDao completionDao;

    // Statistics data
    private int totalTasks = 0;
    private int completedTasks = 0;
    private int overdueTasks = 0;
    private int todayCompletions = 0;
    private int weekCompletions = 0;
    private int longestStreak = 0;

    public StatsManager(Context context) {
        this.taskDao = new TaskDao(context);
        this.completionDao = new CompletionDao(context);
    }

    /**
     * Calculate all statistics from the task list
     */
    public void calculateStatistics(List<Task> tasks) {
        // Reset counters
        totalTasks = 0;
        completedTasks = 0;
        overdueTasks = 0;
        longestStreak = 0;

        long currentTime = System.currentTimeMillis();

        for (Task task : tasks) {
            totalTasks++;

            if (task.isCompleted()) {
                completedTasks++;
            } else if (task.getDueDate() > 0 && task.getDueDate() < currentTime) {
                overdueTasks++;
            }

            // Track longest streak
            if (task.getLongestStreak() > longestStreak) {
                longestStreak = task.getLongestStreak();
            }
        }

        // Calculate completion statistics
        calculateCompletionStats();
    }

    /**
     * Calculate completion statistics for today and this week
     */
    private void calculateCompletionStats() {
        Calendar calendar = Calendar.getInstance();

        // Today's completions
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long todayStart = calendar.getTimeInMillis();

        calendar.add(Calendar.DAY_OF_YEAR, 1);
        long todayEnd = calendar.getTimeInMillis();

        todayCompletions = completionDao.getCompletionsInRange(todayStart, todayEnd);

        // This week's completions
        calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long weekStart = calendar.getTimeInMillis();

        calendar.add(Calendar.WEEK_OF_YEAR, 1);
        long weekEnd = calendar.getTimeInMillis();

        weekCompletions = completionDao.getCompletionsInRange(weekStart, weekEnd);
    }

    /**
     * Get completion percentage
     */
    public int getCompletionPercentage() {
        if (totalTasks == 0) return 0;
        return (completedTasks * 100) / totalTasks;
    }

    /**
     * Get formatted statistics summary
     */
    public String getStatsSummary() {
        StringBuilder stats = new StringBuilder();

        stats.append("📊 Tasks: ").append(completedTasks)
             .append("/").append(totalTasks)
             .append(" (").append(getCompletionPercentage()).append("%)");

        if (overdueTasks > 0) {
            stats.append(" | ⚠️ Overdue: ").append(overdueTasks);
        }

        stats.append("\n✅ Today: ").append(todayCompletions)
             .append(" | Week: ").append(weekCompletions);

        if (longestStreak > 0) {
            stats.append(" | 🔥 Best Streak: ").append(longestStreak).append(" days");
        }

        return stats.toString();
    }

    /**
     * Calculate streak for a task
     */
    public void updateTaskStreak(Task task) {
        long now = System.currentTimeMillis();
        long lastStreakDate = task.getLastStreakDate();
        int currentStreak = task.getCurrentStreak();

        // Calculate days since last streak update
        long daysSinceLastStreak = 0;
        if (lastStreakDate > 0) {
            daysSinceLastStreak = (now - lastStreakDate) / (24 * 60 * 60 * 1000);
        }

        if (daysSinceLastStreak == 0) {
            // Already updated today, no change
            return;
        } else if (daysSinceLastStreak == 1) {
            // Consecutive day, increment streak
            currentStreak++;
        } else {
            // Streak broken, reset to 1
            currentStreak = 1;
        }

        // Update longest streak if needed
        int longestStreak = task.getLongestStreak();
        if (currentStreak > longestStreak) {
            longestStreak = currentStreak;
        }

        // Update task
        task.setCurrentStreak(currentStreak);
        task.setLongestStreak(longestStreak);
        task.setLastStreakDate(now);

        // Save to database
        taskDao.updateStreaks(task.getId(), currentStreak, longestStreak, now);
    }

    // Getters for individual statistics
    public int getTotalTasks() { return totalTasks; }
    public int getCompletedTasks() { return completedTasks; }
    public int getOverdueTasks() { return overdueTasks; }
    public int getTodayCompletions() { return todayCompletions; }
    public int getWeekCompletions() { return weekCompletions; }
    public int getLongestStreak() { return longestStreak; }

    /**
     * Close database connections
     */
    public void close() {
        taskDao.close();
        completionDao.close();
    }
}