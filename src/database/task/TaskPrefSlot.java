package database.task;

import java.time.LocalTime;
import java.util.UUID;
import java.time.DayOfWeek;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Index;

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
    public DayOfWeek day;
    public LocalTime start;
}