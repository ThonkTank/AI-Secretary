package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.Calendar;

/**
 * StreakManager - Manages streak calculation and updates for recurring tasks
 *
 * Phase 4.1: Streak-Berechnung
 * Centralized streak logic for consistent behavior across the app.
 */
public class StreakManager {

    private static final long DAY_IN_MILLIS = 86400000; // 24 hours

    /**
     * Calculate current streak for a task based on completion history
     *
     * @param task The task to calculate streak for
     * @return The current streak count
     */
    public static int calculateStreak(TaskEntity task) {
        if (!task.isRecurring) {
            return 0; // Only recurring tasks have streaks
        }

        return task.currentStreak;
    }

    /**
     * Update streak when a task is completed
     *
     * @param task The task being completed
     * @return Updated streak count
     */
    public static int updateStreak(TaskEntity task) {
        if (!task.isRecurring) {
            return 0; // Only recurring tasks have streaks
        }

        long now = System.currentTimeMillis();
        long lastUpdate = task.streakLastUpdated;

        // Check if completed on consecutive days
        if (lastUpdate > 0) {
            long daysSinceLastUpdate = (now - lastUpdate) / DAY_IN_MILLIS;

            if (daysSinceLastUpdate <= 1) {
                // Completed on time - increment streak
                task.currentStreak++;
                if (task.currentStreak > task.longestStreak) {
                    task.longestStreak = task.currentStreak;
                }
            } else if (daysSinceLastUpdate == 2 && isGracePeriod(task)) {
                // Grace period: Allow 1 day skip for certain recurrence types
                task.currentStreak++;
                if (task.currentStreak > task.longestStreak) {
                    task.longestStreak = task.currentStreak;
                }
            } else {
                // Missed too many days - reset streak
                task.currentStreak = 1;
            }
        } else {
            // First completion
            task.currentStreak = 1;
            task.longestStreak = 1;
        }

        task.streakLastUpdated = now;
        return task.currentStreak;
    }

    /**
     * Reset streak for a task (when task is missed or uncompleted)
     *
     * @param task The task to reset
     */
    public static void resetStreak(TaskEntity task) {
        if (!task.isRecurring) {
            return;
        }

        task.currentStreak = 0;
        // Don't reset longest streak - it's a "best ever" record
    }

    /**
     * Check if a streak is at risk (task overdue)
     *
     * @param task The task to check
     * @return true if streak is at risk
     */
    public static boolean isStreakAtRisk(TaskEntity task) {
        if (!task.isRecurring || task.currentStreak == 0) {
            return false;
        }

        // If task is overdue and not completed, streak is at risk
        long now = System.currentTimeMillis();
        return task.dueAt > 0 && task.dueAt < now && !task.completed;
    }

    /**
     * Calculate how many days until streak expires
     *
     * @param task The task to check
     * @return Days until streak expires, or -1 if not at risk
     */
    public static int getDaysUntilStreakExpires(TaskEntity task) {
        if (!task.isRecurring || task.currentStreak == 0) {
            return -1;
        }

        if (task.dueAt == 0) {
            return -1; // No due date set
        }

        long now = System.currentTimeMillis();
        long timeUntilDue = task.dueAt - now;

        if (timeUntilDue < 0) {
            // Already overdue
            long timeOverdue = -timeUntilDue;
            long daysOverdue = timeOverdue / DAY_IN_MILLIS;

            // Streak expires after 1 day overdue (with grace period of 1 additional day)
            if (daysOverdue >= 2) {
                return 0; // Streak already expired
            } else {
                return 1; // Expires in 1 day
            }
        } else {
            // Not yet due
            long daysUntilDue = timeUntilDue / DAY_IN_MILLIS;
            return (int) daysUntilDue;
        }
    }

    /**
     * Check if grace period applies for this recurrence type
     *
     * @param task The task to check
     * @return true if grace period applies
     */
    private static boolean isGracePeriod(TaskEntity task) {
        // Grace period only for flexible recurrence types (x_per_y)
        // Not for strict schedules (every_x_y, scheduled)
        return "x_per_y".equals(task.recurrenceType);
    }

    /**
     * Get streak level (for UI display)
     *
     * @param streak The streak count
     * @return Level: 0 (none), 1 (beginner 1-9), 2 (intermediate 10-24),
     *                3 (advanced 25-49), 4 (expert 50-99), 5 (master 100+)
     */
    public static int getStreakLevel(int streak) {
        if (streak == 0) return 0;
        if (streak < 10) return 1;
        if (streak < 25) return 2;
        if (streak < 50) return 3;
        if (streak < 100) return 4;
        return 5;
    }

    /**
     * Get streak emoji for display
     *
     * @param streak The streak count
     * @return Emoji string
     */
    public static String getStreakEmoji(int streak) {
        int level = getStreakLevel(streak);
        switch (level) {
            case 0: return "";
            case 1: return "🔥";
            case 2: return "🔥🔥";
            case 3: return "🔥🔥🔥";
            case 4: return "🔥🔥🔥🔥";
            case 5: return "🔥🔥🔥🔥🔥";
            default: return "🔥";
        }
    }

    /**
     * Get streak description
     *
     * @param streak The streak count
     * @return Description string
     */
    public static String getStreakDescription(int streak) {
        int level = getStreakLevel(streak);
        switch (level) {
            case 0: return "Keine Streak";
            case 1: return "Anfänger";
            case 2: return "Fortgeschritten";
            case 3: return "Erfahren";
            case 4: return "Experte";
            case 5: return "Meister";
            default: return "";
        }
    }

    /**
     * Check if streak milestone was just reached
     *
     * @param previousStreak Previous streak value
     * @param currentStreak Current streak value
     * @return true if a milestone was reached
     */
    public static boolean isMilestoneReached(int previousStreak, int currentStreak) {
        int[] milestones = {10, 25, 50, 100, 250, 500, 1000};

        for (int milestone : milestones) {
            if (previousStreak < milestone && currentStreak >= milestone) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the milestone that was just reached
     *
     * @param previousStreak Previous streak value
     * @param currentStreak Current streak value
     * @return The milestone value, or -1 if none
     */
    public static int getMilestone(int previousStreak, int currentStreak) {
        int[] milestones = {10, 25, 50, 100, 250, 500, 1000};

        for (int milestone : milestones) {
            if (previousStreak < milestone && currentStreak >= milestone) {
                return milestone;
            }
        }

        return -1;
    }
}
