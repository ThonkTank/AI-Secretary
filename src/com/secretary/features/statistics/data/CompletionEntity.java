package com.secretary.helloworld.features.statistics.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.secretary.helloworld.features.tasks.data.TaskEntity;

/**
 * Room Entity for completions table.
 * Phase 4.5.3: Data Layer - Room Migration
 *
 * Stores historical completion records for task analytics and statistics.
 * Maps to existing SQLite schema (6 columns).
 */
@Entity(
    tableName = "completions",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("task_id")} // Index for foreign key performance
)
public class CompletionEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "completion_id")
    private long completionId;

    @ColumnInfo(name = "task_id")
    private long taskId;

    @ColumnInfo(name = "completed_at")
    private long completedAt;

    @ColumnInfo(name = "time_spent_minutes")
    private int timeSpentMinutes;

    @ColumnInfo(name = "difficulty")
    private int difficulty; // 0-10 scale

    @ColumnInfo(name = "notes")
    private String notes;

    // Constructors
    public CompletionEntity() {
    }

    // Getters and Setters (required by Room)
    public long getCompletionId() {
        return completionId;
    }

    public void setCompletionId(long completionId) {
        this.completionId = completionId;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public int getTimeSpentMinutes() {
        return timeSpentMinutes;
    }

    public void setTimeSpentMinutes(int timeSpentMinutes) {
        this.timeSpentMinutes = timeSpentMinutes;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
