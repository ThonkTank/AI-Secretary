package database.task;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;

@Entity (tableName = "task_follow_ups")
public class TaskFollowUp {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long taskId;
    public long followUp;
    public int amount;
}