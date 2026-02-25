package com.autosecretary.features.task.data;

import java.util.Set;
import java.util.UUID;
import java.time.DayOfWeek;
import java.time.LocalTime;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Room entity for preferred scheduling patterns. Specifies which days of the week
 * and at what time a task prefers to be scheduled. Used by
 * {@link com.autosecretary.features.task.domain.TaskScorer TaskScorer} to compute preferred-time fit.
 */
@Entity (tableName = "task_pref_slots",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskPrefSlot {
    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String taskId;
    public Set<DayOfWeek> days;
    public LocalTime start;
}