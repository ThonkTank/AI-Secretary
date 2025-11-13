package com.secretary.helloworld.features.tasks.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Entity for tasks table.
 * Phase 4.5.3: Data Layer - Room Migration
 *
 * Maps to existing SQLite schema (17 columns).
 * Migrates from TaskDatabaseHelper v4 to Room v5.
 */
@Entity(tableName = "tasks")
public class TaskEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "category")
    private String category;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "due_date")
    private long dueDate;

    @ColumnInfo(name = "is_completed")
    private int isCompleted; // SQLite stores boolean as 0/1

    @ColumnInfo(name = "priority")
    private int priority; // 0=Low, 1=Medium, 2=High, 3=Urgent

    // Recurrence fields
    @ColumnInfo(name = "recurrence_type")
    private int recurrenceType; // 0=None, 1=Interval, 2=Frequency

    @ColumnInfo(name = "recurrence_amount")
    private int recurrenceAmount;

    @ColumnInfo(name = "recurrence_unit")
    private int recurrenceUnit; // 0=Day, 1=Week, 2=Month, 3=Year

    @ColumnInfo(name = "last_completed_date")
    private long lastCompletedDate;

    @ColumnInfo(name = "completions_this_period")
    private int completionsThisPeriod;

    @ColumnInfo(name = "current_period_start")
    private long currentPeriodStart;

    // Streak tracking fields
    @ColumnInfo(name = "current_streak")
    private int currentStreak;

    @ColumnInfo(name = "longest_streak")
    private int longestStreak;

    @ColumnInfo(name = "last_streak_date")
    private long lastStreakDate;

    // Constructors
    public TaskEntity() {
    }

    // Getters and Setters (required by Room)
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public int getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(int isCompleted) {
        this.isCompleted = isCompleted;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

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

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }

    public long getLastStreakDate() {
        return lastStreakDate;
    }

    public void setLastStreakDate(long lastStreakDate) {
        this.lastStreakDate = lastStreakDate;
    }
}
