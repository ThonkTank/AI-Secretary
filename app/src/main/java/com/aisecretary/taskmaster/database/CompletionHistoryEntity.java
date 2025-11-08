package com.aisecretary.taskmaster.database;

/**
 * CompletionHistoryEntity - Database model for Task Completion History
 *
 * Tracks every single task completion for historical analysis and statistics.
 * Phase 3.2: Erledigungs-Zeit Tracking
 */
public class CompletionHistoryEntity {

    // Primary Key
    public long id;

    // Foreign Key to Task
    public long taskId;

    // Completion Timestamp
    public long completedAt; // Unix timestamp when task was completed

    // Performance Data
    public long completionTime; // How long it took (in milliseconds), 0 = not tracked
    public float difficultyRating; // User-rated difficulty (1-5), 0 = not rated

    // Time Analysis
    public int timeOfDay; // Hour when completed (0-23)

    /**
     * Constructor for new completion history entry
     */
    public CompletionHistoryEntity(long taskId, long completedAt, long completionTime, float difficultyRating) {
        this.taskId = taskId;
        this.completedAt = completedAt;
        this.completionTime = completionTime;
        this.difficultyRating = difficultyRating;

        // Extract hour from timestamp
        this.timeOfDay = extractHourFromTimestamp(completedAt);
    }

    /**
     * Default constructor (required for database operations)
     */
    public CompletionHistoryEntity() {
    }

    /**
     * Extract hour (0-23) from Unix timestamp
     */
    private int extractHourFromTimestamp(long timestamp) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return calendar.get(java.util.Calendar.HOUR_OF_DAY);
    }

    @Override
    public String toString() {
        return "CompletionHistory{" +
                "id=" + id +
                ", taskId=" + taskId +
                ", completedAt=" + completedAt +
                ", completionTime=" + completionTime +
                ", difficultyRating=" + difficultyRating +
                ", timeOfDay=" + timeOfDay +
                '}';
    }
}
