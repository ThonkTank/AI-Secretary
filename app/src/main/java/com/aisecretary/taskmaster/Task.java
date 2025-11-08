package com.aisecretary.taskmaster;

import java.util.Date;

/**
 * Task - Data model for a single task
 *
 * Represents a task in the Taskmaster system with all tracking data.
 * Based on specifications in CLAUDE.md
 */
public class Task {

    // Basic properties
    private long id;
    private String title;
    private String description;
    private int priority;
    private boolean completed;

    // Tracking properties (to be implemented)
    private Date createdAt;
    private Date completedAt;
    private Date dueAt;
    private int completionCount;

    // Recurrence properties (to be implemented)
    private boolean isRecurring;
    private String recurrenceType; // "once", "x_per_y", "every_x_y", "scheduled"
    private int recurrenceX;
    private String recurrenceY; // "day", "week", "month"

    // Performance tracking (to be implemented)
    private long averageCompletionTime; // in milliseconds
    private float averageDifficulty;
    private int streak;

    /**
     * Constructor for basic task
     */
    public Task(long id, String title, String description, int priority, boolean completed) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.completed = completed;
        this.createdAt = new Date();
        this.completionCount = 0;
        this.isRecurring = false;
        this.streak = 0;
    }

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        if (completed) {
            this.completedAt = new Date();
            this.completionCount++;
        }
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Date completedAt) {
        this.completedAt = completedAt;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public int getCompletionCount() {
        return completionCount;
    }

    public void setCompletionCount(int completionCount) {
        this.completionCount = completionCount;
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
    }

    public String getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(String recurrenceType) {
        this.recurrenceType = recurrenceType;
    }

    public int getRecurrenceX() {
        return recurrenceX;
    }

    public void setRecurrenceX(int recurrenceX) {
        this.recurrenceX = recurrenceX;
    }

    public String getRecurrenceY() {
        return recurrenceY;
    }

    public void setRecurrenceY(String recurrenceY) {
        this.recurrenceY = recurrenceY;
    }

    public long getAverageCompletionTime() {
        return averageCompletionTime;
    }

    public void setAverageCompletionTime(long averageCompletionTime) {
        this.averageCompletionTime = averageCompletionTime;
    }

    public float getAverageDifficulty() {
        return averageDifficulty;
    }

    public void setAverageDifficulty(float averageDifficulty) {
        this.averageDifficulty = averageDifficulty;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    /**
     * Check if task is overdue
     */
    public boolean isOverdue() {
        if (completed || dueAt == null) {
            return false;
        }
        return new Date().after(dueAt);
    }

    /**
     * Get overdue duration in milliseconds
     */
    public long getOverdueDuration() {
        if (!isOverdue()) {
            return 0;
        }
        return new Date().getTime() - dueAt.getTime();
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", priority=" + priority +
                ", completed=" + completed +
                '}';
    }
}
