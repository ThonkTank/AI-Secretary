package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * StatsManager - Centralized statistics calculation and management
 *
 * Phase 4.2: Statistik-Dashboard
 * Provides methods for calculating and formatting task statistics.
 */
public class StatsManager {

    /**
     * Statistics data class for today's progress
     */
    public static class TodayStats {
        public int completedCount;
        public int totalCount;
        public float completionPercentage;

        public TodayStats(int completed, int total) {
            this.completedCount = completed;
            this.totalCount = total;
            this.completionPercentage = total > 0 ? (completed * 100.0f / total) : 0;
        }
    }

    /**
     * Statistics data class for weekly progress
     */
    public static class WeeklyStats {
        public int completedLast7Days;
        public float averagePerDay;
        public int daysWithActivity;

        public WeeklyStats(int completed, int daysActive) {
            this.completedLast7Days = completed;
            this.daysWithActivity = daysActive;
            this.averagePerDay = completed / 7.0f;
        }
    }

    /**
     * Streak summary data class
     */
    public static class StreakSummary {
        public TaskEntity task;
        public int streak;
        public int level;
        public String emoji;
        public String description;
        public boolean atRisk;
        public int daysUntilExpire;

        public StreakSummary(TaskEntity task) {
            this.task = task;
            this.streak = task.currentStreak;
            this.level = StreakManager.getStreakLevel(streak);
            this.emoji = StreakManager.getStreakEmoji(streak);
            this.description = StreakManager.getStreakDescription(streak);
            this.atRisk = StreakManager.isStreakAtRisk(task);
            this.daysUntilExpire = StreakManager.getDaysUntilStreakExpires(task);
        }
    }

    /**
     * Get top streaks sorted by streak count
     *
     * @param tasks All tasks with streaks
     * @param limit Maximum number of results
     * @return List of StreakSummary sorted by streak (highest first)
     */
    public static List<StreakSummary> getTopStreaks(List<TaskEntity> tasks, int limit) {
        List<StreakSummary> summaries = new ArrayList<>();

        for (TaskEntity task : tasks) {
            if (task.currentStreak > 0) {
                summaries.add(new StreakSummary(task));
            }
        }

        // Sort by streak (highest first)
        Collections.sort(summaries, new Comparator<StreakSummary>() {
            @Override
            public int compare(StreakSummary a, StreakSummary b) {
                return Integer.compare(b.streak, a.streak);
            }
        });

        // Limit results
        if (summaries.size() > limit) {
            return summaries.subList(0, limit);
        }

        return summaries;
    }

    /**
     * Get streaks at risk (overdue tasks with active streaks)
     *
     * @param tasks All tasks with streaks
     * @return List of StreakSummary for at-risk streaks
     */
    public static List<StreakSummary> getStreaksAtRisk(List<TaskEntity> tasks) {
        List<StreakSummary> atRisk = new ArrayList<>();

        for (TaskEntity task : tasks) {
            if (task.currentStreak > 0 && StreakManager.isStreakAtRisk(task)) {
                atRisk.add(new StreakSummary(task));
            }
        }

        // Sort by days until expire (most urgent first)
        Collections.sort(atRisk, new Comparator<StreakSummary>() {
            @Override
            public int compare(StreakSummary a, StreakSummary b) {
                return Integer.compare(a.daysUntilExpire, b.daysUntilExpire);
            }
        });

        return atRisk;
    }

    /**
     * Get longest streak across all tasks
     *
     * @param tasks All tasks
     * @return The highest longestStreak value, or 0 if none
     */
    public static int getLongestStreakEver(List<TaskEntity> tasks) {
        int maxStreak = 0;

        for (TaskEntity task : tasks) {
            if (task.longestStreak > maxStreak) {
                maxStreak = task.longestStreak;
            }
        }

        return maxStreak;
    }

    /**
     * Get task with longest current streak
     *
     * @param tasks All tasks
     * @return TaskEntity with longest current streak, or null
     */
    public static TaskEntity getTaskWithLongestCurrentStreak(List<TaskEntity> tasks) {
        TaskEntity topTask = null;
        int maxStreak = 0;

        for (TaskEntity task : tasks) {
            if (task.currentStreak > maxStreak) {
                maxStreak = task.currentStreak;
                topTask = task;
            }
        }

        return topTask;
    }

    /**
     * Format completion percentage as text
     *
     * @param completed Number of completed tasks
     * @param total Total number of tasks
     * @return Formatted string like "75% (3/4)"
     */
    public static String formatCompletionPercentage(int completed, int total) {
        if (total == 0) {
            return "0% (0/0)";
        }

        int percentage = (int) ((completed * 100.0f) / total);
        return percentage + "% (" + completed + "/" + total + ")";
    }

    /**
     * Get motivational message based on completion percentage
     *
     * @param percentage Completion percentage (0-100)
     * @return Motivational message
     */
    public static String getMotivationalMessage(float percentage) {
        if (percentage == 100) {
            return "🎉 Perfekt! Alle Tasks erledigt!";
        } else if (percentage >= 80) {
            return "💪 Super! Fast geschafft!";
        } else if (percentage >= 50) {
            return "👍 Gute Arbeit! Weiter so!";
        } else if (percentage >= 25) {
            return "📈 Du machst Fortschritte!";
        } else if (percentage > 0) {
            return "🚀 Los geht's!";
        } else {
            return "📋 Zeit anzufangen!";
        }
    }

    /**
     * Calculate productivity score (0-100)
     *
     * @param todayCompleted Completed tasks today
     * @param todayTotal Total tasks today
     * @param weeklyAverage Average tasks per day (last 7 days)
     * @param longestStreak Longest current streak
     * @return Productivity score
     */
    public static int calculateProductivityScore(int todayCompleted, int todayTotal,
                                                   float weeklyAverage, int longestStreak) {
        int score = 0;

        // Today's completion (40 points max)
        if (todayTotal > 0) {
            score += (todayCompleted * 40) / todayTotal;
        }

        // Weekly average (30 points max - 3 tasks/day = 100%)
        score += Math.min(30, (int) (weeklyAverage * 10));

        // Streak bonus (30 points max - 30 days = 100%)
        score += Math.min(30, longestStreak);

        return Math.min(100, score);
    }

    /**
     * Get productivity level description
     *
     * @param score Productivity score (0-100)
     * @return Description
     */
    public static String getProductivityLevel(int score) {
        if (score >= 90) return "🏆 Herausragend";
        if (score >= 75) return "⭐ Sehr gut";
        if (score >= 60) return "👍 Gut";
        if (score >= 40) return "📈 Solide";
        if (score >= 20) return "🌱 Entwicklung";
        return "🔰 Anfang";
    }

    /**
     * Get progress bar representation
     *
     * @param percentage Percentage (0-100)
     * @param width Width in characters
     * @return Progress bar string
     */
    public static String getProgressBar(float percentage, int width) {
        int filled = (int) ((percentage / 100.0f) * width);
        int empty = width - filled;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        for (int i = 0; i < empty; i++) {
            bar.append("░");
        }

        return bar.toString();
    }

    /**
     * Format time duration in human-readable format
     *
     * @param milliseconds Duration in milliseconds
     * @return Formatted string like "2h 30m" or "45m"
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds == 0) {
            return "-";
        }

        long minutes = milliseconds / (60 * 1000);
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours > 0) {
            if (remainingMinutes > 0) {
                return hours + "h " + remainingMinutes + "m";
            } else {
                return hours + "h";
            }
        } else {
            return minutes + "m";
        }
    }

    /**
     * Format difficulty rating with stars
     *
     * @param difficulty Difficulty rating (0-5)
     * @return Formatted string like "★★★☆☆"
     */
    public static String formatDifficulty(float difficulty) {
        int stars = Math.round(difficulty);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < 5; i++) {
            if (i < stars) {
                result.append("★");
            } else {
                result.append("☆");
            }
        }

        return result.toString();
    }
}
