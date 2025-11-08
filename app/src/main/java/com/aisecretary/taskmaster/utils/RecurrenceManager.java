package com.aisecretary.taskmaster.utils;

import com.aisecretary.taskmaster.database.TaskEntity;

import java.util.Calendar;

/**
 * RecurrenceManager - Centralized logic for recurring task management
 *
 * Handles all recurrence-related calculations and task reset logic.
 * Phase 2.3: Wiederkehrende Tasks - Erweitert
 */
public class RecurrenceManager {

    // Grace period in milliseconds (24 hours) - used for "x_per_y" tasks
    private static final long GRACE_PERIOD_MS = 24 * 60 * 60 * 1000;

    /**
     * Calculate the next due date for a recurring task
     *
     * @param task The task entity
     * @return Next due date in milliseconds, or 0 if not recurring
     */
    public static long calculateNextDueDate(TaskEntity task) {
        if (!task.isRecurring) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long lastCompleted = task.lastCompletedAt > 0 ? task.lastCompletedAt : task.createdAt;

        if ("every_x_y".equals(task.recurrenceType)) {
            // "alle x y" - fixed interval from last completion
            long interval = calculateRecurrenceInterval(task.recurrenceX, task.recurrenceY);
            return lastCompleted + interval;
        }
        else if ("x_per_y".equals(task.recurrenceType)) {
            // "x pro y" - flexible within period
            // Calculate interval and distribute evenly
            long periodInterval = calculateRecurrenceInterval(1, task.recurrenceY);
            long taskInterval = periodInterval / task.recurrenceX;
            return lastCompleted + taskInterval;
        }
        else if ("scheduled".equals(task.recurrenceType)) {
            // Scheduled tasks with specific time
            // TODO: Implement scheduled task logic based on preferredHour and recurrence pattern
            // For now, return next day at preferred hour
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.set(Calendar.HOUR_OF_DAY, task.preferredHour);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            // If time has passed today, schedule for tomorrow
            if (cal.getTimeInMillis() <= now) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }

            return cal.getTimeInMillis();
        }

        return 0;
    }

    /**
     * Check if a recurring task should be reset (marked as incomplete)
     *
     * @param task The task entity
     * @return true if task should be reset, false otherwise
     */
    public static boolean shouldResetTask(TaskEntity task) {
        if (!task.isRecurring || !task.completed) {
            return false;
        }

        long now = System.currentTimeMillis();
        long nextDueDate = calculateNextDueDate(task);

        if (nextDueDate == 0) {
            return false;
        }

        // Reset if we've passed the next due date
        return now >= nextDueDate;
    }

    /**
     * Reset a recurring task to incomplete state with new due date
     *
     * @param task The task entity to reset
     */
    public static void resetTask(TaskEntity task) {
        if (!task.isRecurring) {
            return;
        }

        // Calculate next due date
        long nextDueDate = calculateNextDueDate(task);

        // Reset completion status
        task.completed = false;
        task.completedAt = 0;

        // Set new due date
        task.dueAt = nextDueDate;

        // Clear overdue status
        task.overdueSince = 0;
    }

    /**
     * Calculate recurrence interval in milliseconds
     *
     * @param x Number of time units
     * @param y Time unit ("day", "week", "month")
     * @return Interval in milliseconds
     */
    public static long calculateRecurrenceInterval(int x, String y) {
        long dayInMillis = 86400000; // 24 * 60 * 60 * 1000

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
     * Get a human-readable description of the recurrence pattern
     *
     * @param task The task entity
     * @return Recurrence description (e.g., "Alle 2 Tage", "3 mal pro Woche")
     */
    public static String getRecurrenceDescription(TaskEntity task) {
        if (!task.isRecurring) {
            return "Einmalig";
        }

        if ("every_x_y".equals(task.recurrenceType)) {
            String unit = getUnitLabel(task.recurrenceY, task.recurrenceX > 1);
            if (task.recurrenceX == 1) {
                return "Jeden " + unit;
            }
            return "Alle " + task.recurrenceX + " " + unit;
        }
        else if ("x_per_y".equals(task.recurrenceType)) {
            String unit = getUnitLabel(task.recurrenceY, false);
            return task.recurrenceX + " mal pro " + unit;
        }
        else if ("scheduled".equals(task.recurrenceType)) {
            return "Geplant (" + formatTime(task.preferredHour) + " Uhr)";
        }

        return "Wiederkehrend";
    }

    /**
     * Get the next reset time for a recurring task (when it will be marked incomplete again)
     *
     * @param task The task entity
     * @return Timestamp of next reset, or 0 if not applicable
     */
    public static long getNextResetTime(TaskEntity task) {
        if (!task.isRecurring || !task.completed) {
            return 0;
        }

        return calculateNextDueDate(task);
    }

    /**
     * Check if a task is due soon (within 24 hours)
     *
     * @param task The task entity
     * @return true if due within 24 hours
     */
    public static boolean isDueSoon(TaskEntity task) {
        if (task.completed || task.dueAt == 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        long timeUntilDue = task.dueAt - now;

        return timeUntilDue > 0 && timeUntilDue <= 86400000; // 24 hours
    }

    /**
     * Get time until task is due (in hours)
     *
     * @param task The task entity
     * @return Hours until due, or -1 if overdue or not applicable
     */
    public static int getHoursUntilDue(TaskEntity task) {
        if (task.completed || task.dueAt == 0) {
            return -1;
        }

        long now = System.currentTimeMillis();
        long timeUntilDue = task.dueAt - now;

        if (timeUntilDue < 0) {
            return -1; // Overdue
        }

        return (int) (timeUntilDue / (60 * 60 * 1000));
    }

    // Helper methods

    private static String getUnitLabel(String unit, boolean plural) {
        switch (unit) {
            case "day":
                return plural ? "Tage" : "Tag";
            case "week":
                return plural ? "Wochen" : "Woche";
            case "month":
                return plural ? "Monate" : "Monat";
            default:
                return unit;
        }
    }

    private static String formatTime(int hour) {
        return String.format("%02d:00", hour);
    }
}
