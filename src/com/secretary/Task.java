package com.secretary.helloworld;

/**
 * Task entity representing a single task in the Taskmaster system.
 * Phase 1: Basic task with minimal fields.
 */
public class Task {
    private long id;
    private String title;
    private String description;
    private long createdAt;
    private long dueDate;
    private boolean isCompleted;
    private int priority; // 0=Low, 1=Medium, 2=High, 3=Urgent

    // Constructors
    public Task() {
        this.createdAt = System.currentTimeMillis();
        this.isCompleted = false;
        this.priority = 1; // Default to Medium
    }

    public Task(String title, String description) {
        this();
        this.title = title;
        this.description = description;
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

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getDueDate() {
        return dueDate;
    }

    public void setDueDate(long dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // Utility methods
    public String getPriorityString() {
        switch (priority) {
            case 0: return "Low";
            case 2: return "High";
            case 3: return "Urgent";
            default: return "Medium";
        }
    }

    @Override
    public String toString() {
        return title + (isCompleted ? " ✓" : "");
    }
}