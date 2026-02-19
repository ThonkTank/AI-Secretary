package database.task;

import java.time.LocalDate;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;

@Entity (tableName = "task_blocked_days")
public class TaskBlockedDay {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long taskId;
    public LocalDate blockedDay;
}