package com.autosecretary.features.task.domain.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

import androidx.room.Ignore;

/**
 * Room entity for task dependencies. The task identified by {@code taskId} requires the
 * task identified by {@code prerequisiteId} to be scheduled or completed first.
 */
@Entity(tableName = "task_prerequisites",
    primaryKeys = {"taskId", "prerequisiteId"},
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))
public class TaskPrerequisite {
    @NonNull
    public String taskId;
    @NonNull
    public String prerequisiteId;
    public int minGapMinutes = 0;

    public TaskPrerequisite() {}

    @Ignore
    public TaskPrerequisite(String taskId, String prerequisiteId) {
        this(taskId, prerequisiteId, 0);
    }

    @Ignore
    public TaskPrerequisite(String taskId, String prerequisiteId, int minGapMinutes) {
        this.taskId = taskId;
        this.prerequisiteId = prerequisiteId;
        this.minGapMinutes = minGapMinutes;
    }
}
