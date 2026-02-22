package database.task;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity (tableName = "task_follow_ups",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskFollowUp {
    @PrimaryKey()
    public String id = UUID.randomUUID().toString();
    public String taskId;
    public long followUp;
    public int amount;
}