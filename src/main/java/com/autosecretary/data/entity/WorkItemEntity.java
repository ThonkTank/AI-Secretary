package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "work_items")
public final class WorkItemEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String kind = "TASK";
    @NonNull public String title = "";
    public int durationMinutes;
    public String deadlineAt;
    public String timePreference;
    public boolean flexible;
    @NonNull public String createdAt = "";
    public boolean completed;
    public int cadenceDays;
    public String nextDueDate;
    public int currentStreak;
    public int bestStreak;
    public int totalCompletions;
    public long revision;
}
