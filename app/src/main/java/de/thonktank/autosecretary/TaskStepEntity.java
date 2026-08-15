package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_steps", foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id", childColumns = "taskId", onDelete = ForeignKey.CASCADE), indices = @Index("taskId"))
public class TaskStepEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    public int position;
    @NonNull public String text;
    public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position, @NonNull String text) { this.id = id; this.taskId = taskId; this.position = position; this.text = text; }
}
