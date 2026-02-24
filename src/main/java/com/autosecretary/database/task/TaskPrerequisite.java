package com.autosecretary.database.task;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

import java.util.UUID;
import androidx.room.Ignore;

@Entity(tableName = "task_prerequisites",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))
public class TaskPrerequisite {
    @PrimaryKey @NonNull
    public String id = UUID.randomUUID().toString();
    public String taskId;
    public String prerequisiteId;

    public TaskPrerequisite() {}

    @Ignore
    public TaskPrerequisite(String taskId, String prerequisiteId) {
        this.taskId = taskId;
        this.prerequisiteId = prerequisiteId;
    }
}
