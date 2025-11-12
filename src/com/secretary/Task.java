package com.secretary.helloworld;

/**
 * Task entity representing a single task in the Taskmaster system.
 * Phase 1: Basic task with minimal fields.
 */
public class Task {
    // Recurrence Types
    public static final int RECURRENCE_NONE = 0;
    public static final int RECURRENCE_INTERVAL = 1; // "Every X Y" (e.g., every 3 days)
    public static final int RECURRENCE_FREQUENCY = 2; // "X times per Y" (e.g., 3 times per week)

    // Time Units for Recurrence
    public static final int UNIT_DAY = 0;
    public static final int UNIT_WEEK = 1;
    public static final int UNIT_MONTH = 2;
    public static final int UNIT_YEAR = 3;

    private long id;
    private String title;
    private String description;
    private long createdAt;
    private long dueDate;
    private boolean isCompleted;
    private int priority; // 0=Low, 1=Medium, 2=High, 3=Urgent

    // Recurrence fields
    private int recurrenceType = RECURRENCE_NONE;
    private int recurrenceAmount = 0; // The "X" in both patterns
    private int recurrenceUnit = UNIT_DAY; // The "Y" time unit
    private long lastCompletedDate = 0; // For tracking
    private int completionsThisPeriod = 0; // For FREQUENCY type tracking
    private long currentPeriodStart = 0; // When current period started

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

    // Recurrence Getters and Setters
    public int getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(int recurrenceType) {
        this.recurrenceType = recurrenceType;
    }

    public int getRecurrenceAmount() {
        return recurrenceAmount;
    }

    public void setRecurrenceAmount(int recurrenceAmount) {
        this.recurrenceAmount = recurrenceAmount;
    }

    public int getRecurrenceUnit() {
        return recurrenceUnit;
    }

    public void setRecurrenceUnit(int recurrenceUnit) {
        this.recurrenceUnit = recurrenceUnit;
    }

    public long getLastCompletedDate() {
        return lastCompletedDate;
    }

    public void setLastCompletedDate(long lastCompletedDate) {
        this.lastCompletedDate = lastCompletedDate;
    }

    public int getCompletionsThisPeriod() {
        return completionsThisPeriod;
    }

    public void setCompletionsThisPeriod(int completionsThisPeriod) {
        this.completionsThisPeriod = completionsThisPeriod;
    }

    public long getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(long currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
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

    public String getRecurrenceString() {
        if (recurrenceType == RECURRENCE_NONE) {
            return "No repeat";
        }

        String unitStr = getUnitString(recurrenceUnit);

        if (recurrenceType == RECURRENCE_INTERVAL) {
            // "Every X Y" format
            if (recurrenceAmount == 1) {
                return "Every " + unitStr;
            } else {
                return "Every " + recurrenceAmount + " " + unitStr + "s";
            }
        } else if (recurrenceType == RECURRENCE_FREQUENCY) {
            // "X times per Y" format
            if (recurrenceAmount == 1) {
                return "Once per " + unitStr;
            } else {
                return recurrenceAmount + " times per " + unitStr;
            }
        }

        return "Unknown recurrence";
    }

    private String getUnitString(int unit) {
        switch (unit) {
            case UNIT_DAY: return "day";
            case UNIT_WEEK: return "week";
            case UNIT_MONTH: return "month";
            case UNIT_YEAR: return "year";
            default: return "unknown";
        }
    }

    public boolean isRecurring() {
        return recurrenceType != RECURRENCE_NONE;
    }

    /**
     * Get progress string for frequency-based recurring tasks
     */
    public String getProgressString() {
        if (recurrenceType != RECURRENCE_FREQUENCY) {
            return "";
        }

        return "(" + completionsThisPeriod + "/" + recurrenceAmount + ")";
    }

    /**
     * Get next appearance info for completed interval tasks
     */
    public String getNextAppearanceString() {
        if (recurrenceType != RECURRENCE_INTERVAL || !isCompleted || dueDate <= 0) {
            return "";
        }

        long now = System.currentTimeMillis();
        long timeUntilDue = dueDate - now;

        if (timeUntilDue <= 0) {
            return " (due now)";
        }

        // Convert to readable format
        long hours = timeUntilDue / (1000 * 60 * 60);
        long days = hours / 24;

        if (days > 0) {
            return " (reappears in " + days + " day" + (days > 1 ? "s" : "") + ")";
        } else if (hours > 0) {
            return " (reappears in " + hours + " hour" + (hours > 1 ? "s" : "") + ")";
        } else {
            long minutes = timeUntilDue / (1000 * 60);
            return " (reappears in " + minutes + " minute" + (minutes > 1 ? "s" : "") + ")";
        }
    }

    /**
     * Check if this frequency task needs more completions
     */
    public boolean needsMoreCompletions() {
        if (recurrenceType != RECURRENCE_FREQUENCY) {
            return false;
        }
        return completionsThisPeriod < recurrenceAmount;
    }

    @Override
    public String toString() {
        return title + (isCompleted ? " ✓" : "");
    }
}