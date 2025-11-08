package com.aisecretary.taskmaster.database;

/**
 * TaskEntity - Database model for Tasks
 *
 * Simplified Room-like entity for SQLite persistence.
 * Based on Task.java model with all Taskmaster features.
 */
public class TaskEntity {

    // Primary Key
    public long id;

    // Basic Properties
    public String title;
    public String description;
    public int priority; // 1-4 (1=low, 4=critical)
    public boolean completed;

    // Timestamps
    public long createdAt; // Unix timestamp
    public long completedAt; // Unix timestamp (0 if not completed)
    public long dueAt; // Unix timestamp (0 if no due date)

    // Tracking
    public int completionCount; // How many times completed
    public long lastCompletedAt; // Last completion timestamp

    // Recurrence
    public boolean isRecurring;
    public String recurrenceType; // "once", "x_per_y", "every_x_y", "scheduled"
    public int recurrenceX; // e.g., "3" in "3 times per week"
    public String recurrenceY; // "day", "week", "month"
    public long recurrenceStartDate; // When recurrence starts
    public long recurrenceEndDate; // When recurrence ends (0 = no end)

    // Performance Tracking
    public long averageCompletionTime; // Average time in milliseconds
    public float averageDifficulty; // Average difficulty rating (1-5)

    // Streak
    public int currentStreak; // Current consecutive completion streak
    public int longestStreak; // Best streak ever
    public long streakLastUpdated; // Last time streak was updated

    // Preferred Time (for intelligent scheduling)
    public String preferredTimeOfDay; // "morning", "afternoon", "evening", null
    public int preferredHour; // 0-23 (0 = not set)

    // Chain/Dependencies
    public long chainId; // 0 = not part of a chain
    public int chainOrder; // Position in chain

    // Category (optional)
    public String category; // null or category name

    // Overdue tracking
    public long overdueSince; // Timestamp when became overdue (0 = not overdue)

    /**
     * Empty constructor (required for database operations)
     */
    public TaskEntity() {
    }

    /**
     * Constructor for creating a new task
     */
    public TaskEntity(String title, String description, int priority) {
        this.id = 0; // Will be auto-generated
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.completed = false;
        this.createdAt = System.currentTimeMillis();
        this.completedAt = 0;
        this.dueAt = 0;
        this.completionCount = 0;
        this.lastCompletedAt = 0;
        this.isRecurring = false;
        this.recurrenceType = "once";
        this.recurrenceX = 0;
        this.recurrenceY = null;
        this.recurrenceStartDate = 0;
        this.recurrenceEndDate = 0;
        this.averageCompletionTime = 0;
        this.averageDifficulty = 0;
        this.currentStreak = 0;
        this.longestStreak = 0;
        this.streakLastUpdated = 0;
        this.preferredTimeOfDay = null;
        this.preferredHour = 0;
        this.chainId = 0;
        this.chainOrder = 0;
        this.category = null;
        this.overdueSince = 0;
    }

    /**
     * Check if task is overdue
     */
    public boolean isOverdue() {
        if (completed || dueAt == 0) {
            return false;
        }
        return System.currentTimeMillis() > dueAt;
    }

    /**
     * Get overdue duration in milliseconds
     */
    public long getOverdueDuration() {
        if (!isOverdue()) {
            return 0;
        }
        return System.currentTimeMillis() - dueAt;
    }

    /**
     * Check if task is due today
     */
    public boolean isDueToday() {
        if (dueAt == 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        long dayStart = now - (now % 86400000); // Start of today
        long dayEnd = dayStart + 86400000; // End of today

        return dueAt >= dayStart && dueAt < dayEnd;
    }

    /**
     * Get priority color resource name
     */
    public String getPriorityColor() {
        switch (priority) {
            case 1: return "priority1";
            case 2: return "priority2";
            case 3: return "priority3";
            case 4: return "priority4";
            default: return "priority1";
        }
    }

    /**
     * Get priority star count
     */
    public String getPriorityStars() {
        switch (priority) {
            case 1: return "⭐";
            case 2: return "⭐⭐";
            case 3: return "⭐⭐⭐";
            case 4: return "⭐⭐⭐⭐";
            default: return "⭐";
        }
    }

    @Override
    public String toString() {
        return "TaskEntity{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", priority=" + priority +
                ", completed=" + completed +
                ", streak=" + currentStreak +
                '}';
    }
}
