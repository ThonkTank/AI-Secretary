package database.task;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;

@Entity (tableName = "task_follow_ups"
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskFollowUp {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long taskId;
    public long followUp;
    public int amount;
}